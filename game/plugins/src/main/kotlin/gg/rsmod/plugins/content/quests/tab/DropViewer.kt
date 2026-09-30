package gg.rsmod.plugins.content.quests.tab

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentItem
import gg.rsmod.plugins.api.ext.setComponentText
import gg.rsmod.plugins.api.ext.setInterfaceEvents
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.content.drops.DropEntry
import gg.rsmod.plugins.content.drops.DropTable
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.game.tools.importer.QuestTabInterfaceImportTool as L

/**
 * Drop Viewer (owner 2026-09-30, reference "dropviewer.png"): opened from the Deadman panel's "Drops" button (quest tab,
 * Deadman). Every row is read from the drop table the game itself rolls ([DropTableFactory], the same builder a kill uses),
 * never from a second list: each table rolls once per kill against `max(declared total, occupied slots)` slots, the
 * guaranteed table always drops, and a nested table multiplies its own chance in - so "Chance" is the real per-kill chance.
 *
 * Find NPC / Find Drop ask for a name and list the matching sources (10 at a time) on the left; clicking one shows it.
 * Previous / Next Page page through the rows ([L.DV_ROWS] per page).
 */
object DropViewer {
    const val INTERFACE = L.DROPS

    class Row(
        val item: Int,
        val quantity: String,
        val chance: Double,
    )

    private val NPC = AttributeKey<Int>()
    private val PAGE = AttributeKey<Int>()
    private val RESULTS = AttributeKey<List<Int>>()

    private const val EVENTS_OP1 = 0x2

    /** King Black Dragon, the reference screenshot's source, when it has a table; else the first table. */
    private const val DEFAULT_NPC = 50

    /** Rows per npc id; the tables are built once (the builder only needs a player for conditional tables). */
    private val cache = HashMap<Int, List<Row>>()

    fun rows(
        player: Player,
        npcId: Int,
    ): List<Row> =
        cache.getOrPut(npcId) {
            val built = DropTableFactory.tables(player, npcId) ?: return@getOrPut emptyList()
            val out = ArrayList<Row>()
            for (table in built) {
                if (table.name.equals(gg.rsmod.plugins.content.drops.GUARANTEED_TABLE_NAME, ignoreCase = true)) {
                    collect(table, 1.0, out, guaranteed = true)
                } else {
                    collect(table, 1.0, out, guaranteed = false)
                }
            }
            // Same item and quantity from two tables: one row, chances added (at most "Always").
            val merged = LinkedHashMap<String, Row>()
            for (row in out) {
                val key = "${row.item}|${row.quantity}"
                val have = merged[key]
                merged[key] = if (have == null) row else Row(row.item, row.quantity, minOf(1.0, have.chance + row.chance))
            }
            merged.values.sortedWith(compareByDescending<Row> { it.chance }.thenBy { it.item })
        }

    private fun collect(
        table: DropTable,
        weight: Double,
        out: MutableList<Row>,
        guaranteed: Boolean,
    ) {
        val entries = table.entries
        if (entries.isEmpty()) return
        val occupied = entries.last().index
        val denominator = maxOf(table.totalSlots, occupied).toDouble()
        var previous = 0
        for (entry in entries) {
            val chance = if (guaranteed) weight else weight * (entry.index - previous) / denominator
            previous = entry.index
            when (val drop = entry.drop) {
                is DropEntry.ItemDrop -> out += Row(drop.item.id, drop.item.amount.toString(), chance)
                is DropEntry.ItemRangeDrop -> out += Row(drop.item.id, "${drop.quantityRange.first}-${drop.quantityRange.last}", chance)
                is DropEntry.MultiDrop -> drop.items.forEach { out += Row(it.id, it.amount.toString(), chance) }
                is DropEntry.TableDrop -> collect(drop.table, chance, out, guaranteed = false)
                DropEntry.NothingDrop -> Unit
            }
        }
    }

    fun open(
        player: Player,
        npcId: Int? = null,
    ) {
        val ids = DropTableFactory.tableIds()
        val npc = npcId ?: player.attr[NPC] ?: (if (DEFAULT_NPC in ids) DEFAULT_NPC else ids.minOrNull() ?: return)
        player.openInterface(INTERFACE, InterfaceDestination.MAIN_SCREEN)
        // The window's X, like the Info window's (QuestTab.openInfo): without events the client never sends the click.
        player.setInterfaceEvents(INTERFACE, L.I_CLOSE, -1..-1, EVENTS_OP1)
        player.setInterfaceEvents(INTERFACE, L.DV_FIND_DROP, -1..-1, EVENTS_OP1)
        player.setInterfaceEvents(INTERFACE, L.DV_FIND_NPC, -1..-1, EVENTS_OP1)
        player.setInterfaceEvents(INTERFACE, L.DV_PREVIOUS, -1..-1, EVENTS_OP1)
        player.setInterfaceEvents(INTERFACE, L.DV_NEXT, -1..-1, EVENTS_OP1)
        for (i in 0 until L.DV_RESULTS) player.setInterfaceEvents(INTERFACE, L.DV_RESULT_FIRST + i, -1..-1, EVENTS_OP1)
        showResults(player, player.attr[RESULTS] ?: emptyList())
        show(player, npc, 0)
    }

    fun isOpen(player: Player): Boolean = player.interfaces.isVisible(INTERFACE)

    /** Shows [npcId]'s drops from [page]. */
    fun show(
        player: Player,
        npcId: Int,
        page: Int,
    ) {
        player.attr[NPC] = npcId
        val world = player.world
        val def = world.definitions.getNullable(NpcDef::class.java, npcId)
        val name = def?.name?.takeIf { it.isNotBlank() && it != "null" } ?: "NPC $npcId"
        player.setComponentText(INTERFACE, L.I_TITLE, name)
        player.setComponentText(INTERFACE, L.DV_STATS, stats(player, npcId))
        val rows = rows(player, npcId)
        val pages = maxOf(1, (rows.size + L.DV_ROWS - 1) / L.DV_ROWS)
        val shown = page.coerceIn(0, pages - 1)
        player.attr[PAGE] = shown
        for (r in 0 until L.DV_ROWS) {
            val base = L.dvRow(r)
            val row = rows.getOrNull(shown * L.DV_ROWS + r)
            if (row == null) {
                player.setComponentHidden(INTERFACE, base + L.DV_ROW_ITEM, hidden = true)
                player.setComponentText(INTERFACE, base + L.DV_ROW_NAME, "")
                player.setComponentText(INTERFACE, base + L.DV_ROW_QUANTITY, "")
                player.setComponentText(INTERFACE, base + L.DV_ROW_RARITY, "")
                continue
            }
            val item = world.definitions.getNullable(ItemDef::class.java, row.item)
            player.setComponentItem(INTERFACE, base + L.DV_ROW_ITEM, row.item, row.quantity.substringBefore('-').toIntOrNull() ?: 1)
            player.setComponentHidden(INTERFACE, base + L.DV_ROW_ITEM, hidden = false)
            player.setComponentText(INTERFACE, base + L.DV_ROW_NAME, item?.name ?: "Item ${row.item}")
            player.setComponentText(INTERFACE, base + L.DV_ROW_QUANTITY, "<col=ff981f>Amount:</col><br>${row.quantity}")
            player.setComponentText(INTERFACE, base + L.DV_ROW_RARITY, "<col=ff981f>Chance:</col><br><col=${colour(row.chance)}>${rarity(row.chance)}</col>")
        }
        val empty = rows.isEmpty()
        player.setComponentHidden(INTERFACE, L.DV_EMPTY, hidden = !empty)
        if (empty) player.setComponentText(INTERFACE, L.DV_EMPTY, "$name has no drop table.")
        player.setComponentText(INTERFACE, L.DV_PAGE_TEXT, "Page ${shown + 1} / $pages")
    }

    fun page(
        player: Player,
        delta: Int,
    ) {
        val npc = player.attr[NPC] ?: return
        show(player, npc, (player.attr[PAGE] ?: 0) + delta)
    }

    /** "Always", or "1 / N" rounded like the wiki's drop tables. */
    fun rarity(chance: Double): String =
        when {
            chance >= 0.9999 -> "Always"
            chance <= 0.0 -> "Never"
            else -> {
                val n = 1.0 / chance
                if (n < 10) "1 / ${"%.1f".format(java.util.Locale.US, n).removeSuffix(".0")}" else "1 / ${Math.round(n)}"
            }
        }

    /** The reference's colours: Always blue, common green, uncommon orange, rare red. */
    fun colour(chance: Double): String =
        when {
            chance >= 0.9999 -> "3d7dff"
            chance >= 1.0 / 25 -> "0dc10d"
            chance >= 1.0 / 200 -> "ff981f"
            else -> "ff0000"
        }

    private fun stats(
        player: Player,
        npcId: Int,
    ): String {
        val def = player.world.definitions.getNullable(NpcDef::class.java, npcId)
        val combat = player.world.plugins.allNpcCombatDefs()[npcId]
        val level = def?.combatLevel ?: -1
        val parts = ArrayList<String>()
        if (level > 0) parts += "Combat: <col=ffffff>$level</col>"
        if (combat != null) {
            parts += "Hitpoints: <col=ffffff>${combat.lifepoints}</col>"
            parts += "Attack style: <col=ffffff>${combat.attackStyleType.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }}</col>"
            parts += "Aggressive: <col=ffffff>${if (combat.aggressiveRadius > 0) "Yes" else "No"}</col>"
            if (combat.slayerReq > 1) parts += "Slayer: <col=ffffff>${combat.slayerReq}</col>"
        }
        val rows = rows(player, npcId).size
        return "<col=ff981f>" + parts.joinToString("   ") + "</col><br><col=ff981f>Drops: <col=ffffff>$rows</col></col>"
    }

    // ---- Find NPC / Find Drop --------------------------------------------------------------------------------------------

    /** Sources with a drop table whose name contains [query]: one per name + level. */
    fun findNpc(
        player: Player,
        query: String,
    ): List<Int> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return distinct(player, DropTableFactory.tableIds().filter { id -> npcName(player, id).lowercase().contains(q) })
    }

    /** Sources whose table can drop an item whose name contains [query]. */
    fun findDrop(
        player: Player,
        query: String,
    ): List<Int> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val defs = player.world.definitions
        return distinct(
            player,
            DropTableFactory.tableIds().filter { id ->
                rows(player, id).any { row -> defs.getNullable(ItemDef::class.java, row.item)?.name?.lowercase()?.contains(q) == true }
            },
        )
    }

    private fun distinct(
        player: Player,
        ids: List<Int>,
    ): List<Int> {
        val seen = HashSet<String>()
        return ids.sorted().filter { id ->
            val name = npcName(player, id)
            name.isNotBlank() && seen.add("$name|${player.world.definitions.getNullable(NpcDef::class.java, id)?.combatLevel}")
        }.sortedBy { npcName(player, it) }
    }

    fun npcName(
        player: Player,
        id: Int,
    ): String = player.world.definitions.getNullable(NpcDef::class.java, id)?.name?.takeIf { it != "null" } ?: ""

    fun showResults(
        player: Player,
        ids: List<Int>,
    ) {
        player.attr[RESULTS] = ids
        for (i in 0 until L.DV_RESULTS) {
            val id = ids.getOrNull(i)
            player.setComponentHidden(INTERFACE, L.DV_RESULT_FIRST + i, hidden = id == null)
            if (id != null) {
                val level = player.world.definitions.getNullable(NpcDef::class.java, id)?.combatLevel ?: -1
                player.setComponentText(INTERFACE, L.DV_RESULT_FIRST + i, npcName(player, id) + if (level > 0) " ($level)" else "")
            }
        }
    }

    fun result(
        player: Player,
        index: Int,
    ) {
        val id = player.attr[RESULTS]?.getOrNull(index) ?: return
        show(player, id, 0)
    }
}
