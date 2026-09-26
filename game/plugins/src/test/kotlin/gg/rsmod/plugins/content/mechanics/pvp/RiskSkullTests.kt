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
        player.inventory[0] = Item(1, 1) // value 10
        player.inventory[1] = Item(2, 1) // value 5
        player.inventory[2] = Item(3, 1) // value 20 -> the one Protect Item would keep
        player.inventory[3] = Item(4, 1) // value 1

        // Owner 2026-09-26: without Protect Item everything is lost; with it only the single most valuable item is kept.
        assertEquals(36L, RiskSkull.calculateRiskedValue(player, testValueProvider()))
        player.attr[gg.rsmod.game.model.attr.PROTECT_ITEM_ATTR] = true
        assertEquals(16L, RiskSkull.calculateRiskedValue(player, testValueProvider()))
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
    fun `owner 2026-09-26 - an unskulled player without keys always shows the dark-eyed tier skull`() {
        val player = newPlayer()
        player.inventory[0] = Item(5, 1)
        player.inventory[1] = Item(5, 1)

        RiskSkull.refresh(player, testValueProvider())

        verify { player.skullIcon = SkullIcon.DMM_MEDIUM_RISK.id }
        verify(exactly = 0) { player.skullIcon = SkullIcon.NONE.id }
    }

    @Test
    fun `refresh colours the key-carrier skull by the value at risk, yellow-eyed`() {
        val player = newPlayer()
        player.inventory[0] = Item(gg.rsmod.plugins.api.cfg.Items.LOOT_KEY, 1)
        player.inventory[1] = Item(5, 1)
        player.inventory[2] = Item(5, 1)
        player.inventory[3] = Item(5, 1)
        player.inventory[4] = Item(5, 1)

        RiskSkull.refresh(player, testValueProvider())

        // Nothing is protected without Protect Item - all four 500k stacks are at risk -> Green, yellow-eyed (key carrier).
        verify { player.skullIcon = SkullIcon.DMM_MEDIUM_RISK_SKULLED.id }
        verify { player.lootKeyIcons = 1 }
    }

    @Test
    fun `refresh colours a PK-skulled player's yellow-eyed skull by the value at risk and re-evaluates as it changes`() {
        val player = newPlayer(skulled = true)
        player.inventory[0] = Item(5, 1) // 500k -> Iron

        RiskSkull.refresh(player, testValueProvider())
        verify { player.skullIcon = SkullIcon.DMM_LOW_RISK_SKULLED.id }
        verify(exactly = 0) { player.skullIcon = SkullIcon.RED.id }

        every { player.skullIcon } returns SkullIcon.DMM_LOW_RISK_SKULLED.id
        player.inventory[1] = Item(5, 1)
        player.inventory[2] = Item(5, 1)
        player.inventory[3] = Item(5, 1) // 2M at risk -> Green

        RiskSkull.refresh(player, testValueProvider())
        verify { player.skullIcon = SkullIcon.DMM_MEDIUM_RISK_SKULLED.id }
    }

    @Test
    fun `a PK-skulled player with nothing at risk still shows the yellow-eyed bronze skull, never RED and never none`() {
        val player = newPlayer(skulled = true)

        RiskSkull.refresh(player, testValueProvider())

        verify { player.skullIcon = SkullIcon.DMM_VERY_LOW_RISK_SKULLED.id }
        verify(exactly = 0) { player.skullIcon = SkullIcon.RED.id }
        verify(exactly = 0) { player.skullIcon = SkullIcon.NONE.id }
    }

    @Test
    fun `iconFor is the dark-eyed tier unskulled and the yellow-eyed tier skulled`() {
        assertEquals(SkullIcon.DMM_VERY_LOW_RISK, RiskSkull.iconFor(newPlayer(), testValueProvider()))
        assertEquals(SkullIcon.DMM_VERY_LOW_RISK_SKULLED, RiskSkull.iconFor(newPlayer(skulled = true), testValueProvider()))
        assertEquals(SkullIcon.DMM_VERY_LOW_RISK, SkullIcon.DMM_VERY_LOW_RISK_SKULLED.tier())
        assertEquals(24, SkullIcon.DMM_VERY_HIGH_RISK.skulled().id)
    }

    @Test
    fun `refresh shows at least a yellow-eyed bronze skull plus the key count for a player carrying loot keys`() {
        val player = newPlayer()
        player.inventory[0] = Item(gg.rsmod.plugins.api.cfg.Items.LOOT_KEY, 1)
        player.inventory[1] = Item(gg.rsmod.plugins.api.cfg.Items.LOOT_KEY_23697, 1)

        RiskSkull.refresh(player, testValueProvider())

        verify { player.skullIcon = SkullIcon.DMM_VERY_LOW_RISK_SKULLED.id }
        verify { player.lootKeyIcons = 2 }
    }

    @Test
    fun `the colour is frozen in a safe zone - it keeps the last dangerous-area tier and never shows the live value`() {
        val player = newPlayer()
        player.inventory[0] = Item(5, 1) // 500k -> Iron, computed in the dangerous area
        assertEquals(SkullIcon.DMM_LOW_RISK, RiskSkull.iconFor(player, testValueProvider()))

        every { player.tile } returns SAFE_TILE
        for (slot in 1 until 20) player.inventory[slot] = Item(5, 1) // now worth 10m - but in a city
        assertEquals(SkullIcon.DMM_LOW_RISK, RiskSkull.iconFor(player, testValueProvider()), "anti-scouting: frozen in the safe zone")

        val fresh = newPlayer(tile = SAFE_TILE)
        fresh.inventory[0] = Item(5, 1)
        assertEquals(SkullIcon.DMM_VERY_LOW_RISK, RiskSkull.iconFor(fresh, testValueProvider()), "never computed: bronze, not the live value")
    }

    private fun newPlayer(
        protectItem: Boolean = false,
        currentSkullIcon: Int = SkullIcon.NONE.id,
        skulled: Boolean = false,
        tile: gg.rsmod.game.model.Tile = DANGEROUS_TILE,
    ): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.tile } returns tile
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

        /** Edgeville: a Deadman dangerous area. */
        private val DANGEROUS_TILE = gg.rsmod.game.model.Tile(3094, 3469, 0)

        /** Lumbridge castle courtyard: a guarded (safe) city. */
        private val SAFE_TILE = gg.rsmod.game.model.Tile(3222, 3218, 0)
    }
}
