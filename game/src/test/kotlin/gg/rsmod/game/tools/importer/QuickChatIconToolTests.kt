package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** N238 (owner 2026-10-23): no Quick Chat icon anywhere in the chat - no clientscript of either cache pushes `<img=3>` any more. */
class QuickChatIconToolTests {
    private val caches = listOf(Paths.get("..", "data", "cache").toFile(), Paths.get("..", "..", "..", "file-server", "cache").toFile())

    @Test
    fun `no clientscript draws the Quick Chat icon, and the patched scripts keep their shape`() {
        caches.forEach { cache ->
            val library = CacheLibrary(cache.toString())
            try {
                val offenders = mutableListOf<Int>()
                library.index(QuickChatIconTool.CLIENTSCRIPT_INDEX).archiveIds().forEach { id ->
                    val data = library.data(QuickChatIconTool.CLIENTSCRIPT_INDEX, id, 0) ?: return@forEach
                    if (data.size < 20) return@forEach
                    val code = runCatching { ClientScriptEditor.decode(data).code }.getOrNull() ?: return@forEach
                    if (code.any { it.string == QuickChatIconTool.ICON }) offenders += id
                }
                assertTrue(offenders.isEmpty(), "$cache: scripts still drawing the Quick Chat icon: $offenders")
                QuickChatIconTool.SCRIPTS.forEach { id ->
                    val data = library.data(QuickChatIconTool.CLIENTSCRIPT_INDEX, id, 0)!!
                    assertEquals(data.toList(), QuickChatIconTool.strip(data).toList(), "$cache $id: idempotent")
                }
            } finally {
                library.close()
            }
        }
    }
}
