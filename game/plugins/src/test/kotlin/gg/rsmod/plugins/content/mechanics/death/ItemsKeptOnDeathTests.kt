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
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Owner 2026-09-26: the Items Kept on Death screen shows the new rules exactly - keep 1 with Protect Item, everything else
 * to the gravestone on a PvM death, untradeables broken (<= 20) or destroyed (> 20) with the killer's coins on a PvP death.
 */
class ItemsKeptOnDeathTests {
    private val value = ItemRiskValueProvider { if (it == Items.AVERNIC_DEFENDER) 600_000L else 1L }

    @Test
    fun `PvM - protect item keeps the most valuable item, everything else goes to the gravestone`() {
        val player = newPlayer()
        player.inventory[0] = Item(Items.AVERNIC_DEFENDER, 1)
        player.inventory[1] = Item(Items.COINS_995, 100)
        val preview = ItemsKeptOnDeath.preview(player, ItemsKeptOnDeath.Toggles(true, false, false, false), value)
        assertEquals(listOf(Items.AVERNIC_DEFENDER), preview.entries.filter { it.section == ItemsKeptOnDeath.Section.KEPT }.map { it.item.id })
        assertEquals(listOf(Items.COINS_995 to 100), preview.entries.filter { it.section == ItemsKeptOnDeath.Section.GRAVESTONE }.map { it.item.id to it.item.amount })
        assertEquals(0, preview.graveFee, "the gravestone is free")
    }

    @Test
    fun `PvP - an untradeable breaks below level 20 and is destroyed above it`() {
        val player = newPlayer()
        player.inventory[0] = Item(Items.AVERNIC_DEFENDER, 1)
        player.inventory[1] = Item(Items.COINS_995, 100)
        val low = ItemsKeptOnDeath.preview(player, ItemsKeptOnDeath.Toggles(false, false, true, false), value)
        val kept = low.entries.single { it.section == ItemsKeptOnDeath.Section.KEPT }
        assertEquals(Items.AVERNIC_DEFENDER, kept.item.id)
        assertTrue("600,000 coins" in kept.message, kept.message)
        assertEquals(listOf(Items.COINS_995), low.entries.filter { it.section == ItemsKeptOnDeath.Section.LOST }.map { it.item.id })

        val deep = ItemsKeptOnDeath.preview(player, ItemsKeptOnDeath.Toggles(false, false, true, true), value)
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