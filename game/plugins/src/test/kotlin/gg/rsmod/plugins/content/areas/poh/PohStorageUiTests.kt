package gg.rsmod.plugins.content.areas.poh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The costume room on interface 467 (owner 2026-10-02: house options are interfaces, not chat menus): the generated set table matches
 * the containers, and the 30 rows page with "More..." / "Back..." rows like the 2009scape StorageInterface.
 */
class PohStorageUiTests {
    @Test
    fun `every set's items are accepted by its own furniture and no item is in two sets`() {
        PohStorageSets.ALL.forEach { set ->
            set.ids.forEach { id -> assertTrue(PohStorage.accepts(set.family, id), "${set.name}: $id not accepted by ${set.family}") }
        }
        val all = PohStorageSets.ALL.flatMap { it.ids.toList() }
        assertEquals(all.size, all.toSet().size, "an item is in two sets")
        PohStorage.Family.values().forEach { assertTrue(PohStorageSets.of(it).isNotEmpty(), "$it has no sets") }
    }

    @Test
    fun `a family that fits shows every set, a larger one pages with More and Back`() {
        val small = PohStorageSets.of(PohStorage.Family.FANCY_DRESS)
        assertTrue(small.size <= PohStorageUi.ROWS)
        assertEquals(small.size, PohStorageUi.rows(small, 0).size)

        val big = PohStorageSets.of(PohStorage.Family.TREASURE_CHEST)
        assertTrue(big.size > PohStorageUi.ROWS)
        val first = PohStorageUi.rows(big, 0)
        assertEquals(PohStorageUi.ROWS - 1, first.size)
        assertTrue(first.last() is PohStorageUi.Row.More)
        val seen = mutableListOf<PohStorageSets.StorageSet>()
        var page = 0
        while (true) {
            val rows = PohStorageUi.rows(big, page)
            assertTrue(rows.size <= PohStorageUi.ROWS)
            if (page > 0) assertTrue(rows.last() is PohStorageUi.Row.Back)
            seen += rows.filterIsInstance<PohStorageUi.Row.SetRow>().map { it.set }
            if (rows.none { it is PohStorageUi.Row.More }) break
            page++
        }
        assertEquals(big, seen, "every set exactly once over the pages")
    }

    @Test
    fun `row components follow interface 467's layout`() {
        assertEquals(15, PohStorageUi.nameComponent(0))
        assertEquals(16, PohStorageUi.buttonComponent(0))
        assertEquals(74, PohStorageUi.buttonComponent(29))
        assertEquals(29, PohStorageUi.rowOf(74))
        assertEquals(-1, PohStorageUi.rowOf(75))
    }
}
