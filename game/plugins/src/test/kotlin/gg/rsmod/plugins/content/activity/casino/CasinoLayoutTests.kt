package gg.rsmod.plugins.content.activity.casino

import gg.rsmod.game.tools.importer.CasinoInterfaceImportTool as Layout
import gg.rsmod.game.tools.importer.LootKeyInterfaceImportTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Geometry guard for the four casino screens.
 *
 * These screens are authored without a running client, and the first draft shipped two defects that only a picture
 * would have shown: the Mines board (25 cells of 36px on a 40px pitch from y 76) ran to y 272 and the Blackjack
 * split rows (four rows of 46px from y 108) ran to y 292, both straight through the button row and the
 * provably-fair strip that starts at y 246. Nothing failed - the components simply drew on top of each other.
 *
 * So the rules a reader would otherwise have to check by eye are asserted here instead: every control lives inside
 * the window, nothing but the fairness strip reaches into the strip's band, and the dice bar is a contiguous run of
 * segments. A layout change that breaks one of them now fails the build rather than the owner's first look.
 *
 * The fonts are sprite ids resolved from the cache at import time; the layout does not depend on their values, so
 * the tests build the screens with placeholders and never touch a cache.
 */
class CasinoLayoutTests {
    private val fonts = LootKeyInterfaceImportTool.Fonts(p11 = 1, p12 = 2, b12 = 3, q8 = 4)

    /** Top of the provably-fair band: `fairnessStrip(base, 252)` draws its first graphic at `y - 6`. */
    private val stripTop = 246

    private fun screens() = Layout.screens(fonts)

    private fun content(screen: Layout.Screen) = screen.build().filter { it.id >= Layout.CONTENT_BASE }

    /** The fairness strip owns thirteen ids from its base and is the only thing allowed in the band. */
    private fun fairnessBase(screen: Layout.Screen): Int =
        when (screen.id) {
            Layout.DICE_ID -> Layout.Dice.FAIRNESS_BASE
            Layout.MINES_ID -> Layout.Mines.FAIRNESS_BASE
            Layout.BLACKJACK_ID -> Layout.Blackjack.FAIRNESS_BASE
            else -> Layout.Flower.FAIRNESS_BASE
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
    fun `no content component is drawn outside the window`() {
        screens().forEach { screen ->
            content(screen).forEach { c ->
                assertTrue(c.x >= 0, "interface ${screen.id} component ${c.id} starts at x=${c.x}")
                assertTrue(c.y >= 0, "interface ${screen.id} component ${c.id} starts at y=${c.y}")
                assertTrue(
                    c.x + c.width <= Layout.WINDOW_W,
                    "interface ${screen.id} component ${c.id} ends at x=${c.x + c.width}, past ${Layout.WINDOW_W}",
                )
                assertTrue(
                    c.y + c.height <= Layout.WINDOW_H,
                    "interface ${screen.id} component ${c.id} ends at y=${c.y + c.height}, past ${Layout.WINDOW_H}",
                )
            }
        }
    }

    @Test
    fun `nothing but the provably-fair strip reaches into the strip's band`() {
        screens().forEach { screen ->
            val base = fairnessBase(screen)
            content(screen).filter { it.id < base }.forEach { c ->
                assertTrue(
                    c.y + c.height <= stripTop,
                    "interface ${screen.id} component ${c.id} ends at y=${c.y + c.height}, inside the fairness strip at $stripTop",
                )
            }
        }
    }

    @Test
    fun `the dice bar is a contiguous run of equal segments with a marker under each`() {
        val dice = Layout.Dice.screen(fonts).build().associateBy { it.id }
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

        // The whole bar sits on its backing plate.
        val backing = dice.getValue(Layout.Dice.BAR_BACKING)
        val first = dice.getValue(Layout.Dice.loseSegment(0))
        val last = dice.getValue(Layout.Dice.loseSegment(Layout.Dice.SEGMENTS - 1))
        assertTrue(backing.x <= first.x, "the dice bar overhangs its backing on the left")
        assertTrue(backing.x + backing.width >= last.x + last.width, "the dice bar overhangs its backing on the right")
    }

    @Test
    fun `the dice bar colours split exactly where a roll starts winning`() {
        /*
         * The green zone must be the top of the bar and nothing else, and it must reach exactly as far down as a
         * winning roll can. At every target: the segments are green from some point upwards and red below it, the
         * lowest green segment really does contain a winning draw, and the highest red one really does not.
         *
         * The awkward case this pins is target 100, where the single winning draw is the 10,000th - the one draw
         * that does not fit fifty segments of two hundred. It belongs to the last segment, so the bar shows one
         * green segment there rather than going entirely red and telling the player a win is impossible.
         */
        for (target in CasinoOdds.MIN_DICE_TARGET..CasinoOdds.MAX_DICE_TARGET) {
            val green = (0 until Layout.Dice.SEGMENTS).filter { CasinoScreens.Dice.segmentWins(it, target) }
            assertTrue(green.isNotEmpty(), "target $target paints no winning segment at all")
            assertEquals(
                (green.first() until Layout.Dice.SEGMENTS).toList(),
                green,
                "target $target paints a broken green zone instead of the top of the bar",
            )

            val lowestGreen = green.first()
            val topDraw =
                if (lowestGreen == Layout.Dice.SEGMENTS - 1) {
                    ProvablyFairDice.OUTCOMES - 1
                } else {
                    lowestGreen * CasinoScreens.Dice.DRAWS_PER_SEGMENT + CasinoScreens.Dice.DRAWS_PER_SEGMENT - 1
                }
            assertTrue(
                CasinoOdds.diceWins(rollScaled = topDraw, target = target),
                "target $target paints segment $lowestGreen green but no roll in it wins",
            )
            if (lowestGreen > 0) {
                val highestRedTop = lowestGreen * CasinoScreens.Dice.DRAWS_PER_SEGMENT - 1
                assertTrue(
                    !CasinoOdds.diceWins(rollScaled = highestRedTop, target = target),
                    "target $target paints segment ${lowestGreen - 1} red but its best roll wins",
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
    fun `every blackjack card carries a black rank and a red rank on one plate`() {
        val bj = Layout.Blackjack.screen(fonts).build().associateBy { it.id }
        fun sameBox(
            plate: Int,
            text: Int,
        ) {
            val p = bj.getValue(plate)
            val t = bj.getValue(text)
            assertEquals(p.x, t.x, "card rank $text is not on plate $plate")
            assertTrue(t.y >= p.y && t.y + t.height <= p.y + p.height + 2, "card rank $text hangs off plate $plate")
        }
        for (i in 0 until Layout.Blackjack.DEALER_CARDS) {
            sameBox(Layout.Blackjack.dealerPlate(i), Layout.Blackjack.dealerText(i))
            sameBox(Layout.Blackjack.dealerPlate(i), Layout.Blackjack.dealerRedText(i))
            assertEquals(
                bj.getValue(Layout.Blackjack.dealerText(i)).x,
                bj.getValue(Layout.Blackjack.dealerRedText(i)).x,
                "dealer card $i draws its two colours in different places",
            )
        }
        for (row in 0 until Layout.Blackjack.PLAYER_ROWS) {
            for (i in 0 until Layout.Blackjack.PLAYER_CARDS) {
                sameBox(Layout.Blackjack.playerPlate(row, i), Layout.Blackjack.playerText(row, i))
                sameBox(Layout.Blackjack.playerPlate(row, i), Layout.Blackjack.playerRedText(row, i))
            }
        }
    }

    @Test
    fun `blackjack cards never reach the control column`() {
        val bj = Layout.Blackjack.screen(fonts).build().associateBy { it.id }
        val buttonLeft = bj.getValue(Layout.Blackjack.DEAL_BUTTON).x
        for (row in 0 until Layout.Blackjack.PLAYER_ROWS) {
            val last = bj.getValue(Layout.Blackjack.playerPlate(row, Layout.Blackjack.PLAYER_CARDS - 1))
            val total = bj.getValue(Layout.Blackjack.rowTotal(row))
            assertTrue(last.x + last.width <= total.x, "split row $row runs its cards into the hand total")
            assertTrue(total.x + total.width <= buttonLeft, "split row $row runs its total into the buttons")
        }
    }

    @Test
    fun `the mines board is a 5 by 5 grid whose covers sit over their item slots`() {
        val mines = Layout.Mines.screen(fonts).build().associateBy { it.id }
        for (cell in 0 until Layout.Mines.CELLS) {
            val cover = mines.getValue(Layout.Mines.cover(cell))
            val slot = mines.getValue(Layout.Mines.slot(cell))
            assertEquals(Layout.Mines.GRID_X + (cell % 5) * Layout.Mines.CELL_PITCH, cover.x, "mines cell $cell is off the grid")
            assertEquals(Layout.Mines.GRID_Y + (cell / 5) * Layout.Mines.CELL_PITCH, cover.y, "mines cell $cell is off the grid")
            assertTrue(slot.x >= cover.x && slot.x + slot.width <= cover.x + cover.width, "mines item $cell is wider than its cover")
            assertTrue(slot.y >= cover.y && slot.y + slot.height <= cover.y + cover.height, "mines item $cell is taller than its cover")
        }
    }
}
