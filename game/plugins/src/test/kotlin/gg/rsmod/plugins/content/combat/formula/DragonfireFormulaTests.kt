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
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Coverage for [DragonfireFormula] (dragonfire further-foundations pass, 2026-09-02
 * autonomous run - see `RSPS_DECISIONS.md` for the full sourcing write-up). Percentages
 * are the chromatic dragon table on the OSRS Wiki "Dragonfire" page (50 base max hit):
 * none = 100%, shield alone = 10%, prayer alone = 20%, shield+prayer = 10% (no better than
 * shield alone), antifire alone = 70%, super antifire alone = full immunity, and any potion
 * combined with a shield or prayer = full immunity.
 */
class DragonfireFormulaTests {
    @Test
    fun `deals full damage with no protection`() {
        assertEquals(50.0, getMaxHit(newTarget()))
    }

    @Test
    fun `an anti-dragon shield alone reduces damage to 10 percent`() {
        assertEquals(5.0, getMaxHit(newTarget(shield = Items.ANTIDRAGON_SHIELD)))
    }

    @Test
    fun `a dragonfire shield alone reduces damage to 10 percent, same as an anti-dragon shield`() {
        assertEquals(5.0, getMaxHit(newTarget(shield = Items.DRAGONFIRE_SHIELD)))
    }

    @Test
    fun `Protect from Magic alone reduces damage to 20 percent`() {
        assertEquals(10.0, getMaxHit(newTarget(prayer = true)))
    }

    @Test
    fun `a shield plus Protect from Magic is still only 10 percent, no better than the shield alone`() {
        assertEquals(5.0, getMaxHit(newTarget(shield = Items.ANTIDRAGON_SHIELD, prayer = true)))
    }

    @Test
    fun `a regular antifire potion alone reduces damage to 70 percent`() {
        assertEquals(35.0, getMaxHit(newTarget(antifire = true)))
    }

    @Test
    fun `a super antifire potion alone grants full immunity`() {
        assertEquals(0.0, getMaxHit(newTarget(superAntifire = true)))
    }

    @Test
    fun `a regular antifire potion plus a shield grants full immunity`() {
        assertEquals(0.0, getMaxHit(newTarget(shield = Items.ANTIDRAGON_SHIELD, antifire = true)))
    }

    @Test
    fun `a regular antifire potion plus Protect from Magic grants full immunity`() {
        assertEquals(0.0, getMaxHit(newTarget(prayer = true, antifire = true)))
    }

    @Test
    fun `a super antifire potion plus a shield grants full immunity`() {
        assertEquals(0.0, getMaxHit(newTarget(shield = Items.ANTIDRAGON_SHIELD, superAntifire = true)))
    }

    @Test
    fun `the explicit dragonfire immunity attribute overrides everything else`() {
        assertEquals(0.0, getMaxHit(newTarget(immune = true)))
    }

    @Test
    fun `deals full damage against a target the attacking pawn is not an npc`() {
        // Pre-existing behaviour: the reduction table only ever applies when the source of
        // the hit is an Npc (real dragons), matching the original formula's structure.
        val attacker = mockk<Player>(relaxed = true)
        val formula = DragonfireFormula(maxHit = 50)
        assertEquals(50.0, formula.getMaxHit(attacker, newTarget(shield = Items.ANTIDRAGON_SHIELD), 1.0, 1.0))
    }

    private fun getMaxHit(target: Player): Double {
        val attacker = mockk<Npc>(relaxed = true)
        val formula = DragonfireFormula(maxHit = 50)
        return formula.getMaxHit(attacker, target, 1.0, 1.0)
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
