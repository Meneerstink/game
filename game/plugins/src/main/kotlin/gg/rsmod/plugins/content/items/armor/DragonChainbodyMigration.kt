package gg.rsmod.plugins.content.items.armor

import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.quests.tab.CollectionLog

/** N11: KQ reward 2513 is a non-wearable legacy definition; migrate every held/logged copy to wearable 3140. */
object DragonChainbodyMigration {
    const val UNWEARABLE = Items.DRAGON_CHAINBODY
    const val WEARABLE = Items.DRAGON_CHAINBODY_3140

    /** Converts [player]'s chainbodies in place; returns how many stacks changed (the Collection Log entry not counted). */
    fun migrate(player: Player): Int {
        var changed = 0
        listOf(player.inventory, player.bank, player.equipment, player.gravestone, player.deathRecovery).forEach { changed += migrate(it) }
        migrateLog(player)
        return changed
    }

    fun migrate(container: ItemContainer): Int {
        var changed = 0
        for (slot in 0 until container.capacity) {
            val item = container[slot] ?: continue
            if (item.id != UNWEARABLE) continue
            container[slot] = Item(WEARABLE, item.amount).copyAttr(item)
            changed++
        }
        return changed
    }

    /** The saved log text with the 2513 entry renamed to 3140 (counts added when both exist); null when nothing changes. */
    fun migratedLog(text: String?): String? {
        val map = CollectionLog.parse(text)
        val old = map.remove(UNWEARABLE) ?: return null
        val have = map[WEARABLE]
        map[WEARABLE] = if (have == null) old else CollectionLog.Entry(have.count + old.count, have.kc)
        return CollectionLog.format(map)
    }

    /** The "New in your log" text with 2513 renamed to 3140; null when it has no 2513. */
    fun migratedRecent(text: String?): String? {
        if (text.isNullOrEmpty()) return null
        val parts = text.split(';')
        if (parts.none { it.substringBefore(':') == "$UNWEARABLE" }) return null
        return parts.joinToString(";") { if (it.substringBefore(':') == "$UNWEARABLE") "$WEARABLE:" + it.substringAfter(':') else it }
    }

    private fun migrateLog(player: Player) {
        val log = migratedLog(player.attr[CollectionLog.ATTR])
        val recent = migratedRecent(player.attr[CollectionLog.RECENT])
        if (log == null && recent == null) return
        log?.let { player.attr[CollectionLog.ATTR] = it }
        recent?.let { player.attr[CollectionLog.RECENT] = it }
        CollectionLog.reload(player)
    }
}
