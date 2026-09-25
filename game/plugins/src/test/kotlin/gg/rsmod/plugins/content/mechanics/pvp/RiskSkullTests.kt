package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.PROTECT_ITEM_ATTR
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.plugins.api.SkullIcon
import gg.rsmod.plugins.content.mechanics.death.ItemRiskValueProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Coverage for [RiskSkull] (Deadman PvP guards plan, 2026-09-16, superseding the earlier
 * provisional master-plan table): the tier thresholds match the owner's explicit
 * Bronze/Iron/Green/Blue/Red values exactly, [RiskSkull.calculateRiskedValue] reuses
 * [gg.rsmod.plugins.content.mechanics.death.DeathItemRiskCalculator] so it only counts stacks
 * that would actually be lost, and [RiskSkull.refresh] derives the head icon from the skull timer
 * plus the live risk (owner 2026-09-17: recoloured whenever the risk changes; no skull when
 * unskulled and key-less; never the plain RED frame).
 */
class RiskSkullTests {
    @Test
    fun `tierFor maps every threshold boundary to the owner's 2026-09-16 table`() {
        assertEquals(SkullIcon.NONE, RiskSkull.tierFor(0))
        assertEquals(SkullIcon.DMM_VERY_LOW_RISK, RiskSkull.tierFor(1))
        assertEquals(SkullIcon.DMM_VERY_LOW_RISK, RiskSkull.tierFor(200_000))
        assertEquals(SkullIcon.DMM_LOW_RISK, RiskSkull.tierFor(200_001))
        assertEquals(SkullIcon.DMM_LOW_RISK, RiskSkull.tierFor(800_000))
        assertEquals(SkullIcon.DMM_MEDIUM_RISK, RiskSkull.tierFor(800_001))
        assertEquals(SkullIcon.DMM_MEDIUM_RISK, RiskSkull.tierFor(2_000_000))
        assertEquals(SkullIcon.DMM_HIGH_RISK, RiskSkull.tierFor(2_000_001))
        assertEquals(SkullIcon.DMM_HIGH_RISK, RiskSkull.tierFor(8_000_000))
        assertEquals(SkullIcon.DMM_VERY_HIGH_RISK, RiskSkull.tierFor(8_000_001))
        assertEquals(SkullIcon.DMM_VERY_HIGH_RISK, RiskSkull.tierFor(50_000_000))
    }

    @Test
    fun `calculateRiskedValue sums only the item stacks that would actually be lost`() {
        val player = newPlayer()
        player.inventory[0] = Item(1, 1) // value 10 -> protected (top 3)
        player.inventory[1] = Item(2, 1) // value 5 -> protected
        player.inventory[2] = Item(3, 1) // value 20 -> protected
        player.inventory[3] = Item(4, 1) // value 1 -> the 4th stack, lost

        assertEquals(1L, RiskSkull.calculateRiskedValue(player, testValueProvider()))
    }

    @Test
    fun `calculateRiskedValue counts everything for a skulled player, matching real risk`() {
        val player = newPlayer(skulled = true)
        player.inventory[0] = Item(1, 1) // value 10
        player.inventory[1] = Item(2, 1) // value 5

        // Skulled with no active Protect Item keeps 0 stacks - both are lost.
        assertEquals(15L, RiskSkull.calculateRiskedValue(player, testValueProvider()))
    }

    @Test
    fun `refresh shows no skull at all for an unskulled player without loot keys, whatever the risk`() {
        // Owner 2026-09-17: "als een player unskulled is geeft die nu een witte skull aan, dit mag
        // niet" - value at risk alone never puts a skull above the head any more.
        val player = newPlayer(currentSkullIcon = SkullIcon.DMM_LOW_RISK.id)
        player.inventory[0] = Item(5, 1)
        player.inventory[1] = Item(5, 1)
        player.inventory[2] = Item(5, 1)
        player.inventory[3] = Item(5, 1)

        RiskSkull.refresh(player, testValueProvider())

        verify { player.skullIcon = SkullIcon.NONE.id }
        verify(exactly = 0) { player.skullIcon = SkullIcon.DMM_LOW_RISK.id }
    }

    @Test
    fun `refresh colours the key-carrier skull by the value at risk`() {
        val player = newPlayer()
        player.inventory[0] = Item(gg.rsmod.plugins.api.cfg.Items.LOOT_KEY, 1)
        player.inventory[1] = Item(5, 1)
        player.inventory[2] = Item(5, 1)
        player.inventory[3] = Item(5, 1)
        player.inventory[4] = Item(5, 1)

        RiskSkull.refresh(player, testValueProvider())

        // Audit D-10 (deliberate change): a key carrier IS skulled, so nothing is protected - all four 500k stacks
        // (plus the worthless key) are at risk -> Green tier (was Iron while key carriers kept their 3 items).
        verify { player.skullIcon = SkullIcon.DMM_MEDIUM_RISK.id }
        verify { player.lootKeyIcons = 1 }
    }

    @Test
    fun `refresh colours a PK-skulled player's skull by the value at risk and re-evaluates as it changes`() {
        // Owner 2026-09-17: "the skull above the head colour needs to be updating ... when the risk
        // changes of a player it needs to recalculate and change colors depending on risk".
        val player = newPlayer(skulled = true)
        player.inventory[0] = Item(5, 1) // 500k, nothing protected while skulled -> Iron

        RiskSkull.refresh(player, testValueProvider())
        verify { player.skullIcon = SkullIcon.DMM_LOW_RISK.id }
        verify(exactly = 0) { player.skullIcon = SkullIcon.RED.id }

        every { player.skullIcon } returns SkullIcon.DMM_LOW_RISK.id
        player.inventory[1] = Item(5, 1)
        player.inventory[2] = Item(5, 1)
        player.inventory[3] = Item(5, 1) // 2M at risk -> Green

        RiskSkull.refresh(player, testValueProvider())
        verify { player.skullIcon = SkullIcon.DMM_MEDIUM_RISK.id }
    }

    @Test
    fun `a PK-skulled player with nothing at risk still shows the bronze skull, never RED and never none`() {
        val player = newPlayer(skulled = true)

        RiskSkull.refresh(player, testValueProvider())

        verify { player.skullIcon = SkullIcon.DMM_VERY_LOW_RISK.id }
        verify(exactly = 0) { player.skullIcon = SkullIcon.RED.id }
        verify(exactly = 0) { player.skullIcon = SkullIcon.NONE.id }
    }

    @Test
    fun `iconFor is NONE for an unskulled key-less player and a tier for a skulled one`() {
        assertEquals(SkullIcon.NONE, RiskSkull.iconFor(newPlayer(), testValueProvider()))
        assertEquals(SkullIcon.DMM_VERY_LOW_RISK, RiskSkull.iconFor(newPlayer(skulled = true), testValueProvider()))
    }

    @Test
    fun `refresh shows at least a bronze skull plus the key count for a player carrying loot keys`() {
        val player = newPlayer()
        player.inventory[0] = Item(gg.rsmod.plugins.api.cfg.Items.LOOT_KEY, 1)
        player.inventory[1] = Item(gg.rsmod.plugins.api.cfg.Items.LOOT_KEY_23697, 1)

        RiskSkull.refresh(player, testValueProvider())

        verify { player.skullIcon = SkullIcon.DMM_VERY_LOW_RISK.id }
        verify { player.lootKeyIcons = 2 }
    }

    @Test
    fun `refresh clears the risk skull when nothing is at risk`() {
        // Start from a stale risk-tier icon so clearing it back to NONE is an observable change,
        // rather than the already-NONE default (which correctly makes refresh a no-op).
        val player = newPlayer(currentSkullIcon = SkullIcon.DMM_LOW_RISK.id)

        RiskSkull.refresh(player, testValueProvider())

        verify { player.skullIcon = SkullIcon.NONE.id }
    }

    private fun newPlayer(
        protectItem: Boolean = false,
        currentSkullIcon: Int = SkullIcon.NONE.id,
        skulled: Boolean = false,
    ): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.skullIcon } returns currentSkullIcon
        // The skull state is the running PK skull timer (PvpSkull.isSkulled), never an icon id.
        every { player.timers } returns TimerMap().also { if (skulled) it[SKULL_ICON_DURATION_TIMER] = PvpSkull.SKULL_DURATION_CYCLES }
        every { player.inventory } returns ItemContainer(DEFINITIONS, INVENTORY_KEY)
        every { player.equipment } returns ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        val attr = AttributeMap()
        if (protectItem) attr[PROTECT_ITEM_ATTR] = true
        every { player.attr } returns attr
        return player
    }

    /** Fixture value provider: ids used in these tests map 1:1 to the value given here. */
    private fun testValueProvider(): ItemRiskValueProvider =
        ItemRiskValueProvider { id ->
            when (id) {
                1 -> 10L
                2 -> 5L
                3 -> 20L
                4 -> 1L
                5 -> 500_000L
                else -> 0L
            }
        }

    companion object {
        private val DEFINITIONS = DefinitionSet()
    }
}
