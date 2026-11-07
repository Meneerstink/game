package gg.rsmod.plugins.content.quests.tab

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.content.areas.wilderness.bosses.OldWildernessBosses
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
            CollectionLogPages.TOA -> player.attr[gg.rsmod.plugins.content.activity.toa.ToaChest.COMPLETIONS] ?: 0
            else -> DropSources.byKey(player, key)?.let { source -> ofNpcs(player, source.npcs) } ?: 0
        }

    // Count completed fights, including entries that group several NPC forms under the same name.
    private fun completedForm(id: Int): Int = when (id) {
        OldWildernessBosses.VETION -> OldWildernessBosses.VETION_REBORN
        Npcs.CALVARION -> Npcs.CALVARION_ENRAGED
        Npcs.KALPHITE_QUEEN, Npcs.KALPHITE_QUEEN_1159 -> Npcs.KALPHITE_QUEEN_1160
        else -> id
    }

    fun ofNpcs(player: Player, ids: IntArray): Int = ids.map(::completedForm)
        .distinct().fold(0L) { sum, id -> sum + runCatching { player.getNpcKillCount(id) }.getOrDefault(0) }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    /** N524: NpcDeathAction has already incremented the killer's persisted counter before this hook. */
    fun onNpcKilled(player: Player, npcId: Int) {
        if (npcId == Npcs.KALPHITE_QUEEN || npcId == Npcs.KALPHITE_QUEEN_1159) return
        val rewardId = OldWildernessBosses.rewardNpc(npcId) ?: return
        val source = DropSources.forNpc(player, rewardId) ?: return
        if (source.group != DropSources.Group.BOSSES) return
        if (CollectionLogPages.pagesFor(player, source.key).isEmpty()) return
        player.message(bossMessage(source.name, get(player, source.key)))
    }

    /** OSRS sentence; red count, comma grouping (2009scape BossKillCounter supplies the colour). */
    fun bossMessage(name: String, count: Int): String = "Your $name kill count is: <col=ff0000>${String.format(java.util.Locale.US, "%,d", count)}</col>."

    /** A Deadman's chest was opened by [player]. */
    fun chestOpened(player: Player) {
        player.attr[CHESTS] = ((player.attr[CHESTS] ?: 0).toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /** The kills of every page source together ("Breaches kills: N", "Dagannoth Kings kills: N"). */
    fun ofPage(player: Player, page: CollectionLogPages.Page): Int = page.sources.fold(0L) { sum, key -> sum + get(player, key) }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
