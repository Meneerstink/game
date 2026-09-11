package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.model.entity.Player

/**
 * Safe-death areas (2011 "safe minigames" such as the TzHaar Fight Cave and Clan Wars): dying
 * there keeps every item, so the whole item-risk pipeline is skipped for these deaths.
 */
object SafeDeath {
    private val checks = ArrayList<(Player) -> Boolean>()

    fun register(check: (Player) -> Boolean) {
        checks.add(check)
    }

    fun isSafe(player: Player): Boolean = checks.any { it(player) }
}
