package gg.rsmod.plugins.content.quests.tab

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.tools.importer.N108InterfaceImportTool.V

/**
 * N279 (owner 2026-10-24: "echte data, controleer de hele Drop Viewer"): checks EVERY entry of the Drop Viewer with the real tables
 * of the running server, at the player's own bracket - `::dvaudit` writes the report (problems first, then every entry with its
 * groups and lines) to `logs/dropviewer-audit.txt`.
 *
 * Per entry: a name, a model (the chest its loc model), every line in exactly one non-empty group in the design's order, the
 * rarest first inside a group (charms in their fixed order), every drop table open right under its line, the rows fit the
 * table's slots, and nothing overlaps: the name + star left of "Show on map", the labels and the bracket line inside the window,
 * the Always bar inside the table, every item name inside its column (cut with "..." when it has to be).
 */
object DropViewerAudit {
    const val REPORT = "logs/dropviewer-audit.txt"

    /** "Show on map" starts at x 408; the star (12 wide) + 3 px must end before it. */
    private const val STAR_LIMIT = 408 - 3 - 12

    fun run(player: Player): Pair<List<String>, List<String>> {
        val world = player.world
        val problems = ArrayList<String>()
        val dump = ArrayList<String>()
        val sources = DropSources.all(player)
        for (s in sources) {
            fun bad(what: String) {
                problems += "${s.name} (${s.key}): $what"
            }
            if (s.name.isBlank()) bad("no name")
            if (s.kind != DropSources.Kind.CHEST && s.modelNpc <= 0) bad("no model")
            if (s.kind == DropSources.Kind.CHEST && DropViewer.chestModel(player) < 0) bad("no chest model")
            val starX = DropViewer.starX(world, DropViewer.headName(world, s.name))
            if (starX > STAR_LIMIT) bad("name + star reach Show on map (star at $starX)")
            val view = DropSources.view(player, s)
            val rows = DropViewer.rowsOf(player, s, view)
            if (rows.size > V.ROW_SLOTS) bad("${rows.size} rows, the table has ${V.ROW_SLOTS}")
            // Every top-level line shows once, under a heading, and no heading is empty.
            val shown = rows.filter { r -> r.line != null && r.level == 0 && view.always.none { it === r.line } }.map { it.line!!.key }
            if (shown.sorted() != view.lines.map { it.key }.sorted()) bad("lines missing or doubled: ${view.lines.size} lines, ${shown.size} shown")
            rows.forEachIndexed { i, r ->
                if (r.header != null && (rows.getOrNull(i + 1)?.line == null)) bad("empty group ${r.header}")
            }
            val groups = DropGroups.grouped(world, view.lines, DropViewer.uniques(player, s))
            val order = groups.map { it.first.ordinal }
            if (order != order.sorted()) bad("groups out of order: ${groups.map { it.first.title }}")
            for ((g, lines) in groups) {
                if (g == DropGroups.Group.CHARMS) continue
                val keys = lines.map { if (it.normal <= 0.0) Double.MAX_VALUE else 1.0 / it.normal }
                for (k in 1 until keys.size) if (keys[k] > keys[k - 1] * (1 + 1e-9)) bad("${g.title}: ${lines[k].name} (1/${keys[k]}) under a commoner line")
            }
            // Tables open: every kid right under its table.
            rows.forEachIndexed { i, r ->
                val l = r.line ?: return@forEachIndexed
                if (l.tag == DropSources.Tag.TABLE && l.kids.isNotEmpty() && rows.getOrNull(i + 1)?.level != r.level + 1) bad("${l.name} is not open")
            }
            // Head widths.
            val sub = N108Ui.p11(world, subPlain(player, s))
            if (V.NAME_X + sub > DropViewer.WINDOW_RIGHT) bad("the line under the name runs out of the window ($sub px)")
            val (a, b, c) = DropViewer.bracketLine(player, s)
            val end = DropViewer.BRACKET_X + N108Ui.p11(world, a) + 4 + N108Ui.b12(world, b) + 4 + N108Ui.p11(world, c)
            if (end > DropViewer.WINDOW_RIGHT) bad("the bracket line runs out of the window ($end)")
            // The Always items: all in the bar, or all at the head of the table.
            if (!DropViewer.alwaysInBar(world, view.always)) {
                val listed = rows.count { r -> r.line != null && r.level == 0 && view.always.any { it === r.line } }
                if (listed != view.always.size) bad("${view.always.size} Always items, $listed in the table")
            }
            // The dump: what the player sees, line by line.
            dump += ""
            dump += "== ${s.name} [${s.group.title} / ${s.kind}] level ${s.level} key ${s.key} npcs ${s.npcs.take(6).joinToString(",")}${if (s.npcs.size > 6) ",..." else ""}"
            dump += "   Always: " + (view.always.joinToString(", ") { "${it.name} x${it.quantity}" }.ifEmpty { "nothing" })
            for (r in rows) {
                val l = r.line
                if (l == null) {
                    dump += "   -- ${r.header}"
                    continue
                }
                val name = N108Ui.fit(world, "p12_full", l.name + (l.note?.let { " ($it)" } ?: ""), DropViewer.nameRoom(r.level, false))
                dump += "   " + "    ".repeat(r.level + 1) + "$name | ${l.quantity} | ${N108Ui.chance(l.your)} | ${N108Ui.chance(l.normal)}" + if (name != l.name && l.note == null) "  [cut: ${l.name}]" else ""
            }
        }
        val header = listOf("Drop Viewer audit: ${sources.size} entries, bracket ${gg.rsmod.plugins.content.mechanics.pvp.annihilation.AnnihilationTier.of(player).label}, ${problems.size} problems")
        return (header + problems.map { "PROBLEM $it" }) to dump
    }

    private fun subPlain(player: Player, s: DropSources.Source): String = Regex("<[^>]*>").replace(DropViewer.subLine(player, s), "")

    fun write(player: Player): List<String> {
        val (head, dump) = run(player)
        java.io.File(REPORT).apply { parentFile?.mkdirs() }.writeText((head + dump).joinToString("\n"))
        return head
    }
}
