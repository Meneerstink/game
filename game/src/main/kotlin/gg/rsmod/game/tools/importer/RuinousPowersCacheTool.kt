package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.tools.importer.ClientScriptEditor.Ins
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * N463 (owner 2026-10-31: "ik wil binnen 4 uurtjes ruinous prayers volledig werkend alles correct"; his screenshot of the OSRS Wiki
 * book: "zo zien ze eruit precies dit verzin geen dingen"): the OSRS Ruinous Powers book as a THIRD prayer book in the rev-667
 * prayer tab (interface 271), next to Normal and Ancient Curses. Everything comes from the OSRS cache (OpenRS2 2686, build 240):
 * the 24 prayers' names, levels and effect texts are the item params 1752/1753/1754 of enum 4959's objects, the tab icons are
 * sprites 4842-4889 (param 1757 coloured / 1756 grey), the overheads are frames 12/13/14/10/19/20/21 of OSRS headicons_prayer
 * (matched pixel for pixel against the Wiki's Dampen Melee/Ranged/Magic, Wrath, Rebuke, Purge and Vindication overhead files).
 *
 * How the 667 tab draws a book: clientscript 1237 builds one cell per entry of enum 2279 (normal) or 863 (curses, varbit 6840 = 1);
 * each entry is a struct with param 734 tooltip, 735 coloured sprite, 736 grey sprite, 737 level, 738 refusal text, 739 members;
 * scripts 2296 / 2297 map the struct to its active / quick-select varbit; 1388 builds the quick-select grid, 1293 the cell
 * backgrounds, 5 the quick highlight, 2294 / 2295 the click checks, 2299 / 2303 the quick-prayer texts. The Ruinous book rides the
 * curse layout (varbit 6840 = 1) with a new flag varbit [BOOK_FLAG_VARBIT]: three small new scripts answer "which enum"
 * ([ENUM_SCRIPT]), "how many cells" ([COUNT_SCRIPT]) and "is it really the curse book" ([IS_CURSES_SCRIPT]), and the existing
 * scripts call them where they had the curse enum 863, the curse count 20 or the curse test. The new prayers' state lives in
 * three new varps (active bits, quick-select bits, book flag), one varbit per prayer, so Normal and Curses keep their own saved
 * quick selections. Nothing of the two existing books changes when the flag is 0.
 *
 * One [CacheTransaction] over both production caches. Usage: `java -cp <game classpath> ...RuinousPowersCacheTool [--apply]`.
 */
object RuinousPowersCacheTool {
    val TARGETS = OsrsItemImportTool.TARGETS
    val SOURCE_CACHE = OsrsItemImportTool.SOURCE_CACHE

    private const val CONFIG_INDEX = 2
    private const val SPRITE_INDEX = 8
    private const val SCRIPT_INDEX = 12
    private const val ENUM_INDEX = 17
    private const val VARBIT_INDEX = 22
    private const val VARP_GROUP = 16
    private const val STRUCT_GROUP = 26

    /** One Ruinous prayer in the OSRS book's own order (by level, 5 per row - the owner's screenshot). */
    class RuinousCell(val name: String, val level: Int, val osrsColour: Int, val osrsGrey: Int, val text: String)

    /** Names, levels and texts: OSRS item params 1752 / 1753 / 1754 (build 240), copied verbatim. */
    val CELLS =
        listOf(
            RuinousCell("Rejuvenation", 60, 4842, 4866, "Hitpoints regenerate 3x faster."),
            RuinousCell("Ancient Strength", 61, 4843, 4867, "Increases your Attack and Strength by 20%."),
            RuinousCell("Ancient Sight", 62, 4844, 4868, "Increases your Ranged by 20%."),
            RuinousCell("Ancient Will", 63, 4845, 4869, "Increases your magic by 20% and magic strength by 3%."),
            RuinousCell("Protect Item", 65, 4865, 4889, "Protect one additional item on death."),
            RuinousCell("Ruinous Grace", 66, 4846, 4870, "When running, Prayer points are drained instead of run energy."),
            RuinousCell("Dampen Magic", 67, 4847, 4871, "Provides up to 100% protection from magic damage"),
            RuinousCell("Dampen Ranged", 69, 4848, 4872, "Provides up to 100% protection from ranged damage"),
            RuinousCell("Dampen Melee", 71, 4849, 4873, "Provides up to 100% protection from melee damage"),
            RuinousCell("Trinitas", 72, 4850, 4874, "Increases your Attack, Strength, Ranged and Magic by 15%. Increases your magic strength by 2%."),
            RuinousCell("Berserker", 74, 4851, 4875, "Boosted combat stats last 50% longer."),
            RuinousCell("Purge", 75, 4852, 4876, "1/3 of damage dealt is also removed from both yours and your opponent's Prayer points."),
            RuinousCell("Metabolise", 77, 4853, 4877, "The delay before you can attack after eating food is reduced."),
            RuinousCell("Rebuke", 78, 4857, 4881, "Increases recoil damage by 10%."),
            RuinousCell("Vindication", 80, 4863, 4887, "Heals you by 25% of your Prayer level if your health falls below 10%."),
            RuinousCell("Decimate", 82, 4854, 4878, "Increases your Attack by 30% and Strength by 27%."),
            RuinousCell("Annihilate", 84, 4855, 4879, "Increases your Ranged Attack by 30% and Ranged Strength by 27%."),
            RuinousCell("Vaporise", 86, 4856, 4880, "Increases your Magic Attack by 30% and Magic Damage by 4%."),
            RuinousCell("Fumus' Vow", 87, 4858, 4882, "Attacks that inflict poison cause poison to trigger damage immediately."),
            RuinousCell("Umbra's Vow", 88, 4859, 4883, "Attacks reduce your opponent's Defence by 5% of the damage dealt, up to 15%."),
            RuinousCell("Cruor's Vow", 89, 4860, 4884, "While activated, defeating a foe will drain 3 prayer points and heal 3 hitpoints."),
            RuinousCell("Glacies' Vow", 90, 4861, 4885, "Attacks reduce a monster's Magic by 5% of damage dealt, up to 15%."),
            RuinousCell("Wrath", 91, 4862, 4886, "On death, deal up to 25% of your Prayer level in damage to nearby opponents."),
            RuinousCell("Intensify", 92, 4864, 4888, "Increases your accuracy with all styles by 50%."),
        )

    // ---- ids (all free in the 667 cache on 2026-10-31: sprites <= 8758, structs <= 2761, enums <= 5201, varbits <= 9929,
    // varps <= 2403, clientscripts <= 5396) ----
    const val SPRITE_COLOUR_BASE = 8759
    const val SPRITE_GREY_BASE = SPRITE_COLOUR_BASE + 24
    const val STRUCT_BASE = 2762
    const val ENUM_ID = 5202
    const val ACTIVE_VARP = 2404
    const val QUICK_VARP = 2405
    const val BOOK_VARP = 2406
    const val BOOK_FLAG_VARBIT = 9930
    const val ACTIVE_VARBIT_BASE = 9931
    const val QUICK_VARBIT_BASE = ACTIVE_VARBIT_BASE + 24
    const val ENUM_SCRIPT = 5397
    const val COUNT_SCRIPT = 5398
    const val IS_CURSES_SCRIPT = 5399

    private const val CURSE_ENUM = 863
    private const val CURSE_COUNT = 20
    private const val CURSES_VARBIT = 6840

    /** OSRS headicons_prayer frames appended to the 667 sheet (group 440) as frames 27.. in this order. */
    val HEADICON_FRAMES = listOf("Dampen Melee" to 12, "Dampen Ranged" to 13, "Dampen Magic" to 14, "Wrath" to 10, "Rebuke" to 19, "Purge" to 20, "Vindication" to 21)
    const val HEADICON_GROUP = 440
    const val HEADICON_FIRST_NEW_FRAME = 27

    // clientscript opcodes (rev 667 ScriptRunner: 25 pushes a varbit's value, 86/87 branch on == 1 / == 0)
    private const val PUSH_INT = 0
    private const val GET_VARBIT = 25
    private const val GOSUB = 40
    private const val RETURN = 21
    private const val BRANCH_IF_TRUE = 86

    // ---- encoders ----

    private class Out {
        val b = ByteArrayOutputStream()
        fun p1(v: Int) = b.write(v and 0xFF)
        fun p2(v: Int) { p1(v ushr 8); p1(v) }
        fun p3(v: Int) { p1(v ushr 16); p2(v) }
        fun p4(v: Int) { p2(v ushr 16); p2(v) }
        fun str(s: String) { b.write(s.toByteArray(Charsets.ISO_8859_1)); p1(0) }
        fun bytes() = b.toByteArray()
    }

    fun struct(slot: Int): ByteArray {
        val cell = CELLS[slot]
        return Out().apply {
            p1(249); p1(6)
            p1(0); p3(737); p4(cell.level)
            p1(1); p3(738); str("You need a Prayer level of at least ${cell.level} to use ${cell.name}.")
            p1(0); p3(735); p4(SPRITE_COLOUR_BASE + slot)
            p1(0); p3(736); p4(SPRITE_GREY_BASE + slot)
            p1(1); p3(734); str("Level ${cell.level}<br>${cell.name}<br>${cell.text}")
            p1(0); p3(739); p4(1)
            p1(0)
        }.bytes()
    }

    /** Same layout as curse enum 863: int keys (u16 form, opcode 8) to struct ids, default -1. */
    fun enum(): ByteArray =
        Out().apply {
            p1(1); p1(105)
            p1(2); p1(74)
            p1(8); p2(CELLS.size); p2(CELLS.size)
            CELLS.indices.forEach { p2(it); p4(STRUCT_BASE + it) }
            p1(4); p4(-1)
            p1(0)
        }.bytes()

    fun varbit(varp: Int, bit: Int): ByteArray = Out().apply { p1(1); p2(varp); p1(bit); p1(bit); p1(0) }.bytes()

    /** A tiny script without arguments or locals: [ops] then the 16-byte trailer and an empty switch footer. */
    fun script(ops: List<Pair<Int, Int>>): ByteArray =
        Out().apply {
            p1(0) // empty name
            ops.forEach { (op, v) ->
                p2(op)
                if (op == RETURN) p1(v) else p4(v)
            }
            p4(ops.size)
            repeat(6) { p2(0) }
            p2(0)
        }.bytes().also { ClientScriptEditor.decode(it) }

    /** varbit flag ? [ifRuinous] : [otherwise] */
    private fun choose(ifRuinous: Int, otherwise: Int) =
        listOf(
            GET_VARBIT to BOOK_FLAG_VARBIT, BRANCH_IF_TRUE to 2,
            PUSH_INT to otherwise, RETURN to 0,
            PUSH_INT to ifRuinous, RETURN to 0,
        )

    fun enumScript() = script(choose(ENUM_ID, CURSE_ENUM))

    fun countScript() = script(choose(CELLS.size, CURSE_COUNT))

    /** 1 only for the real curse book: varbit 6840 set and the Ruinous flag clear. */
    fun isCursesScript() =
        script(
            listOf(
                GET_VARBIT to BOOK_FLAG_VARBIT, BRANCH_IF_TRUE to 2,
                GET_VARBIT to CURSES_VARBIT, RETURN to 0,
                PUSH_INT to 0, RETURN to 0,
            ),
        )

    // ---- clientscript patches ----

    /** Every `PUSH_INT 863` (the curse enum) becomes a call to [ENUM_SCRIPT]. */
    private fun curseEnum(s: ClientScriptEditor): Int {
        var n = 0
        s.code.filter { it.opcode == PUSH_INT && it.int == CURSE_ENUM }.forEach { it.opcode = GOSUB; it.int = ENUM_SCRIPT; n++ }
        return n
    }

    /** The curse cell count: `PUSH_INT 20` within the four instructions after a `GET_VARBIT 6840`. */
    private fun curseCount(s: ClientScriptEditor): Int {
        var n = 0
        s.code.forEachIndexed { i, ins ->
            if (ins.opcode == PUSH_INT && ins.int == CURSE_COUNT &&
                (maxOf(0, i - 4) until i).any { s.code[it].opcode == GET_VARBIT && s.code[it].int == CURSES_VARBIT }
            ) {
                ins.opcode = GOSUB
                ins.int = COUNT_SCRIPT
                n++
            }
        }
        return n
    }

    /** Every `GET_VARBIT 6840` becomes "is it the real curse book" (texts that say "curses"). */
    private fun curseTest(s: ClientScriptEditor): Int {
        var n = 0
        s.code.filter { it.opcode == GET_VARBIT && it.int == CURSES_VARBIT }.forEach { it.opcode = GOSUB; it.int = IS_CURSES_SCRIPT; n++ }
        return n
    }

    /** The onVarTransmit trigger list [first], [second], count 2 -> [first], [second], [added], count 3. */
    private fun addTrigger(s: ClientScriptEditor, first: Int, second: Int, added: Int): Int {
        val at = s.find(PUSH_INT to first, PUSH_INT to second, PUSH_INT to 2)
        check(at.size == 1) { "trigger list $first,$second found ${at.size}x" }
        s.code[at[0] + 2].int = 3
        s.insert(at[0] + 2, listOf(Ins(PUSH_INT, int = added)))
        return 1
    }

    /** 2296 / 2297: switch table 0 (the curse structs) gains a case per Ruinous struct returning its varbit. */
    private fun structVarbits(s: ClientScriptEditor, varbitBase: Int): Int {
        val table = s.switchTables()[0]
        check(table.none { it.first >= STRUCT_BASE }) { "already has Ruinous cases" }
        CELLS.indices.forEach { slot ->
            val get = Ins(GET_VARBIT, int = varbitBase + slot)
            s.code.add(get)
            s.code.add(Ins(RETURN, int = 0))
            table.add((STRUCT_BASE + slot) to get)
        }
        return CELLS.size
    }

    class Patch(val script: Int, val label: String, val apply: (ClientScriptEditor) -> Int, val expected: Int)

    val PATCHES =
        listOf(
            Patch(5, "quick highlight loop: Ruinous cell count", ::curseCount, 1),
            Patch(1237, "prayer grid: Ruinous enum + count + active varp trigger", { s -> curseEnum(s) + curseCount(s) + addTrigger(s, 1395, 1582, ACTIVE_VARP) }, 3),
            Patch(1293, "cell backgrounds: Ruinous cell count", ::curseCount, 1),
            Patch(1388, "quick-select grid: Ruinous count + quick varp trigger", { s -> curseCount(s) + addTrigger(s, 1397, 1587, QUICK_VARP) }, 2),
            Patch(2294, "members check: Ruinous enum", ::curseEnum, 1),
            Patch(2295, "click check: Ruinous enum", ::curseEnum, 1),
            Patch(2296, "active varbit per struct: Ruinous enum + 24 cases", { s -> curseEnum(s) + structVarbits(s, ACTIVE_VARBIT_BASE) }, 25),
            Patch(2297, "quick varbit per struct: Ruinous enum + 24 cases", { s -> curseEnum(s) + structVarbits(s, QUICK_VARBIT_BASE) }, 25),
            Patch(2299, "quick-prayer texts: 'curses' only for the curse book", ::curseTest, 2),
            Patch(2303, "orb texts: 'curses' only for the curse book", ::curseTest, 2),
        )

    // ---- sprites ----

    class Frame(val ox: Int, val oy: Int, val w: Int, val h: Int, val flags: Int, val idx: IntArray, val alpha: IntArray?)

    class Sheet(val width: Int, val height: Int, val palette: IntArray, val frames: List<Frame>)

    /** Rev-667 / OSRS indexed sprite group (the client's IndexedImage.load). */
    fun decodeSheet(b: ByteArray): Sheet {
        fun u1(p: Int) = b[p].toInt() and 0xFF
        fun u2(p: Int) = (u1(p) shl 8) or u1(p + 1)
        val n = b.size
        val count = u2(n - 2)
        var p = n - 7 - count * 8
        val width = u2(p)
        val height = u2(p + 2)
        val palSize = u1(p + 4) + 1
        p += 5
        val ox = IntArray(count) { u2(p + 2 * it) }; p += 2 * count
        val oy = IntArray(count) { u2(p + 2 * it) }; p += 2 * count
        val sw = IntArray(count) { u2(p + 2 * it) }; p += 2 * count
        val sh = IntArray(count) { u2(p + 2 * it) }
        p = n - 7 - count * 8 - (palSize - 1) * 3
        val palette = IntArray(palSize)
        for (i in 1 until palSize) {
            palette[i] = (u1(p) shl 16) or (u1(p + 1) shl 8) or u1(p + 2)
            p += 3
        }
        p = 0
        val frames = ArrayList<Frame>()
        for (f in 0 until count) {
            val w = sw[f]
            val h = sh[f]
            val flags = u1(p++)
            fun plane(): IntArray {
                val out = IntArray(w * h)
                if (flags and 1 == 0) {
                    for (i in 0 until w * h) out[i] = u1(p++)
                } else {
                    for (x in 0 until w) for (y in 0 until h) out[y * w + x] = u1(p++)
                }
                return out
            }
            val idx = plane()
            val alpha = if (flags and 2 != 0) plane() else null
            frames += Frame(ox[f], oy[f], w, h, flags, idx, alpha)
        }
        return Sheet(width, height, palette, frames)
    }

    fun encodeSheet(sheet: Sheet): ByteArray {
        val o = Out()
        sheet.frames.forEach { f ->
            o.p1(f.flags)
            fun plane(a: IntArray) {
                if (f.flags and 1 == 0) {
                    a.forEach(o::p1)
                } else {
                    for (x in 0 until f.w) for (y in 0 until f.h) o.p1(a[y * f.w + x])
                }
            }
            plane(f.idx)
            f.alpha?.let(::plane)
        }
        for (i in 1 until sheet.palette.size) o.p3(sheet.palette[i])
        o.p2(sheet.width)
        o.p2(sheet.height)
        o.p1(sheet.palette.size - 1)
        sheet.frames.forEach { o.p2(it.ox) }
        sheet.frames.forEach { o.p2(it.oy) }
        sheet.frames.forEach { o.p2(it.w) }
        sheet.frames.forEach { o.p2(it.h) }
        o.p2(sheet.frames.size)
        return o.bytes()
    }

    /** The 667 head-icon sheet with the OSRS Ruinous overheads appended; earlier frames and palette entries unchanged. */
    fun mergedHeadicons(current: ByteArray, osrs: ByteArray): ByteArray {
        val mine = decodeSheet(current)
        check(encodeSheet(mine).contentEquals(current)) { "667 headicons_prayer does not round-trip; refusing to rewrite it" }
        check(mine.frames.size == HEADICON_FIRST_NEW_FRAME) { "667 headicons_prayer has ${mine.frames.size} frames, expected $HEADICON_FIRST_NEW_FRAME" }
        val source = decodeSheet(osrs)
        val palette = mine.palette.toMutableList()
        val added =
            HEADICON_FRAMES.map { (_, frameId) ->
                val f = source.frames[frameId]
                val idx =
                    IntArray(f.idx.size) { i ->
                        val k = f.idx[i]
                        if (k == 0) {
                            0
                        } else {
                            val colour = source.palette[k]
                            // Index 0 is the transparent slot; a real colour (black = 0 included) is looked up from index 1.
                            (1 until palette.size).firstOrNull { palette[it] == colour } ?: palette.size.also { palette += colour }
                        }
                    }
                Frame(f.ox, f.oy, f.w, f.h, 0, idx, f.alpha)
            }
        check(palette.size <= 256) { "merged palette has ${palette.size} colours" }
        return encodeSheet(Sheet(mine.width, mine.height, palette.toIntArray(), mine.frames + added))
    }

    // ---- "Show stat adjustments" panel (second transaction, run with "stats") ----

    const val STAT_SCRIPT = 5400
    private const val PUSH_INT_LOCAL = 33
    private const val BRANCH_NOT = 7
    private const val BRANCH_IF_FALSE = 87
    private const val ADD = 4000

    /**
     * The panel under the book (clientscript 1706 -> 1708 = 1709 normal prayers + 1710 curses, per skill 0 Attack, 2 Strength,
     * 1 Defence, 4 Ranged, 6 Magic) knew only the two old books, so the Ruinous book showed 0 %. [STAT_SCRIPT] gives the Ruinous
     * share, from the same OSRS texts as the server multipliers (RuinousPowers): the strongest active prayer per skill. The book has
     * no Defence prayer; Ranged and Magic show the accuracy, like Attack.
     */
    val STAT_TABLE: List<Pair<Int, List<Pair<Int, Int>>>> =
        listOf(
            0 to listOf(23 to 50, 15 to 30, 1 to 20, 9 to 15),
            2 to listOf(15 to 27, 1 to 20, 9 to 15),
            4 to listOf(23 to 50, 16 to 30, 2 to 20, 9 to 15),
            6 to listOf(23 to 50, 17 to 30, 3 to 20, 9 to 15),
        )

    fun statScript(): ByteArray {
        val editor = ClientScriptEditor.decode(script(listOf(RETURN to 0)))
        editor.code.clear()
        editor.intArgs = 1
        editor.intLocals = 1
        val end = Ins(PUSH_INT, int = 0)
        val code = editor.code
        code += Ins(GET_VARBIT, int = BOOK_FLAG_VARBIT)
        code += Ins(BRANCH_IF_FALSE, target = end)
        val heads = ArrayList<Ins>()
        val skillBranches = ArrayList<Ins>()
        STAT_TABLE.forEach { (skill, prayers) ->
            val head = Ins(PUSH_INT_LOCAL, int = 0)
            heads += head
            code += head
            code += Ins(PUSH_INT, int = skill)
            code += Ins(BRANCH_NOT).also { skillBranches += it }
            prayers.forEach { (slot, pct) ->
                code += Ins(GET_VARBIT, int = ACTIVE_VARBIT_BASE + slot)
                val skip = Ins(BRANCH_IF_FALSE)
                code += skip
                code += Ins(PUSH_INT, int = pct)
                code += Ins(RETURN, int = 0)
                skip.target = skip // linked below
            }
            code += Ins(PUSH_INT, int = 0)
            code += Ins(RETURN, int = 0)
        }
        code += end
        code += Ins(RETURN, int = 0)
        // A skipped prayer falls through to the next test (three instructions on); a skill that is not this block's to the next head.
        code.forEachIndexed { i, ins -> if (ins.opcode == BRANCH_IF_FALSE && ins.target === ins) ins.target = code[i + 3] }
        skillBranches.forEachIndexed { i, ins -> ins.target = heads.getOrNull(i + 1) ?: end }
        return editor.encode().also { ClientScriptEditor.decode(it) }
    }

    /** 1708: the skill's percentage = normal + curses + (Ruinous) [STAT_SCRIPT]. */
    private fun statSum(s: ClientScriptEditor): Int {
        val at = s.find(PUSH_INT_LOCAL to 0, GOSUB to 1710, ADD to null)
        check(at.size == 1) { "1708 layout changed" }
        s.insert(at[0] + 3, listOf(Ins(PUSH_INT_LOCAL, int = 0), Ins(GOSUB, int = STAT_SCRIPT), Ins(ADD, int = 0)))
        return 1
    }

    /** 1698: the panel also refreshes when a Ruinous prayer goes on or off (trigger list 1583,1584,1582,1395 + the active varp). */
    private fun statTrigger(s: ClientScriptEditor): Int {
        val at = s.find(PUSH_INT to 1582, PUSH_INT to 1395, PUSH_INT to 4)
        check(at.size == 1) { "1698 trigger list changed" }
        s.code[at[0] + 2].int = 5
        s.insert(at[0] + 2, listOf(Ins(PUSH_INT, int = ACTIVE_VARP)))
        return 1
    }

    fun statMutations(): List<CacheMutation> {
        val mutations = ArrayList<CacheMutation>()
        val library = CacheLibrary(TARGETS[0])
        try {
            check(library.data(SCRIPT_INDEX, STAT_SCRIPT, 0) == null) { "clientscript $STAT_SCRIPT is not free" }
            mutations += CacheMutation(SCRIPT_INDEX, STAT_SCRIPT, 0, statScript(), "clientscript $STAT_SCRIPT: Ruinous share of the stat adjustments")
            listOf(1708 to ::statSum, 1698 to ::statTrigger).forEach { (id, patch) ->
                val current = requireNotNull(library.data(SCRIPT_INDEX, id, 0)) { "clientscript $id missing" }
                val editor = ClientScriptEditor.decode(current)
                check(editor.encode().contentEquals(current)) { "clientscript $id does not round-trip" }
                patch(editor)
                mutations += CacheMutation(SCRIPT_INDEX, id, 0, editor.encode(), "clientscript $id: Ruinous stat adjustments", expectedCurrentSha1 = CacheItemProbeTool.sha1(current))
            }
        } finally {
            library.close()
        }
        return mutations
    }

    /**
     * N492 (Claude 2026-10-31, owner 20:0x "ja nu fixen"): with "Show stat adjustments" open the curse layout's grid keeps its full
     * height and no scrollbar (script 1704's GET_VARBIT 6840 branch: the 20 curses fit in 4 rows above the panel), but the 24 Ruinous
     * prayers need 5 rows, so the fifth sat half under the panel. 1704 asks "is it the real curse book" ([IS_CURSES_SCRIPT]) instead:
     * Ruinous takes the Normal route - the grid shrinks above the panel and the cache's scrollbar (script 31) appears, as for the 30
     * Normal prayers.
     */
    fun scrollMutations(): List<CacheMutation> {
        val library = CacheLibrary(TARGETS[0])
        try {
            val current = requireNotNull(library.data(SCRIPT_INDEX, STAT_SCROLL_SCRIPT, 0)) { "clientscript $STAT_SCROLL_SCRIPT missing" }
            val editor = ClientScriptEditor.decode(current)
            check(editor.encode().contentEquals(current)) { "clientscript $STAT_SCROLL_SCRIPT does not round-trip" }
            val changed = curseTest(editor)
            check(changed == 1) { "clientscript $STAT_SCROLL_SCRIPT: $changed curse tests, expected 1" }
            return listOf(
                CacheMutation(SCRIPT_INDEX, STAT_SCROLL_SCRIPT, 0, editor.encode(), "clientscript $STAT_SCROLL_SCRIPT: Ruinous scrolls under the stat adjustments (N492)", expectedCurrentSha1 = CacheItemProbeTool.sha1(current)),
            )
        } finally {
            library.close()
        }
    }

    /** The prayer tab's grid size / scrollbar when the stat adjustments open or close (called by 1698). */
    const val STAT_SCROLL_SCRIPT = 1704

    // ---- main ----

    fun mutations(): List<CacheMutation> {
        val mutations = ArrayList<CacheMutation>()
        val osrsSprites = HashMap<Int, ByteArray>()
        val osrsHeadicons: ByteArray
        ModernCacheReader(File(SOURCE_CACHE)).use { source ->
            CELLS.forEach { cell ->
                listOf(cell.osrsColour, cell.osrsGrey).forEach { id -> osrsSprites[id] = source.file(SPRITE_INDEX, id, 0) ?: error("OSRS sprite $id missing") }
            }
            osrsHeadicons = source.file(SPRITE_INDEX, HEADICON_GROUP, 0) ?: error("OSRS headicons_prayer missing")
        }
        val library = CacheLibrary(TARGETS[0])
        try {
            fun absent(index: Int, group: Int, file: Int) =
                check(library.data(index, group, file) == null) { "idx$index grp$group file$file is not free" }
            CELLS.forEachIndexed { slot, cell ->
                absent(SPRITE_INDEX, SPRITE_COLOUR_BASE + slot, 0)
                absent(SPRITE_INDEX, SPRITE_GREY_BASE + slot, 0)
                mutations += CacheMutation(SPRITE_INDEX, SPRITE_COLOUR_BASE + slot, 0, osrsSprites[cell.osrsColour]!!, "Ruinous ${cell.name} icon (osrs ${cell.osrsColour})")
                mutations += CacheMutation(SPRITE_INDEX, SPRITE_GREY_BASE + slot, 0, osrsSprites[cell.osrsGrey]!!, "Ruinous ${cell.name} grey icon (osrs ${cell.osrsGrey})")
                absent(CONFIG_INDEX, STRUCT_GROUP, STRUCT_BASE + slot)
                mutations += CacheMutation(CONFIG_INDEX, STRUCT_GROUP, STRUCT_BASE + slot, struct(slot), "struct ${STRUCT_BASE + slot}: Ruinous ${cell.name}")
                absent(VARBIT_INDEX, (ACTIVE_VARBIT_BASE + slot) ushr 10, (ACTIVE_VARBIT_BASE + slot) and 0x3FF)
                mutations += CacheMutation(VARBIT_INDEX, (ACTIVE_VARBIT_BASE + slot) ushr 10, (ACTIVE_VARBIT_BASE + slot) and 0x3FF, varbit(ACTIVE_VARP, slot), "varbit ${ACTIVE_VARBIT_BASE + slot}: ${cell.name} active")
                mutations += CacheMutation(VARBIT_INDEX, (QUICK_VARBIT_BASE + slot) ushr 10, (QUICK_VARBIT_BASE + slot) and 0x3FF, varbit(QUICK_VARP, slot), "varbit ${QUICK_VARBIT_BASE + slot}: ${cell.name} quick-selected")
            }
            mutations += CacheMutation(VARBIT_INDEX, BOOK_FLAG_VARBIT ushr 10, BOOK_FLAG_VARBIT and 0x3FF, varbit(BOOK_VARP, 0), "varbit $BOOK_FLAG_VARBIT: Ruinous book shown")
            listOf(ACTIVE_VARP, QUICK_VARP, BOOK_VARP).forEach { varp ->
                absent(CONFIG_INDEX, VARP_GROUP, varp)
                mutations += CacheMutation(CONFIG_INDEX, VARP_GROUP, varp, byteArrayOf(0), "varp $varp (Ruinous Powers)")
            }
            absent(ENUM_INDEX, ENUM_ID ushr 8, ENUM_ID and 0xFF)
            mutations += CacheMutation(ENUM_INDEX, ENUM_ID ushr 8, ENUM_ID and 0xFF, enum(), "enum $ENUM_ID: Ruinous Powers cells -> structs")
            listOf(ENUM_SCRIPT to enumScript(), COUNT_SCRIPT to countScript(), IS_CURSES_SCRIPT to isCursesScript()).forEach { (id, bytes) ->
                absent(SCRIPT_INDEX, id, 0)
                mutations += CacheMutation(SCRIPT_INDEX, id, 0, bytes, "clientscript $id (Ruinous book helper)")
            }
            PATCHES.forEach { patch ->
                val current = requireNotNull(library.data(SCRIPT_INDEX, patch.script, 0)) { "clientscript ${patch.script} missing" }
                val editor = ClientScriptEditor.decode(current)
                check(editor.encode().contentEquals(current)) { "clientscript ${patch.script} does not round-trip" }
                val changed = patch.apply(editor)
                check(changed == patch.expected) { "clientscript ${patch.script}: $changed changes, expected ${patch.expected}" }
                mutations += CacheMutation(SCRIPT_INDEX, patch.script, 0, editor.encode(), "clientscript ${patch.script}: ${patch.label}", expectedCurrentSha1 = CacheItemProbeTool.sha1(current))
            }
            val heads = requireNotNull(library.data(SPRITE_INDEX, HEADICON_GROUP, 0)) { "667 headicons_prayer missing" }
            mutations += CacheMutation(SPRITE_INDEX, HEADICON_GROUP, 0, mergedHeadicons(heads, osrsHeadicons), "headicons_prayer: Ruinous overheads as frames $HEADICON_FIRST_NEW_FRAME..${HEADICON_FIRST_NEW_FRAME + HEADICON_FRAMES.size - 1}", expectedCurrentSha1 = CacheItemProbeTool.sha1(heads))
        } finally {
            library.close()
        }
        return mutations
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val transaction = CacheTransaction(targets = TARGETS, mutations = if ("scroll" in args) scrollMutations() else if ("stats" in args) statMutations() else mutations())
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} mutations=${transaction.mutations.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKING: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY_RUN")
            return
        }
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        if (problems.isNotEmpty()) {
            problems.forEach { println("  VERIFY_FAILURE: $it") }
            println("ROLLED_BACK ${transaction.rollback()}")
            error("transaction ${transaction.id} failed verification and was rolled back")
        }
        println("APPLIED transaction=${applied.transactionId} writes=${applied.applied} skipped=${applied.skipped} VERIFY_OK")
    }
}
