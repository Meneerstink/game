package gg.rsmod.plugins.content.mechanics.doors

import gg.rsmod.game.fs.def.ObjectDef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The definitions used here are the real shapes from the production cache, read with
 * `./gradlew :game:runObjectDefProbeTool --args="C:/RSPS/game/game/data/cache <ids>"`. They are
 * transcribed rather than loaded so the rule can be tested without a cache, but every id and option
 * list below is the one the game actually ships.
 */
class DoorPairingTests {
    private fun def(
        id: Int,
        name: String,
        vararg options: Pair<Int, String>,
    ): ObjectDef =
        ObjectDef(id).apply {
            this.name = name
            options.forEach { (slot, option) -> this.options[slot] = option }
        }

    private fun derive(
        defs: List<ObjectDef>,
        excluded: Set<Int> = emptySet(),
    ): List<DerivedDoor> {
        val byId = defs.associateBy { it.id }
        return DoorPairing.derive(ids = byId.keys.sorted(), lookup = { byId[it] }, excluded = excluded)
    }

    @Test
    fun `an open half above the closed half is paired`() {
        val doors =
            derive(
                listOf(
                    def(10527, "Door", 0 to "Open"),
                    def(10528, "Door", 0 to "Close"),
                ),
            )
        assertEquals(listOf(DerivedDoor(closed = 10527, opened = 10528, optionSlot = 0)), doors)
    }

    @Test
    fun `an open half below the closed half is paired`() {
        // single-doors.json ships 15536 -> 15535, so the rule cannot prefer a direction.
        val doors =
            derive(
                listOf(
                    def(15535, "Door", 0 to "Close"),
                    def(15536, "Door", 0 to "Open"),
                ),
            )
        assertEquals(listOf(DerivedDoor(closed = 15536, opened = 15535, optionSlot = 0)), doors)
    }

    @Test
    fun `a closed half flanked by two open halves is refused`() {
        /*
         * Objects 24930-24933, verbatim. Both neighbours of 24931 qualify, and single-doors.json
         * resolves it as 24931 -> 24930; nothing in the definitions says so, so guessing here would
         * swing the wrong leaf.
         */
        val doors =
            derive(
                listOf(
                    def(24930, "Door", 0 to "Close"),
                    def(24931, "Door", 0 to "Open"),
                    def(24932, "Door", 0 to "Close"),
                    def(24933, "Door", 0 to "Open"),
                    def(24934, "Door"),
                ),
            )
        assertTrue("expected no pair for the flanked door, got $doors", doors.none { it.closed == 24931 })
    }

    @Test
    fun `two closed halves contesting one open half are both refused`() {
        // The Gate triple 1551/1552/1553, verbatim: 1551 and 1553 both resolve to 1552.
        val doors =
            derive(
                listOf(
                    def(1551, "Gate", 0 to "Open"),
                    def(1552, "Gate", 0 to "Close"),
                    def(1553, "Gate", 0 to "Open"),
                    def(1554, ""),
                ),
            )
        assertEquals(emptyList<DerivedDoor>(), doors)
    }

    @Test
    fun `no two pairs ever bind the same id and option slot`() {
        /*
         * bindObject throws on a duplicate (id, option) - a collision here would stop the server
         * booting rather than misbehave quietly.
         */
        val doors =
            derive(
                listOf(
                    def(1530, "Door", 0 to "Open"),
                    def(1531, "Door", 0 to "Close"),
                    def(1533, "Door", 0 to "Open"),
                    def(1534, "Door", 0 to "Close"),
                    def(15535, "Door", 0 to "Close"),
                    def(15536, "Door", 0 to "Open"),
                ),
            )
        val slots = doors.flatMap { listOf(it.closed to it.optionSlot, it.opened to it.optionSlot) }
        assertEquals(slots.size, slots.toSet().size)
    }

    @Test
    fun `a differently named neighbour is not the other half`() {
        // 2406 'Door' sits next to 2407 'Magic door', which is a different object entirely.
        val doors =
            derive(
                listOf(
                    def(2406, "Door", 0 to "Open"),
                    def(2407, "Magic door", 0 to "Close"),
                ),
            )
        assertEquals(emptyList<DerivedDoor>(), doors)
    }

    @Test
    fun `the close option must sit in the same slot as the open option`() {
        val doors =
            derive(
                listOf(
                    def(500, "Door", 1 to "Open"),
                    def(501, "Door", 0 to "Close"),
                ),
            )
        assertEquals(emptyList<DerivedDoor>(), doors)
    }

    @Test
    fun `excluded ids take part in no pair in either role`() {
        /*
         * Double doors and gates are excluded wholesale. Treating one leaf as a single door leaves
         * the other shut and the tile half-blocked, so the exclusion has to cover the open half
         * too, not just the closed id being iterated.
         */
        val defs =
            listOf(
                def(31824, "Door", 0 to "Close"),
                def(31825, "Door", 0 to "Open"),
                def(31826, "Door", 0 to "Close"),
                def(31827, "Door", 0 to "Open"),
            )
        assertEquals(emptyList<DerivedDoor>(), derive(defs, excluded = setOf(31824, 31825, 31826, 31827)))
        assertEquals(emptyList<DerivedDoor>(), derive(defs, excluded = setOf(31824, 31826)))
    }

    @Test
    fun `a nameless definition is never a door`() {
        val doors =
            derive(
                listOf(
                    def(700, "", 0 to "Open"),
                    def(701, "", 0 to "Close"),
                ),
            )
        assertEquals(emptyList<DerivedDoor>(), doors)
    }
}
