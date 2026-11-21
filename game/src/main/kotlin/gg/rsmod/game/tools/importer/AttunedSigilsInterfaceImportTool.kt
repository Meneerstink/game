package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.RspsPaths
import gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool.Builder
import gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool.Comp
import java.io.File
import javax.imageio.ImageIO

/**
 * SIGIL-INFO-UI (owner design 2026-11-21, screens/SIGILS/mock/sigil_venster_v3.png, team/dossiers/GOEDGEKEURDE-ONTWERPEN.txt): the
 * "Attuned Sigils" window - the OSRS Adventure Paths frame (500 x 334) with three columns Slot 1 / 2 / 3. A filled slot shows the
 * sigil's picture (its skull by tier x kind with its own icon, as the OSRS window draws it), name, type, tier, the wiki effect
 * text and an Unattune button; an empty slot shows the red cross and the wiki's "Look out for sigils" line.
 *
 * Its own interface and its own sprite range: the N108 art takes its sprite ids from the SORTED manifest (8600..8767) and the ids
 * right after it belong to other imports (census 2026-11-21: sprites 8600..9110 and interfaces ..1240 are in the cache), so these
 * sprites start at [SPRITE_BASE] in the fixed order of [SPRITES] - append only, never reorder.
 *
 * Art: data/art/sigils at 1x - frame, columns, button and cross by screens/SIGILS/mock/make_sigil_art.py; the 65 sigil pictures by
 * make_sigil_slots.py (skull = OSRS cache sprites 3503-3511 by OUR tier and kind, icon = the OSRS Wiki's "Sigil of X icon"). Dry run by default; `--apply` writes through a
 * [CacheTransaction] (stop the test server and the file server first). `--art <dir>` reads the pictures from another checkout.
 */
object AttunedSigilsInterfaceImportTool {
    /** First free interface (census 2026-11-21: 1240 Combat Brackets is the last one). */
    const val INTERFACE_ID = 1241

    /** First free sprite (census 2026-11-21: 9110 is the last one). */
    const val SPRITE_BASE = 9111

    /** The sigils S01..S65 (Sigil.number); a new sigil gets the next sprite. */
    const val SIGILS = 65

    /** Sprite names in id order. APPEND ONLY. */
    val SPRITES: List<String> =
        listOf("bg", "col_combat", "col_utility", "col_skilling", "col_empty", "btn", "cross") +
            (1..SIGILS).map { "sigil_%02d".format(it) }

    fun spriteId(name: String): Int {
        val i = SPRITES.indexOf(name)
        require(i >= 0) { "sprite '$name' is not in the sigil art" }
        return SPRITE_BASE + i
    }

    /** The picture of sigil [number] (1..[SIGILS]): its skull with its icon. */
    fun sigil(number: Int): Int = spriteId("sigil_%02d".format(number))

    /** The column fill of a kind, or the grey one of an empty slot. */
    fun column(kind: String?): Int = spriteId("col_${kind?.lowercase() ?: "empty"}")

    /** Component ids and window pixels (the approved mock, make_sigil_art.py). */
    object AS {
        const val WINDOW = 1
        const val BG = 2
        const val CLOSE = 3
        const val TITLE = 4
        /** The scrolling part of the three columns (skull .. effect text); the Slot headings and the buttons stay put. */
        const val SCROLL = 5
        const val SCROLLBAR = 6
        const val SLOT_FIRST = 7
        const val SLOT_STRIDE = 11
        const val SLOTS = 3
        const val COUNT = SLOT_FIRST + SLOTS * SLOT_STRIDE

        // per slot
        const val S_COLUMN = 0
        const val S_HEAD = 1
        const val S_SKULL = 2 // the sigil's picture, or the red cross of an empty slot
        const val S_NAME = 3
        const val S_TYPE = 4
        const val S_TIER = 5
        const val S_EFFECT = 6
        const val S_BUTTON = 7
        const val S_BUTTON_TEXT = 8
        const val S_BUTTON_HIT = 9
        const val S_HINT = 10 // "Use Attune on a sigil at a bank." in an empty slot

        const val X0 = 10
        const val Y0 = 38
        const val Y1 = 324
        const val COL_W = 153
        const val COL_STEP = 154
        const val SKULL_W = 76
        const val SKULL_H = 81
        const val SKULL_Y = 27
        const val CROSS = 72
        const val NAME_Y = 112
        const val NAME_W = COL_W - 8
        const val TEXT_W = COL_W - 12
        const val BTN_W = 96
        const val BTN_H = 24
        const val BTN_Y = Y1 - 28
        const val LINE_P12 = 14
        const val LINE_P11 = 13
        const val SCROLL_Y = Y0 + 24
        const val SCROLL_H = BTN_Y - 4 - SCROLL_Y
        const val SCROLL_W = SLOTS * COL_STEP - 1
        const val BAR_X = 474

        /** Inside [SCROLL]: the skull, the red cross and the name of an empty slot, the name of a filled one. */
        const val IN_SKULL_Y = Y0 + SKULL_Y - SCROLL_Y
        const val IN_CROSS_Y = Y0 + 29 - SCROLL_Y
        const val IN_NAME_Y = Y0 + NAME_Y - SCROLL_Y
        const val IN_EMPTY_NAME_Y = Y0 + 108 - SCROLL_Y
        const val IN_EMPTY_TEXT_Y = Y0 + 135 - SCROLL_Y

        /** A column's left edge inside [SCROLL]. */
        fun inX(i: Int) = i * COL_STEP

        fun slot(i: Int) = SLOT_FIRST + i * SLOT_STRIDE

        fun x(i: Int) = X0 + i * COL_STEP
    }

    private const val TYPE_LAYER = 0
    private const val TYPE_GRAPHIC = 5
    const val ORANGE = 0xFF981F
    const val WHITE = 0xFFFFFF
    const val BUTTON_TEXT = 0xDFDFDF

    private fun Builder.g(
        id: Int,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        sprite: String,
        hidden: Boolean = false,
        parent: Int = AS.WINDOW,
    ) = add(Comp(id, TYPE_GRAPHIC, x, y, w, h, parent, hidden = hidden, graphic = spriteId(sprite)))

    fun window(fonts: LootKeyInterfaceImportTool.Fonts): List<Comp> {
        val b = Builder(fonts)
        b.add(Comp(0, TYPE_LAYER, 0, 0, 0, 0, -1, resizeX = 1, resizeY = 1))
        b.add(Comp(AS.WINDOW, TYPE_LAYER, 0, 0, 500, 334, 0, reposX = 1, reposY = 1))
        b.g(AS.BG, 0, 0, 500, 334, "bg")
        b.layer(AS.CLOSE, 470, 4, 26, 24, AS.WINDOW, ops = listOf("Close"))
        b.text(AS.TITLE, 0, 10, 500, 16, AS.WINDOW, fonts.b12, "Attuned Sigils", colour = ORANGE, alignX = 1, alignY = 0)
        b.layer(AS.SCROLL, AS.X0, AS.SCROLL_Y, AS.SCROLL_W, AS.SCROLL_H, AS.WINDOW)
        b.scrollbarFor(AS.SCROLLBAR, AS.BAR_X, AS.Y0, AS.Y1 - AS.Y0 + 1, AS.WINDOW, INTERFACE_ID, AS.SCROLL)
        for (i in 0 until AS.SLOTS) {
            val base = AS.slot(i)
            val x = AS.x(i)
            val ix = AS.inX(i)
            val y = AS.Y0
            b.g(base + AS.S_COLUMN, x, y, AS.COL_W, AS.Y1 - AS.Y0 + 1, "col_empty")
            b.text(base + AS.S_HEAD, x, y + 8, AS.COL_W, 16, AS.WINDOW, fonts.b12, "Slot ${i + 1}", colour = ORANGE, alignX = 1, alignY = 0)
            b.g(base + AS.S_SKULL, ix + (AS.COL_W - AS.CROSS) / 2, AS.IN_CROSS_Y, AS.CROSS, AS.CROSS, "cross", parent = AS.SCROLL)
            b.text(base + AS.S_NAME, ix + 4, AS.IN_NAME_Y, AS.NAME_W, 2 * AS.LINE_P12 + 2, AS.SCROLL, fonts.p12, "", colour = ORANGE, alignX = 1, alignY = 0, lineHeight = AS.LINE_P12)
            b.text(base + AS.S_TYPE, ix, AS.IN_NAME_Y + 19, AS.COL_W, 14, AS.SCROLL, fonts.p11, "", colour = ORANGE, alignX = 1, alignY = 0, hidden = true)
            b.text(base + AS.S_TIER, ix, AS.IN_NAME_Y + 32, AS.COL_W, 14, AS.SCROLL, fonts.p11, "", colour = ORANGE, alignX = 1, alignY = 0, hidden = true)
            b.text(base + AS.S_EFFECT, ix + 6, AS.IN_NAME_Y + 49, AS.TEXT_W, 6 * AS.LINE_P11, AS.SCROLL, fonts.p11, "", colour = ORANGE, alignX = 1, alignY = 0, lineHeight = AS.LINE_P11)
            val bx = x + (AS.COL_W - AS.BTN_W) / 2
            b.g(base + AS.S_BUTTON, bx, AS.BTN_Y, AS.BTN_W, AS.BTN_H, "btn", hidden = true)
            b.text(base + AS.S_BUTTON_TEXT, bx, AS.BTN_Y + 5, AS.BTN_W, 14, AS.WINDOW, fonts.p11, "Unattune", colour = BUTTON_TEXT, alignX = 1, alignY = 0, hidden = true)
            b.layer(base + AS.S_BUTTON_HIT, bx, AS.BTN_Y, AS.BTN_W, AS.BTN_H, AS.WINDOW, hidden = true, ops = listOf("Unattune"))
            b.text(base + AS.S_HINT, x + 6, AS.Y1 - 34, AS.TEXT_W, 2 * AS.LINE_P11 + 2, AS.WINDOW, fonts.p11, "Use Attune on a sigil at a bank.", colour = WHITE, alignX = 1, alignY = 0, lineHeight = AS.LINE_P11)
        }
        return b.list.sortedBy { it.id }.also { list ->
            require(list.size == AS.COUNT && list.withIndex().all { (i, c) -> c.id == i }) { "component ids are not 0..${AS.COUNT - 1}" }
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val art = args.indexOf("--art").let { if (it >= 0) args[it + 1] else RspsPaths.data("art/sigils") }
        val index = DeathsOfficeInterfaceImportTool.INDEX_INTERFACES
        val spritesIndex = DeathsOfficeInterfaceImportTool.INDEX_SPRITES
        val library = CacheLibrary(LootKeyInterfaceImportTool.TARGETS[0])
        val fonts: LootKeyInterfaceImportTool.Fonts
        val current: Map<Int, String>
        val currentSprites = HashMap<Int, String>()
        try {
            fonts = LootKeyInterfaceImportTool.fonts(library)
            val files = library.index(index).archive(INTERFACE_ID)?.fileIds()?.toList() ?: emptyList()
            current = files.associateWith { f -> CacheItemProbeTool.sha1(library.data(index, INTERFACE_ID, f)!!) }
            SPRITES.indices.forEach { i ->
                library.data(spritesIndex, SPRITE_BASE + i, 0)?.let { currentSprites[SPRITE_BASE + i] = CacheItemProbeTool.sha1(it) }
            }
            val last = library.index(spritesIndex).archiveIds().maxOrNull() ?: -1
            val lastInterface = library.index(index).archiveIds().maxOrNull() ?: -1
            println("CENSUS last sprite=$last last interface=$lastInterface | this import: interface $INTERFACE_ID (${current.size} files now), sprites $SPRITE_BASE..${SPRITE_BASE + SPRITES.size - 1} (${currentSprites.size} now)")
            // A first import must land on free ids; a re-import may only replace its own.
            if (current.isEmpty()) require(lastInterface < INTERFACE_ID) { "interface $INTERFACE_ID is not free (last $lastInterface)" }
            if (currentSprites.isEmpty()) require(last < SPRITE_BASE) { "sprite $SPRITE_BASE is not free (last $last)" }
            require(last <= SPRITE_BASE + SPRITES.size - 1 || currentSprites.size == SPRITES.size) { "another import took sprites after $SPRITE_BASE; move new sprites behind it" }
        } finally {
            library.close()
        }
        val mutations = mutableListOf<CacheMutation>()
        SPRITES.forEachIndexed { i, name ->
            val image = ImageIO.read(File("$art/$name.png")) ?: error("missing $art/$name.png")
            mutations += CacheMutation(spritesIndex, SPRITE_BASE + i, 0, StoreArtTool.encode(image), "sigil sprite $name -> ${SPRITE_BASE + i}", expectedCurrentSha1 = currentSprites[SPRITE_BASE + i])
        }
        val comps = window(fonts)
        comps.forEach { c ->
            mutations += CacheMutation(index, INTERFACE_ID, c.id, DeathsOfficeInterfaceImportTool.encode(c), "interface $INTERFACE_ID:${c.id} type=${c.type}", expectedCurrentSha1 = current[c.id])
        }
        current.keys.filter { it >= comps.size }.sorted().forEach { f ->
            mutations += CacheMutation(index, INTERFACE_ID, f, null, "interface $INTERFACE_ID:$f (old layout)", expectedCurrentSha1 = current[f])
        }
        val transaction = CacheTransaction(targets = LootKeyInterfaceImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} mutations=${mutations.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.take(20).forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY RUN: pass --apply to write interface $INTERFACE_ID (${comps.size} components) and sprites $SPRITE_BASE..${SPRITE_BASE + SPRITES.size - 1}")
            return
        }
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        println("APPLIED ${applied.applied} skipped=${applied.skipped} journal=${applied.journalDir}")
        check(problems.isEmpty()) { "verify failed: $problems" }
        println("VERIFY_OK transaction=${transaction.id}")
    }
}
