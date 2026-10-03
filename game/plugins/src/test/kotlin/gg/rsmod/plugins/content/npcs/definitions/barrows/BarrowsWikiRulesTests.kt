package gg.rsmod.plugins.content.npcs.definitions.barrows

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Barrows numbers against the OSRS Wiki (2026-10-03, owner E13 "Barrows precies als OSRS"):
 *  - Barrows/Strategies: prayer drain "begins at eight Prayer points, and increases by one for each brother killed, for a maximum
 *    of fourteen"; "No new monster spawns if a room already contains eleven enemies".
 *  - Chest (Barrows): reward potential = min(combat levels killed, 1000) + 2 per brother (max 1012); one roll plus one per brother
 *    killed, at most seven.
 */
class BarrowsWikiRulesTests {
    @Test
    fun `prayer drain is 8 plus one per brother killed, at most 14`() {
        assertEquals(8, Barrows.drainAmount(0))
        assertEquals(11, Barrows.drainAmount(3))
        assertEquals(14, Barrows.drainAmount(6))
        assertEquals(14, Barrows.drainAmount(9))
    }

    @Test
    fun `reward potential caps the levels at 1000 and adds 2 per brother`() {
        assertEquals(520 + 8, Barrows.rewardPotential(520, 4))
        assertEquals(1012, Barrows.rewardPotential(5000, 6))
        assertEquals(0, Barrows.rewardPotential(0, 0))
    }

    @Test
    fun `one roll plus one per brother, at most seven`() {
        assertEquals(1, Barrows.rollCount(0))
        assertEquals(5, Barrows.rollCount(4))
        assertEquals(7, Barrows.rollCount(6))
    }

    @Test
    fun `a room holds at most eleven enemies`() {
        assertEquals(11, Barrows.MAX_ROOM_ENEMIES)
    }
}
