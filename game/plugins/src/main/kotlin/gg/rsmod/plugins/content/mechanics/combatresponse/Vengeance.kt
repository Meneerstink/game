package gg.rsmod.plugins.content.mechanics.combatresponse

import gg.rsmod.game.model.attr.VENGEANCE_ACTIVE_ATTR
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.VENGEANCE_COOLDOWN
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.ext.hit
import gg.rsmod.plugins.api.ext.message
import kotlin.math.max

/**
 * Vengeance (Lunar spellbook). Sourced from the OSRS Wiki "Vengeance" page: rebounds 75% of
 * the next hit taken back onto whoever dealt it (rounded down, minimum 1), single-use, with
 * a 50-tick (30 real-world second) cooldown before it can be recast. Stacks with Ring of
 * recoil and the Dharok + amulet of the damned bonus.
 *
 * This object only implements the *reflect* half of the mechanic - [activate] is the hook
 * the eventual Lunar spellbook cast handler should call once that spellbook exists; casting
 * itself (rune consumption, level requirement, spellbook navigation) is out of scope for the
 * P6 combat-response foundation pass.
 *
 * Not implemented (no source found / explicitly out of scope this pass): Vengeance being
 * cleared on entering a player-owned house - this codebase has no POH system yet.
 */
object Vengeance {
    const val REFLECT_PERCENT = 0.75
    const val COOLDOWN_TICKS = 50

    fun isActive(pawn: Pawn): Boolean = pawn.attr[VENGEANCE_ACTIVE_ATTR] == true

    fun isOnCooldown(pawn: Pawn): Boolean = pawn.timers.has(VENGEANCE_COOLDOWN)

    /**
     * Primes Vengeance on [pawn]. Returns false (no-op) if still on cooldown from a previous
     * cast - the caller (spell-cast handler) is expected to also enforce runes/level/etc.
     * before calling this.
     */
    fun activate(pawn: Pawn): Boolean {
        if (isOnCooldown(pawn)) return false
        pawn.attr[VENGEANCE_ACTIVE_ATTR] = true
        pawn.timers[VENGEANCE_COOLDOWN] = COOLDOWN_TICKS
        return true
    }

    fun onIncomingHit(
        attacker: Pawn,
        target: Pawn,
        damage: Int,
    ) {
        if (!isActive(target)) return
        target.attr.remove(VENGEANCE_ACTIVE_ATTR)

        val reflected = max(1, (damage * REFLECT_PERCENT).toInt())
        attacker.hit(damage = reflected, type = HitType.REFLECTED)
        attacker.damageMap.add(target, reflected)

        if (target is Player) {
            // Sourced exact line ("Taste vengeance!") - OSRS Wiki "Vengeance".
            target.message("Taste vengeance!")
        }
    }
}
