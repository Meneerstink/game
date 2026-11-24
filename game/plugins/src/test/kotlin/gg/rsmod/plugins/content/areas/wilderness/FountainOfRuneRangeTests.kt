package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.model.Tile
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** FONTEIN-RUNES (24-11): the free-rune range is the OSRS Wiki infobox polygon, ground plane only. */
class FountainOfRuneRangeTests {
    @Test fun `the fountain and its yard are in range, the outside and other planes are not`() {
        assertTrue(FountainOfRune.inRange(Tile(3373, 3892, 0)) || FountainOfRune.inRange(Tile(3372, 3891, 0)))
        assertTrue(FountainOfRune.inRange(Tile(3370, 3900, 0)))
        assertTrue(FountainOfRune.inRange(Tile(3385, 3890, 0)))
        assertFalse(FountainOfRune.inRange(Tile(3360, 3892, 0)))
        assertFalse(FountainOfRune.inRange(Tile(3366, 3891, 0))) // west of the 3367 wall line below 3893
        assertFalse(FountainOfRune.inRange(Tile(3387, 3900, 0))) // outside the north-east diagonal
        assertFalse(FountainOfRune.inRange(Tile(3370, 3900, 1)))
    }
}
