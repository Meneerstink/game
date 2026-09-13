package gg.rsmod.plugins.content.mechanics.restrictions

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.message

enum class RestrictedAction { EAT, DRINK, PRAYER, SPECIAL_ATTACK, TELEPORT, SUMMON }

/**
 * RCV-010 C2: one shared gate for activity rules (Duel Arena rules today; any other ruleset registers the same
 * way). Every consumer asks [refuse] once at its single entry point - eating, potions, prayers and curses, the
 * special-attack button, teleports and summoning - so a rule is enforced everywhere it applies or nowhere.
 */
object ActivityRestrictions {
    private val rules = mutableListOf<(Player, RestrictedAction) -> String?>()

    fun register(rule: (Player, RestrictedAction) -> String?) {
        rules += rule
    }

    fun refusal(player: Player, action: RestrictedAction): String? = rules.firstNotNullOfOrNull { it(player, action) }

    /** True (and the player is told why) when a registered rule forbids [action]. */
    fun refuse(player: Player, action: RestrictedAction): Boolean {
        val message = refusal(player, action) ?: return false
        player.message(message)
        return true
    }

    internal fun clearForTests() = rules.clear()
}
