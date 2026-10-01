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
 * Wears off when the player enters a player-owned house ([clear], player_house.plugin.kts; OSRS Wiki "Vengeance") and on
 * logout (the attribute is not persisted).
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

    /**
     * Vengeance Other (OSRS Wiki "Vengeance Other": "Vengeance spells may only be cast every 30 seconds by the same caster"; Void
     * VengeanceOther: the delay is the caster's): primes [target] and starts the cooldown on [caster] only. Until 2026-10-01 the
     * cooldown went on the target, so a teammate's Vengeance Other locked the target out of his own Vengeance for 30 seconds while
     * the caster could recast at once.
     */
    fun activateOn(
        caster: Pawn,
        target: Pawn,
    ): Boolean {
        if (isOnCooldown(caster)) return false
        prime(target)
        caster.timers[VENGEANCE_COOLDOWN] = COOLDOWN_TICKS
        return true
    }

    /** Primes [target] without touching any cooldown - the caster's delay is the cast's business (Vengeance Group). */
    fun prime(target: Pawn) {
        target.attr[VENGEANCE_ACTIVE_ATTR] = true
    }

    fun clear(pawn: Pawn) {
        pawn.attr.remove(VENGEANCE_ACTIVE_ATTR)
    }

    fun onIncomingHit(
        attacker: Pawn,
        target: Pawn,
        damage: Int,
    ) {
        if (!isActive(target)) return
        // OSRS Wiki "Vengeance": it does not work against the recoil-immune monsters (here: Nex); it stays primed.
        if (attacker is gg.rsmod.game.model.entity.Npc && attacker.id in RingOfRecoil.RECOIL_IMMUNE_NPCS) return
        target.attr.remove(VENGEANCE_ACTIVE_ATTR)

        val reflected = max(1, (damage * REFLECT_PERCENT).toInt())
        attacker.hit(damage = reflected, type = HitType.REFLECTED)
        attacker.damageMap.add(target, reflected)
        gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach.recordDamage(attacker, target, reflected)

        if (target is Player) {
            // OSRS Wiki "Vengeance": "The player will say Taste vengeance! when the rebound is activated" - overhead text that the
            // attacker and bystanders see, not a private game message (combat run 2026-09-29).
            target.forceChat("Taste vengeance!")
        }
    }
}
