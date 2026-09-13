package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * [ProductionTabClientScriptPatchTool] against the real game cache, read-only: the patch touches only the
 * four spare-tab instructions of clientscript 1766, keeps the instruction count, decodes cleanly and is
 * idempotent (works whether or not the transaction has already been applied).
 */
class ProductionTabClientScriptPatchToolTests {
    private fun script(): ByteArray {
        val library = CacheLibrary(Paths.get("..", "data", "cache").toFile().toString())
        return try {
            assertNotNull(library.data(ProductionTabClientScriptPatchTool.CLIENTSCRIPT_INDEX, ProductionTabClientScriptPatchTool.SCRIPT_ID, 0))
        } finally {
            library.close()
        }
    }

    @Test
    fun `patch swaps only the spare-tab sprite and label and is idempotent`() {
        val current = script()
        val before = ProductionTabClientScriptPatchTool.decode(current)
        val patched = ProductionTabClientScriptPatchTool.patch(current)
        val after = ProductionTabClientScriptPatchTool.decode(patched)

        assertEquals(before.size, after.size)
        assertEquals(ProductionTabClientScriptPatchTool.NEW_SPRITE, after[460].intOperand)
        assertEquals(ProductionTabClientScriptPatchTool.NEW_SPRITE, after[463].intOperand)
        assertEquals(ProductionTabClientScriptPatchTool.NEW_LABEL, after[467].stringOperand)
        assertEquals(ProductionTabClientScriptPatchTool.NEW_LABEL, after[471].stringOperand)

        val changed = before.indices.filter { i ->
            before[i].opcode != after[i].opcode || before[i].intOperand != after[i].intOperand || before[i].stringOperand != after[i].stringOperand
        }
        val expectedChanged = if (before[460].intOperand == ProductionTabClientScriptPatchTool.NEW_SPRITE) emptyList() else listOf(460, 463, 467, 471)
        assertEquals(expectedChanged, changed)

        assertContentEquals(patched, ProductionTabClientScriptPatchTool.patch(patched))
    }
}
