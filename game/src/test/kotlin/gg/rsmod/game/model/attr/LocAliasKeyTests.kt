package gg.rsmod.game.model.attr

import gg.rsmod.game.model.Tile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** 16-11 CoX leader 'Reload': the per-player loc alias key must tell tiles, planes and loc ids apart (ids above 65535 included). */
class LocAliasKeyTests {
    @Test
    fun sameTileAndIdGiveTheSameKey() {
        assertEquals(locAliasKey(Tile(6510, 32, 3), 66678), locAliasKey(Tile(6510, 32, 3), 66678))
    }

    @Test
    fun tilePlaneAndIdEachChangeTheKey() {
        val base = locAliasKey(Tile(6510, 32, 3), 66678)
        assertNotEquals(base, locAliasKey(Tile(6511, 32, 3), 66678))
        assertNotEquals(base, locAliasKey(Tile(6510, 33, 3), 66678))
        assertNotEquals(base, locAliasKey(Tile(6510, 32, 2), 66678))
        assertNotEquals(base, locAliasKey(Tile(6510, 32, 3), 66679))
        // a loc id past the 667 u16 range stays distinct from its wrapped value (66678 - 65536 = 1142)
        assertNotEquals(base, locAliasKey(Tile(6510, 32, 3), 1142))
    }
}
