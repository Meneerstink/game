package gg.rsmod.game.tools.importer

import java.io.File

/**
 * Builds the four casino screens - Dice, Mines, Blackjack and Flower Poker - as revision-667 if3 interfaces and
 * writes them into both production caches through [CacheTransaction].
 *
 * Owner 2026-09-20: "Correct interfaces, buttons, animations/GFX and game states."
 *
 * **This tool adds no sprites.** That is a deliberate constraint, not a shortcut:
 *
 *  - the stone frame, buttons and close cross reuse the OSRS sprites [LootKeyInterfaceImportTool] already imported
 *    at [LootKeyInterfaceImportTool.SPRITE_BASE], so the casino looks like the rest of this server's custom UI;
 *  - flowers, gems and mines are drawn as **real cache items** through `IF_SETOBJECT` (the flower items 2460-2476,
 *    an uncut gem and a cannonball), so they need no art of their own;
 *  - playing cards are drawn as a rectangle plus rank/suit text in the cache's own fonts, because this revision
 *    has no card art and a PNG-to-667 sprite encoder does not exist in this project. Building one, quantising 54
 *    card images to a 255-colour palette and importing them is a far larger change with no way to verify it short
 *    of looking at the running client.
 *
 * The result is that the only cache mutation here is four new groups in the interface index - the sprite index is
 * untouched - which is the smallest, most reversible change that still delivers real interfaces.
 *
 * Every screen is static: the server fills the texts with IF_SETTEXT, swaps item slots with IF_SETOBJECT, shows
 * and hides layers with IF_SETHIDE and arms the buttons with IF_SETEVENTS, so nothing depends on a clientscript
 * the 667 cache does not have.
 *
 * Usage: `./gradlew :game:runCasinoInterfaceImportTool --args="[--apply]"`
 */
object CasinoInterfaceImportTool {
    val TARGETS = OsrsItemImportTool.TARGETS

    const val INDEX_INTERFACES = 3

    /**
     * New 667 interface ids.
     *
     * Probed, not assumed: `--probe` reported the interface index holds 1152 archives with 1151 the highest, so
     * 1152-1155 is the first free run of four. (1149 is the loot-key screen and 1150-1151 were taken after it,
     * which is why the loot-key tool's "last interface is 1148" comment can no longer be trusted.)
     */
    const val DICE_ID = 1152
    const val MINES_ID = 1153
    const val BLACKJACK_ID = 1154
    const val FLOWER_ID = 1155

    // ---- shared frame geometry ----

    const val WINDOW_W = 480
    const val WINDOW_H = 320

    const val ROOT = 0
    const val WINDOW = 1
    const val TITLE = 12
    const val CLOSE = 13

    /** First id a screen may use for its own content; 0..19 are the shared frame. */
    const val CONTENT_BASE = 20

    val ORANGE = LootKeyInterfaceImportTool.ORANGE
    val WHITE = LootKeyInterfaceImportTool.WHITE
    val RED = LootKeyInterfaceImportTool.RED
    const val GREEN = 0x00FF00
    const val YELLOW = 0xFFFF00
    const val GREY = 0x9F9F9F
    val LINE = LootKeyInterfaceImportTool.LINE

    /**
     * The two halves of the dice bar: a muted table red and green rather than the pure 0xFF0000/0x00FF00 used for
     * text, so that three hundred pixels of it do not glare, and so the white roll marker still reads on top.
     */
    const val BAR_LOSE = 0x8E2F2F
    const val BAR_WIN = 0x2F8E4A

    /** Card ranks, printed on the pale plate: hearts and diamonds red, spades and clubs black. */
    const val CARD_RED = 0xC00000
    const val CARD_BLACK = 0x101010

    private const val TYPE_LAYER = 0
    private const val TYPE_RECTANGLE = 3
    private const val TYPE_TEXT = 4
    private const val TYPE_GRAPHIC = 5
    private const val TYPE_LINE = 9

    private fun sprite(osrsId: Int) = LootKeyInterfaceImportTool.sprite(osrsId)

    /** Builder for one screen: shared frame first, then the screen's own components. */
    class Screen(
        val id: Int,
        val title: String,
        val fonts: LootKeyInterfaceImportTool.Fonts,
    ) {
        val list = mutableListOf<LootKeyInterfaceImportTool.Component>()

        fun add(c: LootKeyInterfaceImportTool.Component) {
            require(list.none { it.id == c.id }) { "duplicate component ${c.id} on interface $id" }
            list += c
        }

        fun layer(
            id: Int,
            x: Int,
            y: Int,
            w: Int,
            h: Int,
            parent: Int,
            hidden: Boolean = false,
            reposX: Int = 0,
            reposY: Int = 0,
            resizeX: Int = 0,
            resizeY: Int = 0,
        ) = add(
            LootKeyInterfaceImportTool.Component(
                id, TYPE_LAYER, x, y, w, h, parent,
                hidden = hidden, reposX = reposX, reposY = reposY, resizeX = resizeX, resizeY = resizeY,
            ),
        )

        fun graphic(
            id: Int,
            x: Int,
            y: Int,
            w: Int,
            h: Int,
            parent: Int,
            spriteId: Int,
            tiling: Boolean = false,
            ops: List<String> = emptyList(),
            opBase: String = "",
            hidden: Boolean = false,
        ) = add(
            LootKeyInterfaceImportTool.Component(
                id, TYPE_GRAPHIC, x, y, w, h, parent,
                graphic = spriteId, tiling = tiling, ops = ops, opBase = opBase, hidden = hidden,
            ),
        )

        /** An item slot: a graphic with no sprite, filled by the server with IF_SETOBJECT. */
        fun item(
            id: Int,
            x: Int,
            y: Int,
            parent: Int,
            size: Int = 32,
            ops: List<String> = emptyList(),
            opBase: String = "",
            hidden: Boolean = false,
        ) = add(
            LootKeyInterfaceImportTool.Component(
                id, TYPE_GRAPHIC, x, y, size, size, parent,
                graphic = -1, ops = ops, opBase = opBase, hidden = hidden,
            ),
        )

        fun text(
            id: Int,
            x: Int,
            y: Int,
            w: Int,
            h: Int,
            parent: Int,
            font: Int,
            value: String,
            colour: Int,
            alignX: Int = 1,
            alignY: Int = 1,
            lineHeight: Int = 0,
            ops: List<String> = emptyList(),
            opBase: String = "",
            hidden: Boolean = false,
        ) = add(
            LootKeyInterfaceImportTool.Component(
                id, TYPE_TEXT, x, y, w, h, parent,
                font = font, text = value, colour = colour, alignX = alignX, alignY = alignY,
                shadow = true, lineHeight = lineHeight, ops = ops, opBase = opBase, hidden = hidden,
            ),
        )

        fun rect(
            id: Int,
            x: Int,
            y: Int,
            w: Int,
            h: Int,
            parent: Int,
            colour: Int,
            filled: Boolean = true,
            transparency: Int = 0,
            hidden: Boolean = false,
            ops: List<String> = emptyList(),
            opBase: String = "",
        ) = add(
            LootKeyInterfaceImportTool.Component(
                id, TYPE_RECTANGLE, x, y, w, h, parent,
                colour = colour, filled = filled, transparency = transparency, hidden = hidden, ops = ops, opBase = opBase,
            ),
        )

        fun line(
            id: Int,
            x: Int,
            y: Int,
            w: Int,
            h: Int,
            parent: Int,
            colour: Int,
        ) = add(LootKeyInterfaceImportTool.Component(id, TYPE_LINE, x, y, w, h, parent, colour = colour))

        /**
         * A pressable button: the OSRS dialog-button sprite with a caption centred on it. Returns the id of the
         * sprite component, which is the one the server arms and the client sends the op for.
         */
        fun button(
            id: Int,
            x: Int,
            y: Int,
            w: Int,
            h: Int,
            parent: Int,
            caption: String,
            op: String,
            captionId: Int = id + 1,
            hidden: Boolean = false,
        ): Int {
            graphic(id, x, y, w, h, parent, sprite(653), ops = listOf(op), hidden = hidden)
            text(captionId, x, y, w, h, parent, fonts.p12, caption, ORANGE, hidden = hidden)
            return id
        }

        /** The shared stone frame, copied from the loot-key screen's proven geometry. */
        fun frame() {
            layer(ROOT, 0, 0, 0, 0, parent = -1, resizeX = 1, resizeY = 1)
            layer(WINDOW, 0, 0, WINDOW_W, WINDOW_H, parent = ROOT, reposX = 1, reposY = 1)

            graphic(2, 1, 1, WINDOW_W - 2, WINDOW_H - 2, WINDOW, sprite(297), tiling = true)
            graphic(3, 32, -13, WINDOW_W - 64, 20, WINDOW, sprite(820), tiling = true)
            graphic(4, 32, WINDOW_H - 20, WINDOW_W - 64, 20, WINDOW, sprite(822), tiling = true)
            graphic(5, -13, 32, 20, WINDOW_H - 64, WINDOW, sprite(821), tiling = true)
            graphic(6, WINDOW_W - 20, 32, 20, WINDOW_H - 64, WINDOW, sprite(823), tiling = true)
            graphic(7, 0, 0, 32, 32, WINDOW, sprite(824))
            graphic(8, WINDOW_W - 32, 0, 32, 32, WINDOW, sprite(825))
            graphic(9, 0, WINDOW_H - 32, 32, 32, WINDOW, sprite(826))
            graphic(10, WINDOW_W - 32, WINDOW_H - 32, 32, 32, WINDOW, sprite(827))
            graphic(11, 11, 15, WINDOW_W - 22, 20, WINDOW, sprite(828), tiling = true)
            text(TITLE, 0, 5, WINDOW_W, 20, WINDOW, fonts.b12, title, ORANGE)
            graphic(CLOSE, WINDOW_W - 26, 10, 16, 16, WINDOW, sprite(831), ops = listOf("Close"))
            graphic(14, 0, 17, 32, 32, WINDOW, sprite(829))
            graphic(15, WINDOW_W - 11, 17, 11, 32, WINDOW, sprite(830))
        }

        /**
         * The provably-fair strip every screen carries along its bottom: the server-seed commitment, the player's
         * own seed and the next nonce, with the two controls that manage them.
         */
        fun fairnessStrip(
            base: Int,
            y: Int,
        ) {
            graphic(base, 11, y - 6, WINDOW_W - 22, 20, WINDOW, sprite(828), tiling = true)
            graphic(base + 1, 0, y - 4, 32, 32, WINDOW, sprite(829))
            graphic(base + 2, WINDOW_W - 11, y - 4, 11, 32, WINDOW, sprite(830))
            text(base + 3, 14, y + 12, 200, 14, WINDOW, fonts.p11, "Server seed:", GREY, alignX = 0)
            text(base + 4, 100, y + 12, 160, 14, WINDOW, fonts.p11, "", WHITE, alignX = 0)
            text(base + 5, 14, y + 26, 200, 14, WINDOW, fonts.p11, "Client seed:", GREY, alignX = 0)
            text(base + 6, 100, y + 26, 160, 14, WINDOW, fonts.p11, "", WHITE, alignX = 0)
            text(base + 7, 270, y + 12, 90, 14, WINDOW, fonts.p11, "Nonce:", GREY, alignX = 0)
            text(base + 8, 320, y + 12, 60, 14, WINDOW, fonts.p11, "0", WHITE, alignX = 0)
            button(base + 9, WINDOW_W - 160, y + 22, 68, 24, WINDOW, "Set seed", "Set seed", captionId = base + 10)
            button(base + 11, WINDOW_W - 84, y + 22, 68, 24, WINDOW, "Verify", "Verify", captionId = base + 12)
        }

        /**
         * Returns the components as a gapless `0..n` run.
         *
         * Component ids are file ids inside the interface's cache archive, and this client walks them by index, so
         * a hole would shift every later component. The named id constants above are assigned by hand to keep the
         * server code readable, which inevitably leaves a few unused numbers between groups; rather than hand-tune
         * them, any gap is filled here with an inert hidden layer. It costs a handful of bytes and removes a whole
         * class of off-by-one bug.
         */
        fun build(): List<LootKeyInterfaceImportTool.Component> {
            val used = list.map { it.id }.toSet()
            val filler =
                (0..used.max()).filter { it !in used }.map { missing ->
                    LootKeyInterfaceImportTool.Component(missing, TYPE_LAYER, 0, 0, 0, 0, WINDOW, hidden = true)
                }
            return (list + filler).sortedBy { it.id }
        }
    }

    // ================================================================= Dice

    /**
     * Dice.
     *
     * The screen is built around the win/lose bar, because a dice game whose only feedback is a number is a
     * spreadsheet. The bar is the 0.00-100.00 range drawn as 50 segments of two points each: every segment exists
     * twice, once red and once green, and the server shows whichever half matches the current target. Moving the
     * target therefore slides the colour boundary in real time, and a marker under the bar lights up where the
     * roll actually landed. It is built from plain rectangles because this client has no way to resize or recolour
     * a component after the fact - [gg.rsmod.plugins.api.ext] exposes text, sprite, item, model, anim and hide,
     * and nothing else - so "one rectangle that grows" is not available, while "a hundred rectangles of which the
     * right half is hidden" is.
     *
     * Texts that must change colour (the roll readout, the outcome line) are built the same way: one component per
     * colour, stacked, with all but one hidden.
     */
    object Dice {
        const val BET_TEXT = CONTENT_BASE + 1
        const val BET_MINUS = CONTENT_BASE + 2
        const val BET_PLUS = CONTENT_BASE + 4
        const val BET_CUSTOM = CONTENT_BASE + 6
        const val TARGET_TEXT = CONTENT_BASE + 9
        const val TARGET_MINUS = CONTENT_BASE + 10
        const val TARGET_PLUS = CONTENT_BASE + 12
        const val TARGET_CUSTOM = CONTENT_BASE + 14
        const val BAR_BACKING = CONTENT_BASE + 16

        /** Segments of the win/lose bar; each covers two points of the 0-100 range. */
        const val SEGMENTS = 50

        const val BAR_FIRST = CONTENT_BASE + 20
        const val MARKER_FIRST = BAR_FIRST + SEGMENTS * 2
        const val AFTER_BAR = MARKER_FIRST + SEGMENTS

        /** The red half of segment [index] - shown while that segment is below the target. */
        fun loseSegment(index: Int) = BAR_FIRST + index * 2

        /** The green half of segment [index] - shown while that segment is at or above the target. */
        fun winSegment(index: Int) = BAR_FIRST + index * 2 + 1

        /** The tick under segment [index]; exactly one is ever visible, under the segment the roll fell in. */
        fun marker(index: Int) = MARKER_FIRST + index

        const val SCALE_FIRST = AFTER_BAR
        const val ROLL_IDLE = AFTER_BAR + 5
        const val ROLL_WIN = AFTER_BAR + 6
        const val ROLL_LOSE = AFTER_BAR + 7
        const val CHANCE_TEXT = AFTER_BAR + 10
        const val MULTIPLIER_TEXT = AFTER_BAR + 12
        const val PAYOUT_TEXT = AFTER_BAR + 14
        const val COINS_TEXT = AFTER_BAR + 16
        const val ROLL_BUTTON = AFTER_BAR + 17
        const val OUTCOME_IDLE = AFTER_BAR + 19
        const val OUTCOME_WIN = AFTER_BAR + 20
        const val OUTCOME_LOSE = AFTER_BAR + 21
        const val FAIRNESS_BASE = AFTER_BAR + 22
        const val SEED_HASH_TEXT = FAIRNESS_BASE + 4
        const val CLIENT_SEED_TEXT = FAIRNESS_BASE + 6
        const val NONCE_TEXT = FAIRNESS_BASE + 8
        const val SET_SEED_BUTTON = FAIRNESS_BASE + 9
        const val VERIFY_BUTTON = FAIRNESS_BASE + 11

        // Bar geometry. 50 segments of 6px sit on a 300px track, so point `v` of the range is at BAR_X + v * 3.
        const val BAR_X = 32
        const val BAR_Y = 104
        const val BAR_H = 18
        const val SEGMENT_W = 6

        fun screen(fonts: LootKeyInterfaceImportTool.Fonts): Screen {
            val s = Screen(DICE_ID, "Dice", fonts)
            s.frame()

            s.text(CONTENT_BASE, 20, 42, 120, 16, WINDOW, fonts.p12, "Bet amount", GREY, alignX = 0)
            s.text(BET_TEXT, 140, 42, 150, 16, WINDOW, fonts.b12, "1,000", WHITE, alignX = 0)
            s.button(BET_MINUS, 300, 38, 30, 24, WINDOW, "-", "Halve bet", captionId = CONTENT_BASE + 3)
            s.button(BET_PLUS, 334, 38, 30, 24, WINDOW, "+", "Double bet", captionId = CONTENT_BASE + 5)
            s.button(BET_CUSTOM, 368, 38, 90, 24, WINDOW, "Set bet", "Set bet", captionId = CONTENT_BASE + 7)

            s.text(CONTENT_BASE + 8, 20, 72, 120, 16, WINDOW, fonts.p12, "Roll over", GREY, alignX = 0)
            s.text(TARGET_TEXT, 140, 72, 150, 16, WINDOW, fonts.b12, "50", WHITE, alignX = 0)
            s.button(TARGET_MINUS, 300, 68, 30, 24, WINDOW, "-", "Lower target", captionId = CONTENT_BASE + 11)
            s.button(TARGET_PLUS, 334, 68, 30, 24, WINDOW, "+", "Raise target", captionId = CONTENT_BASE + 13)
            s.button(TARGET_CUSTOM, 368, 68, 90, 24, WINDOW, "Set target", "Set target", captionId = CONTENT_BASE + 15)

            // The track, then the two coloured halves of every segment on top of it.
            s.rect(BAR_BACKING, BAR_X - 2, BAR_Y - 2, SEGMENTS * SEGMENT_W + 4, BAR_H + 4, WINDOW, 0x1B1B17)
            for (i in 0 until SEGMENTS) {
                val x = BAR_X + i * SEGMENT_W
                s.rect(loseSegment(i), x, BAR_Y, SEGMENT_W, BAR_H, WINDOW, BAR_LOSE)
                s.rect(winSegment(i), x, BAR_Y, SEGMENT_W, BAR_H, WINDOW, BAR_WIN, hidden = true)
                s.rect(marker(i), x + 1, BAR_Y + BAR_H + 2, SEGMENT_W - 2, 4, WINDOW, WHITE, hidden = true)
            }

            // Scale under the bar: 0 / 25 / 50 / 75 / 100, each centred on the point it marks.
            for (i in 0 until 5) {
                val value = i * 25
                s.text(SCALE_FIRST + i, BAR_X + value * 3 - 20, BAR_Y + BAR_H + 8, 40, 12, WINDOW, fonts.p11, "$value", GREY)
            }

            // The roll readout, to the right of the bar, in three colours of which one is shown at a time.
            s.text(ROLL_IDLE, 344, BAR_Y - 4, 120, 26, WINDOW, fonts.b12, "-", GREY)
            s.text(ROLL_WIN, 344, BAR_Y - 4, 120, 26, WINDOW, fonts.b12, "", GREEN, hidden = true)
            s.text(ROLL_LOSE, 344, BAR_Y - 4, 120, 26, WINDOW, fonts.b12, "", RED, hidden = true)
            s.text(AFTER_BAR + 8, 344, BAR_Y + BAR_H + 8, 120, 12, WINDOW, fonts.p11, "Last roll", GREY)

            s.text(AFTER_BAR + 9, 20, 150, 110, 16, WINDOW, fonts.p12, "Win chance", GREY, alignX = 0)
            s.text(CHANCE_TEXT, 130, 150, 100, 16, WINDOW, fonts.p12, "50.01%", WHITE, alignX = 0)
            s.text(AFTER_BAR + 11, 250, 150, 90, 16, WINDOW, fonts.p12, "Multiplier", GREY, alignX = 0)
            s.text(MULTIPLIER_TEXT, 344, 150, 120, 16, WINDOW, fonts.p12, "1.98x", WHITE, alignX = 0)
            s.text(AFTER_BAR + 13, 20, 170, 110, 16, WINDOW, fonts.p12, "Payout", GREY, alignX = 0)
            s.text(PAYOUT_TEXT, 130, 170, 110, 16, WINDOW, fonts.p12, "1,979", YELLOW, alignX = 0)
            s.text(AFTER_BAR + 15, 250, 170, 90, 16, WINDOW, fonts.p12, "Coins", GREY, alignX = 0)
            s.text(COINS_TEXT, 344, 170, 120, 16, WINDOW, fonts.p12, "0", WHITE, alignX = 0)

            s.button(ROLL_BUTTON, 196, 192, 88, 32, WINDOW, "Roll", "Roll", captionId = AFTER_BAR + 18)

            s.text(OUTCOME_IDLE, 0, 228, WINDOW_W, 18, WINDOW, fonts.p12, "Set your bet and roll.", GREY)
            s.text(OUTCOME_WIN, 0, 228, WINDOW_W, 18, WINDOW, fonts.p12, "", GREEN, hidden = true)
            s.text(OUTCOME_LOSE, 0, 228, WINDOW_W, 18, WINDOW, fonts.p12, "", RED, hidden = true)

            s.fairnessStrip(FAIRNESS_BASE, 252)
            return s
        }
    }

    // ================================================================= Mines

    object Mines {
        const val GRID_FIRST = CONTENT_BASE + 10
        const val CELL_STRIDE = 2
        const val CELLS = 25

        const val MINES_TEXT = CONTENT_BASE + 1
        const val MINES_MINUS = CONTENT_BASE + 2
        const val MINES_PLUS = CONTENT_BASE + 4
        const val BET_TEXT = CONTENT_BASE + 7
        const val BET_CUSTOM = CONTENT_BASE + 8

        const val AFTER_GRID = GRID_FIRST + CELLS * CELL_STRIDE
        const val START_BUTTON = AFTER_GRID
        const val CASHOUT_BUTTON = AFTER_GRID + 2
        const val MULTIPLIER_TEXT = AFTER_GRID + 5
        const val NEXT_TEXT = AFTER_GRID + 7
        const val VALUE_TEXT = AFTER_GRID + 9
        const val STATUS_TEXT = AFTER_GRID + 10
        const val COINS_TEXT = AFTER_GRID + 12
        const val FAIRNESS_BASE = AFTER_GRID + 13

        /**
         * Board geometry.
         *
         * The first draft put 36px cells on a 40px pitch starting at y 76, which runs the last two rows of the
         * board from y 196 to y 272 - straight through the fairness strip, which starts at y 246. A 30px cell on a
         * 34px pitch from y 70 ends the board at y 236 and leaves the strip alone.
         */
        const val GRID_X = 24
        const val GRID_Y = 70
        const val CELL_PITCH = 34
        const val CELL_SIZE = 30
        const val SEED_HASH_TEXT = FAIRNESS_BASE + 4
        const val CLIENT_SEED_TEXT = FAIRNESS_BASE + 6
        const val NONCE_TEXT = FAIRNESS_BASE + 8
        const val SET_SEED_BUTTON = FAIRNESS_BASE + 9
        const val VERIFY_BUTTON = FAIRNESS_BASE + 11

        /** Cell `i` occupies [GRID_FIRST] + i * 2 (the cover plate) and + 1 (the item slot under it). */
        fun cover(cell: Int) = GRID_FIRST + cell * CELL_STRIDE

        fun slot(cell: Int) = GRID_FIRST + cell * CELL_STRIDE + 1

        fun screen(fonts: LootKeyInterfaceImportTool.Fonts): Screen {
            val s = Screen(MINES_ID, "Mines", fonts)
            s.frame()

            s.text(CONTENT_BASE, 20, 44, 100, 16, WINDOW, fonts.p12, "Mines", GREY, alignX = 0)
            s.text(MINES_TEXT, 90, 44, 60, 16, WINDOW, fonts.b12, "3", WHITE, alignX = 0)
            s.button(MINES_MINUS, 130, 40, 30, 24, WINDOW, "-", "Fewer mines", captionId = CONTENT_BASE + 3)
            s.button(MINES_PLUS, 164, 40, 30, 24, WINDOW, "+", "More mines", captionId = CONTENT_BASE + 5)
            s.text(CONTENT_BASE + 6, 210, 44, 60, 16, WINDOW, fonts.p12, "Bet", GREY, alignX = 0)
            s.text(BET_TEXT, 250, 44, 120, 16, WINDOW, fonts.b12, "1,000", WHITE, alignX = 0)
            s.button(BET_CUSTOM, 370, 40, 88, 24, WINDOW, "Set bet", "Set bet", captionId = CONTENT_BASE + 9)

            // 5x5 board on the left, the numbers and controls in a column beside it.
            for (cell in 0 until CELLS) {
                val column = cell % 5
                val row = cell / 5
                val x = GRID_X + column * CELL_PITCH
                val y = GRID_Y + row * CELL_PITCH
                s.item(slot(cell), x + 3, y + 3, WINDOW, size = 24)
                s.graphic(cover(cell), x, y, CELL_SIZE, CELL_SIZE, WINDOW, sprite(170), ops = listOf("Reveal"), opBase = "Tile ${cell + 1}")
            }

            s.button(START_BUTTON, 250, 74, 100, 30, WINDOW, "Start", "Start", captionId = AFTER_GRID + 1)
            s.button(CASHOUT_BUTTON, 250, 108, 100, 30, WINDOW, "Cash out", "Cash out", captionId = AFTER_GRID + 3, hidden = true)

            s.text(AFTER_GRID + 4, 215, 146, 95, 16, WINDOW, fonts.p12, "Multiplier", GREY, alignX = 0)
            s.text(MULTIPLIER_TEXT, 312, 146, 150, 16, WINDOW, fonts.p12, "-", WHITE, alignX = 0)
            s.text(AFTER_GRID + 6, 215, 164, 95, 16, WINDOW, fonts.p12, "Next", GREY, alignX = 0)
            s.text(NEXT_TEXT, 312, 164, 150, 16, WINDOW, fonts.p12, "-", WHITE, alignX = 0)
            s.text(AFTER_GRID + 8, 215, 182, 95, 16, WINDOW, fonts.p12, "Cash out", GREY, alignX = 0)
            s.text(VALUE_TEXT, 312, 182, 150, 16, WINDOW, fonts.p12, "-", YELLOW, alignX = 0)
            s.text(AFTER_GRID + 11, 215, 200, 95, 16, WINDOW, fonts.p12, "Coins", GREY, alignX = 0)
            s.text(COINS_TEXT, 312, 200, 150, 16, WINDOW, fonts.p12, "0", WHITE, alignX = 0)
            s.text(STATUS_TEXT, 212, 218, 252, 28, WINDOW, fonts.p12, "Pick your mines and start.", GREY, lineHeight = 14)

            s.fairnessStrip(FAIRNESS_BASE, 252)
            return s
        }
    }

    // ================================================================= Blackjack

    /**
     * Blackjack.
     *
     * Two layout facts drive this screen. First, a card is three components, not two: the pale plate, a black rank
     * and a red rank, stacked, with the server showing whichever matches the suit. The client cannot recolour a
     * text after the fact, so a single rank component can only ever be one colour - and the first draft's fixed
     * black meant hearts and diamonds were indistinguishable from spades and clubs, which is the one thing a card
     * has to get right.
     *
     * Second, the cards sit on the left and the controls in a column on the right. Laid out top to bottom instead,
     * four split rows of cards (y 108 plus 46 a row) reach y 292, straight through the button row at y 200 and the
     * fairness strip at y 246. Side by side, four rows of 30px cards end at y 242 and the buttons never collide
     * with them.
     */
    object Blackjack {
        /** Plate, black rank, red rank. */
        const val CARD_STRIDE = 3
        const val DEALER_CARDS = 8
        const val PLAYER_ROWS = 4
        const val PLAYER_CARDS = 8

        const val DEALER_TOTAL = CONTENT_BASE + 1
        const val BET_TEXT = CONTENT_BASE + 3
        const val COINS_TEXT = CONTENT_BASE + 5

        const val DEALER_FIRST = CONTENT_BASE + 6
        const val PLAYER_FIRST = DEALER_FIRST + DEALER_CARDS * CARD_STRIDE
        const val AFTER_CARDS = PLAYER_FIRST + PLAYER_ROWS * PLAYER_CARDS * CARD_STRIDE

        const val ROW_TOTAL_FIRST = AFTER_CARDS
        const val DEAL_BUTTON = AFTER_CARDS + PLAYER_ROWS
        const val HIT_BUTTON = DEAL_BUTTON + 2
        const val STAND_BUTTON = DEAL_BUTTON + 4
        const val DOUBLE_BUTTON = DEAL_BUTTON + 6
        const val SPLIT_BUTTON = DEAL_BUTTON + 8
        const val INSURE_BUTTON = DEAL_BUTTON + 10
        const val DECLINE_BUTTON = DEAL_BUTTON + 12
        const val BET_CUSTOM = DEAL_BUTTON + 14
        const val STATUS_TEXT = DEAL_BUTTON + 16
        const val FAIRNESS_BASE = DEAL_BUTTON + 17
        const val SEED_HASH_TEXT = FAIRNESS_BASE + 4
        const val CLIENT_SEED_TEXT = FAIRNESS_BASE + 6
        const val NONCE_TEXT = FAIRNESS_BASE + 8
        const val SET_SEED_BUTTON = FAIRNESS_BASE + 9
        const val VERIFY_BUTTON = FAIRNESS_BASE + 11

        const val CARD_X = 20
        const val CARD_PITCH = 28
        const val CARD_W = 26
        const val DEALER_Y = 54
        const val PLAYER_Y = 110
        const val ROW_PITCH = 34

        /** The pale card plate of dealer card [index]. */
        fun dealerPlate(index: Int) = DEALER_FIRST + index * CARD_STRIDE

        /** The black rank of dealer card [index] - shown for spades and clubs. */
        fun dealerText(index: Int) = DEALER_FIRST + index * CARD_STRIDE + 1

        /** The red rank of dealer card [index] - shown for hearts and diamonds. */
        fun dealerRedText(index: Int) = DEALER_FIRST + index * CARD_STRIDE + 2

        fun playerPlate(
            row: Int,
            index: Int,
        ) = PLAYER_FIRST + (row * PLAYER_CARDS + index) * CARD_STRIDE

        fun playerText(
            row: Int,
            index: Int,
        ) = PLAYER_FIRST + (row * PLAYER_CARDS + index) * CARD_STRIDE + 1

        fun playerRedText(
            row: Int,
            index: Int,
        ) = PLAYER_FIRST + (row * PLAYER_CARDS + index) * CARD_STRIDE + 2

        fun rowTotal(row: Int) = ROW_TOTAL_FIRST + row

        fun screen(fonts: LootKeyInterfaceImportTool.Fonts): Screen {
            val s = Screen(BLACKJACK_ID, "Blackjack", fonts)
            s.frame()

            s.text(CONTENT_BASE, 20, 40, 60, 14, WINDOW, fonts.p11, "Dealer", GREY, alignX = 0)
            s.text(DEALER_TOTAL, 82, 40, 90, 14, WINDOW, fonts.p11, "", WHITE, alignX = 0)
            s.text(CONTENT_BASE + 2, 200, 40, 40, 14, WINDOW, fonts.p11, "Bet", GREY, alignX = 0)
            s.text(BET_TEXT, 240, 40, 100, 14, WINDOW, fonts.p11, "1,000", WHITE, alignX = 0)
            s.text(CONTENT_BASE + 4, 344, 40, 46, 14, WINDOW, fonts.p11, "Coins", GREY, alignX = 0)
            s.text(COINS_TEXT, 392, 40, 74, 14, WINDOW, fonts.p11, "0", WHITE, alignX = 0)

            for (i in 0 until DEALER_CARDS) {
                val x = CARD_X + i * CARD_PITCH
                s.rect(dealerPlate(i), x, DEALER_Y, CARD_W, 32, WINDOW, 0xD8D8D0, hidden = true)
                s.text(dealerText(i), x, DEALER_Y + 8, CARD_W, 16, WINDOW, fonts.b12, "", CARD_BLACK, hidden = true)
                s.text(dealerRedText(i), x, DEALER_Y + 8, CARD_W, 16, WINDOW, fonts.b12, "", CARD_RED, hidden = true)
            }

            s.text(STATUS_TEXT, 20, 90, 446, 16, WINDOW, fonts.p12, "Place your bet and deal.", GREY)

            for (row in 0 until PLAYER_ROWS) {
                val y = PLAYER_Y + row * ROW_PITCH
                s.text(rowTotal(row), 250, y + 8, 40, 14, WINDOW, fonts.p11, "", WHITE, alignX = 0, hidden = true)
                for (i in 0 until PLAYER_CARDS) {
                    val x = CARD_X + i * CARD_PITCH
                    s.rect(playerPlate(row, i), x, y, CARD_W, 30, WINDOW, 0xD8D8D0, hidden = true)
                    s.text(playerText(row, i), x, y + 7, CARD_W, 16, WINDOW, fonts.b12, "", CARD_BLACK, hidden = true)
                    s.text(playerRedText(row, i), x, y + 7, CARD_W, 16, WINDOW, fonts.b12, "", CARD_RED, hidden = true)
                }
            }

            // Controls in their own column, clear of the four possible split rows.
            s.button(DEAL_BUTTON, 296, 110, 80, 26, WINDOW, "Deal", "Deal", captionId = DEAL_BUTTON + 1)
            s.button(HIT_BUTTON, 384, 110, 80, 26, WINDOW, "Hit", "Hit", captionId = HIT_BUTTON + 1, hidden = true)
            s.button(STAND_BUTTON, 296, 144, 80, 26, WINDOW, "Stand", "Stand", captionId = STAND_BUTTON + 1, hidden = true)
            s.button(DOUBLE_BUTTON, 384, 144, 80, 26, WINDOW, "Double", "Double", captionId = DOUBLE_BUTTON + 1, hidden = true)
            s.button(SPLIT_BUTTON, 296, 178, 80, 26, WINDOW, "Split", "Split", captionId = SPLIT_BUTTON + 1, hidden = true)
            s.button(BET_CUSTOM, 384, 178, 80, 26, WINDOW, "Set bet", "Set bet", captionId = BET_CUSTOM + 1)
            s.button(INSURE_BUTTON, 296, 212, 80, 26, WINDOW, "Insure", "Insurance", captionId = INSURE_BUTTON + 1, hidden = true)
            s.button(DECLINE_BUTTON, 384, 212, 80, 26, WINDOW, "Decline", "Decline", captionId = DECLINE_BUTTON + 1, hidden = true)

            s.fairnessStrip(FAIRNESS_BASE, 252)
            return s
        }
    }

    // ================================================================= Flower Poker

    object Flower {
        const val HAND_SIZE = 5

        const val CHALLENGER_NAME = CONTENT_BASE + 1
        const val OPPONENT_NAME = CONTENT_BASE + 3
        const val CHALLENGER_FIRST = CONTENT_BASE + 4
        const val OPPONENT_FIRST = CHALLENGER_FIRST + HAND_SIZE
        const val AFTER_FLOWERS = OPPONENT_FIRST + HAND_SIZE

        const val CHALLENGER_HAND = AFTER_FLOWERS
        const val OPPONENT_HAND = AFTER_FLOWERS + 1
        const val POT_TEXT = AFTER_FLOWERS + 3
        const val STAKE_TEXT = AFTER_FLOWERS + 5
        const val STAKE_BUTTON = AFTER_FLOWERS + 6
        const val ACCEPT_BUTTON = AFTER_FLOWERS + 8
        const val DECLINE_BUTTON = AFTER_FLOWERS + 10
        const val CHALLENGER_TICK = AFTER_FLOWERS + 12
        const val OPPONENT_TICK = AFTER_FLOWERS + 13
        const val STATUS_TEXT = AFTER_FLOWERS + 14
        const val ROUND_TEXT = AFTER_FLOWERS + 15
        const val FAIRNESS_BASE = AFTER_FLOWERS + 16
        const val SEED_HASH_TEXT = FAIRNESS_BASE + 4
        const val CLIENT_SEED_TEXT = FAIRNESS_BASE + 6
        const val NONCE_TEXT = FAIRNESS_BASE + 8
        const val SET_SEED_BUTTON = FAIRNESS_BASE + 9
        const val VERIFY_BUTTON = FAIRNESS_BASE + 11

        fun challengerFlower(index: Int) = CHALLENGER_FIRST + index

        fun opponentFlower(index: Int) = OPPONENT_FIRST + index

        fun screen(fonts: LootKeyInterfaceImportTool.Fonts): Screen {
            val s = Screen(FLOWER_ID, "Flower Poker", fonts)
            s.frame()

            s.text(CONTENT_BASE, 20, 44, 200, 16, WINDOW, fonts.p12, "You", GREY, alignX = 0)
            s.text(CHALLENGER_NAME, 20, 44, 200, 16, WINDOW, fonts.p12, "", WHITE, alignX = 0)
            s.text(CONTENT_BASE + 2, 20, 120, 200, 16, WINDOW, fonts.p12, "Opponent", GREY, alignX = 0)
            s.text(OPPONENT_NAME, 20, 120, 200, 16, WINDOW, fonts.p12, "", WHITE, alignX = 0)

            for (i in 0 until HAND_SIZE) {
                s.item(challengerFlower(i), 24 + i * 40, 62, WINDOW, size = 32)
                s.item(opponentFlower(i), 24 + i * 40, 138, WINDOW, size = 32)
            }

            s.text(CHALLENGER_HAND, 240, 66, 220, 18, WINDOW, fonts.b12, "", YELLOW, alignX = 0)
            s.text(OPPONENT_HAND, 240, 142, 220, 18, WINDOW, fonts.b12, "", YELLOW, alignX = 0)

            s.text(AFTER_FLOWERS + 2, 20, 178, 60, 16, WINDOW, fonts.p12, "Pot", GREY, alignX = 0)
            s.text(POT_TEXT, 70, 178, 160, 16, WINDOW, fonts.b12, "0", YELLOW, alignX = 0)
            s.text(AFTER_FLOWERS + 4, 240, 178, 70, 16, WINDOW, fonts.p12, "Stake", GREY, alignX = 0)
            s.text(STAKE_TEXT, 300, 178, 160, 16, WINDOW, fonts.p12, "0", WHITE, alignX = 0)

            s.button(STAKE_BUTTON, 20, 200, 90, 28, WINDOW, "Set stake", "Set stake", captionId = AFTER_FLOWERS + 7)
            s.button(ACCEPT_BUTTON, 116, 200, 90, 28, WINDOW, "Accept", "Accept", captionId = AFTER_FLOWERS + 9)
            s.button(DECLINE_BUTTON, 212, 200, 90, 28, WINDOW, "Decline", "Decline", captionId = AFTER_FLOWERS + 11)
            s.text(CHALLENGER_TICK, 310, 202, 150, 14, WINDOW, fonts.p11, "", GREEN, alignX = 0)
            s.text(OPPONENT_TICK, 310, 216, 150, 14, WINDOW, fonts.p11, "", GREEN, alignX = 0)

            s.text(STATUS_TEXT, 20, 226, 320, 16, WINDOW, fonts.p12, "Agree a stake and both accept.", GREY, alignX = 0)
            s.text(ROUND_TEXT, 340, 226, 120, 16, WINDOW, fonts.p11, "", GREY, alignX = 0)

            s.fairnessStrip(FAIRNESS_BASE, 252)
            return s
        }
    }

    // =================================================================

    fun screens(fonts: LootKeyInterfaceImportTool.Fonts): List<Screen> =
        listOf(Dice.screen(fonts), Mines.screen(fonts), Blackjack.screen(fonts), Flower.screen(fonts))

    /**
     * Read-only: prints the highest interface group already in the target cache and the first free run of four.
     *
     * The loot-key tool's "first id above the cache's last interface (1148)" was true when it was written; the
     * cache has grown since, and a stale assumption there is what the preflight caught on 2026-09-20 (1150 and
     * 1151 were already taken). Probing beats assuming.
     */
    private fun probe() {
        val library = com.displee.cache.CacheLibrary(TARGETS[0])
        try {
            val index = library.index(INDEX_INTERFACES)
            val ids = index.archiveIds().toSortedSet()
            println("INTERFACE_INDEX archives=${ids.size} highest=${ids.lastOrNull()}")
            var candidate = (ids.lastOrNull() ?: 0) + 1
            while ((candidate until candidate + 4).any { it in ids }) {
                candidate++
            }
            println("FIRST_FREE_RUN_OF_FOUR=$candidate")
            println("TAKEN_NEAR_1149=${(1145..1175).filter { it in ids }}")
        } finally {
            library.close()
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        if ("--probe" in args) {
            probe()
            return
        }
        val apply = "--apply" in args

        /*
         * `--replace-from-journal=<tx id>`: re-import over a run this tool already applied.
         *
         * Without it the second apply is refused, and rightly so - the interface files already exist, so every
         * mutation preflights as a CONFLICT rather than a CREATE, which is the guard that stops one tool silently
         * overwriting another's work. But these four screens were authored without ever being seen in a client,
         * so the first look at them is certain to want a component moved. Pinning the sha1s this tool itself
         * recorded in its journal says "replace exactly what I wrote last time": a layout revision goes through,
         * while anything else that has touched those files still blocks.
         */
        val previous =
            args.firstOrNull { it.startsWith("--replace-from-journal=") }
                ?.substringAfter('=')
                ?.let(LootKeyInterfaceImportTool::journalIntendedSha1s)
                ?: emptyMap()

        val fonts =
            com.displee.cache.CacheLibrary(TARGETS[0]).let { library ->
                try {
                    LootKeyInterfaceImportTool.fonts(library)
                } finally {
                    library.close()
                }
            }
        println("FONTS p11=${fonts.p11} p12=${fonts.p12} b12=${fonts.b12} q8=${fonts.q8}")

        val mutations = mutableListOf<CacheMutation>()
        screens(fonts).forEach { screen ->
            val components = screen.build()
            println("SCREEN ${screen.id} '${screen.title}' components=${components.size}")
            components.forEach { c ->
                mutations +=
                    CacheMutation(
                        INDEX_INTERFACES,
                        screen.id,
                        c.id,
                        LootKeyInterfaceImportTool.encode(c),
                        "interface ${screen.id}:${c.id} type=${c.type}",
                        expectedCurrentSha1 = previous["idx${INDEX_INTERFACES}_grp${screen.id}_file${c.id}"],
                    )
            }
        }

        val transaction = CacheTransaction(targets = TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} mutations=${mutations.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY RUN: pass --apply to write interfaces $DICE_ID, $MINES_ID, $BLACKJACK_ID and $FLOWER_ID")
            return
        }
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        println("APPLIED ${applied.applied} skipped=${applied.skipped} journal=${applied.journalDir}")
        if (problems.isEmpty()) {
            println("VERIFY_OK transaction=${transaction.id}")
        } else {
            problems.forEach { println("  VERIFY_PROBLEM: $it") }
            error("verify failed")
        }
        File("C:/RSPS/import-journal/${transaction.id}/casino-interfaces.txt")
            .writeText(screens(fonts).joinToString("\n") { "${it.id}\t${it.title}\t${it.build().size} components" })
    }
}
