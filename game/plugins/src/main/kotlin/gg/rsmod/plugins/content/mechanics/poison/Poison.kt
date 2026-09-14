package gg.rsmod.plugins.content.mechanics.poison

import gg.rsmod.game.model.attr.POISON_TICKS_LEFT_ATTR
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.POISON_IMMUNITY
import gg.rsmod.game.model.timer.POISON_TIMER
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.setVarp

/**
 * This object represents the game mechanic for poisoning a Pawn and
 * causing periodic damage to them over time. It provides methods for
 * poisoning a Pawn, getting the damage dealt by a given number of ticks,
 * checking if a Pawn is immune to poison, and setting the state of the
 * player's HP orb.
 *
 * @author Tom <rspsmods@gmail.com>
 * @author Alycia <https://github.com/alycii>
 */
object Poison {
    /** The VARP id for the player's HP orb. */
    private const val POISON_VARP = 102

    /**
     * Gets the damage that will be dealt after a given number of ticks.
     *
     * @param ticks the number of ticks left until the poison expires
     * @return the damage that will be dealt after the specified number of ticks
     * @since 1.0
     */
    fun getDamageForTicks(ticks: Int) = (ticks / 5) + 1

    fun isPoisoned(pawn: Pawn): Boolean = pawn.attr.has(POISON_TICKS_LEFT_ATTR)

    /**
     * Checks if a given pawn is immune to poison.
     *
     * @param pawn the pawn to check for poison immunity
     * @return true if the pawn is immune to poison, false otherwise
     * @since 1.0
     */
    fun isImmune(pawn: Pawn): Boolean =
        when (pawn) {
            is Player -> pawn.timers.has(POISON_IMMUNITY)
            is Npc -> pawn.combatDef.poisonImmunity
            else -> false
        }

    /**
     * Poisons a pawn, causing periodic damage to them over time.
     *
     * @param pawn the pawn to poison
     * @param initialDamage the initial damage dealt by the poison
     * @return true if the poison was applied successfully, false otherwise
     * @since 1.0
     */
    /**
     * Poisons [pawn] at an OSRS poison severity: a hit deals ceil(severity / 5) and the severity drops by one per hit, which is this
     * model's `ticks = severity - 1` (severity 10 -> first hit 2, 11 -> 3, 20 -> 4, 22 -> 5; OSRS Wiki "Ancient sceptre" smoke table).
     */
    fun poisonSeverity(
        pawn: Pawn,
        severity: Int,
    ): Boolean = applyPoisonTicks(pawn, severity - 1)

    fun poison(
        pawn: Pawn,
        initialDamage: Int,
    ): Boolean = applyPoisonTicks(pawn, (initialDamage * 5) - 4)

    private fun applyPoisonTicks(
        pawn: Pawn,
        ticks: Int,
    ): Boolean {
        if (isImmune(pawn)) return false

        // OSRS/ Void/ Novite all keep the stronger poison and restart its 30-cycle timer when
        // an equal or stronger application lands. The old guard was inverted: it only entered
        // when no poison existed, so poisoned targets could never be refreshed or upgraded.
        val oldTicks = pawn.attr[POISON_TICKS_LEFT_ATTR]
        val oldDamage = oldTicks?.let(::getDamageForTicks) ?: 0
        val newDamage = getDamageForTicks(ticks)
        if (oldDamage > newDamage) return false

        pawn.timers[POISON_TIMER] = 30
        pawn.attr[POISON_TICKS_LEFT_ATTR] = ticks
        if (oldTicks == null && pawn is Player) {
            pawn.message("You have been poisoned!")
        }
        return true
    }

    /**
     * Cures regular poison: stops the poison timer, clears the remaining ticks and resets the HP orb. The one poison
     * cure every caller uses (antipoison potions, the Ferox Enclave pool, Summoning, the Crown dev tool) - RCV-011: the
     * copies each cleared state by hand and one of them left the orb showing poison.
     *
     * @return true if the pawn was poisoned.
     */
    fun cure(pawn: Pawn): Boolean {
        val poisoned = pawn.attr.has(POISON_TICKS_LEFT_ATTR) || pawn.timers.has(POISON_TIMER)
        pawn.timers.remove(POISON_TIMER)
        pawn.attr.remove(POISON_TICKS_LEFT_ATTR)
        setPoisonVarp(pawn, OrbState.NONE)
        return poisoned
    }

    /**
     * Sets the state of the player's HP orb to indicate whether they
     * are currently poisoned or not.
     *
     * @param pawn the player to set the HP orb state for
     * @param state the new state of the HP orb
     * @since 1.0
     */
    fun setPoisonVarp(
        pawn: Pawn,
        state: OrbState,
    ) {
        if (pawn is Player) {
            val value =
                when (state) {
                    OrbState.NONE -> 0
                    OrbState.POISON -> 1
                    OrbState.VENOM -> 2
                }
            pawn.setVarp(POISON_VARP, value)
        }
    }

    /**
     * An enum representing the possible states of the player's HP orb.
     *
     * @since 1.0
     */
    enum class OrbState {
        /** The player is not poisoned. */
        NONE,

        /** The player is poisoned. */
        POISON,

        /**
         * The player is envenomed. Shares the same orb/varp as [POISON] (real RS only has
         * one poison-family HP orb) - not independently cache-verified for this specific
         * 667 cache, inferred by convention from the existing NONE=0/POISON=1 pattern this
         * file already used. See `Venom` (mechanics.poison package) for the venom effect
         * itself.
         */
        VENOM,
    }
}
