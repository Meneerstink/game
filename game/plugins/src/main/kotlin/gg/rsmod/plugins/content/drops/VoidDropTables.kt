package gg.rsmod.plugins.content.drops

import com.google.gson.Gson
import java.io.File
import java.io.FileReader

/**
 * RCV-011 Q-043-d: Void-format drop tables (generated JSON of Void `*.drops.toml` rows, item keys already resolved to
 * 667 ids) turned into this server's [DropTableBuilder] DSL.
 *
 * Void semantics (`DropTable.collect`, `DropTables.load`): an `all` table awards every item and rolls every referenced
 * table once; a `first` table draws one number in `[0, roll)` and walks its entries' cumulative `chance`, awarding the
 * first entry whose running total exceeds the draw (nothing past the last entry); a reference may override the
 * referenced table's `roll`. That is exactly the slot model of [TableBuilder] (entry index = running total, a uniform
 * draw below the last index), so every Void probability is reproduced 1:1. Shapes Void allows but these tables do not
 * use (a reference without `chance` inside a `first` table, an `all` table referenced from a `first` table) are
 * rejected instead of approximated.
 */
object VoidDropTables {
    class Drop(
        val key: String? = null,
        val item: Int = -1,
        val min: Int = 1,
        val max: Int = 1,
        val chance: Int = 1,
        val table: String? = null,
        val roll: Int? = null,
    )

    class Table(
        val type: String = "first",
        val roll: Int = 1,
        val drops: List<Drop> = emptyList(),
    )

    class Excluded(
        val table: String = "",
        val entry: String = "",
        val reason: String = "",
    )

    class Document(
        val source: String = "",
        val npcs: Map<String, String> = emptyMap(),
        val tables: Map<String, Table> = emptyMap(),
        val excluded: List<Excluded> = emptyList(),
    )

    fun load(file: File): Document = FileReader(file).use { Gson().fromJson(it, Document::class.java) }

    /**
     * The DSL table of Void table [root] (an `all` table), plus [extra] tables appended by the caller.
     * Validated once here; the returned lambda is cheap to apply per kill.
     */
    fun builder(
        doc: Document,
        root: String,
        extra: DropTableBuilder.() -> Unit = {},
    ): DropTableBuilder.() -> Unit {
        val fixed = ArrayList<Drop>()
        val ranged = ArrayList<Drop>()
        val rolled = ArrayList<Pair<String, Int>>()

        fun walkAll(name: String) {
            val table = doc.tables[name] ?: error("unknown drop table '$name'")
            require(table.type == "all") { "'$name' must be an all table" }
            table.drops.forEach { drop ->
                val ref = drop.table
                if (ref != null) {
                    require(drop.chance == -1) { "'$name': a reference inside an all table carries no chance" }
                    val target = doc.tables[ref] ?: error("unknown drop table '$ref'")
                    if (target.type == "all") walkAll(ref) else rolled += ref to (drop.roll ?: target.roll).also { validate(doc, ref, it) }
                } else if (drop.min == drop.max) {
                    fixed += drop
                } else {
                    ranged += drop
                }
            }
        }
        walkAll(root)

        return {
            if (fixed.isNotEmpty()) {
                guaranteed { fixed.forEach { obj(it.item, quantity = it.min) } }
            }
            // Guaranteed rows only hand out fixed amounts, so an always-awarded range is its own one-slot table.
            ranged.forEachIndexed { index, drop ->
                table("range_${index}_${drop.key}") {
                    total(1)
                    obj(drop.item, quantityRange = drop.min..drop.max, slots = 1)
                }
            }
            rolled.forEachIndexed { index, (name, roll) ->
                table("roll_${index}_$name") { fill(doc, this, name, roll) }
            }
            extra()
        }
    }

    private fun validate(
        doc: Document,
        name: String,
        roll: Int,
    ) {
        val table = doc.tables[name] ?: error("unknown drop table '$name'")
        require(table.type != "all") { "'$name': an all table cannot be rolled" }
        require(roll >= 1) { "'$name': roll $roll" }
        var used = 0
        table.drops.forEach { drop ->
            require(drop.chance >= 1) { "'$name': every entry of a rolled table needs a chance" }
            drop.table?.let { validate(doc, it, drop.roll ?: (doc.tables[it] ?: error("unknown drop table '$it'")).roll) }
            used += drop.chance
        }
        require(used <= roll) { "'$name': chances $used exceed roll $roll" }
    }

    private fun fill(
        doc: Document,
        builder: TableBuilder,
        name: String,
        roll: Int,
    ) {
        val table = doc.tables.getValue(name)
        builder.total(roll)
        var used = 0
        table.drops.forEach { drop ->
            val ref = drop.table
            if (ref != null) {
                val nestedRoll = drop.roll ?: doc.tables.getValue(ref).roll
                builder.table({ main { fill(doc, this, ref, nestedRoll) } }, slots = drop.chance)
            } else if (drop.min == drop.max) {
                builder.obj(drop.item, quantity = drop.min, slots = drop.chance)
            } else {
                builder.obj(drop.item, quantityRange = drop.min..drop.max, slots = drop.chance)
            }
            used += drop.chance
        }
        if (used < roll) builder.nothing(roll - used)
    }
}
