package gg.rsmod.plugins.content.items.osrs

import kotlin.math.floor

/**
 * OSRS-IMPORT Ancient godsword - Blood Sacrifice (OSRS Wiki "Ancient godsword" raw wikitext, 2026-09-14):
 * "In addition to doubled accuracy, it inflicts damage with a 10% higher maximum hit than a normal attack, consuming 50%
 * of the player's special attack energy." "Upon a successful hit, the target will be marked for sacrifice and will have
 * eight ticks (4.8 seconds) to move at least five tiles away from the attacker." "If the target fails to do so, they will
 * take 25 typeless magic damage, and the attacker will be healed for 15% of the target's max Hitpoints level, up to a cap
 * of 25 against NPCs and 15 against players." "The sacrifice must deal the full 25 damage in order to receive the full
 * healing effect."
 *
 * PROVISIONAL (not stated): with less than 25 damage dealt the heal is limited to the damage actually dealt.
 */
object AncientGodsword {
    const val SPECIAL_ENERGY = 50
    const val SPECIAL_ACCURACY = 2.0
    const val SPECIAL_DAMAGE = 1.1
    const val MARK_TICKS = 8
    const val ESCAPE_DISTANCE = 5
    const val SACRIFICE_DAMAGE = 25
    const val HEAL_CAP_NPC = 25
    const val HEAL_CAP_PLAYER = 15

    /** Heal for a sacrifice that dealt [dealt] damage to a target with [targetMaxHitpoints]. */
    fun heal(
        targetMaxHitpoints: Int,
        targetIsPlayer: Boolean,
        dealt: Int,
    ): Int {
        val cap = if (targetIsPlayer) HEAL_CAP_PLAYER else HEAL_CAP_NPC
        val full = minOf(cap, floor(targetMaxHitpoints * 0.15).toInt())
        return minOf(full, dealt).coerceAtLeast(0)
    }
}
