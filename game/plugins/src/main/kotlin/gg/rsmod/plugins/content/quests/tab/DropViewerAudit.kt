package gg.rsmod.plugins.content.quests.tab

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.tools.importer.N108InterfaceImportTool.V
import gg.rsmod.plugins.content.mechanics.pvp.chest.DeadmanChestLoot
import gg.rsmod.plugins.content.skills.summoning.Pets

/**
 * N279 (owner 2026-10-24: "echte data, controleer de hele Drop Viewer"): checks EVERY entry of the Drop Viewer with the real tables
 * of the running server, at the player's own bracket - `::dvaudit` writes the report (problems first, then every entry with its
 * groups and lines) to `logs/dropviewer-audit.txt`.
 *
 * N315 / N319 (owner 2026-10-24: "telkens halve fixes" - the audit said 0 problems while the sort followed a column the player does
 * not read and the Qty column was never measured): it checks what the PLAYER sees, column by column -
 *  - a name, a model, every line once in exactly one non-empty group in the design's order (Extra rolls under Pets, N304);
 *  - inside a group AND inside every drop table: the rarest first on YOUR chance (N315), charms in their fixed order;
 *  - every drop table open right under its line, the rows fit the table's slots;
 *  - every column measured in its own font: the name (cut names are counted and listed), the second line, the quantity (46 px,
 *    "(noted)" on its own line - N309), Your chance (b12) and Normal (p11) inside their cells;
 *  - the head: name + star left of "Show on map", the line under the name and the bracket line inside the window; the Always bar
 *    (pictures in their own size, a note on its second line - N307) inside the table; the list names;
 *  - the pets (N306 / N313 / N316): a pet line is the roll's own chance (OSRS x the bracket, Normal = OSRS), the chest has no pet
 *    whose boss exists here, a breach monster shows only its own pet.
 */
object DropViewerAudit {
    const val REPORT = "logs/dropviewer-audit.txt"

    /** "Show on map" starts at x 408; the star (12 wide) + 3 px must end before it. */
    private const val STAR_LIMIT = 408 - 3 - 12

    /** The room a text has inside its cell: the cell minus 1 px each side. */
    private const val PAD = 2

    fun run(player: Player): Pair<List<String>, List<String>> {
        val world = player.world
        val problems = ArrayList<String>()
        val dump = ArrayList<String>()
        val cuts = ArrayList<String>()
        val sources = DropSources.all(player)
        val rate = DropViewer.rate(player)
        val bossPets = Pets.infos.values.filter { it.dropOnly }.mapNotNull { Pets.baseItem(it.pet) }.toSet()
        var lineCount = 0
        val minionKeys = sources.flatMap { DropViewer.minionsOf(sources, it) }.map { it.key }.toSet()
        for (s in sources) {
            fun bad(what: String) {
                problems += "${s.name} (${s.key}): $what"
            }
            if (s.name.isBlank()) bad("no name")
            if (s.kind != DropSources.Kind.CHEST && s.modelNpc <= 0) bad("no model")
            if (s.kind == DropSources.Kind.CHEST && DropViewer.chestModel(player) < 0) bad("no chest model")
            val starX = DropViewer.starX(world, DropViewer.headName(world, s.name))
            if (starX > STAR_LIMIT) bad("name + star reach Show on map (star at $starX)")
            // The list name (p11, 100 px; a bodyguard under its general 10 px in, N311).
            val listRoom = if (s.key in minionKeys) 90 else 100
            if (DropViewer.wrapTwo(world, "p11_full", s.name, listRoom).second) cuts += "list: ${s.name} (${N108Ui.p11(world, s.name)} px)"
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
            // N315: the rarest first on the chance the player reads (Your chance), in every group and every table.
            fun rarestFirst(where: String, lines: List<DropSources.Line>) {
                val keys = lines.map { val p = if (it.your > 0.0) it.your else it.normal; if (p <= 0.0) Double.MAX_VALUE else 1.0 / p }
                for (k in 1 until keys.size) if (keys[k] > keys[k - 1] * (1 + 1e-9)) bad("$where: ${lines[k].name} (your 1/${keys[k]}) under a commoner line")
            }
            for ((g, lines) in groups) if (g != DropGroups.Group.CHARMS) rarestFirst(g.title, lines)
            rows.forEachIndexed { i, r ->
                val l = r.line ?: return@forEachIndexed
                if (l.tag != DropSources.Tag.TABLE || l.kids.isEmpty()) return@forEachIndexed
                // Tables open: every kid right under its table, in the order the player reads.
                if (rows.getOrNull(i + 1)?.level != r.level + 1) bad("${l.name} is not open")
                val kids = rows.drop(i + 1).takeWhile { it.line != null && it.level > r.level }.filter { it.level == r.level + 1 }.mapNotNull { it.line }
                rarestFirst("table ${l.name}", kids)
            }
            // Every column of every line, measured in its own font (what the player reads).
            for (r in rows) {
                val l = r.line ?: continue
                lineCount++
                val table = l.tag == DropSources.Tag.TABLE
                val full = DropViewer.plainName(l.name) + (l.note?.let { " ($it)" } ?: "")
                if (DropViewer.rowName(world, l, DropViewer.nameRoom(r.level, table), l.sub != null).cut) cuts += "${s.name}: $full"
                l.sub?.let { if (N108Ui.p11(world, it) > DropViewer.nameRoom(r.level, table)) bad("second line of ${l.name} does not fit: $it") }
                if (!table) {
                    val qty = DropViewer.qtyCell(world, l)
                    qty.split("<br>").forEach { part ->
                        if (N108Ui.p11(world, part) > DropViewer.QTY_ROOM) bad("Qty '$part' of ${l.name} is wider than the column (${N108Ui.p11(world, part)} px)")
                    }
                    if (qty.split("<br>").size > 2) bad("Qty of ${l.name} takes more than two lines")
                }
                val your = N108Ui.chance(l.your)
                if (N108Ui.b12(world, your) > V.YOUR_W - PAD) bad("Your chance '$your' of ${l.name} is wider than its cell (${N108Ui.b12(world, your)} px)")
                val normal = N108Ui.chance(l.normal)
                if (N108Ui.p11(world, normal) > V.NORMAL_W - PAD) bad("Normal '$normal' of ${l.name} is wider than its cell (${N108Ui.p11(world, normal)} px)")
            }
            // The pets: the roll's own chance; the chest without a boss's pet; a breach monster only its own pet.
            for (r in rows) {
                val l = r.line ?: continue
                if (l.tag != DropSources.Tag.PET) continue
                when (s.kind) {
                    DropSources.Kind.CHEST -> if (l.item in bossPets) bad("the chest shows ${l.name}, whose boss exists here")
                    DropSources.Kind.NPC, DropSources.Kind.BREACH, DropSources.Kind.JAD -> {
                        val npc = if (s.kind == DropSources.Kind.NPC) s.tableNpc else s.modelNpc
                        val own = Pets.dropFor(npc)?.let { Pets.baseItem(it.pet) }
                        if (l.item != own) bad("pet ${l.name} is not this monster's own pet")
                        if (Math.abs(l.your - Pets.dropChance(npc, rate)) > 1e-12) bad("pet ${l.name}: Your chance ${N108Ui.chance(l.your)} is not OSRS x the bracket")
                        if (Math.abs(l.normal - Pets.dropChance(npc)) > 1e-12) bad("pet ${l.name}: Normal ${N108Ui.chance(l.normal)} is not the OSRS chance")
                    }
                    else -> bad("pet ${l.name} on a ${s.kind} entry")
                }
            }
            if (s.kind == DropSources.Kind.CHEST && DeadmanChestLoot.pets().any { Pets.baseItem(it.first) in bossPets }) bad("the chest list holds a boss's pet")
            if (s.kind == DropSources.Kind.BREACH) {
                view.lines.filter { it.tag == DropSources.Tag.TABLE }.flatMap { it.kids }.filter { Pets.isPetItem(it.item) }.forEach { bad("a chest pet in the breach: ${it.name}") }
            }
            // Head widths.
            val sub = N108Ui.p11(world, subPlain(player, s))
            if (V.NAME_X + sub > DropViewer.WINDOW_RIGHT) bad("the line under the name runs out of the window ($sub px)")
            val (a, b, c) = DropViewer.bracketLine(player, s)
            val end = DropViewer.BRACKET_X + N108Ui.p11(world, a) + 4 + N108Ui.b12(world, b) + 4 + N108Ui.p11(world, c)
            if (end > DropViewer.WINDOW_RIGHT) bad("the bracket line runs out of the window ($end)")
            // The Always items: all in the bar, or all at the head of the table; an item with a note stays in the bar (N307).
            if (!DropViewer.alwaysInBar(world, view.always)) {
                val listed = rows.count { r -> r.line != null && r.level == 0 && view.always.any { it === r.line } }
                if (listed != view.always.size) bad("${view.always.size} Always items, $listed in the table")
                if (view.always.any { it.note != null }) bad("an Always item with a note does not fit the bar: ${view.always.map { it.name }}")
            }
            // The dump: what the player sees, line by line.
            dump += ""
            dump += "== ${s.name} [${s.group.title} / ${s.kind}] level ${s.level} key ${s.key} npcs ${s.npcs.take(6).joinToString(",")}${if (s.npcs.size > 6) ",..." else ""}"
            dump += "   Always: " + (view.always.joinToString(", ") { "${it.name} x${it.quantity}" + (DropViewer.alwaysNote(it)?.let { n -> " $n" } ?: "") }.ifEmpty { "nothing" })
            for (r in rows) {
                val l = r.line
                if (l == null) {
                    dump += "   -- ${r.header}"
                    continue
                }
                val table = l.tag == DropSources.Tag.TABLE
                val name = DropViewer.rowName(world, l, DropViewer.nameRoom(r.level, table), l.sub != null).text.replace("<br>", " / ").replace(Regex("<[^>]*>"), "")
                val qty = if (table) "" else DropViewer.qtyCell(world, l).replace("<br>", " ").replace(Regex("<[^>]*>"), "")
                dump += "   " + "    ".repeat(r.level + 1) + "$name${l.sub?.let { " / $it" } ?: ""} | $qty | ${N108Ui.chance(l.your)} | ${N108Ui.chance(l.normal)}"
            }
        }
        val header = listOf(
            "Drop Viewer audit: ${sources.size} entries, $lineCount lines, bracket ${gg.rsmod.plugins.content.mechanics.pvp.annihilation.AnnihilationTier.of(player).label}, ${problems.size} problems, ${cuts.size} names cut with ...",
        )
        return (header + problems.map { "PROBLEM $it" } + cuts.map { "CUT $it" }) to dump
    }

    private fun subPlain(player: Player, s: DropSources.Source): String = Regex("<[^>]*>").replace(DropViewer.subLine(player, s), "")

    fun write(player: Player): List<String> {
        val (head, dump) = run(player)
        java.io.File(REPORT).apply { parentFile?.mkdirs() }.writeText((head + dump).joinToString("\n"))
        return head
    }
}
