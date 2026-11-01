package gg.rsmod.plugins.content.magic.teleports

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.queue.QueueTaskSet
import gg.rsmod.plugins.content.areas.poh.PohGarden
import gg.rsmod.plugins.content.areas.poh.PohTeleports
import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.canTeleport
import gg.rsmod.plugins.content.magic.teleport
import gg.rsmod.plugins.content.mechanics.pvp.AreaState
import gg.rsmod.plugins.content.mechanics.pvp.PvpSkull
import gg.rsmod.plugins.content.mechanics.pvp.SevenSecondAction
import gg.rsmod.plugins.content.mechanics.travel.SpiritTrees
import gg.rsmod.game.model.collision.Landing
import gg.rsmod.plugins.testutil.BacklogBundleWorld
import java.io.File
import java.net.URLClassLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GrandExchangeLandingTests {
    private val world get() = BacklogBundleWorld.world
    data class Destination(val name: String, val tile: Tile, val type: TeleportType)

    @Test
    fun `real worn ring handler replaces charges with a full inventory after skull approval`() {
        BacklogBundleWorld.bind("gg.rsmod.plugins.content.items.jewellery.Ring_of_wealth_plugin")
        val rings = world.definitions.getAll(gg.rsmod.game.fs.def.ItemDef::class.java).values
            .filterIsInstance<gg.rsmod.game.fs.def.ItemDef>()
            .filter { it.name.startsWith("Ring of wealth (") && it.equipmentMenu.any { option -> option == "Grand Exchange" } }
        assertTrue(rings.isNotEmpty(), "charged rings from cache")
        rings.forEach { def ->
            val p = BacklogBundleWorld.player()
            p.tile = Tile(3100, 3530)
            repeat(p.inventory.capacity) { p.inventory[it] = gg.rsmod.game.model.item.Item(385) }
            p.equipment[12] = gg.rsmod.game.model.item.Item(def.id)
            p.attr[gg.rsmod.game.model.attr.INTERACTING_ITEM_ID] = def.id
            PvpSkull.applyTestSkull(p)
            assertTrue(world.plugins.executeEquipmentOption(p, def.id, def.equipmentMenu.indexOf("Grand Exchange") + 1))
            val queues = Pawn::class.java.getDeclaredField("queues").apply { isAccessible = true }.get(p) as QueueTaskSet
            queues.cycle()
            assertTrue(SevenSecondAction.isActive(p), "${def.id}: handler starts countdown")
            SevenSecondAction.complete(p)
            repeat(30) { queues.cycle() }
            assertEquals(Landing.resolve(world.collision, JewelleryDestinations.RING_OF_WEALTH.getValue("Grand Exchange")), p.tile)
            assertFalse(AreaState.isDangerous(p.tile))
            assertTrue(p.equipment[12]?.id != def.id, "charge consumed")
            assertEquals(p.inventory.capacity, p.inventory.occupiedSlotCount)
            queues.terminateTasks()
        }
    }

    /** Enumerate production tables, including the compiled home enum, rather than a handwritten destination list. */
    private fun destinations(): List<Destination> {
        val player = BacklogBundleWorld.player()
        val leaves = PohTeleports.leaves().filter { it.name == "Grand Exchange" || it.name == "Home" }
            .map { Destination("POH ${it.name}", it.tile(player)!!, it.type) }
        val rings = JewelleryDestinations.RING_OF_WEALTH.filterKeys { it == "Grand Exchange" }
            .map { Destination("ring ${it.key}", it.value, TeleportType.JEWELRY) }
        val trees = SpiritTrees.STATIONS.filter { it.label == "Grand Exchange" }.map { Destination("tree ${it.label}", it.tile, TeleportType.MODERN) }
        val garden = PohGarden.SPIRIT_TREE.filter { it.label == "Grand Exchange" }.map { Destination("garden ${it.label}", it.tile, TeleportType.MODERN) }
        val loader = URLClassLoader(arrayOf(File("scripts/build/classes/kotlin/main").toURI().toURL()), javaClass.classLoader)
        val homeClass = loader.loadClass("gg.rsmod.plugins.content.magic.teleports.Home_teleport_spells_plugin\$HomeTeleport")
        @Suppress("UNCHECKED_CAST")
        val homes = homeClass.enumConstants.map {
            val destination = homeClass.getMethod("getEndTile").invoke(it) as (gg.rsmod.game.model.World) -> Tile
            Destination("home $it", destination(world), TeleportType.MODERN)
        }
        // Also sweep literal GE entries anywhere in content so a new independent table cannot escape this guard.
        val literals = File("src/main/kotlin/gg/rsmod/plugins/content").walkTopDown().filter { it.isFile && it.extension in setOf("kt", "kts") }.flatMap { file ->
            val source = file.readText()
            val fixed = Regex("\"Grand Exchange\"(?: to|,)\\s*Tile\\((\\d+),\\s*(\\d+)(?:,\\s*(\\d+))?\\)")
                .findAll(source).map { m -> Destination(file.name, Tile(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].ifEmpty { "0" }.toInt()), TeleportType.MODERN) }
            val homeCalls = Regex("teleport\\((?:\\w+\\.)*gameContext\\.home").findAll(source)
                .map { Destination("${file.name} home", world.gameContext.home, TeleportType.MODERN) }
            fixed + homeCalls
        }.toList()
        assertTrue(homes.size == 3 && rings.isNotEmpty() && leaves.isNotEmpty() && trees.isNotEmpty())
        return leaves + rings + trees + garden + homes + literals
    }

    @Test
    fun `every GE destination and every possible fixed landing correction stays safe on the real map`() {
        assertTrue(AreaState.isDangerous(Tile(3164, 3460)), "reported tile must remain dangerous; fix destinations, not PvP boundaries")
        destinations().forEach { d ->
            val landed = Landing.resolve(world.collision, d.tile)
            assertFalse(AreaState.isDangerous(d.tile), "${d.name}: requested ${d.tile}")
            assertFalse(AreaState.isDangerous(landed), "${d.name}: corrected $landed")
            assertEquals(Landing.OPEN_GROUND, Landing.pocket(world.collision, landed).size, "${d.name}: can walk away")
            // Even a future map obstruction cannot make Landing.resolve choose a dangerous neighbour.
            for (dx in -Landing.SEARCH_RADIUS..Landing.SEARCH_RADIUS) for (dz in -Landing.SEARCH_RADIUS..Landing.SEARCH_RADIUS) {
                assertFalse(AreaState.isDangerous(Tile(d.tile.x + dx, d.tile.z + dz, d.tile.height)), "${d.name}: correction neighbourhood $dx,$dz")
            }
        }
    }

    @Test
    fun `all GE landings survive full inventory skull countdown and Wilderness restrictions`() {
        destinations().forEach { d ->
            val p = BacklogBundleWorld.player()
            p.tile = Tile(3100, 3530) // low Wilderness: all teleport types may leave
            repeat(p.inventory.capacity) { p.inventory[it] = gg.rsmod.game.model.item.Item(385) }
            PvpSkull.applyTestSkull(p)
            var approved = false
            p.canTeleport(d.type) { approved = true; p.teleport(d.tile, d.type) }
            assertFalse(approved, "${d.name}: skull must wait")
            assertTrue(SevenSecondAction.isActive(p))
            SevenSecondAction.complete(p)
            assertTrue(approved)
            val queues = Pawn::class.java.getDeclaredField("queues").apply { isAccessible = true }.get(p) as QueueTaskSet
            repeat(30) { queues.cycle() }
            assertFalse(AreaState.isDangerous(p.tile), "${d.name}: landed ${p.tile}")
            assertEquals(Landing.resolve(world.collision, d.tile), p.tile)
            assertEquals(p.inventory.capacity, p.inventory.occupiedSlotCount)
            p.tile = Tile(3100, 3900)
            p.canTeleport(d.type) { error("deep Wilderness must refuse ${d.name}") }
            queues.terminateTasks()
        }
    }
}
