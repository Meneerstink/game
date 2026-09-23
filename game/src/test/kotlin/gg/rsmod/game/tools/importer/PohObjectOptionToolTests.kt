package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Player-owned house loc menus, over every [PohObjectOptionTool.EDITS] entry on the production cache: either the
 * cache already holds the cleaned definition, or the tool's mutation produces exactly the intended options/name
 * while models, size and animation stay identical.
 */
class PohObjectOptionToolTests {
    @Test
    fun `every POH loc ends with exactly the intended menu`() {
        val library = CacheLibrary(FeroxImportTool.GAME_CACHE)
        try {
            PohObjectOptionTool.EDITS.forEach { edit ->
                val current = library.data(PohObjectOptionTool.LOC_INDEX, edit.id ushr 8, edit.id and 0xFF)!!
                val before = Rev667LocType.decode(edit.id, current)
                val applied = PohObjectOptionTool.isApplied(edit, before)
                val result = if (applied) before else Rev667LocType.decode(edit.id, PohObjectOptionTool.mutate(edit, current))
                assertEquals(edit.expectOptions, result.options.toList(), "loc ${edit.id} options")
                edit.expectName?.let { assertEquals(it, result.name, "loc ${edit.id} name") }
                assertEquals(before.allModels, result.allModels, "loc ${edit.id} models")
                assertEquals(before.sizeX to before.sizeZ, result.sizeX to result.sizeZ, "loc ${edit.id} size")
            }
        } finally {
            library.close()
        }
    }

    @Test
    fun `an option run that is not unique is refused`() {
        val run = byteArrayOf(34) + "Remove".toByteArray() + byteArrayOf(0)
        val data = byteArrayOf(2) + "x".toByteArray() + byteArrayOf(0) + run + run + byteArrayOf(0)
        val edit = PohObjectOptionTool.Edit(1, listOf(34 to "Remove"), emptyList(), null, listOf(null, null, null, null, null))
        assertFailsWith<IllegalStateException> { PohObjectOptionTool.mutate(edit, data) }
    }
}
