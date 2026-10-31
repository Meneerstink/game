package gg.rsmod.plugins.content.activity.pest_control

import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.quests.tab.CollectionLog

/**
 * N471 (2026-10-30): the Void Knights sold the 667 deflector 19711, which has no inventory option and no worn model, so the shield
 * could not be worn. The reward is the wearable 19712 now; at login every 19711 a player holds becomes 19712 (same amount and
 * attributes), and a Collection Log entry for 19711 moves to 19712 with its count and kill count.
 */
object VoidDeflectorMigration {
    const val UNWEARABLE = Items.VOID_KNIGHT_DEFLECTOR
    const val WEARABLE = Items.VOID_KNIGHT_DEFLECTOR_19712

    /** Converts [player]'s deflectors in place; returns how many stacks changed (the Collection Log entry not counted). */
    fun migrate(player: Player): Int {
        var changed = 0
        // Every container the save loads (beast-of-burden cargo, gift, ... - Player.containers), as RemovedItems.purge walks them.
        LinkedHashSet<ItemContainer>().apply {
            add(player.inventory)
            add(player.bank)
            add(player.equipment)
            add(player.gravestone)
            add(player.deathRecovery)
            addAll(player.containers.values)
        }.forEach { changed += migrate(it) }
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

    /** The saved log text with the 19711 entry renamed to 19712 (counts added when both exist); null when nothing changes. */
    fun migratedLog(text: String?): String? {
        val map = CollectionLog.parse(text)
        val old = map.remove(UNWEARABLE) ?: return null
        val have = map[WEARABLE]
        map[WEARABLE] = if (have == null) old else CollectionLog.Entry(have.count + old.count, have.kc)
        return CollectionLog.format(map)
    }

    /** The "New in your log" text with 19711 renamed to 19712; null when it has no 19711. */
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
