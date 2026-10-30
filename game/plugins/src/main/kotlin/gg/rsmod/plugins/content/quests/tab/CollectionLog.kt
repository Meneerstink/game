package gg.rsmod.plugins.content.quests.tab

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.npcs.definitions.barrows.Barrows

/**
 * The player's Collection Log (N104, owner "net als OSRS"): per log item how many he received and his kill count when the first
 * one came. New account attributes only (spec H3/H4): the log starts empty from now on - nothing is guessed from what he owns -
 * and an account without them loads as an empty log. The one exception is the Barrows chest log that already existed
 * ([Barrows.COLLECTION_LOG], real records): those items count as obtained.
 *
 * Every reward route calls [record] with the Drop Viewer entry it came from: monster drops (DropTableFactory), pets (Pets.award),
 * breach loot, the Deadman's chest, the chest guardian's emblem, the Barrows chest, the Fight Caves cape and the elite casket.
 * A new item sends the OSRS line "New item added to your collection log: <item>".
 */
object CollectionLog {
    /** "item:count:kc;..." */
    val ATTR = AttributeKey<String>(persistenceKey = "collection_log")

    /** The last new items, newest first: "item:sourceKey:kc;..." (tab 4 "New in your log"). */
    val RECENT = AttributeKey<String>(persistenceKey = "collection_log_recent")

    const val RECENT_SIZE = 5

    class Entry(val count: Int, val kc: Int)

    class Recent(val item: Int, val source: String, val kc: Int)

    private val PARSED = AttributeKey<LinkedHashMap<Int, Entry>>()

    fun parse(text: String?): LinkedHashMap<Int, Entry> {
        val out = LinkedHashMap<Int, Entry>()
        text?.split(';')?.forEach { part ->
            val f = part.split(':')
            val item = f.getOrNull(0)?.toIntOrNull() ?: return@forEach
            out[item] = Entry(f.getOrNull(1)?.toIntOrNull() ?: 1, f.getOrNull(2)?.toIntOrNull() ?: 0)
        }
        return out
    }

    fun format(map: Map<Int, Entry>): String = map.entries.joinToString(";") { "${it.key}:${it.value.count}:${it.value.kc}" }

    fun entries(player: Player): Map<Int, Entry> {
        val map = player.attr[PARSED] ?: parse(player.attr[ATTR]).also { player.attr[PARSED] = it }
        val barrows = (player.attr[Barrows.COLLECTION_LOG] ?: "").split(',').mapNotNull { it.toIntOrNull() }
        if (barrows.none { it !in map }) return map
        val out = LinkedHashMap(map)
        barrows.forEach { if (it !in out) out[it] = Entry(1, 0) }
        return out
    }

    fun has(player: Player, item: Int): Boolean = item in entries(player)

    /** Drops the parsed copy so the next read uses [ATTR] again (after a login conversion rewrote it, N471). */
    fun reload(player: Player) {
        player.attr.remove(PARSED)
    }

    fun recent(player: Player): List<Recent> =
        (player.attr[RECENT] ?: "").split(';').mapNotNull { part ->
            val f = part.split(':')
            val item = f.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            Recent(item, f.getOrNull(1) ?: "", f.getOrNull(2)?.toIntOrNull() ?: 0)
        }

    /**
     * [items] (id to amount) came to [player] from [sourceKey] (a Drop Viewer entry key, null when the route has none). Only log
     * items count; returns the ids that were new.
     */
    fun record(player: Player, sourceKey: String?, items: List<Pair<Int, Int>>): List<Int> {
        if (items.isEmpty()) return emptyList()
        val logItems = runCatching { CollectionLogPages.allItems(player) }.getOrElse { return emptyList() }
        val map = player.attr[PARSED] ?: parse(player.attr[ATTR]).also { player.attr[PARSED] = it }
        val before = entries(player).keys.toSet()
        val kc = sourceKey?.let { KillCounts.get(player, it) } ?: 0
        val fresh = ArrayList<Int>()
        var changed = false
        for ((id, amount) in items) {
            val item = unnoted(player, id)
            if (item !in logItems) continue
            val have = map[item]
            map[item] = if (have == null) Entry(amount.coerceAtLeast(1), kc) else Entry((have.count.toLong() + amount.coerceAtLeast(1)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), have.kc)
            changed = true
            if (item !in before && item !in fresh) fresh += item
        }
        if (!changed) return emptyList()
        player.attr[ATTR] = format(map)
        if (fresh.isNotEmpty()) {
            val recent = fresh.reversed().map { Recent(it, sourceKey ?: "", kc) } + recent(player)
            player.attr[RECENT] = recent.take(RECENT_SIZE).joinToString(";") { "${it.item}:${it.source}:${it.kc}" }
            fresh.forEach { id ->
                val name = player.world.definitions.getNullable(ItemDef::class.java, id)?.name ?: "item"
                player.message("New item added to your collection log: <col=ef1020>$name</col>")
            }
        }
        return fresh
    }

    private fun unnoted(player: Player, id: Int): Int {
        val def = player.world.definitions.getNullable(ItemDef::class.java, id) ?: return id
        return if (def.noted && def.noteLinkId > 0) def.noteLinkId else id
    }

    /** "Obtained: N/M" of [page]. */
    fun obtained(player: Player, page: CollectionLogPages.Page): Int = page.items.count { has(player, it) }

    fun totalObtained(player: Player): Int {
        val have = entries(player).keys
        return CollectionLogPages.allItems(player).count { it in have }
    }
}
