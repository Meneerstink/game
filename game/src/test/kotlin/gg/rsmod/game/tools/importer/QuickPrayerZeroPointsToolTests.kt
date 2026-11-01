package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * N483: quick prayers can be chosen at 0 Prayer points (OSRS); switching a prayer on at 0 points keeps its refusal (script 2289
 * still calls 2295 with the points check).
 */
class QuickPrayerZeroPointsToolTests {
    private val caches = listOf(Paths.get("..", "data", "cache").toFile(), Paths.get("..", "..", "..", "file-server", "cache").toFile())

    @Test
    fun `5401 is 2295 without the points refusal, the quick-prayer scripts call it, the Activate menu keeps 2295`() {
        caches.forEach { cache ->
            val library = CacheLibrary(cache.toString())
            try {
                fun script(id: Int) = ClientScriptEditor.decode(library.data(QuickPrayerZeroPointsTool.SCRIPT_INDEX, id, 0)!!)
                val base = library.data(QuickPrayerZeroPointsTool.SCRIPT_INDEX, QuickPrayerZeroPointsTool.CAN_CHOOSE, 0)!!
                val stripped = ClientScriptEditor.decode(QuickPrayerZeroPointsTool.withoutPointsCheck(base))
                val at = stripped.find(40 to QuickPrayerZeroPointsTool.POINTS, 87 to null).single()
                // the branch after the points call lands on the very next instruction: both outcomes continue with the other checks
                assertTrue(stripped.code[at + 1].target === stripped.code[at + 2], "$cache: points branch neutralised")
                assertEquals(script(QuickPrayerZeroPointsTool.CAN_CHOOSE).code.size, stripped.code.size, "$cache: nothing else removed")
                // the original 2295 still refuses (Activate at 0 points, OSRS prayer_op)
                val original = script(QuickPrayerZeroPointsTool.CAN_CHOOSE)
                val o = original.find(40 to QuickPrayerZeroPointsTool.POINTS, 87 to null).single()
                assertTrue(original.code[o + 1].target !== original.code[o + 2], "$cache: 2295 keeps its points refusal")
                // what the cache holds once applied
                library.data(QuickPrayerZeroPointsTool.SCRIPT_INDEX, QuickPrayerZeroPointsTool.NEW_SCRIPT, 0)?.let {
                    assertEquals(QuickPrayerZeroPointsTool.withoutPointsCheck(base).toList(), it.toList(), "$cache: 5401 content")
                    QuickPrayerZeroPointsTool.QUICK_CALLERS.forEach { id ->
                        val code = script(id).code
                        assertEquals(1, code.count { c -> c.opcode == 40 && c.int == QuickPrayerZeroPointsTool.NEW_SCRIPT }, "$cache $id calls 5401")
                        assertTrue(code.none { c -> c.opcode == 40 && c.int == QuickPrayerZeroPointsTool.CAN_CHOOSE }, "$cache $id no longer calls 2295")
                    }
                    assertEquals(1, script(2289).code.count { c -> c.opcode == 40 && c.int == QuickPrayerZeroPointsTool.CAN_CHOOSE }, "$cache 2289 keeps 2295")
                }
            } finally {
                library.close()
            }
        }
    }
}
