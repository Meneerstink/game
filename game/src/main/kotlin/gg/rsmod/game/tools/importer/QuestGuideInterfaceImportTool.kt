package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool.Builder
import gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool.Comp
import gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool.WINDOW
import gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool.sprite

/**
 * "Choose your path" - the permanent quest-choice screen (owner mission 2026-09-26) as revision-667 interface
 * [INTERFACE_ID]. Route: an own 667 interface in the OSRS style, built from the OSRS sprites the Death's Office import
 * already brought into both caches (steelborder frame, v2 stone buttons, radio tick, bank padlock) - the OSRS Leagues
 * relic screen itself is drawn by OSRS clientscripts (league_relics_*) that the 667 client cannot run.
 *
 * Layout (488 x 320, fits the 512 x 334 fixed main screen): title and close button, one line of explanation, three rows
 * (MAGIC / PRAYER / GEAR) with the row label and the row's Confirm button on the left and equal cards to the right (a
 * two-card row is centred), and a "This choice is permanent. Are you sure?" confirmation over a dimmed window.
 *
 * Every component is static; the server fills and shows them (content: `QuestGuideUi`).
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.QuestGuideInterfaceImportTool [--apply]`. The tool owns
 * interface [INTERFACE_ID]; a re-run replaces its own components (pinned to what is there) and removes left-overs.
 */
object QuestGuideInterfaceImportTool {
    const val INTERFACE_ID = 1165

    const val WIDTH = 488
    const val HEIGHT = 320

    private const val STONE_IN = 921
    private const val STONE_OUT = 929

    const val GOLD = 0xFFB83F
    const val ORANGE = 0xFF981F
    const val CREAM = 0xEDE3C6
    const val WHITE = 0xFFFFFF
    const val GREY = 0x8A8A8A

    const val SUBTITLE = 14

    const val ROWS = 3
    const val ROW_FIRST = 15
    const val ROW_STRIDE = 108
    const val ROW_LABEL = 0
    const val ROW_CONFIRM = 1
    const val CONFIRM_ACTIVE = 2 // layer, +1..+9 stone, +10 text
    const val CONFIRM_ACTIVE_TEXT = 12
    const val CONFIRM_DISABLED = 13
    const val CONFIRM_DISABLED_TEXT = 23

    const val CARDS = 3
    const val CARD_OFFSET = 24
    const val CARD_STRIDE = 28
    const val CARD_NORMAL = 1 // layer, +1..+9 stone out
    const val CARD_DOWN = 11 // layer, +1..+9 stone in (locked)
    const val CARD_FRAME_OUTER = 21
    const val CARD_FRAME_INNER = 22
    const val CARD_ICON = 23
    const val CARD_NAME = 24
    const val CARD_DESC = 25
    const val CARD_TICK = 26
    const val CARD_LOCK = 27

    const val DIM = ROW_FIRST + ROWS * ROW_STRIDE // 339
    const val DIALOG = DIM + 1 // 340, +1..+9 stone
    const val DIALOG_TEXT = DIALOG + 10 // 350
    const val YES = DIALOG_TEXT + 1 // 351, +1..+9 stone, +10 text
    const val NO = YES + 11 // 362
    const val COUNT = NO + 11 // 373

    const val CARD_WIDTH = 124
    const val CARD_HEIGHT = 78
    const val CARD_GAP = 6
    const val CARDS_X = 90
    const val ROWS_Y = 52
    const val ROW_PITCH = 84

    fun rowBase(row: Int) = ROW_FIRST + row * ROW_STRIDE

    fun cardBase(row: Int, card: Int) = rowBase(row) + CARD_OFFSET + card * CARD_STRIDE

    /** Card x inside the window; a row with [count] cards is centred in the three-card width. */
    fun cardX(card: Int, count: Int) = CARDS_X + (CARDS - count) * (CARD_WIDTH + CARD_GAP) / 2 + card * (CARD_WIDTH + CARD_GAP)

    fun rowY(row: Int) = ROWS_Y + row * ROW_PITCH

    /** Default card placement (three cards); the server moves a two-card row's cards with IF_SETPOSITION. */
    fun components(fonts: LootKeyInterfaceImportTool.Fonts): List<Comp> {
        val b = Builder(fonts)
        b.frame(WIDTH, HEIGHT, "Choose your path")
        b.text(
            SUBTITLE, 12, 34, WIDTH - 24, 16, WINDOW, fonts.p11,
            "Choose one unlock per row. Your choice is permanent - other options can be earned by completing their quest.",
            colour = CREAM,
        )
        for (r in 0 until ROWS) {
            val base = rowBase(r)
            val y = rowY(r)
            b.text(base + ROW_LABEL, 10, y + 6, 74, 18, WINDOW, fonts.b12, "", colour = ORANGE)
            b.layer(base + ROW_CONFIRM, 10, y + 44, 74, 28, WINDOW, ops = listOf("Confirm"))
            b.layer(base + CONFIRM_ACTIVE, 0, 0, 74, 28, base + ROW_CONFIRM, hidden = true)
            b.stoneButton(base + CONFIRM_ACTIVE + 1, 74, 28, base + CONFIRM_ACTIVE, STONE_OUT)
            b.text(base + CONFIRM_ACTIVE_TEXT, 0, 0, 74, 28, base + CONFIRM_ACTIVE, fonts.p12, "Confirm", colour = WHITE)
            b.layer(base + CONFIRM_DISABLED, 0, 0, 74, 28, base + ROW_CONFIRM)
            b.stoneButton(base + CONFIRM_DISABLED + 1, 74, 28, base + CONFIRM_DISABLED, STONE_IN)
            b.text(base + CONFIRM_DISABLED_TEXT, 0, 0, 74, 28, base + CONFIRM_DISABLED, fonts.p12, "Confirm", colour = GREY)
            for (c in 0 until CARDS) {
                val card = cardBase(r, c)
                b.layer(card, cardX(c, CARDS), y, CARD_WIDTH, CARD_HEIGHT, WINDOW, hidden = true, ops = listOf("Select"))
                b.layer(card + CARD_NORMAL, 0, 0, CARD_WIDTH, CARD_HEIGHT, card)
                b.stoneButton(card + CARD_NORMAL + 1, CARD_WIDTH, CARD_HEIGHT, card + CARD_NORMAL, STONE_OUT)
                b.layer(card + CARD_DOWN, 0, 0, CARD_WIDTH, CARD_HEIGHT, card, hidden = true)
                b.stoneButton(card + CARD_DOWN + 1, CARD_WIDTH, CARD_HEIGHT, card + CARD_DOWN, STONE_IN)
                b.rect(card + CARD_FRAME_OUTER, 0, 0, CARD_WIDTH, CARD_HEIGHT, card, GOLD, hidden = true)
                b.rect(card + CARD_FRAME_INNER, 1, 1, CARD_WIDTH - 2, CARD_HEIGHT - 2, card, GOLD, hidden = true)
                b.item(card + CARD_ICON, (CARD_WIDTH - 36) / 2, 4, card, emptyList(), outline = 1, hidden = false)
                b.text(card + CARD_NAME, 3, 37, CARD_WIDTH - 6, 14, card, fonts.p12, "", colour = GOLD)
                b.text(card + CARD_DESC, 4, 51, CARD_WIDTH - 8, 25, card, fonts.p11, "", colour = CREAM, alignY = 0, lineHeight = 11)
                b.graphic(card + CARD_TICK, CARD_WIDTH - 22, 4, 17, 17, card, sprite(699))
                b.graphic(card + CARD_LOCK, CARD_WIDTH - 26, 4, 22, 22, card, sprite(1342))
            }
        }
        // The confirmation: the window dimmed, a stone panel with the question and Yes / No.
        b.add(Comp(DIM, 3, 0, 0, WIDTH, HEIGHT, WINDOW, hidden = true, colour = 0x000000, filled = true, transparency = 110))
        b.layer(DIALOG, (WIDTH - 240) / 2, (HEIGHT - 110) / 2, 240, 110, WINDOW, hidden = true)
        b.stoneButton(DIALOG + 1, 240, 110, DIALOG, STONE_OUT)
        b.text(DIALOG_TEXT, 10, 10, 220, 50, DIALOG, fonts.b12, "This choice is permanent. Are you sure?", colour = ORANGE, lineHeight = 15)
        listOf(YES to "Yes", NO to "No").forEachIndexed { i, (id, label) ->
            b.layer(id, 20 + i * 110, 66, 90, 30, DIALOG, ops = listOf(label))
            b.stoneButton(id + 1, 90, 30, id, STONE_OUT)
            b.text(id + 10, 0, 0, 90, 30, id, fonts.b12, label, colour = WHITE)
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
            // The Death's Office sprites this window draws must already be in the cache.
            require(library.index(DeathsOfficeInterfaceImportTool.INDEX_SPRITES).archive(sprite(STONE_OUT)) != null) { "Death's Office sprites missing" }
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
