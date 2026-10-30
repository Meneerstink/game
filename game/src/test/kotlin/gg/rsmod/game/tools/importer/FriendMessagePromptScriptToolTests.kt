package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * N440 (owner 2026-10-30 "Bouw OSRS-venster"): "Message" on a friend or on a chat name opens the box "Enter message to send to
 * <name>" (script 107) in both caches, and the normal chat is not left in "To <name>" mode (VARC 1650 = 0).
 */
class FriendMessagePromptScriptToolTests {
    private val caches = listOf(Paths.get("..", "data", "cache").toFile(), Paths.get("..", "..", "..", "file-server", "cache").toFile())

    @Test
    fun `both Message options open the OSRS message box and the patch is idempotent`() {
        caches.forEach { cache ->
            val library = CacheLibrary(cache.toString())
            try {
                FriendMessagePromptScriptTool.SCRIPTS.keys.forEach { id ->
                    val data = library.data(FriendMessagePromptScriptTool.CLIENTSCRIPT_INDEX, id, 0)!!
                    val patched = FriendMessagePromptScriptTool.patch(id, data)
                    val code = ClientScriptEditor.decode(patched).code
                    assertTrue(code.none { it.opcode == 40 && it.int == FriendMessagePromptScriptTool.TYPING_LINE_SCRIPT }, "$cache $id: still calls 1558")
                    assertEquals(1, code.count { it.opcode == 40 && it.int == FriendMessagePromptScriptTool.PROMPT_SCRIPT }, "$cache $id: calls 107 once")
                    val at = code.indexOfFirst { it.opcode == 40 && it.int == FriendMessagePromptScriptTool.PROMPT_SCRIPT }
                    assertEquals(listOf(0 to 0, 43 to 1650, 35 to code[at - 1].int), (at - 3 until at).map { code[it].opcode to code[it].int })
                    assertEquals(patched.toList(), FriendMessagePromptScriptTool.patch(id, patched).toList(), "$cache $id: idempotent")
                }
            } finally {
                library.close()
            }
        }
    }
}
