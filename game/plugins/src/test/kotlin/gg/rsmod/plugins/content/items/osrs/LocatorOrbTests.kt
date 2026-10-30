package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.Server
import gg.rsmod.game.message.impl.ResumePauseButtonMessage
import gg.rsmod.game.model.queue.impl.PawnQueueTaskSet
import kotlinx.coroutines.Dispatchers
import gg.rsmod.game.model.Hit
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.ContainerKey
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.game.plugin.Plugin
import gg.rsmod.game.plugin.PluginRepository
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.content.mechanics.death.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.net.URLClassLoader
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocatorOrbTests {
    private val scriptLoaders = mutableListOf<URLClassLoader>()

    @AfterTest
    fun closeScriptLoaders() {
        scriptLoaders.forEach { it.close() }
        scriptLoaders.clear()
    }

    private class Fixture {
        val definitions = mockk<DefinitionSet>()
        val defs = mutableMapOf<Int, ItemDef>()
        val world = mockk<World>(relaxed = true)
        val player = mockk<Player>(relaxed = true)
        val attrs = AttributeMap()
        val skills = mockk<SkillSet>(relaxed = true)
        val inventory = ItemContainer(definitions, INVENTORY_KEY)
        val equipment = ItemContainer(definitions, 14, ContainerStackType.NORMAL)
        val containers = hashMapOf<ContainerKey, ItemContainer>(INVENTORY_KEY to inventory)
        var hp = 99
        var level = 62
        val pending = mutableListOf<Hit>()
        val queues = PawnQueueTaskSet()

        init {
            every { definitions.get(ItemDef::class.java, any()) } answers { def(secondArg()) }
            every { definitions.getNullable(ItemDef::class.java, any()) } answers { defs[secondArg<Int>()] }
            every { world.definitions } returns definitions
            every { player.world } returns world
            every { player.inventory } returns inventory
            every { player.equipment } returns equipment
            every { player.containers } returns containers
            every { player.attr } returns attrs
            every { player.skills } returns skills
            every { skills.getCurrentLevel(Skills.CRAFTING) } answers { level }
            every { player.getCurrentLifepoints() } answers { hp }
            every { player.isDead() } answers { hp <= 0 }
            every { player.addHit(any()) } answers { pending += firstArg<Hit>() }
            every { player.queue(any(), any(), any()) } answers {
                queues.queue(player, Dispatchers.Unconfined, firstArg(), thirdArg(), persistent = secondArg())
            }
            def(LocatorOrb.INERT).apply { name = "Inert locator orb"; inventoryMenu[4] = "Destroy" }
            def(LocatorOrb.ACTIVE).apply { name = "Locator orb"; inventoryMenu[0] = "Feel"; inventoryMenu[4] = "Destroy" }
        }

        fun def(id: Int): ItemDef = defs.getOrPut(id) { ItemDef(id).apply { cost = 2; tradeable = false } }

        fun materials() {
            inventory[0] = Item(Items.GLASSBLOWING_PIPE)
            inventory[1] = Item(Items.DRAGONSTONE)
            inventory[2] = Item(Items.MOLTEN_GLASS)
            inventory[3] = Item(Items.MOLTEN_GLASS)
        }

        fun store(name: String, item: Int) {
            val key = ContainerKey(name = name, capacity = 28, stackType = ContainerStackType.NORMAL)
            containers[key] = ItemContainer(definitions, key).also { it[0] = Item(item) }
        }

        // Exercise the actual Hit cancellation callback at the same point Pawn.hitsCycle invokes it.
        fun resolve() {
            val field = Hit::class.java.getDeclaredField("cancelCondition").apply { isAccessible = true }
            pending.forEach { hit ->
                @Suppress("UNCHECKED_CAST")
                val cancelled = (field.get(hit) as () -> Boolean).invoke()
                if (!cancelled) hp -= hit.hitmarks.sumOf { it.damage }
            }
            pending.clear()
        }
    }

    private fun scripts(f: Fixture): PluginRepository {
        val repo = PluginRepository(f.world)
        every { f.world.plugins } returns repo
        every { f.definitions.get(NpcDef::class.java, Npcs.AVA) } returns NpcDef(Npcs.AVA).apply {
            options[0] = "Talk-to"
            options[1] = "Buy device"
        }
        every { f.player.executePlugin<Unit>(any()) } answers { firstArg<Plugin.() -> Unit>().invoke(Plugin(f.player)) }
        // Suspended dialogues load their inner classes later, so retain the loader until this test completes.
        val loader = URLClassLoader(arrayOf(File("scripts/build/classes/kotlin/main").toURI().toURL()), javaClass.classLoader)
        scriptLoaders += loader
        for (script in listOf("items.osrs.Locator_orb_plugin", "areas.draynor.Ava_plugin")) {
                loader.loadClass("gg.rsmod.plugins.content.$script")
                    .getConstructor(PluginRepository::class.java, World::class.java, Server::class.java)
                    .newInstance(repo, f.world, mockk<Server>(relaxed = true))
        }
        return repo
    }

    @Test
    fun `compiled Feel confirmation cancels safely, confirms once, and Ava Talk-to bypasses device requirements`() {
        val f = Fixture()
        val repo = scripts(f)
        f.level = 1
        f.inventory[0] = Item(LocatorOrb.INERT)
        assertTrue(repo.executeNpc(f.player, Npcs.AVA, 1))
        f.queues.cycle()
        f.queues.submitReturnValue(ResumePauseButtonMessage(interfaceId = 230, component = 2, button = 2))
        f.queues.cycle()
        assertEquals(LocatorOrb.ACTIVE, f.inventory[0]?.id)
        assertTrue(f.attrs[LocatorOrb.ATTUNED] == true)
        verify(exactly = 0) { f.skills.getCurrentLevel(any()) }
        // Closing and No both leave HP and saved confirmation untouched.
        assertTrue(repo.executeItem(f.player, LocatorOrb.ACTIVE, 1))
        f.queues.cycle()
        f.queues.terminateTasks()
        assertTrue(f.pending.isEmpty())
        assertFalse(f.attrs[LocatorOrb.WARNING_ACCEPTED] == true)
        assertTrue(repo.executeItem(f.player, LocatorOrb.ACTIVE, 1))
        f.queues.cycle()
        f.queues.submitReturnValue(ResumePauseButtonMessage(interfaceId = 228, component = 3, button = 3))
        f.queues.cycle()
        assertTrue(f.pending.isEmpty())
        assertFalse(f.attrs[LocatorOrb.WARNING_ACCEPTED] == true)
        assertTrue(repo.executeItem(f.player, LocatorOrb.ACTIVE, 1))
        f.queues.cycle()
        f.queues.submitReturnValue(ResumePauseButtonMessage(interfaceId = 228, component = 2, button = 2))
        f.queues.cycle()
        assertTrue(f.attrs[LocatorOrb.WARNING_ACCEPTED] == true)
        assertEquals(1, f.pending.size)
        f.resolve()
        assertEquals(89, f.hp)
        assertTrue(repo.executeItem(f.player, LocatorOrb.ACTIVE, 1))
        assertEquals(0, f.queues.size, "saved confirmation skips the dialog")
        f.resolve()
        assertEquals(79, f.hp)
    }

    @Test
    fun `compiled orb and Ava scripts register and execute both crafting directions, activation and Feel`() {
        val f = Fixture()
        val repo = scripts(f)
        assertTrue(repo.hasItemOption(LocatorOrb.ACTIVE, 1), "Feel bound by its real cache option")
        LocatorOrb.IDS.forEach {
            assertTrue(repo.hasItemOption(it, 5), "Destroy bound on OpHeld5 / $it")
            assertTrue(repo.hasItemOption(it, 10), "Destroy bound on interface op 10 / $it")
        }
        assertTrue(1 in repo.boundNpcOptions(Npcs.AVA), "Talk-to")
        for ((first, second) in listOf(Items.GLASSBLOWING_PIPE to Items.DRAGONSTONE, Items.DRAGONSTONE to Items.GLASSBLOWING_PIPE)) {
            f.materials()
            assertTrue(repo.executeItemOnItem(f.player, first, second))
            assertEquals(1, f.inventory.getItemCount(LocatorOrb.INERT))
            assertTrue(repo.executeItemOnNpc(f.player, Npcs.AVA, LocatorOrb.INERT))
            assertEquals(1, f.inventory.getItemCount(LocatorOrb.ACTIVE))
            f.attrs[LocatorOrb.WARNING_ACCEPTED] = true
            assertTrue(repo.executeItem(f.player, LocatorOrb.ACTIVE, 1))
            f.resolve()
            f.inventory.remove(LocatorOrb.ACTIVE)
        }
        assertEquals(79, f.hp)
    }

    @Test
    fun `pipe and cut dragonstone consume exactly two glass and one stone, even with a full inventory`() {
        val f = Fixture()
        f.materials()
        (4..27).forEach { f.inventory[it] = Item(1000 + it) }
        assertTrue(LocatorOrb.craft(f.player))
        assertEquals(LocatorOrb.INERT, f.inventory[1]?.id)
        assertEquals(1, f.inventory.getItemCount(Items.GLASSBLOWING_PIPE))
        assertEquals(0, f.inventory.getItemCount(Items.DRAGONSTONE))
        assertEquals(0, f.inventory.getItemCount(Items.MOLTEN_GLASS))
        assertFalse(LocatorOrb.craft(f.player), "no second orb without new materials")
    }

    @Test
    fun `crafting level, missing materials, noted glass and missing cache do not consume anything`() {
        for (failure in listOf("level", "glass", "stone", "pipe", "noted", "cache")) {
            val f = Fixture()
            f.materials()
            when (failure) {
                "level" -> f.level = 61
                "glass" -> f.inventory[3] = null
                "stone" -> f.inventory[1] = null
                "pipe" -> f.inventory[0] = null
                "noted" -> { f.inventory[2] = Item(1776, 2); f.inventory[3] = null }
                "cache" -> f.defs.remove(LocatorOrb.ACTIVE)
            }
            val before = f.inventory.items.copyOf().toList()
            assertFalse(LocatorOrb.craft(f.player), failure)
            assertEquals(before, f.inventory.items.toList(), failure)
        }
    }

    @Test
    fun `Ava requires the first inert orb, attunes in its own slot without quest or shop skills, then replaces for free`() {
        val f = Fixture()
        assertFalse(LocatorOrb.replace(f.player), "no free first orb")
        f.level = 1
        f.inventory[27] = Item(LocatorOrb.INERT)
        assertTrue(LocatorOrb.activate(f.player))
        assertEquals(LocatorOrb.ACTIVE, f.inventory[27]?.id)
        assertTrue(f.attrs[LocatorOrb.ATTUNED] == true)
        assertFalse(LocatorOrb.replace(f.player), "already in inventory")
        f.inventory[27] = null
        assertTrue(LocatorOrb.replace(f.player))
        assertEquals(1, f.inventory.getItemCount(LocatorOrb.ACTIVE))
        assertEquals(0, f.inventory.getItemCount(Items.COINS_995))
        assertFalse(LocatorOrb.replace(f.player), "no repeated free duplicates")
        verify(exactly = 0) { f.skills.getCurrentLevel(any()) }
    }

    @Test
    fun `replacement searches bank, equipment, death storage, BoB and every registered container for either orb`() {
        for (location in listOf("bank", "equipment", "gravestone", "death_recovery", "bob_test", "random_event_gift")) {
            for (id in LocatorOrb.IDS) {
                val f = Fixture()
                f.attrs[LocatorOrb.ATTUNED] = true
                f.store(location, id)
                assertFalse(LocatorOrb.replace(f.player), "$location / $id")
                assertEquals(0, f.inventory.getItemCount(LocatorOrb.ACTIVE))
            }
        }
    }

    @Test
    fun `a full inventory cannot swallow a replacement or reset the saved entitlement`() {
        val f = Fixture()
        f.attrs[LocatorOrb.ATTUNED] = true
        repeat(28) { f.inventory[it] = Item(1000 + it) }
        assertFalse(LocatorOrb.replace(f.player))
        assertTrue(f.attrs[LocatorOrb.ATTUNED] == true)
        f.inventory[7] = null
        assertTrue(LocatorOrb.replace(f.player))
        assertEquals(LocatorOrb.ACTIVE, f.inventory[7]?.id)
    }

    @Test
    fun `Feel requires confirmation and possession, then ten damage down to one, including same-tick spam and combat`() {
        val f = Fixture()
        f.inventory[0] = Item(LocatorOrb.ACTIVE)
        assertFalse(LocatorOrb.feel(f.player))
        assertTrue(f.pending.isEmpty())
        f.attrs[LocatorOrb.WARNING_ACCEPTED] = true
        assertTrue(LocatorOrb.feel(f.player))
        f.resolve()
        assertEquals(89, f.hp)
        repeat(20) { assertTrue(LocatorOrb.feel(f.player)) }
        f.hp = 7 // an earlier combat hit lands before all the orb hits
        f.resolve()
        assertEquals(1, f.hp)
        assertTrue(LocatorOrb.feel(f.player))
        f.resolve()
        assertEquals(1, f.hp)
        f.inventory[0] = null
        assertFalse(LocatorOrb.feel(f.player), "cannot use a stale item option")
        f.inventory[0] = Item(LocatorOrb.ACTIVE)
        f.hp = 0
        assertFalse(LocatorOrb.feel(f.player), "dead players stay dead")
    }

    @Test
    fun `both orbs follow K01 quest items - gravestone in PvM, non-lockable lost for coins in PvP`() {
        for (id in LocatorOrb.IDS) {
            val f = Fixture()
            f.inventory[0] = Item(id)
            assertFalse(UntradeableDeathProtection.lockable(id))
            val pvm = DeathResolver.resolve(f.player, null, false, { 2L }, contextOverride = DeathContext.PVM_SAFE)
            assertEquals(listOf(id), pvm.itemRisk.lost.map { it.item.id })
            assertTrue(UntradeableDeathProtection.splitPvp(f.definitions, pvm, false).second.isEmpty())
            for (deep in listOf(false, true)) {
                val fate = UntradeableDeathProtection.fateOf(f.definitions, Item(id), deep)
                assertEquals(UntradeableFate.LOST_FOR_COINS, fate.fate)
                assertEquals(1L, fate.killerCoins)
                assertEquals(2L, UntradeableDeathProtection.reclaimPrice(f.definitions, id))
            }
        }
    }
}
