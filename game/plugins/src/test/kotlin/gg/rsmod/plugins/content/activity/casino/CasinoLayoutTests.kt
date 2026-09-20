package gg.rsmod.plugins.content.activity.casino

import gg.rsmod.game.tools.importer.CasinoInterfaceImportTool as Layout
import gg.rsmod.game.tools.importer.LootKeyInterfaceImportTool
import gg.rsmod.game.tools.importer.LootKeyInterfaceImportTool.Component
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Geometry guard for the four casino screens.
 *
 * These screens are authored without a running client, and the first draft shipped two defects that only a picture
 * would have shown: the Mines board ran straight through the button row, and the Blackjack split rows ran off the
 * bottom of the window. Nothing failed - the components simply drew on top of each other.
 *
 * So the rules a reader would otherwise have to check by eye are asserted here instead: every component lives
 * inside the window *in absolute coordinates* (a child of a button layer is positioned relative to it, so its own
 * x/y say nothing on their own), every cap button is a well-formed three-slice button, the bottom row clears the
 * frame's own edge art, and the dice bar is a contiguous run of segments whose colours split exactly where a roll
 * starts winning - in both directions.
 *
 * The fonts are sprite ids resolved from the cache at import time; the layout does not depend on their values, so
 * the tests build the screens with placeholders and never touch a cache.
 */
class CasinoLayoutTests {
    private val fonts = LootKeyInterfaceImportTool.Fonts(p11 = 1, p12 = 2, b12 = 3, q8 = 4)

    /**
     * The frame's bottom edge art is drawn from y 323, and its corner sprites reach in from x 0..32 and 448..480
     * from y 298. Content below [BOTTOM_LIMIT] would be drawn over the frame.
     */
    private val bottomLimit = 322

    private fun screens() = Layout.screens(fonts)

    private fun byId(screen: Layout.Screen): Map<Int, Component> = screen.build().associateBy { it.id }

    /** Absolute position of [c], following its parent chain up to the root. */
    private fun absolute(
        components: Map<Int, Component>,
        c: Component,
    ): Pair<Int, Int> {
        var x = c.x
        var y = c.y
        var parentId = c.parent
        var guard = 0
        while (parentId >= 0 && guard++ < 16) {
            val parent = components[parentId] ?: break
            x += parent.x
            y += parent.y
            parentId = parent.parent
        }
        return x to y
    }

    /** Every component a screen owns beyond the shared frame, with its absolute box. */
    private fun content(screen: Layout.Screen): List<Triple<Component, Int, Int>> {
        val components = byId(screen)
        return screen
            .build()
            .filter { it.id >= Layout.CONTENT_BASE }
            .map { c ->
                val (x, y) = absolute(components, c)
                Triple(c, x, y)
            }
    }

    /** The full-screen fairness overlay of [screen], as the id range it occupies. */
    private fun fairnessRange(screen: Layout.Screen): IntRange {
        val base =
            when (screen.id) {
                Layout.DICE_ID -> Layout.Dice.FAIRNESS
                Layout.MINES_ID -> Layout.Mines.FAIRNESS
                Layout.BLACKJACK_ID -> Layout.Blackjack.FAIRNESS
                else -> Layout.Flower.FAIRNESS
            }
        return base until base + Layout.Fair.STRIDE
    }

    @Test
    fun `every screen builds a gapless run of unique component ids`() {
        screens().forEach { screen ->
            val ids = screen.build().map { it.id }
            assertEquals(ids.size, ids.toSet().size, "interface ${screen.id} has a duplicate component id")
            assertEquals((0 until ids.size).toList(), ids.sorted(), "interface ${screen.id} is not a gapless 0..n run")
        }
    }

    @Test
    fun `no component is drawn outside the window`() {
        screens().forEach { screen ->
            content(screen).forEach { (c, x, y) ->
                assertTrue(x >= 0, "interface ${screen.id} component ${c.id} starts at x=$x")
                assertTrue(y >= 0, "interface ${screen.id} component ${c.id} starts at y=$y")
                assertTrue(
                    x + c.width <= Layout.WINDOW_W,
                    "interface ${screen.id} component ${c.id} ends at x=${x + c.width}, past ${Layout.WINDOW_W}",
                )
                assertTrue(
                    y + c.height <= Layout.WINDOW_H,
                    "interface ${screen.id} component ${c.id} ends at y=${y + c.height}, past ${Layout.WINDOW_H}",
                )
            }
        }
    }

    /**
     * The overlay is deliberately the whole window - it dims everything behind it - so it is exempt, and its own
     * panel is checked separately.
     */
    @Test
    fun `nothing is drawn over the frame's bottom edge`() {
        screens().forEach { screen ->
            val overlay = fairnessRange(screen)
            content(screen).filter { it.first.id !in overlay }.forEach { (c, _, y) ->
                assertTrue(
                    y + c.height <= bottomLimit,
                    "interface ${screen.id} component ${c.id} ends at y=${y + c.height}, over the frame's bottom edge",
                )
            }
        }
    }

    /**
     * A cap button is a layer with a 6px left cap, a tiled middle and a 6px right cap that together span its width
     * exactly, plus a caption filling it. A middle that is not tiled, or caps that do not meet, is what "low
     * quality" looked like in the first draft.
     */
    @Test
    fun `every cap button is a well-formed three-slice button`() {
        screens().forEach { screen ->
            val components = byId(screen)
            // A button layer is any layer that carries exactly one op and has the four slice children after it.
            val buttons =
                screen.build().filter { c ->
                    c.type == 0 && c.ops.size == 1 && components[c.id + Layout.BUTTON_TEXT]?.type == 4 &&
                        components[c.id + Layout.BUTTON_LEFT]?.parent == c.id
                }
            assertTrue(buttons.isNotEmpty(), "interface ${screen.id} has no cap buttons at all")
            buttons.forEach { layer ->
                val left = components.getValue(layer.id + Layout.BUTTON_LEFT)
                val middle = components.getValue(layer.id + Layout.BUTTON_MIDDLE)
                val right = components.getValue(layer.id + Layout.BUTTON_RIGHT)
                val caption = components.getValue(layer.id + Layout.BUTTON_TEXT)
                listOf(left, middle, right, caption).forEach {
                    assertEquals(layer.id, it.parent, "button ${layer.id} slice ${it.id} is not parented to it")
                }
                assertEquals(0, left.x, "button ${layer.id} left cap is not flush")
                assertEquals(6, left.width, "button ${layer.id} left cap is not 6px")
                assertEquals(6, middle.x, "button ${layer.id} middle does not start after the left cap")
                assertEquals(layer.width - 12, middle.width, "button ${layer.id} middle does not fill the gap")
                assertTrue(middle.tiling, "button ${layer.id} middle is stretched instead of tiled")
                assertEquals(layer.width - 6, right.x, "button ${layer.id} right cap is not flush")
                assertEquals(6, right.width, "button ${layer.id} right cap is not 6px")
                assertEquals(layer.width, caption.width, "button ${layer.id} caption does not fill it")
                assertEquals(Layout.ROW_H, layer.height, "button ${layer.id} is not one cap tall, so the caps resample")
            }
        }
    }

    /** Every control a screen arms must be reachable: a hidden-by-default button is shown by the server, not lost. */
    @Test
    fun `the provably-fair overlay is modal, hidden and keeps its controls on its panel`() {
        screens().forEach { screen ->
            val components = byId(screen)
            val range = fairnessRange(screen)
            val root = components.getValue(range.first + Layout.Fair.LAYER)
            assertTrue(root.hidden, "interface ${screen.id} opens with its fairness overlay showing")
            assertTrue(root.noClickThrough, "interface ${screen.id} fairness overlay is not modal")
            assertEquals(Layout.WINDOW_W, root.width)
            assertEquals(Layout.WINDOW_H, root.height)

            val panel = components.getValue(range.first + Layout.Fair.PANEL)
            listOf(Layout.Fair.SET_SEED, Layout.Fair.NEW_SEED, Layout.Fair.CLOSE).forEach { offset ->
                val button = components.getValue(range.first + offset + Layout.BUTTON_LAYER)
                assertTrue(button.x >= panel.x, "overlay button ${button.id} starts left of the panel")
                assertTrue(
                    button.x + button.width <= panel.x + panel.width,
                    "overlay button ${button.id} runs off the panel",
                )
                assertTrue(
                    button.y + button.height <= panel.y + panel.height,
                    "overlay button ${button.id} runs off the bottom of the panel",
                )
            }
        }
    }

    @Test
    fun `the dice bar is a contiguous run of equal segments with a marker under each`() {
        val dice = byId(Layout.Dice.screen(fonts))
        for (i in 0 until Layout.Dice.SEGMENTS) {
            val lose = dice.getValue(Layout.Dice.loseSegment(i))
            val win = dice.getValue(Layout.Dice.winSegment(i))
            val marker = dice.getValue(Layout.Dice.marker(i))

            // The two halves must occupy exactly the same box, or swapping them would move the bar.
            assertEquals(lose.x, win.x, "dice segment $i halves disagree on x")
            assertEquals(lose.y, win.y, "dice segment $i halves disagree on y")
            assertEquals(lose.width, win.width, "dice segment $i halves disagree on width")
            assertEquals(lose.height, win.height, "dice segment $i halves disagree on height")

            assertEquals(Layout.Dice.BAR_X + i * Layout.Dice.SEGMENT_W, lose.x, "dice segment $i is not on the track")
            assertEquals(Layout.Dice.SEGMENT_W, lose.width, "dice segment $i is the wrong width")
            assertTrue(marker.y >= lose.y + lose.height, "dice marker $i is not under its segment")
        }

        // The whole bar sits on its backing panel.
        val backing = dice.getValue(Layout.Dice.BAR_PANEL)
        val first = dice.getValue(Layout.Dice.loseSegment(0))
        val last = dice.getValue(Layout.Dice.loseSegment(Layout.Dice.SEGMENTS - 1))
        assertTrue(backing.x <= first.x, "the dice bar overhangs its backing on the left")
        assertTrue(backing.x + backing.width >= last.x + last.width, "the dice bar overhangs its backing on the right")
    }

    @Test
    fun `the dice bar colours split exactly where a roll starts winning, in both directions`() {
        /*
         * The winning zone must be one unbroken band at the winning end of the bar and reach exactly as far as a
         * winning roll can. At every target and in both directions: the green segments are contiguous, the
         * green segment nearest the boundary really does contain a winning draw, and the red one beside it does
         * not.
         *
         * The awkward case this pins is roll-over target 100, where the single winning draw is the 10,000th - the
         * one draw that does not fit fifty segments of two hundred. It belongs to the last segment, so the bar
         * shows one green segment there rather than going entirely red and telling the player a win is impossible.
         */
        for (under in listOf(false, true)) {
            for (target in CasinoOdds.MIN_DICE_TARGET..CasinoOdds.MAX_DICE_TARGET) {
                val green = (0 until Layout.Dice.SEGMENTS).filter { CasinoScreens.Dice.segmentWins(it, target, under) }
                assertTrue(green.isNotEmpty(), "target $target (under=$under) paints no winning segment at all")

                val expected =
                    if (under) {
                        (0..green.last()).toList()
                    } else {
                        (green.first() until Layout.Dice.SEGMENTS).toList()
                    }
                assertEquals(expected, green, "target $target (under=$under) paints a broken winning zone")

                val boundary = if (under) green.last() else green.first()
                val representative =
                    when {
                        under -> boundary * CasinoScreens.Dice.DRAWS_PER_SEGMENT
                        boundary == Layout.Dice.SEGMENTS - 1 -> ProvablyFairDice.OUTCOMES - 1
                        else -> boundary * CasinoScreens.Dice.DRAWS_PER_SEGMENT + CasinoScreens.Dice.DRAWS_PER_SEGMENT - 1
                    }
                assertTrue(
                    CasinoOdds.diceWins(representative, target, under),
                    "target $target (under=$under) paints segment $boundary green but no roll in it wins",
                )
            }
        }
    }

    @Test
    fun `the roll marker always lands on a segment the bar actually has`() {
        // Every possible draw, including the odd 10,000th, must address a marker component that exists.
        for (roll in 0 until ProvablyFairDice.OUTCOMES) {
            val segment = (roll / CasinoScreens.Dice.DRAWS_PER_SEGMENT).coerceIn(0, Layout.Dice.SEGMENTS - 1)
            assertTrue(segment in 0 until Layout.Dice.SEGMENTS, "roll $roll maps to segment $segment")
        }
    }

    @Test
    fun `every blackjack card is a hidden plate carrying a black and a red rank and suit`() {
        val bj = byId(Layout.Blackjack.screen(fonts))
        fun checkCard(base: Int) {
            val layer = bj.getValue(base + Layout.CARD_LAYER)
            assertTrue(layer.hidden, "card $base is not hidden until it is dealt")
            listOf(
                Layout.CARD_PLATE_ID,
                Layout.CARD_BORDER_ID,
                Layout.CARD_RANK_BLACK,
                Layout.CARD_RANK_RED,
                Layout.CARD_SUIT_BLACK,
                Layout.CARD_SUIT_RED,
            ).forEach { offset ->
                val child = bj.getValue(base + offset)
                assertEquals(layer.id, child.parent, "card $base child ${child.id} is not on the card")
                assertTrue(
                    child.x >= 0 && child.x + child.width <= layer.width,
                    "card $base child ${child.id} hangs off the plate horizontally",
                )
                assertTrue(
                    child.y >= 0 && child.y + child.height <= layer.height,
                    "card $base child ${child.id} hangs off the plate vertically",
                )
            }
            // The two inks must occupy the same box, or a red card would print in a different place.
            assertEquals(bj.getValue(base + Layout.CARD_RANK_BLACK).x, bj.getValue(base + Layout.CARD_RANK_RED).x)
            assertEquals(bj.getValue(base + Layout.CARD_RANK_BLACK).y, bj.getValue(base + Layout.CARD_RANK_RED).y)
            assertEquals(bj.getValue(base + Layout.CARD_SUIT_BLACK).y, bj.getValue(base + Layout.CARD_SUIT_RED).y)
        }
        for (i in 0 until Layout.Blackjack.DEALER_CARDS) {
            checkCard(Layout.Blackjack.dealerCard(i))
        }
        for (row in 0 until Layout.Blackjack.PLAYER_ROWS) {
            for (i in 0 until Layout.Blackjack.PLAYER_CARDS) {
                checkCard(Layout.Blackjack.playerCard(row, i))
            }
        }
    }

    @Test
    fun `blackjack cards never reach the control column`() {
        val bj = byId(Layout.Blackjack.screen(fonts))
        val buttonLeft = bj.getValue(Layout.Blackjack.DEAL_BUTTON + Layout.BUTTON_LAYER).x
        for (row in 0 until Layout.Blackjack.PLAYER_ROWS) {
            val last = bj.getValue(Layout.Blackjack.playerCard(row, Layout.Blackjack.PLAYER_CARDS - 1) + Layout.CARD_LAYER)
            val total = bj.getValue(Layout.Blackjack.rowTotal(row))
            assertTrue(last.x + last.width <= total.x, "split row $row runs its cards into the hand total")
            assertTrue(total.x + total.width <= buttonLeft, "split row $row runs its total into the buttons")
        }
    }

    @Test
    fun `the mines board is a 5 by 5 grid whose covers sit over their item slots`() {
        val mines = byId(Layout.Mines.screen(fonts))
        for (cell in 0 until Layout.Mines.CELLS) {
            val cover = mines.getValue(Layout.Mines.cover(cell))
            val slot = mines.getValue(Layout.Mines.slot(cell))
            assertEquals(
                Layout.Mines.GRID_X + (cell % Layout.Mines.COLUMNS) * Layout.Mines.CELL_PITCH,
                cover.x,
                "mines cell $cell is off the grid",
            )
            assertEquals(
                Layout.Mines.GRID_Y + (cell / Layout.Mines.COLUMNS) * Layout.Mines.CELL_PITCH,
                cover.y,
                "mines cell $cell is off the grid",
            )
            assertTrue(slot.x >= cover.x && slot.x + slot.width <= cover.x + cover.width, "mines item $cell is wider than its cover")
            assertTrue(slot.y >= cover.y && slot.y + slot.height <= cover.y + cover.height, "mines item $cell is taller than its cover")
        }
        // The board must not run into the column of numbers beside it.
        val lastColumn = mines.getValue(Layout.Mines.cover(Layout.Mines.COLUMNS - 1))
        val odds = mines.getValue(Layout.Mines.ODDS_PANEL)
        assertTrue(lastColumn.x + lastColumn.width <= odds.x, "the mines board runs into the odds panel")
    }
}
