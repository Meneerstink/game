package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool.Builder
import gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool.Comp
import gg.rsmod.game.tools.importer.ChoosePathArtTool as Art

/**
 * "Choose your path" - the permanent quest-choice screen (owner mission 2026-09-26) as revision-667 interface
 * [INTERFACE_ID]. Route: an own 667 interface (the OSRS Leagues relic screen is drawn by OSRS clientscripts the 667 client
 * cannot run), painted in the owner-approved premium Deadman design ([ChoosePathArtTool] sprites: obsidian and polished
 * gold, MAGIC sapphire, PRAYER gold, GEAR ruby).
 *
 * Layout (488 x 320, fits the 512 x 334 fixed main screen): the painted window (title, row plaques, frame) with the close
 * button, one line of explanation over two rows, per row the Confirm button under the painted plaque and up to three cards
 * (a two-card row is centred by the server), and the "This choice is permanent. Are you sure?" confirmation over a dimmed
 * window. Each card stacks its four painted faces (normal / selected / chosen / locked), the item icon, name, unlock text
 * and the tick or padlock badge; the server shows the right ones (content: `QuestGuideUi`).
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.QuestGuideInterfaceImportTool [--apply]`. The tool owns
 * interface [INTERFACE_ID]; a re-run replaces its own components (pinned to what is there) and removes left-overs.
 */
object QuestGuideInterfaceImportTool {
    const val INTERFACE_ID = 1165

    const val WIDTH = 488
    const val HEIGHT = 320

    const val ORANGE = 0xFF981F
    const val GOLD = 0xFFD24A
    const val CREAM = 0xF3E7C8
    const val WHITE = 0xFFFFFF
    const val GREY = 0x8A8A8A

    const val ROOT = 0
    const val WINDOW = 1
    const val BACKGROUND = 2
    const val CLOSE = 3
    const val SUBTITLE = 4

    const val ROWS = 3
    const val ROW_FIRST = 5
    const val ROW_STRIDE = 34
    const val ROW_CONFIRM = 0
    const val CONFIRM_ACTIVE = 1
    const val CONFIRM_DISABLED = 2
    const val CONFIRM_TEXT = 3

    const val CARDS = 3
    const val CARD_OFFSET = 4
    const val CARD_STRIDE = 10
    const val FACE_FIRST = 1 // +1 normal, +2 selected, +3 chosen, +4 locked
    const val CARD_ICON = 5
    const val CARD_NAME = 6
    const val CARD_DESC = 7
    const val CARD_TICK = 8
    const val CARD_LOCK = 9

    const val DIM = ROW_FIRST + ROWS * ROW_STRIDE // 107
    const val DIALOG = DIM + 1 // 108
    const val DIALOG_ART = DIALOG + 1
    const val DIALOG_TEXT = DIALOG + 2
    const val YES = DIALOG + 3 // 111: layer, +1 art, +2 text
    const val NO = YES + 3 // 114
    const val COUNT = NO + 3 // 117

    const val CARD_WIDTH = 124
    const val CARD_HEIGHT = 72
    const val CARD_GAP = 6
    const val CARDS_X = 92
    const val ROWS_Y = 68
    const val ROW_PITCH = 78

    private const val TYPE_RECTANGLE = 3

    fun rowBase(row: Int) = ROW_FIRST + row * ROW_STRIDE

    fun cardBase(row: Int, card: Int) = rowBase(row) + CARD_OFFSET + card * CARD_STRIDE

    /** Card x inside the window; a row with [count] cards is centred in the three-card width. */
    fun cardX(card: Int, count: Int) = CARDS_X + (CARDS - count) * (CARD_WIDTH + CARD_GAP) / 2 + card * (CARD_WIDTH + CARD_GAP)

    fun rowY(row: Int) = ROWS_Y + row * ROW_PITCH

    fun components(fonts: LootKeyInterfaceImportTool.Fonts): List<Comp> {
        val b = Builder(fonts)
        b.add(Comp(ROOT, 0, 0, 0, 0, 0, -1, resizeX = 1, resizeY = 1))
        b.add(Comp(WINDOW, 0, 0, 0, WIDTH, HEIGHT, ROOT, reposX = 1, reposY = 1))
        b.graphic(BACKGROUND, 0, 0, WIDTH, HEIGHT, WINDOW, Art.BACKGROUND)
        b.graphic(CLOSE, WIDTH - 32, 10, 22, 22, WINDOW, Art.CLOSE, ops = listOf("Close"))
        b.text(
            SUBTITLE, 30, 42, WIDTH - 60, 24, WINDOW, fonts.p11,
            "Choose one unlock per row. Your choice is permanent - other options can be earned by completing their quest.",
            colour = CREAM, lineHeight = 12,
        )
        for (r in 0 until ROWS) {
            val base = rowBase(r)
            val y = rowY(r)
            b.layer(base + ROW_CONFIRM, 15, y + 42, 66, 26, WINDOW, ops = listOf("Confirm"))
            b.add(Comp(base + CONFIRM_ACTIVE, 5, 0, 0, 66, 26, base + ROW_CONFIRM, hidden = true, graphic = Art.CONFIRM_ACTIVE))
            b.add(Comp(base + CONFIRM_DISABLED, 5, 0, 0, 66, 26, base + ROW_CONFIRM, graphic = Art.CONFIRM_DISABLED))
            b.text(base + CONFIRM_TEXT, 0, 0, 66, 26, base + ROW_CONFIRM, fonts.b12, "Confirm", colour = GREY)
            for (c in 0 until CARDS) {
                val card = cardBase(r, c)
                b.layer(card, cardX(c, CARDS), y, CARD_WIDTH, CARD_HEIGHT, WINDOW, hidden = true, ops = listOf("Select"))
                for (state in 0 until 4) {
                    b.add(Comp(card + FACE_FIRST + state, 5, 0, 0, CARD_WIDTH, CARD_HEIGHT, card, hidden = state != 0, graphic = Art.card(r, state)))
                }
                b.item(card + CARD_ICON, (CARD_WIDTH - 36) / 2, 2, card, emptyList(), outline = 1, hidden = false)
                b.text(card + CARD_NAME, 3, 36, CARD_WIDTH - 6, 14, card, fonts.p12, "", colour = GOLD)
                b.text(card + CARD_DESC, 4, 50, CARD_WIDTH - 8, 22, card, fonts.p11, "", colour = CREAM, alignY = 0, lineHeight = 10)
                b.add(Comp(card + CARD_TICK, 5, CARD_WIDTH - 23, 4, 20, 20, card, hidden = true, graphic = Art.TICK))
                b.add(Comp(card + CARD_LOCK, 5, CARD_WIDTH - 23, 4, 20, 20, card, hidden = true, graphic = Art.LOCK))
            }
        }
        b.add(Comp(DIM, TYPE_RECTANGLE, 0, 0, WIDTH, HEIGHT, WINDOW, hidden = true, colour = 0x000000, filled = true, transparency = 110))
        b.layer(DIALOG, (WIDTH - 260) / 2, (HEIGHT - 118) / 2, 260, 118, WINDOW, hidden = true)
        b.graphic(DIALOG_ART, 0, 0, 260, 118, DIALOG, Art.DIALOG)
        b.text(DIALOG_TEXT, 12, 32, 236, 36, DIALOG, fonts.b12, "This choice is permanent.<br>Are you sure?", colour = GOLD, lineHeight = 15)
        listOf(Triple(YES, "Yes", Art.YES), Triple(NO, "No", Art.NO)).forEachIndexed { i, (id, label, sprite) ->
            b.layer(id, 26 + i * 112, 76, 96, 28, DIALOG, ops = listOf(label))
            b.graphic(id + 1, 0, 0, 96, 28, id, sprite)
            b.text(id + 2, 0, 0, 96, 28, id, fonts.b12, label, colour = if (i == 0) 0x3A1E02 else WHITE)
        }
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
            // The window's own art must be imported first (ChoosePathArtTool --apply).
            require(library.index(DeathsOfficeInterfaceImportTool.INDEX_SPRITES).archive(Art.BACKGROUND) != null) { "Choose-your-path sprites missing: run ChoosePathArtTool --apply first" }
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
