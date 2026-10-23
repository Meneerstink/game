package gg.rsmod.game.tools.importer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A45: the eight Quest Tutor quests sort first, in the Tutor's order, and a struct round-trips unchanged except its sort name. */
class QuestTutorOrderCacheToolTests {
    @Test
    fun `tutor sort names come before every letter, in the tutor's order`() {
        val names = listOf("Desert Treasure", "Lunar Diplomacy", "Desert Treasure II", "The Temple at Senntisten", "King's Ransom", "Dragon Slayer II", "Song of the Elves", "Monkey Madness II")
        val sorts = names.mapIndexed { i, n -> QuestTutorOrderCacheTool.sortName(i, n).lowercase() }
        assertEquals(sorts, sorts.sorted(), "Tutor order kept")
        assertTrue(sorts.all { it < "a" && it < "0" }, "before every quest name: $sorts")
        assertEquals(8, QuestTutorOrderCacheTool.TUTOR_SLOTS.toSet().size)
    }

    @Test
    fun `a struct round-trips`() {
        val params = linkedMapOf<Int, Any>(847 to 30, 845 to "Desert Treasure", 846 to "Desert Treasure", 848 to 3, 856 to 1)
        val bytes = QuestTutorOrderCacheTool.encodeParams(params)
        assertEquals(params, QuestTutorOrderCacheTool.decodeParams(bytes))
    }
}
