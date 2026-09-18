package gg.rsmod.game.tools.importer

import gg.rsmod.game.tools.importer.LootKeyInterfaceImportTool.Component
import java.io.File

/**
 * Builds the OSRS "Dizana's Quiver" interface (OSRS interface 592, owner reference `C:\RSPS\foto\dizana interface.png`) as the
 * revision-667 if3 interface [INTERFACE_ID], with the same transactional, both-caches route as [LootKeyInterfaceImportTool].
 *
 * Source (read-only, pinned OpenRS2 2686 cache, decoded 2026-09-19):
 *  - OSRS 592:7 title "<u=FF9933>Dizana's Quiver" (bold font, orange 0xFF9933, centred at y 5);
 *  - OSRS 592:3 the quiver art = model 52244 (the quiver's own inventory model, already imported as 667 model
 *    [QUIVER_MODEL]) at rotation x 1651 / z 1104 / y 0, zoom 160, offset (-5, 11), inside the 250-high layer 592:2 at (10, 5);
 *  - OSRS 592:6/10/11 the ammunition slot: slot background sprite 170, 36x36, centred (x offset -5) at y 137 of the layer;
 *  - OSRS 592:9 "Charges: 0" (regular font, orange), OSRS 592:12 the close button, sprite 539 (26x23, 2 px from the top right);
 *  - the stone window frame is drawn by an OSRS clientscript: the frame sprites the loot-key import already brought in
 *    (297 background, 820-827 edges and corners) are reused, and the window size is measured from the owner's picture.
 *
 * The interface is static: the server fills the slot with IF_SETOBJECT and the charges text with IF_SETTEXT and enables
 * the ops with IF_SETEVENTS (content: `dizanas_quiver.plugin.kts`).
 *
 * Usage: `./gradlew :game:runDizanasQuiverInterfaceImportTool --args="[--apply]"`
 */
object DizanasQuiverInterfaceImportTool {
    const val INTERFACE_ID = 1150

    /** 667 sprite for OSRS close button 539: the first id above the loot-key sprites (7912..7939). */
    const val CLOSE_SPRITE = 7940
    const val OSRS_CLOSE_SPRITE = 539

    /** 667 model id of OSRS model 52244 (OSRS_IMPORT_MASTER.yml, Dizana's quiver items). */
    const val QUIVER_MODEL = 35559

    const val WIDTH = 202
    const val HEIGHT = 268

    const val ROOT = 0
    const val WINDOW = 1
    const val MODEL = 11
    const val TITLE = 12
    const val CLOSE = 13
    const val SLOT_BACKGROUND = 14
    const val SLOT = 15
    const val CHARGES = 16
    const val COMPONENT_COUNT = 17

    private const val TYPE_LAYER = 0
    private const val TYPE_TEXT = 4
    private const val TYPE_GRAPHIC = 5

    private const val ORANGE = 0xFF9933

    fun components(fonts: LootKeyInterfaceImportTool.Fonts): List<Component> {
        val list = mutableListOf<Component>()
        fun add(c: Component) {
            require(list.none { it.id == c.id }) { "duplicate component ${c.id}" }
            list += c
        }
        fun graphic(id: Int, x: Int, y: Int, w: Int, h: Int, parent: Int, sprite: Int, tiling: Boolean = false, ops: List<String> = emptyList()) =
            add(Component(id, TYPE_GRAPHIC, x, y, w, h, parent, graphic = sprite, tiling = tiling, ops = ops))
        fun text(id: Int, x: Int, y: Int, w: Int, h: Int, font: Int, text: String) =
            add(Component(id, TYPE_TEXT, x, y, w, h, WINDOW, font = font, text = text, colour = ORANGE, alignX = 1, alignY = 1, shadow = true))
        val s = LootKeyInterfaceImportTool::sprite

        add(Component(ROOT, TYPE_LAYER, 0, 0, 0, 0, -1, resizeX = 1, resizeY = 1))
        add(Component(WINDOW, TYPE_LAYER, 0, 0, WIDTH, HEIGHT, ROOT, reposX = 1, reposY = 1))
        // Stone frame, same geometry rules as the loot-key window (edge canvases 32x32 with the art 13 px in).
        graphic(2, 1, 1, WIDTH - 2, HEIGHT - 2, WINDOW, s(297), tiling = true)
        graphic(3, 32, -13, WIDTH - 64, 20, WINDOW, s(820), tiling = true)
        graphic(4, 32, HEIGHT - 20, WIDTH - 64, 20, WINDOW, s(822), tiling = true)
        graphic(5, -13, 32, 20, HEIGHT - 64, WINDOW, s(821), tiling = true)
        graphic(6, WIDTH - 20, 32, 20, HEIGHT - 64, WINDOW, s(823), tiling = true)
        graphic(7, 0, 0, 32, 32, WINDOW, s(824))
        graphic(8, WIDTH - 32, 0, 32, 32, WINDOW, s(825))
        graphic(9, 0, HEIGHT - 32, 32, 32, WINDOW, s(826))
        graphic(10, WIDTH - 32, HEIGHT - 32, 32, 32, WINDOW, s(827))
        // OSRS 592:2/3: the quiver model over the 250-high content layer at (10, 5).
        add(
            Component(
                MODEL, LootKeyInterfaceImportTool.TYPE_MODEL, 10, 5, WIDTH - 20, 250, WINDOW,
                model = QUIVER_MODEL, modelOriginX = -5, modelOriginY = 11, xan = 1651, yan = 0, zan = 1104, zoom = 160,
            ),
        )
        text(TITLE, 0, 5, WIDTH, 20, fonts.b12, "<u=FF9933>Dizana's Quiver")
        graphic(CLOSE, WIDTH - 26 - 2, 2, 26, 23, WINDOW, CLOSE_SPRITE, ops = listOf("Close"))
        // OSRS 592:6/10/11: the ammunition slot, centred (x offset -5) at y 137 of the layer at y 5.
        val slotX = 10 + (WIDTH - 20 - 36) / 2 - 5
        graphic(SLOT_BACKGROUND, slotX, 5 + 137, 36, 36, WINDOW, s(170))
        add(Component(SLOT, TYPE_GRAPHIC, slotX + 2, 5 + 137 + 2, 32, 32, WINDOW, graphic = -1, ops = listOf("Remove", "", "", "", "", "", "", "", "", "Examine")))
        // OSRS 592:8/9: "Charges: ..." near the bottom of the window.
        text(CHARGES, 0, HEIGHT - 36, WIDTH, 20, fonts.p12, "Charges: None")

        require(list.size == COMPONENT_COUNT) { "expected $COMPONENT_COUNT components, built ${list.size}" }
        require(list.map { it.id }.sorted() == (0 until COMPONENT_COUNT).toList()) { "component ids must be 0..${COMPONENT_COUNT - 1}" }
        return list.sortedBy { it.id }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val mutations = mutableListOf<CacheMutation>()
        ModernCacheReader(File(LootKeyInterfaceImportTool.SOURCE_CACHE)).use { source ->
            val bytes = source.file(LootKeyInterfaceImportTool.INDEX_SPRITES, OSRS_CLOSE_SPRITE, 0) ?: error("OSRS sprite $OSRS_CLOSE_SPRITE missing")
            mutations += CacheMutation(LootKeyInterfaceImportTool.INDEX_SPRITES, CLOSE_SPRITE, 0, bytes, "sprite osrs:$OSRS_CLOSE_SPRITE -> 667:$CLOSE_SPRITE")
        }
        val fonts =
            com.displee.cache.CacheLibrary(LootKeyInterfaceImportTool.TARGETS[0]).let { library ->
                try {
                    LootKeyInterfaceImportTool.fonts(library)
                } finally {
                    library.close()
                }
            }
        components(fonts).forEach { c ->
            mutations += CacheMutation(LootKeyInterfaceImportTool.INDEX_INTERFACES, INTERFACE_ID, c.id, LootKeyInterfaceImportTool.encode(c), "interface $INTERFACE_ID:${c.id} type=${c.type}")
        }
        val transaction = CacheTransaction(targets = LootKeyInterfaceImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} mutations=${mutations.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY RUN: pass --apply to write interface $INTERFACE_ID and sprite $CLOSE_SPRITE")
            return
        }
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        println("APPLIED ${applied.applied} skipped=${applied.skipped} journal=${applied.journalDir}")
        if (problems.isEmpty()) println("VERIFY_OK transaction=${transaction.id}") else {
            problems.forEach { println("  VERIFY_PROBLEM: $it") }
            error("verify failed")
        }
    }
}
