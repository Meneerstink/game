package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player

/** Reward-casket openings, not steps or caskets obtained. No historical completions are invented from owned items. */
object ClueCompletions {
    private val counters = ClueScrollTier.values().associateWith { AttributeKey<Int>(persistenceKey = "clue_completions_${it.name.lowercase()}") }
    fun get(player: Player, tier: ClueScrollTier): Int = player.attr[counters.getValue(tier)] ?: 0
    fun opened(player: Player, tier: ClueScrollTier): Int =
        (get(player, tier).toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt().also { player.attr[counters.getValue(tier)] = it }
    fun tierForKey(key: String): ClueScrollTier? = ClueScrollTier.values().firstOrNull { it.key == key }
}
