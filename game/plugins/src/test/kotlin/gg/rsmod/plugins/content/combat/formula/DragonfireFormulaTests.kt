package gg.rsmod.plugins.content.combat.formula

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.DRAGONFIRE_IMMUNITY_ATTR
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.timer.ANTIFIRE_TIMER
import gg.rsmod.game.model.timer.SUPER_ANTIFIRE_TIMER
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.PrayerIcon
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.combat.formula.DragonfireFormula.Companion.protectionOf
import gg.rsmod.plugins.content.combat.formula.DragonfireFormula.Companion.resolve
import gg.rsmod.plugins.content.combat.formula.DragonfireTable.Potion
import gg.rsmod.plugins.content.combat.formula.DragonfireTable.Type
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [DragonfireFormula] on the OSRS model (RCV-012 decision, 2026-09-14): the player's real protection layers are read from equipment,
 * prayer, potion timers and the immunity flag, the max comes from [DragonfireTable], and the accuracy roll only picks the failed / won
 * max (with the sourced OSRS Wiki message) on split rows.
 */
class DragonfireFormulaTests {
    @Test
    fun `protection layers are read from the player`() {
        assertEquals(DragonfireFormula.Protection(false, false, Potion.NONE, false), protectionOf(newTarget()))
        assertTrue(protectionOf(newTarget(shield = Items.ANTIDRAGON_SHIELD)).shield)
        listOf(Items.DRAGONFIRE_SHIELD, Items.DRAGONFIRE_WARD, Items.ANCIENT_WYVERN_SHIELD_UNCHARGED).forEach {
            assertTrue(protectionOf(newTarget(shield = it)).shield, "shield $it")
        }
        assertFalse(protectionOf(newTarget(shield = Items.RUNE_KITESHIELD)).shield)
        assertTrue(protectionOf(newTarget(prayer = true)).prayer)
        assertEquals(Potion.ANTIFIRE, protectionOf(newTarget(antifire = true)).potion)
        assertEquals(Potion.SUPER_ANTIFIRE, protectionOf(newTarget(superAntifire = true)).potion)
        assertEquals(Potion.SUPER_ANTIFIRE, protectionOf(newTarget(antifire = true, superAntifire = true)).potion, "super outranks regular")
        assertTrue(protectionOf(newTarget(immune = true)).immune)
    }

    @Test
    fun `split rows use the accuracy roll and send the sourced message`() {
        val none = protectionOf(newTarget())
        assertEquals(DragonfireFormula.Outcome(50, DragonfireTable.BURNT_MESSAGE), resolve(Type.CHROMATIC, none) { false })
        assertEquals(DragonfireFormula.Outcome(30, DragonfireTable.RESIST_MESSAGE), resolve(Type.CHROMATIC, none) { true })
        val antifire = protectionOf(newTarget(antifire = true))
        assertEquals(DragonfireFormula.Outcome(35, DragonfireTable.BURNT_MESSAGE), resolve(Type.METALLIC, antifire) { false })
        assertEquals(DragonfireFormula.Outcome(15, DragonfireTable.RESIST_MESSAGE), resolve(Type.METALLIC, antifire) { true })
    }

    @Test
    fun `unsplit rows never consult the roll`() {
        val roll = { error("roll consulted") }
        assertEquals(DragonfireFormula.Outcome(5, null), resolve(Type.CHROMATIC, protectionOf(newTarget(shield = Items.ANTIDRAGON_SHIELD)), roll))
        assertEquals(DragonfireFormula.Outcome(10, null), resolve(Type.CHROMATIC, protectionOf(newTarget(prayer = true)), roll))
        assertEquals(DragonfireFormula.Outcome(5, null), resolve(Type.METALLIC, protectionOf(newTarget(shield = Items.ANTIDRAGON_SHIELD, prayer = true)), roll))
        assertEquals(DragonfireFormula.Outcome(65, null), resolve(Type.KING_BLACK_DRAGON_FIERY, protectionOf(newTarget()), roll))
        assertEquals(DragonfireFormula.Outcome(5, null), resolve(Type.KING_BLACK_DRAGON_FIERY, protectionOf(newTarget(prayer = true, antifire = true)), roll))
        assertEquals(DragonfireFormula.Outcome(50, null), resolve(Type.KING_BLACK_DRAGON_SPECIAL, protectionOf(newTarget(superAntifire = true)), roll))
        assertEquals(DragonfireFormula.Outcome(0, null), resolve(Type.CHROMATIC, protectionOf(newTarget(superAntifire = true)), roll))
    }

    @Test
    fun `the frost dragon freeze is blocked by shield plus antifire or a super antifire alone`() {
        val frost = DragonfireTable.FROST_DRAGON_COMBAT_DEF
        fun blocks(target: Player) = DragonfireTable.blocksFreeze(frost, protectionOf(target))
        assertFalse(blocks(newTarget()))
        assertFalse(blocks(newTarget(shield = Items.ANTIDRAGON_SHIELD)), "shield alone")
        assertFalse(blocks(newTarget(antifire = true)), "antifire alone")
        assertFalse(blocks(newTarget(prayer = true, antifire = true)), "prayer is not a shield")
        assertTrue(blocks(newTarget(shield = Items.ANTIDRAGON_SHIELD, antifire = true)))
        assertTrue(blocks(newTarget(shield = Items.DRAGONFIRE_SHIELD, antifire = true)), "shield variant")
        assertTrue(blocks(newTarget(superAntifire = true)))
        assertFalse(DragonfireTable.blocksFreeze("king_black_dragon", protectionOf(newTarget(superAntifire = true))), "no sourced KBD block")
    }

    @Test
    fun `the explicit dragonfire immunity attribute overrides every table`() {
        val immune = protectionOf(newTarget(immune = true))
        Type.values().forEach { assertEquals(DragonfireFormula.Outcome(0, null), resolve(it, immune) { error("roll consulted") }, "$it") }
    }

    @Test
    fun `dragonfire always lands and a non-npc attacker gets the unprotected table max`() {
        val formula = DragonfireFormula(Type.KING_BLACK_DRAGON_FIERY)
        assertEquals(1.0, formula.getAccuracy(mockk<Npc>(relaxed = true), newTarget(), 1.0))
        assertEquals(65.0, formula.getMaxHit(mockk<Player>(relaxed = true), newTarget(shield = Items.ANTIDRAGON_SHIELD), 1.0, 1.0))
    }

    @Test
    fun `an npc attacker against an unsplit row returns the table max without rolling`() {
        assertEquals(5.0, DragonfireFormula(Type.CHROMATIC).getMaxHit(mockk<Npc>(relaxed = true), newTarget(shield = Items.ANTIDRAGON_SHIELD), 1.0, 1.0))
        assertEquals(0.0, DragonfireFormula(Type.METALLIC).getMaxHit(mockk<Npc>(relaxed = true), newTarget(immune = true), 1.0, 1.0))
    }

    private fun newTarget(
        shield: Int? = null,
        prayer: Boolean = false,
        antifire: Boolean = false,
        superAntifire: Boolean = false,
        immune: Boolean = false,
    ): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.prayerIcon } returns if (prayer) PrayerIcon.PROTECT_FROM_MAGIC.id else -1
        val equipment = ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        if (shield != null) {
            equipment[EquipmentType.SHIELD.id] = Item(shield)
        }
        every { player.equipment } returns equipment
        val timers = TimerMap()
        if (antifire) {
            timers[ANTIFIRE_TIMER] = 600
        }
        if (superAntifire) {
            timers[SUPER_ANTIFIRE_TIMER] = 300
        }
        every { player.timers } returns timers
        val attr = AttributeMap()
        if (immune) {
            attr[DRAGONFIRE_IMMUNITY_ATTR] = true
        }
        every { player.attr } returns attr
        return player
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
    }
}
