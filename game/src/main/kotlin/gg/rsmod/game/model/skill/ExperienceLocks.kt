package gg.rsmod.game.model.skill

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player

/**
 * DMM2 V5: Nigel can independently stop combat or Hitpoints XP. Summoning remains skilling.
 * OSRS Transcript:Nigel rev 15394041 (2026-11-04); approved in DMM-PAKKET-2 on 2026-11-16.
 * Read before any XP/orb/event side effect, including exact rewards (modifiers=false).
 * Int flags use the existing player attribute serializer and survive death and reconnect.
 */
object ExperienceLocks {
    val COMBAT_DISABLED = AttributeKey<Int>("deadman_combat_xp_disabled")
    val HITPOINTS_DISABLED = AttributeKey<Int>("deadman_hitpoints_xp_disabled")

    fun combatDisabled(player: Player): Boolean = player.attr[COMBAT_DISABLED] == 1

    fun hitpointsDisabled(player: Player): Boolean = player.attr[HITPOINTS_DISABLED] == 1

    fun setCombatDisabled(player: Player, disabled: Boolean) {
        player.attr[COMBAT_DISABLED] = if (disabled) 1 else 0
    }

    fun setHitpointsDisabled(player: Player, disabled: Boolean) {
        player.attr[HITPOINTS_DISABLED] = if (disabled) 1 else 0
    }

    /**
     * FONTEIN-RUNES (24-11): a spell cast at the Fountain of Rune costs no runes and gives "no experience" (OSRS Wiki 'Fountain of
     * Rune'). Combat-spell XP is given when the hit lands a few ticks later, so the cast blocks all XP until this cycle (not saved).
     */
    val ALL_BLOCKED_UNTIL = AttributeKey<Int>()

    fun blockAllUntil(player: Player, cycle: Int) {
        if ((player.attr[ALL_BLOCKED_UNTIL] ?: 0) < cycle) player.attr[ALL_BLOCKED_UNTIL] = cycle
    }

    // The seven OSRS combat skills are 0..6 in the 667 skill table: Attack, Defence,
    // Strength, Constitution, Ranged, Prayer, Magic. Summoning (23) is deliberately absent.
    fun blocks(player: Player, skill: Int): Boolean =
        (skill in 0..6 && combatDisabled(player)) || (skill == 3 && hitpointsDisabled(player)) ||
            player.attr[ALL_BLOCKED_UNTIL]?.let { player.world.currentCycle <= it } == true
}
