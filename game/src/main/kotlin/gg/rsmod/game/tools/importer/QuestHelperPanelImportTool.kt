package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool.Builder
import gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool.Comp

/**
 * The Quest Helper panel (owner 2026-09-26: "de quest helper is nog basic moet iets uitgebreider"): a small Deadman-red
 * panel in the top-left corner of the game view, opened in the main-screen overlay while a short quest is tracked. It
 * shows the quest, "Step n of N" with a progress bar, the step's full instruction, where to go (marked when the place is
 * Dangerous) and the step's tip (what to bring, the boss's mechanic). Revision-667 interface [INTERFACE_ID].
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.QuestHelperPanelImportTool [--apply]`. The tool owns interface
 * [INTERFACE_ID]; a re-run replaces its own components (pinned to what is there) and removes left-overs.
 */
object QuestHelperPanelImportTool {
    const val INTERFACE_ID = 1166

    const val WIDTH = 204
    const val HEIGHT = 120

    const val ROOT = 0
    const val PANEL = 1
    const val BG = 2
    const val OUTER = 3
    const val INNER = 4
    const val HEADER = 5
    const val TITLE = 6
    const val STEP = 7
    const val BAR = 8
    const val SEGMENT_FIRST = 9
    const val SEGMENTS = 10
    const val INSTRUCTION = SEGMENT_FIRST + SEGMENTS // 19
    const val GOTO = INSTRUCTION + 1 // 20
    const val TIP = GOTO + 1 // 21
    const val COUNT = TIP + 1 // 22

    private const val TYPE_LAYER = 0
    private const val TYPE_RECTANGLE = 3

    const val GOLD = 0xFFB83F
    const val GOLD_DARK = 0xC8932F
    const val BLOOD = 0x8A1016
    const val ORANGE = 0xFF981F
    const val CREAM = 0xEDE3C6
    const val WHITE = 0xFFFFFF

    fun components(fonts: LootKeyInterfaceImportTool.Fonts): List<Comp> {
        val b = Builder(fonts)
        b.add(Comp(ROOT, TYPE_LAYER, 0, 0, 0, 0, -1, resizeX = 1, resizeY = 1))
        b.add(Comp(PANEL, TYPE_LAYER, 6, 6, WIDTH, HEIGHT, ROOT))
        b.add(Comp(BG, TYPE_RECTANGLE, 0, 0, WIDTH, HEIGHT, PANEL, colour = 0x120405, filled = true, transparency = 60))
        b.add(Comp(OUTER, TYPE_RECTANGLE, 0, 0, WIDTH, HEIGHT, PANEL, colour = GOLD_DARK))
        b.add(Comp(INNER, TYPE_RECTANGLE, 1, 1, WIDTH - 2, HEIGHT - 2, PANEL, colour = BLOOD))
        b.add(Comp(HEADER, TYPE_RECTANGLE, 2, 2, WIDTH - 4, 17, PANEL, colour = 0x3A0E12, filled = true, transparency = 30))
        b.text(TITLE, 6, 2, 140, 17, PANEL, fonts.b12, "", colour = GOLD, alignX = 0)
        b.text(STEP, 140, 2, WIDTH - 146, 17, PANEL, fonts.p11, "", colour = ORANGE, alignX = 2)
        b.add(Comp(BAR, TYPE_RECTANGLE, 6, 21, WIDTH - 12, 4, PANEL, colour = 0x2A0A0B, filled = true))
        val segment = (WIDTH - 12) / SEGMENTS
        for (i in 0 until SEGMENTS) {
            b.add(Comp(SEGMENT_FIRST + i, TYPE_RECTANGLE, 6 + i * segment, 21, segment - 1, 4, PANEL, colour = GOLD, filled = true, hidden = true))
        }
        b.text(INSTRUCTION, 6, 28, WIDTH - 12, 50, PANEL, fonts.p11, "", colour = WHITE, alignX = 0, alignY = 0, lineHeight = 12)
        b.text(GOTO, 6, 79, WIDTH - 12, 13, PANEL, fonts.p11, "", colour = CREAM, alignX = 0, alignY = 0)
        b.text(TIP, 6, 93, WIDTH - 12, 25, PANEL, fonts.p11, "", colour = ORANGE, alignX = 0, alignY = 0, lineHeight = 12)
        return b.done(COUNT)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val library = CacheLibrary(LootKeyInterfaceImportTool.TARGETS[0])
        val fonts: LootKeyInterfaceImportTool.Fonts
        val current: Map<Int, String>
        try {
            fonts = LootKeyInterfaceImportTool.fonts(library)
            val files = library.index(DeathsOfficeInterfaceImportTool.INDEX_INTERFACES).archive(INTERFACE_ID)?.fileIds()?.toList() ?: emptyList()
            current = files.associateWith { f -> CacheItemProbeTool.sha1(library.data(DeathsOfficeInterfaceImportTool.INDEX_INTERFACES, INTERFACE_ID, f)!!) }
        } finally {
            library.close()
        }
        val built = components(fonts)
        val writes =
            built.map { c ->
                CacheMutation(
                    DeathsOfficeInterfaceImportTool.INDEX_INTERFACES, INTERFACE_ID, c.id, DeathsOfficeInterfaceImportTool.encode(c),
                    "interface $INTERFACE_ID:${c.id} type=${c.type}", expectedCurrentSha1 = current[c.id],
                )
            }
        val removals =
            current.keys.filter { it >= COUNT }.sorted().map { f ->
                CacheMutation(DeathsOfficeInterfaceImportTool.INDEX_INTERFACES, INTERFACE_ID, f, null, "interface $INTERFACE_ID:$f (old layout)")
            }
        val mutations = writes + removals
        val transaction = CacheTransaction(targets = LootKeyInterfaceImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} mutations=${mutations.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY RUN: pass --apply to write interface $INTERFACE_ID")
            return
        }
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        println("APPLIED ${applied.applied} skipped=${applied.skipped} journal=${applied.journalDir}")
        check(problems.isEmpty()) { "verify failed: $problems" }
        println("VERIFY_OK transaction=${transaction.id}")
    }
}
