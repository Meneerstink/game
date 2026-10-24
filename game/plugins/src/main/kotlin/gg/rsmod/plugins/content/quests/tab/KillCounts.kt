package gg.rsmod.plugins.content.quests.tab

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.npcs.definitions.barrows.Barrows

/**
 * Kill count per Drop Viewer entry (N108 / N104). The engine already counts every npc kill per npc id
 * (`Player.incrementNpcKillCount` in NpcDeathAction, saved as "npc_kill_counts"), so an entry's count is the sum over its npc ids -
 * no second counter, and existing accounts keep the kills they already made. The Barrows chest reads its chest count, the Deadman's
 * chest the chests opened ([CHESTS], new, starts at 0).
 */
object KillCounts {
    val CHESTS = AttributeKey<Int>(persistenceKey = "deadman_chests_opened")

    fun get(player: Player, key: String): Int =
        when (key) {
            "barrows" -> player.attr[Barrows.CHEST_COUNT] ?: 0
            "chest" -> player.attr[CHESTS] ?: 0
            else -> DropSources.byKey(player, key)?.let { source -> ofNpcs(player, source.npcs) } ?: 0
        }

    fun ofNpcs(player: Player, ids: IntArray): Int = ids.fold(0L) { sum, id -> sum + runCatching { player.getNpcKillCount(id) }.getOrDefault(0) }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    /** A Deadman's chest was opened by [player]. */
    fun chestOpened(player: Player) {
        player.attr[CHESTS] = ((player.attr[CHESTS] ?: 0).toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /** The kills of every page source together ("Breaches kills: N", "Dagannoth Kings kills: N"). */
    fun ofPage(player: Player, page: CollectionLogPages.Page): Int = page.sources.fold(0L) { sum, key -> sum + get(player, key) }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
