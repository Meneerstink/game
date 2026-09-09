package gg.rsmod.plugins.content.npcs.bulk

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.plugins.content.drops.DropTableBuilder
import java.io.File
import java.io.FileReader

/**
 * Bulk, data-sourced npc drop tables (`data/cfg/npcs/drop-tables.json`), the loot counterpart of
 * [BulkNpcCombatDefs]. Generated offline by `C:\RSPS\tools\npc-combat-defs\generate-drops.js`
 * from the osrsbox (2007-era) monster drop lists; every item was matched to the 667 cache by id
 * and name before it was written, and every npc by name and combat level. See that script's
 * header for the provenance and exclusion rules.
 *
 * Tables are expressed in slots out of [TOTAL] for one roll of the main table, plus independent
 * tertiary rolls and a guaranteed list, which maps directly onto [DropTableBuilder]. They are
 * only registered for npc ids that have no hand-written drop table, so curated tables win.
 */
object BulkNpcDropTables {
    const val DEFAULT_PATH = "./data/cfg/npcs/drop-tables.json"
    const val TOTAL = 100_000

    class Drop(
        val item: Int = -1,
        val min: Int = 1,
        val max: Int = 1,
        val noted: Boolean = false,
        val slots: Int = 0,
    )

    class Row(
        val id: Int = -1,
        val name: String = "",
        @SerializedName("osrs_id") val osrsId: Int = -1,
        val guaranteed: List<Drop> = emptyList(),
        val main: List<Drop> = emptyList(),
        val tertiary: List<Drop> = emptyList(),
    )

    data class Result(
        val tables: Map<Int, DropTableBuilder.() -> Unit>,
        val skippedUnknownNpc: Int,
        val droppedUnknownItem: Int,
        val droppedUnnotable: Int,
    )

    fun load(
        definitions: DefinitionSet,
        file: File = File(DEFAULT_PATH),
    ): Result {
        val rows: Array<Row> = FileReader(file).use { Gson().fromJson(it, Array<Row>::class.java) }
        return build(rows.asList(), definitions)
    }

    fun build(
        rows: List<Row>,
        definitions: DefinitionSet,
    ): Result {
        val notedIds = HashMap<Int, Int>()
        definitions.getAllKeys(ItemDef::class.java).forEach { id ->
            val def = definitions.getNullable(ItemDef::class.java, id) ?: return@forEach
            if (def.noted && def.noteLinkId > 0) {
                notedIds[def.noteLinkId] = id
            }
        }

        var skipped = 0
        var unknownItem = 0
        var unnotable = 0
        val tables = LinkedHashMap<Int, DropTableBuilder.() -> Unit>()

        fun resolve(drop: Drop): Pair<Int, IntRange>? {
            if (drop.item < 0 || definitions.getNullable(ItemDef::class.java, drop.item) == null) {
                unknownItem++
                return null
            }
            val id =
                if (drop.noted) {
                    notedIds[drop.item] ?: run {
                        unnotable++
                        return null
                    }
                } else {
                    drop.item
                }
            val min = drop.min.coerceAtLeast(1)
            val max = drop.max.coerceAtLeast(min)
            return id to (min..max)
        }

        rows.forEach { row ->
            if (row.id < 0 || definitions.getNullable(NpcDef::class.java, row.id) == null) {
                skipped++
                return@forEach
            }
            val guaranteed = row.guaranteed.mapNotNull(::resolve)
            val main = row.main.mapNotNull { d -> resolve(d)?.let { Triple(it.first, it.second, d.slots.coerceAtLeast(1)) } }
            val tertiary = row.tertiary.mapNotNull { d -> resolve(d)?.let { Triple(it.first, it.second, d.slots.coerceAtLeast(1)) } }
            if (guaranteed.isEmpty() && main.isEmpty() && tertiary.isEmpty()) {
                return@forEach
            }
            val mainSlots = main.sumOf { it.third }
            require(mainSlots <= TOTAL) { "drop-tables.json row ${row.id} (${row.name}): main table uses $mainSlots of $TOTAL slots" }

            tables[row.id] = {
                if (guaranteed.isNotEmpty()) {
                    guaranteed {
                        guaranteed.forEach { (id, range) ->
                            if (range.first == range.last) obj(id, quantity = range.first) else obj(id, quantityRange = range)
                        }
                    }
                }
                if (main.isNotEmpty()) {
                    main {
                        total(TOTAL)
                        main.forEach { (id, range, slots) ->
                            if (range.first == range.last) obj(id, quantity = range.first, slots = slots) else obj(id, quantityRange = range, slots = slots)
                        }
                        if (mainSlots < TOTAL) {
                            nothing(TOTAL - mainSlots)
                        }
                    }
                }
                tertiary.forEachIndexed { index, (id, range, slots) ->
                    table("tertiary_$index") {
                        total(TOTAL)
                        val used = slots.coerceAtMost(TOTAL)
                        if (range.first == range.last) obj(id, quantity = range.first, slots = used) else obj(id, quantityRange = range, slots = used)
                        if (used < TOTAL) {
                            nothing(TOTAL - used)
                        }
                    }
                }
            }
        }
        return Result(tables, skipped, unknownItem, unnotable)
    }
}
