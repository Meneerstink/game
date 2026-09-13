package gg.rsmod.plugins.content.mechanics.poison

import gg.rsmod.game.model.attr.POISON_TICKS_LEFT_ATTR
import gg.rsmod.game.model.attr.VENOM_TICKS_ELAPSED_ATTR
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.POISON_TIMER
import gg.rsmod.game.model.timer.VENOM_IMMUNITY
import gg.rsmod.game.model.timer.VENOM_TIMER
import gg.rsmod.plugins.api.ext.message
import kotlin.math.min

/**
 * The game mechanic for envenoming a Pawn - a stronger, escalating relative of [Poison]
 * that does not wear off on its own and shares its HP orb (see [Poison.OrbState]).
 *
 * Sourced from the OSRS Wiki ("Venom", fetched during this implementation): damage starts
 * at 6, increases by 2 every tick, and caps at 20 after a little over 2 minutes; venom
 * ticks once every 18 seconds - identical cadence to this codebase's own existing
 * [Poison] tick delay (30 cycles), reused rather than reinvented. A pawn can never be
 * simultaneously poisoned and envenomed. Regular antipoison converts venom into regular
 * poison at the same damage level (a downgrade, not a cure); only a full anti-venom cures
 * it outright and grants immunity - this file provides the integration points for both
 * ([downgradeToPoison], [cure]) without any anti-venom item being added yet (out of scope
 * for this pass, see RSPS_DECISIONS.md).
 *
 * @author Claude (2026-09-02 autonomous foundations pass)
 */
object Venom {
    /** Ticks between venom damage. Matches [Poison]'s own tick delay (18s @ 0.6s/cycle). */
    const val VENOM_TICK_DELAY = 30

    private const val INITIAL_DAMAGE = 6
    private const val DAMAGE_STEP = 2
    private const val MAX_DAMAGE = 20

    /**
     * Checks if a given pawn is immune to venom. Distinct from [Poison.isImmune] - real RS
     * has items (e.g. Serpentine helm) that grant venom immunity without poison immunity.
     */
    fun isImmune(pawn: Pawn): Boolean =
        when (pawn) {
            is Player -> pawn.timers.has(VENOM_IMMUNITY)
            is Npc -> pawn.combatDef.venomImmunity
            else -> false
        }

    /**
     * The damage the next venom tick will deal, given how many venom ticks have already
     * elapsed since the pawn was envenomed.
     *
     * @param ticksElapsed the number of venom ticks that have already occurred (0 for the
     *   very first tick)
     * @return the damage for the next tick: 6, 8, 10, ..., capped at 20
     * @since 1.0
     */
    fun damageForTick(ticksElapsed: Int): Int = min(INITIAL_DAMAGE + DAMAGE_STEP * ticksElapsed, MAX_DAMAGE)

    /**
     * Envenoms a pawn. Replaces any active regular poison outright (venom is the stronger
     * effect - real RS states a pawn is never simultaneously poisoned and envenomed).
     * Re-envenoming an already-envenomed pawn is a no-op: their existing progression is
     * never reset or weakened by a repeat hit.
     *
     * @return true if venom was newly applied, false if the pawn was already envenomed or
     *   is immune.
     * @since 1.0
     */
    fun envenom(pawn: Pawn): Boolean {
        if (isImmune(pawn) || pawn.attr.has(VENOM_TICKS_ELAPSED_ATTR)) {
            return false
        }
        Poison.cure(pawn)
        pawn.timers[VENOM_TIMER] = VENOM_TICK_DELAY
        pawn.attr[VENOM_TICKS_ELAPSED_ATTR] = 0
        Poison.setPoisonVarp(pawn, Poison.OrbState.VENOM)
        if (pawn is Player) {
            pawn.message("You have been envenomed!")
        }
        return true
    }

    /**
     * Fully cures venom and grants immunity for [immunityTicks] cycles - the real
     * anti-venom effect ("completely cures venom and provides immunity"). Pass 0 for no
     * immunity window.
     *
     * @return true if the pawn was actually envenomed (and so was actually cured).
     * @since 1.0
     */
    fun cure(
        pawn: Pawn,
        immunityTicks: Int,
        announce: Boolean = true,
    ): Boolean {
        if (!pawn.attr.has(VENOM_TICKS_ELAPSED_ATTR)) {
            return false
        }
        pawn.timers.remove(VENOM_TIMER)
        pawn.attr.remove(VENOM_TICKS_ELAPSED_ATTR)
        if (immunityTicks > 0) {
            pawn.timers[VENOM_IMMUNITY] = immunityTicks
        }
        Poison.setPoisonVarp(pawn, Poison.OrbState.NONE)
        if (announce && pawn is Player) {
            pawn.message("You have been cured of the venom coursing through your veins.")
        }
        return true
    }

    /**
     * Downgrades venom to regular poison at the venom's current damage level - the real
     * regular-antipoison effect ("converts the venom to regular poison, which starts at
     * the same damage the venom had"). This is NOT a full cure; call [cure] for that.
     *
     * @return true if a downgrade actually happened (the pawn was envenomed).
     * @since 1.0
     */
    fun downgradeToPoison(pawn: Pawn): Boolean {
        val ticksElapsed = pawn.attr[VENOM_TICKS_ELAPSED_ATTR] ?: return false
        val currentDamage = damageForTick(ticksElapsed)
        pawn.timers.remove(VENOM_TIMER)
        pawn.attr.remove(VENOM_TICKS_ELAPSED_ATTR)
        if (pawn is Player) {
            pawn.message("Your venom has been weakened into regular poison.")
        }
        Poison.poison(pawn, currentDamage)
        Poison.setPoisonVarp(pawn, Poison.OrbState.POISON)
        return true
    }
}
