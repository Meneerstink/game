package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool.Comp

/**
 * A28 (owner 2026-10-10 "de interface gui voor spiritual fairy tree ziet er raar uit", wanted exact OSRS): the OSRS spirit tree
 * menu (OSRS Wiki `File:Spirit tree interface.png`, 508x325) is the parchment scroll with the quill title "Spirit Tree Locations"
 * and one centred line per destination, "<key>: <name>" in 0x322805, an unreachable one in 0x5F5F5F, the last line "<key>: Cancel".
 * The 667 menu 864 shows the same scroll (model 26397) but with "Spirit Tree" / "Where would you like to go?", a rule and a
 * left-aligned list its clientscripts build. This tool appends the OSRS title and [ROWS] centred rows to 864 (components
 * [TITLE]..); the server hides 864:3-5/7/8 and fills the rows (SpiritTrees). The rows turn white under the mouse like the OSRS
 * menu ([PohTeleportInterfaceImportTool.COLOUR_SCRIPT]).
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.SpiritTreeInterfaceTool [--apply]` with the servers stopped.
 */
object SpiritTreeInterfaceTool {
    const val INTERFACE = 864
    const val TITLE = 9
    const val FIRST_ROW = 10
    const val ROWS = 16
    const val ROW_HEIGHT = 15
    // Owner 2026-10-24 ("de tekst spirit tree locations staat te hoog, overlapt"): title and rows 7 px lower, off the scroll top.
    const val ROW_TOP = 73
    const val TEXT = 0x322805
    const val GREY = 0x5F5F5F
    const val HOVER = 0xFFFFFF
    private const val X = 46
    private const val WIDTH = 392
    private const val SCROLL_LAYER = 1
    private const val TYPE_TEXT = 4

    /** The quill font of 864's own title (864:3), as large as the OSRS menu's title. */
    const val TITLE_FONT = 645

    fun components(fonts: LootKeyInterfaceImportTool.Fonts): List<Comp> {
        val out = mutableListOf<Comp>()
        out += Comp(TITLE, TYPE_TEXT, X, 40, WIDTH, 30, SCROLL_LAYER, font = TITLE_FONT, text = "Spirit Tree Locations", colour = TEXT, textShadow = false)
        for (r in 0 until ROWS) {
            val id = FIRST_ROW + r
            val full = (INTERFACE shl 16) or id
            out +=
                Comp(
                    id, TYPE_TEXT, X, ROW_TOP + r * ROW_HEIGHT, WIDTH, ROW_HEIGHT, SCROLL_LAYER, hidden = true,
                    font = fonts.p12, text = "", colour = TEXT, textShadow = false, ops = listOf("Continue"),
                    onMouseOver = listOf(PohTeleportInterfaceImportTool.COLOUR_SCRIPT, full, HOVER),
                    onMouseLeave = listOf(PohTeleportInterfaceImportTool.COLOUR_SCRIPT, full, TEXT),
                )
        }
        return out
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        val fonts: LootKeyInterfaceImportTool.Fonts
        val current = mutableMapOf<Int, String>()
        try {
            fonts = LootKeyInterfaceImportTool.fonts(library)
            library.index(PohTeleportInterfaceImportTool.INDEX_INTERFACES).archive(INTERFACE) ?: error("interface $INTERFACE missing")
            for (id in TITLE until FIRST_ROW + ROWS) library.data(PohTeleportInterfaceImportTool.INDEX_INTERFACES, INTERFACE, id)?.let { current[id] = CacheItemProbeTool.sha1(it) }
            require(library.index(PohTeleportInterfaceImportTool.INDEX_CLIENTSCRIPTS).archive(PohTeleportInterfaceImportTool.COLOUR_SCRIPT) != null) { "colour script missing" }
        } finally {
            library.close()
        }
        val comps = components(fonts)
        // A re-run replaces exactly what this tool wrote (its own components 9-25), pinned to their current bytes.
        val mutations =
            comps.map { c ->
                val bytes = DeathsOfficeInterfaceImportTool.encode(c)
                CacheMutation(
                    PohTeleportInterfaceImportTool.INDEX_INTERFACES, INTERFACE, c.id, bytes, "interface $INTERFACE:${c.id} (A28)",
                    expectedCurrentSha1 = current[c.id]?.takeIf { it != CacheItemProbeTool.sha1(bytes) },
                )
            }
        val transaction = CacheTransaction(targets = OsrsItemImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} mutations=${mutations.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY RUN")
            return
        }
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        check(problems.isEmpty()) { "verify failed: $problems" }
        println("APPLIED ${applied.applied} VERIFY_OK transaction=${transaction.id}")
    }
}
