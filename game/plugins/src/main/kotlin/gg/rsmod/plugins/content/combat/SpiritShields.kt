package gg.rsmod.plugins.content.combat

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * Passive damage reduction of the two blessed spirit shields with a sigil (overnight run 2026-10-02, PvP audit: neither had
 * any effect on this server - only their equip sound referenced them). Applied once per landed combat hit on a player, in
 * [dealHit] before Deflect, like Novite 667 `Player.handleIngoingHit` (melee / ranged / magic hits only; poison, recoil and
 * other raw hits never pass through here).
 *
 * - Elysian (OSRS Wiki "Elysian spirit shield", combat is OSRS-exact by owner rule): "a 70% chance of reducing the damage the
 *   player receives by 25%"; "if damage would have been taken when the effect activates, a minimum of 1 is reduced".
 * - Divine (not in OSRS; Novite 667 + OSRS Wiki poll text "reducing the damage the player receives by 30%, instead draining
 *   prayer points for 50% of the negated damage"): 30% off, only while the player has the prayer points to pay. ADAPTED
 *   rounding: the negated damage is damage - floor(damage x 0.7) and its half is rounded up, so every reduction costs at
 *   least 1 prayer point (Novite's 990-unit prayer made sub-point costs vanish; this server stores prayer 1:1).
 */
object SpiritShields {
    const val ELYSIAN_CHANCE_PERCENT = 70
    const val ELYSIAN_REDUCTION = 0.25
    const val DIVINE_KEEP = 0.7

    fun modifyIncomingDamage(
        target: Player,
        damage: Int,
        roll: Int = target.world.random(99),
    ): Int {
        if (damage <= 0) return damage
        return when (target.equipment[EquipmentType.SHIELD.id]?.id) {
            Items.ELYSIAN_SPIRIT_SHIELD -> elysian(damage, roll)
            Items.DIVINE_SPIRIT_SHIELD -> {
                val (after, cost) = divine(damage)
                if (cost > target.getCurrentPrayerPoints()) return damage
                target.decreasePrayerPoints(cost)
                after
            }
            else -> damage
        }
    }

    /** [roll] in 0..99: the effect fires below [ELYSIAN_CHANCE_PERCENT]. */
    fun elysian(
        damage: Int,
        roll: Int,
    ): Int {
        if (damage <= 0 || roll >= ELYSIAN_CHANCE_PERCENT) return damage
        return damage - max(1, floor(damage * ELYSIAN_REDUCTION).toInt())
    }

    /** Damage after the divine reduction, and its prayer cost. */
    fun divine(damage: Int): Pair<Int, Int> {
        val after = floor(damage * DIVINE_KEEP).toInt()
        return after to ceil((damage - after) / 2.0).toInt()
    }
}
