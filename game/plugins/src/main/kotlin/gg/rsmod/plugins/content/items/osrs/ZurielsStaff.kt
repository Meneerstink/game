package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.getEquipment
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell
import kotlin.math.floor

/**
 * Audit I-08: the OSRS passives of Zuriel's staff (all four item ids: normal, degraded and the corrupt versions) on Ancient Magicks:
 * blood spells heal 50 % more, ice spells get +10 % accuracy and a longer freeze.
 *
 * Not implemented: the audit's "Ancient Magicks cast speed 4" - that value could not be confirmed, so the 5-tick spell speed stays.
 */
object ZurielsStaff {
    val STAVES: Set<Int> =
        setOf(Items.ZURIELS_STAFF, Items.ZURIELS_STAFF_DEG, Items.CORRUPT_ZURIELS_STAFF, Items.CORRUPT_ZURIELS_STAFF_DEG)

    /** Blood spells heal 50 % more (OSRS Wiki "Zuriel's staff"). */
    const val BLOOD_HEAL_MULTIPLIER = 1.5

    /** Ice spells +10 % accuracy (OSRS Wiki "Zuriel's staff"). */
    const val ICE_ACCURACY_MULTIPLIER = 1.1

    /** Ice spell freeze +10 %, rounded down like the ancient sceptre's boost - niet geverifieerd (exact figure not confirmed). */
    const val ICE_FREEZE_MULTIPLIER = 1.1

    fun wielding(pawn: Pawn): Boolean {
        val weapon = (pawn as? Player)?.getEquipment(EquipmentType.WEAPON)?.id ?: return false
        return weapon in STAVES
    }

    fun bloodHeal(
        pawn: Pawn,
        heal: Int,
    ): Int = if (wielding(pawn)) floor(heal * BLOOD_HEAL_MULTIPLIER).toInt() else heal

    fun iceAccuracyMultiplier(
        pawn: Pawn,
        spell: CombatSpell?,
    ): Double = if (spell != null && spell in AncientSceptres.ICE_SPELLS && wielding(pawn)) ICE_ACCURACY_MULTIPLIER else 1.0

    fun freezeTicks(
        pawn: Pawn,
        spell: CombatSpell,
        ticks: Int,
    ): Int = if (spell in AncientSceptres.ICE_SPELLS && wielding(pawn)) floor(ticks * ICE_FREEZE_MULTIPLIER).toInt() else ticks
}
