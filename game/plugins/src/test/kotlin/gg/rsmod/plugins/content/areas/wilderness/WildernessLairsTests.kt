package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.getWildernessLevel
import gg.rsmod.plugins.content.areas.wilderness.bosses.WildernessLairs
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * N522 bundle 4a: the OSRS Wilderness boss lair fee - 50,000 coins (inventory, then bank), 10,000 less per top-damage boss kill, lost
 * (and dropped for the killer) only to a player kill before leaving the Wilderness since the last lair / Escape Caves visit.
 */
class WildernessLairsTests {
    private fun player(inventory: Int, bank: Int, at: Tile = Tile(3321, 3790)): Player {
        val coinDef = ItemDef(Items.COINS_995).apply { name = "Coins"; stacks = true }
        val definitions = mockk<DefinitionSet>(relaxed = true)
        every { definitions.get(ItemDef::class.java, any()) } returns coinDef
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns definitions
        val player = mockk<Player>(relaxed = true)
        every { player.world } returns world
        every { player.attr } returns AttributeMap()
        every { player.tile } returns at
        every { player.inventory } returns ItemContainer(definitions, 28, ContainerStackType.STACK)
        every { player.bank } returns ItemContainer(definitions, 800, ContainerStackType.STACK)
        if (inventory > 0) player.inventory.add(Items.COINS_995, inventory)
        if (bank > 0) player.bank.add(Items.COINS_995, bank)
        return player
    }

    @Test
    fun `the 50,000 fee comes from the inventory then the bank, and each top-damage boss kill takes 10,000 off the next fee`() {
        val p = player(inventory = 20_000, bank = 100_000)
        assertEquals(50_000, WildernessLairs.fee(p))
        assertTrue(WildernessLairs.pay(p))
        assertEquals(0, p.inventory.getItemCount(Items.COINS_995))
        assertEquals(70_000, p.bank.getItemCount(Items.COINS_995))
        assertTrue(WildernessLairs.paid(p))

        repeat(2) { WildernessLairs.onTopDamageKill(p) }
        assertEquals(30_000, WildernessLairs.fee(p))
        repeat(9) { WildernessLairs.onTopDamageKill(p) }
        assertEquals(0, WildernessLairs.fee(p), "never below nothing")

        val short = player(inventory = 10_000, bank = 39_999)
        assertFalse(WildernessLairs.pay(short))
        assertEquals(10_000, short.inventory.getItemCount(Items.COINS_995))
        assertFalse(WildernessLairs.paid(short))
    }

    @Test
    fun `a player kill before leaving the Wilderness drops the paid fee for the killer, once - a monster death costs nothing`() {
        val p = player(0, 50_000)
        WildernessLairs.pay(p)
        p.attr[WildernessLairs.SINCE_VISIT] = true
        assertEquals(emptyList(), WildernessLairs.onDeath(p, null), "a monster death keeps the fee")
        assertTrue(WildernessLairs.paid(p))
        val loot = WildernessLairs.onDeath(p, player(0, 0))
        assertEquals(listOf(Items.COINS_995 to 50_000), loot.map { it.id to it.amount })
        assertFalse(WildernessLairs.paid(p))
        assertEquals(emptyList(), WildernessLairs.onDeath(p, player(0, 0)))
    }

    @Test
    fun `leaving the Wilderness ends the visit, so a later player kill keeps the fee`() {
        val p = player(0, 50_000, at = Tile(3222, 3218)) // Lumbridge
        WildernessLairs.pay(p)
        p.attr[WildernessLairs.SINCE_VISIT] = true
        WildernessLairs.tick(p)
        assertFalse(p.attr[WildernessLairs.SINCE_VISIT] == true)
        assertEquals(emptyList(), WildernessLairs.onDeath(p, player(0, 0)))
        assertTrue(WildernessLairs.paid(p))

        val inWild = player(0, 50_000, at = Tile(3321, 3790))
        inWild.attr[WildernessLairs.SINCE_VISIT] = true
        WildernessLairs.tick(inWild)
        assertTrue(inWild.attr[WildernessLairs.SINCE_VISIT] == true, "still in the Wilderness")
    }

    @Test
    fun `the Escape Caves routes pair each surface cave with its arrival and its exit, and lair exits share a point within 10 seconds`() {
        assertEquals(4, WildernessLairs.ESCAPE_ROUTES.size)
        WildernessLairs.ESCAPE_ROUTES.forEach { r ->
            assertTrue(WildernessLairs.inEscapeCaves(r.arrival), "${r.arrival}")
            assertTrue(WildernessLairs.inEscapeCaves(r.exitTile), "${r.exitTile}")
            assertTrue(kotlin.math.abs(r.arrival.x - r.exitTile.x) <= 2 && kotlin.math.abs(r.arrival.z - r.exitTile.z) <= 2, "arrival beside its exit")
            assertEquals(r, WildernessLairs.surfaceRoute(r.surface))
            assertEquals(r, WildernessLairs.exitRoute(r.exitTile))
        }
        WildernessLairs.LAIR_EXIT_POINTS.forEach { assertTrue(WildernessLairs.inEscapeCaves(it), "$it") }
        val silk = WildernessLairs.SILK_CHASM_EXIT
        val first = WildernessLairs.lairExitPoint(silk, 1000) { 1 }
        assertEquals(first, WildernessLairs.lairExitPoint(silk, 1010) { 2 }, "within 17 ticks: the same point")
        assertEquals(WildernessLairs.LAIR_EXIT_POINTS[0], WildernessLairs.lairExitPoint(-1, 1011) { 0 }, "another lair rolls its own point")
        assertEquals(WildernessLairs.LAIR_EXIT_POINTS[2], WildernessLairs.lairExitPoint(silk, 1030) { 2 })
    }

    @Test
    fun `the four new lairs - each exit tile names one lair, big lairs lead to the Escape Caves, levels follow the OSRS Wiki`() {
        assertEquals(4, WildernessLairs.LAIRS.size)
        WildernessLairs.LAIRS.forEach { lair ->
            assertEquals(lair, WildernessLairs.lairByExit(lair.exitLoc, lair.exitTile), lair.name)
            assertEquals(lair, WildernessLairs.lairByEntrance(lair.entrance), lair.name)
            assertTrue(lair.contains(lair.arrival), "${lair.name} arrival inside")
            assertTrue(lair.contains(lair.exitTile), "${lair.name} exit inside")
        }
        assertEquals(listOf("Callisto's Den", "Vet'ion's Rest"), WildernessLairs.LAIRS.filter { it.surfaceExit == null }.map { it.name })
        val levels = WildernessLairs.LAIRS.associate { it.name to it.arrival.getWildernessLevel() }
        assertEquals(mapOf("Callisto's Den" to 40, "Vet'ion's Rest" to 35, "Hunter's End" to 21, "Skeletal Tomb" to 21), levels)
        assertEquals(35, gg.rsmod.game.model.Tile(3424, 10212, 2).getWildernessLevel(), "Silk Chasm")
        assertTrue(gg.rsmod.plugins.content.areas.wilderness.bosses.OldWildernessBosses.CALLISTO_SPAWN.let { WildernessLairs.LAIRS[0].contains(it) })
        assertTrue(gg.rsmod.plugins.content.areas.wilderness.bosses.OldWildernessBosses.VETION_SPAWN.let { WildernessLairs.LAIRS[1].contains(it) })
    }

    @Test
    fun `the Escape Caves are open - the four surface caves are placed and the camera fix is in the client`() {
        assertTrue(WildernessLairs.ESCAPE_CAVES_OPEN)
        assertEquals(4, WildernessLairs.SURFACE.count { it.id == WildernessLairs.ESCAPE_CAVE_SURFACE })
    }

    @Test
    fun `while the Escape Caves are closed only the Silk Chasm entrance is placed and its exit leads to its approach side`() {
        if (WildernessLairs.ESCAPE_CAVES_OPEN) return
        assertEquals(listOf(WildernessLairs.SILK_CHASM_ENTRANCE, WildernessLairs.WEB_CHASM_ENTRANCE), WildernessLairs.SURFACE.map { it.id })
        val exit = WildernessLairs.SILK_CHASM_SURFACE_EXIT
        assertTrue(kotlin.math.abs(exit.x - 3321) <= 2 && kotlin.math.abs(exit.z - 3794) <= 2, "beside the entrance")
    }

    @Test fun `logging out in a big lair leaves at an Escape Caves lair exit, small lairs and the surface stay`() {
        val den = WildernessLairs.LAIRS.single { it.name == "Callisto's Den" }
        val rest = WildernessLairs.LAIRS.single { it.name == "Vet'ion's Rest" }
        val hunters = WildernessLairs.LAIRS.single { it.name == "Hunter's End" }
        assertEquals(WildernessLairs.LAIR_EXIT_POINTS[1], WildernessLairs.logoutExit(den.arrival, 1000) { 1 })
        assertEquals(WildernessLairs.LAIR_EXIT_POINTS[2], WildernessLairs.logoutExit(rest.arrival, 2000) { 2 })
        assertEquals(WildernessLairs.LAIR_EXIT_POINTS[0], WildernessLairs.logoutExit(gg.rsmod.plugins.content.areas.wilderness.bosses.VenenatisCombat.VENENATIS_ARENA.spawn, 3000) { 0 })
        kotlin.test.assertNull(WildernessLairs.logoutExit(hunters.arrival, 4000) { 0 }, "Hunter's End leaves to the surface")
        kotlin.test.assertNull(WildernessLairs.logoutExit(Tile(3164, 3494), 5000) { 0 })
        // Within 10 seconds of the previous player out of the same lair: the same point.
        assertEquals(WildernessLairs.LAIR_EXIT_POINTS[1], WildernessLairs.logoutExit(den.arrival, 1010) { 2 })
    }
}
