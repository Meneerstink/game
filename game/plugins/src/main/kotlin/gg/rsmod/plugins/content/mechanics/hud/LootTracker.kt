package gg.rsmod.plugins.content.mechanics.hud

import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.hud.ClientHud
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.ext.message
import java.text.NumberFormat
import java.util.Locale

/**
 * RuneLite Loot Tracker (owner 2026-09-30), server half. The client's Loot Tracker panel (RspsLootTracker, the sidebar's
 * loot tracker tab) only shows what arrives here, so every entry is loot this server really handed the player:
 * [npcKill] is called by the shared kill-drop routine (DropTableFactory.getDrop) once per owner with exactly the items
 * spawned for that owner - after LootShare split them - so one kill is one event, never one per item.
 *
 * Payloads (ClientHud side channel):
 *  - `loot|<name>|<level>|<kills>|<id>,<qty>,<ge>,<ha>;...`   a new kill (session + aggregate)
 *  - `lootr|<name>|<level>|<kills>|<entries>`                  a remembered aggregate (login push; aggregate only)
 *  - `lootclear` / `lootclear|<name>|<level>`                  RuneLite "Reset All" / a box's "Reset"
 * A long record is split over several payloads; only the first carries the kill count (the rest say 0).
 *
 * "Remember loot" (syncPanel, RuneLite: "Saves loot between client sessions") keeps the aggregate with the account.
 */
object LootTracker {
    private val SAVED = AttributeKey<String>(persistenceKey = "loot_tracker")

    /** Stored records: at most this many sources (oldest dropped first), each at most [MAX_ITEMS] item ids. */
    private const val MAX_SOURCES = 200
    private const val MAX_ITEMS = 120

    class Record(
        val name: String,
        val level: Int,
        var kills: Int,
        val items: LinkedHashMap<Int, Long>,
    )

    fun npcKill(
        player: Player,
        npcId: Int,
        items: List<Item>,
    ) {
        if (!ClientHudConfig.bool(player, "loottracker.enabled")) return
        val def = player.world.definitions.getNullable(NpcDef::class.java, npcId) ?: return
        val name = clean(def.name).ifEmpty { return }
        val level = def.combatLevel
        record(player, name, if (level > 0) level else -1, items)
    }

    /** A player kill (PvP loot): RuneLite PlayerLootReceived, recorded under the victim's name without a level. */
    fun playerKill(
        player: Player,
        victim: String,
        items: List<Item>,
    ) {
        if (!ClientHudConfig.bool(player, "loottracker.enabled")) return
        val name = clean(victim).ifEmpty { return }
        record(player, name, -1, items, pvp = true)
    }

    private fun record(
        player: Player,
        name: String,
        level: Int,
        items: List<Item>,
        pvp: Boolean = false,
    ) {
        // Same item twice in one kill (two rolls of one table) is one entry, as RuneLite's LootTrackerPlugin.buildLootTrackerItem stacks it.
        val merged = LinkedHashMap<Int, Long>()
        for (item in items) {
            if (item.amount > 0) merged.merge(item.id, item.amount.toLong(), Long::plus)
        }
        send(player, "loot", name, level, 1, merged)
        if (ClientHudConfig.bool(player, "loottracker.syncPanel")) save(player, name, level, merged)
        val chatKey = if (pvp) "loottracker.pvpKillChatMessage" else "loottracker.npcKillChatMessage"
        if (ClientHudConfig.bool(player, chatKey)) {
            val ha = ClientHudConfig.get(player, "loottracker.priceType") == "HIGH_ALCHEMY"
            var total = 0L
            for ((id, qty) in merged) total += qty * (if (ha) ClientHud.itemAlch(id) else ClientHud.itemValue(player, id)).toLong()
            // RuneLite LootTrackerPlugin: "You've killed <name> for <QuantityFormatter.quantityToStackSize(total)> loot."
            player.message("<col=ef1020>You've killed $name for ${stackSize(total)} loot.</col>")
        }
    }

    /** Login: the remembered aggregate (when "Remember loot" is on). */
    fun push(player: Player) {
        if (!ClientHudConfig.bool(player, "loottracker.enabled") || !ClientHudConfig.bool(player, "loottracker.syncPanel")) return
        for (record in load(player)) send(player, "lootr", record.name, record.level, record.kills, record.items)
    }

    /** RuneLite "Reset All" ([name] null) or one box's "Reset". */
    fun reset(
        player: Player,
        name: String?,
        level: Int,
    ) {
        if (name == null) {
            player.attr.remove(SAVED)
            ClientHud.send(player, "lootclear")
            return
        }
        val records = load(player)
        records.removeIf { it.name.equals(name, ignoreCase = true) && it.level == level }
        store(player, records)
        ClientHud.send(player, "lootclear|${clean(name)}|$level")
    }

    private fun send(
        player: Player,
        tag: String,
        name: String,
        level: Int,
        kills: Int,
        items: Map<Int, Long>,
    ) {
        val head = "$tag|$name|$level|"
        val line = StringBuilder()
        var first = true
        for ((id, qty) in items) {
            val entry = "$id,$qty,${ClientHud.itemValue(player, id)},${ClientHud.itemAlch(id)}"
            if (line.isNotEmpty() && head.length + 12 + line.length + entry.length + 1 > ClientHud.MAX_PAYLOAD) {
                ClientHud.send(player, "$head${if (first) kills else 0}|$line")
                first = false
                line.setLength(0)
            }
            if (line.isNotEmpty()) line.append(';')
            line.append(entry)
        }
        // A kill without loot still counts (RuneLite adds the kill with an empty item list).
        if (line.isNotEmpty() || first) ClientHud.send(player, "$head${if (first) kills else 0}|$line")
    }

    // ---- persistence: records separated by '\n', fields by '|', items "id:qty" by ',' ----------------------------------

    fun load(player: Player): MutableList<Record> {
        val out = ArrayList<Record>()
        for (line in (player.attr[SAVED] ?: "").split('\n')) {
            val parts = line.split('|')
            if (parts.size < 4) continue
            val items = LinkedHashMap<Int, Long>()
            for (pair in parts[3].split(',')) {
                val colon = pair.indexOf(':')
                if (colon <= 0) continue
                val id = pair.substring(0, colon).toIntOrNull() ?: continue
                val qty = pair.substring(colon + 1).toLongOrNull() ?: continue
                items[id] = qty
            }
            out += Record(parts[0], parts[1].toIntOrNull() ?: -1, parts[2].toIntOrNull() ?: 0, items)
        }
        return out
    }

    private fun store(
        player: Player,
        records: List<Record>,
    ) {
        if (records.isEmpty()) {
            player.attr.remove(SAVED)
            return
        }
        player.attr[SAVED] =
            records.takeLast(MAX_SOURCES).joinToString("\n") { r ->
                "${r.name}|${r.level}|${r.kills}|" + r.items.entries.take(MAX_ITEMS).joinToString(",") { "${it.key}:${it.value}" }
            }
    }

    private fun save(
        player: Player,
        name: String,
        level: Int,
        items: Map<Int, Long>,
    ) {
        val records = load(player)
        val record =
            records.firstOrNull { it.name == name && it.level == level }
                ?: Record(name, level, 0, LinkedHashMap()).also { records += it }
        record.kills++
        for ((id, qty) in items) record.items.merge(id, qty, Long::plus)
        store(player, records)
    }

    /** Names travel between '|' separators and are shown in Swing labels: no separators, no markup. */
    private fun clean(name: String): String = name.replace(Regex("<[^>]*>"), "").filterNot { it in "|;,\n" }.trim().take(40)

    /** RuneLite QuantityFormatter.quantityToStackSize, line for line (below 10,000 exact, else 4 significant chars + K/M/B). */
    fun stackSize(quantity: Long): String {
        if (quantity < 0) return "-" + stackSize(if (quantity == Long.MIN_VALUE) Long.MAX_VALUE else -quantity)
        val formatter = NumberFormat.getInstance(Locale.ENGLISH)
        if (quantity < 10_000) return formatter.format(quantity)
        val suffixes = arrayOf("", "K", "M", "B")
        var suffix = suffixes[0]
        var divideBy = 1L
        for (i in suffixes.indices.reversed()) {
            divideBy = Math.pow(10.0, i * 3.0).toLong()
            if (quantity.toDouble() / divideBy >= 1) {
                suffix = suffixes[i]
                break
            }
        }
        var text = formatter.format(quantity.toDouble() / divideBy)
        if (text.length > 4) text = text.substring(0, 4)
        return (if (text.endsWith(".")) text.substring(0, 3) else text) + suffix
    }
}
