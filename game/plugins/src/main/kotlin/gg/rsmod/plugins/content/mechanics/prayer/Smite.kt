package gg.rsmod.plugins.content.mechanics.prayer

import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player

/**
 * Smite's real effect (draining the target's prayer points on a landed hit) had no combat-side
 * hook anywhere - `Prayer.SMITE` only ever drove the overhead icon/toggle in [Prayers.kt]. Sourced
 * from both donors independently: Novite's `Player.java` (`usingPrayer(0, 24)`, i.e. Smite) drains
 * `hit.getDamage() / 4`; Void's `Smite.kt` drains `damage / 40` but Void's damage values are
 * decihitpoints (x10 of Novite's scale, the same conversion factor seen elsewhere across this
 * donor-port), so both sources agree on the real rate: 1 prayer point per 4 damage dealt.
 *
 * Only meaningful against players - NPCs have no prayer-point store in this engine.
 */
object Smite {
    private const val DRAIN_DIVISOR = 4

    fun onDamageDealt(
        attacker: Pawn,
        target: Pawn,
        damage: Int,
    ) {
        if (attacker !is Player || target !is Player || damage <= 0) return
        if (!Prayers.isActive(attacker, Prayer.SMITE)) return
        val drain = damage / DRAIN_DIVISOR
        if (drain > 0) {
            target.decreasePrayerPoints(drain)
        }
    }
}
