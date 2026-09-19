package gg.rsmod.plugins.content.mechanics.poison

import gg.rsmod.game.model.attr.POISON_TICKS_LEFT_ATTR
import gg.rsmod.game.model.attr.VENOM_TICKS_ELAPSED_ATTR
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.POISON_IMMUNITY
import gg.rsmod.game.model.timer.POISON_TIMER
import gg.rsmod.plugins.content.items.potion.Potion
import gg.rsmod.plugins.content.items.potion.PotionType
import gg.rsmod.plugins.content.items.potion.Potions
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

    /**
     * Poisons [pawn] so its first hit deals [initialDamage]. OSRS Wiki "Poison": "the starting damage is decreased by 1 for every 5
     * occurrences of poison damage" and it wears off when the severity reaches zero (severity 30 -> 5 hits each of 6, 5, 4, 3, 2, 1 =
     * 105 damage in 9 minutes), so the severity is 5 x the starting damage. (Was 5 x damage - 4: only two hits at the starting damage.)
     */
    fun poison(
        pawn: Pawn,
        initialDamage: Int,
    ): Boolean = poisonSeverity(pawn, initialDamage * 5)

    private fun applyPoisonTicks(
        pawn: Pawn,
        ticks: Int,
    ): Boolean {
        if (isImmune(pawn)) return false
        // Owner 2026-09-19: a pawn is never poisoned and envenomed at once - venom is the stronger effect, so a poison hit on an
        // envenomed pawn does nothing (it used to overwrite the orb to the green poison orb while the venom kept ticking).
        if (pawn.attr.has(VENOM_TICKS_ELAPSED_ATTR)) return false

        // OSRS/ Void/ Novite all keep the stronger poison and restart its 30-cycle timer when
        // an equal or stronger application lands. The old guard was inverted: it only entered
        // when no poison existed, so poisoned targets could never be refreshed or upgraded.
        val oldTicks = pawn.attr[POISON_TICKS_LEFT_ATTR]
        val oldDamage = oldTicks?.let(::getDamageForTicks) ?: 0
        val newDamage = getDamageForTicks(ticks)
        if (oldDamage > newDamage) return false

        pawn.timers[POISON_TIMER] = 30
        pawn.attr[POISON_TICKS_LEFT_ATTR] = ticks
        if (pawn is Player) {
            setPoisonVarp(pawn, OrbState.POISON)
            if (oldTicks == null) {
                pawn.message("You have been poisoned!")
            }
        }
        return true
    }

    /** Handles the cache HP-orb "Use Cure" action without relying on an item click slot. */
    /**
     * The HP orb's "Use cure". OSRS Wiki "Venom": "clicking the hitpoints orb will automatically cure or reduce venom if the player has
     * an appropriate item in their inventory, with potions being prioritised over other items" - so while envenomed any antipoison
     * works too (it reduces the venom to poison). Order: anti-venoms (a full cure), then the antipoison family, then the Strange fruit.
     */
    fun cureFromInventory(player: Player): Boolean {
        val potionOrder =
            listOf(
                PotionType.ANTI_VENOM,
                PotionType.ANTI_VENOM_PLUS,
                PotionType.EXTENDED_ANTI_VENOM_PLUS,
                PotionType.ANTIPOISON,
                PotionType.SUPER_ANTIPOISON,
                PotionType.ANTIPOISON_PLUS,
                PotionType.ANTIPOISON_PLUS_PLUS,
                PotionType.SANFEW_SERUM,
            )
        val cure =
            potionOrder
                .asSequence()
                .flatMap { type -> Potion.values().asSequence().filter { it.potionType == type } }
                .mapNotNull { potion -> player.inventory.getItemIndex(potion.item, false).takeIf { it >= 0 }?.let { potion to it } }
                .firstOrNull()
        if (cure != null) {
            Potions.drinkAt(player, cure.first, cure.second)
            return true
        }
        val fruitSlot = player.inventory.getItemIndex(gg.rsmod.plugins.api.cfg.Items.STRANGE_FRUIT, false)
        val fruit = gg.rsmod.plugins.content.items.food.Food.STRANGE_FRUIT
        if (fruitSlot >= 0 && gg.rsmod.plugins.content.items.food.Foods.canEat(player, fruit) &&
            player.inventory.remove(fruit.item, beginSlot = fruitSlot).hasSucceeded()
        ) {
            gg.rsmod.plugins.content.items.food.Foods.eat(player, fruit)
            return true
        }
        player.message("You don't have anything to cure.")
        return false
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
