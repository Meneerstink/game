package gg.rsmod.plugins.content.mechanics.death

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.BANK_KEY
import gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.container.key.GRAVESTONE_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.plugins.api.cfg.Items
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Owner 2026-09-26: the Items Kept on Death screen shows the new rules exactly - keep 1 with Protect Item, everything else
 * to the gravestone on a PvM death, untradeables broken (<= 20) or destroyed (> 20) with the killer's coins on a PvP death.
 */
class ItemsKeptOnDeathTests {
    @Test
    fun `N241 - Check prints the line of the item in that slot, slots run section by section`() {
        val item = { id: Int -> gg.rsmod.game.model.item.Item(id) }
        val entries =
            listOf(
                ItemsKeptOnDeath.Entry(ItemsKeptOnDeath.Section.LOST, item(1), "lost 1"),
                ItemsKeptOnDeath.Entry(ItemsKeptOnDeath.Section.KEPT, item(2), "kept 2"),
                ItemsKeptOnDeath.Entry(ItemsKeptOnDeath.Section.DELETED, item(3), "deleted 3"),
                ItemsKeptOnDeath.Entry(ItemsKeptOnDeath.Section.KEPT, item(4), "kept 4"),
                ItemsKeptOnDeath.Entry(ItemsKeptOnDeath.Section.GRAVESTONE, item(5), "grave 5"),
            )
        assertEquals(listOf("kept 2", "kept 4", "grave 5", "lost 1", "deleted 3"), ItemsKeptOnDeath.slotOrder(entries).map { it.message })
        val source = java.io.File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/death/ItemsKeptOnDeath.kt").readText()
        assertTrue("val shown = slotOrder(preview.entries)" in source, "the slots and Check read the same order")
    }

    @Test
    fun `N241 - Wilderness beyond level 20 only exists for a player kill, so the two toggles move together`() {
        val off = ItemsKeptOnDeath.Toggles(skulled = true, killedByPlayer = false, deepWilderness = false)
        val deep = ItemsKeptOnDeath.toggled(off, 3)
        assertTrue(deep.deepWilderness && deep.killedByPlayer, "ticking level 20 unticks PvM death")
        val pvm = ItemsKeptOnDeath.toggled(deep, 2)
        assertTrue(!pvm.killedByPlayer && !pvm.deepWilderness, "ticking PvM death unticks level 20")
        val shallow = ItemsKeptOnDeath.toggled(deep, 3)
        assertTrue(shallow.killedByPlayer && !shallow.deepWilderness, "level 20 off keeps the player kill")
        // N503 D1: the hidden Protect Item toggle changes nothing; D2: the skull toggle flips the skull.
        assertEquals(off, ItemsKeptOnDeath.toggled(off, 0))
        assertEquals(off.copy(skulled = false), ItemsKeptOnDeath.toggled(off, 1))
    }

    private val value = ItemRiskValueProvider { if (it == Items.AVERNIC_DEFENDER) 600_000L else 1L }

    @Test fun `only actual locked states survive in addition to the three ranked PvM items`() {
        gg.rsmod.plugins.content.mechanics.trouver.TrouverLockables.registerAll()
        val p = newPlayer()
        listOf(Items.DRAGON_PICKAXE, Items.DRAGON_2H_SWORD, Items.RUNE_PLATEBODY, Items.FIRE_CAPE,
            Items.FIRE_CAPE_LOCKED_22324, Items.FIRE_CAPE_L_BROKEN, Items.FIRE_CAPE_L_MANGLED).forEachIndexed { i, id -> p.inventory[i] = Item(id) }
        val rank = ItemRiskValueProvider { if (it == Items.FIRE_CAPE) 1L else 100L }
        for (skulled in listOf(false, true)) {
            val resolved = DeathResolver.resolve(p, null, skulled, rank, contextOverride = DeathContext.PVM_SAFE)
            assertTrue(resolved.itemRisk.lost.any { it.item.id == Items.FIRE_CAPE })
            val locked = setOf(Items.FIRE_CAPE_LOCKED_22324, Items.FIRE_CAPE_L_BROKEN, Items.FIRE_CAPE_L_MANGLED)
            assertTrue(locked.all { id -> resolved.itemRisk.protected.any { it.item.id == id } })
            assertEquals(if (skulled) 3 else 6, resolved.itemRisk.protected.size)
        }
    }

    @Test fun `Wilderness consumables are deleted in preview and actual PvM death but retained by normal rules outside`() {
        val p = newPlayer()
        val consumables = listOf(Items.SHARK, Items.SUPER_RESTORE_4, Items.PHOENIX_NECKLACE)
        every { p.tile } returns Tile(3424,10212,2)
        consumables.forEachIndexed { i, id -> p.inventory[i] = Item(id) }
        val preview = ItemsKeptOnDeath.preview(p, ItemsKeptOnDeath.Toggles(false, false, false), value)
        assertEquals(consumables.toSet(), preview.entries.filter { it.section == ItemsKeptOnDeath.Section.DELETED }.map { it.item.id }.toSet())
        val resolved = DeathResolver.resolve(p, null, false, value, contextOverride = DeathContext.PVM_SAFE)
        assertTrue(DeathExecutor.execute(p.world, resolved))
        assertTrue(p.inventory.isEmpty)
        assertTrue(p.gravestone.isEmpty)
        assertTrue(p.deathRecovery.isEmpty)
        every { p.tile } returns Tile(3094,3469)
        consumables.forEach { assertFalse(DeathRules.destroyInWilderness(p, it)) }
    }

    @Test
    fun `N503 D2 PvM - unskulled keeps the 3 most valuable items, everything else goes to the gravestone`() {
        val player = newPlayer()
        player.inventory[0] = Item(Items.AVERNIC_DEFENDER, 1)
        player.inventory[1] = Item(Items.COINS_995, 100)
        val preview = ItemsKeptOnDeath.preview(player, ItemsKeptOnDeath.Toggles(skulled = false, killedByPlayer = false, deepWilderness = false), value)
        // N593 owner 2026-11-05: an unlocked defender occupies one of the three ranked slots.
        assertEquals(listOf(Items.AVERNIC_DEFENDER to 1, Items.COINS_995 to 2), preview.entries.filter { it.section == ItemsKeptOnDeath.Section.KEPT }.map { it.item.id to it.item.amount })
        assertEquals(listOf(Items.COINS_995 to 98), preview.entries.filter { it.section == ItemsKeptOnDeath.Section.GRAVESTONE }.map { it.item.id to it.item.amount })
        assertEquals(0, preview.graveFee, "the gravestone is free")
        val skulled = ItemsKeptOnDeath.preview(player, ItemsKeptOnDeath.Toggles(skulled = true, killedByPlayer = false, deepWilderness = false), value)
        assertEquals(setOf(Items.AVERNIC_DEFENDER to 1, Items.COINS_995 to 100), skulled.entries.filter { it.section == ItemsKeptOnDeath.Section.GRAVESTONE }.map { it.item.id to it.item.amount }.toSet(), "skulled keeps no unlocked item")
    }

    @Test
    fun `PvP - an untradeable breaks below level 20 and is destroyed above it`() {
        val player = newPlayer()
        player.inventory[0] = Item(Items.AVERNIC_DEFENDER, 1)
        player.inventory[1] = Item(Items.COINS_995, 100)
        val low = ItemsKeptOnDeath.preview(player, ItemsKeptOnDeath.Toggles(skulled = false, killedByPlayer = true, deepWilderness = false), value)
        val kept = low.entries.single { it.section == ItemsKeptOnDeath.Section.KEPT }
        assertEquals(Items.AVERNIC_DEFENDER, kept.item.id)
        assertTrue("600,000 coins" in kept.message, kept.message)
        assertEquals(listOf(Items.COINS_995), low.entries.filter { it.section == ItemsKeptOnDeath.Section.LOST }.map { it.item.id })

        val deep = ItemsKeptOnDeath.preview(player, ItemsKeptOnDeath.Toggles(skulled = false, killedByPlayer = true, deepWilderness = true), value)
        assertEquals(listOf(Items.AVERNIC_DEFENDER), deep.entries.filter { it.section == ItemsKeptOnDeath.Section.DELETED }.map { it.item.id })
        assertTrue(deep.entries.none { it.section == ItemsKeptOnDeath.Section.KEPT })
        assertEquals(600_100L, deep.riskValue, "the killer's coins count as risk")
    }

    private fun newPlayer(): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        every { player.timers } returns TimerMap()
        every { player.tile } returns Tile(3094, 3469, 0)
        every { player.inventory } returns ItemContainer(DEFINITIONS, INVENTORY_KEY)
        every { player.equipment } returns ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        every { player.bank } returns ItemContainer(DEFINITIONS, BANK_KEY)
        every { player.deathRecovery } returns ItemContainer(DEFINITIONS, DEATH_RECOVERY_KEY)
        every { player.gravestone } returns ItemContainer(DEFINITIONS, GRAVESTONE_KEY)
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns DEFINITIONS
        every { player.world } returns world
        return player
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            DEFINITIONS.loadAll(CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString()))
            assertNotEquals(0, DEFINITIONS.getCount(ItemDef::class.java))
        }
    }
}
