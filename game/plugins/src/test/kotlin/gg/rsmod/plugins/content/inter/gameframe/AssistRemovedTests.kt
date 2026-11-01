package gg.rsmod.plugins.content.inter.gameframe

import gg.rsmod.game.message.impl.IfSetHideMessage
import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.PaneMountHooks
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * A21 (owner 2026-10-11: "remove req assist completely" - the WHOLE Assist System: right-click, chat tab, assist XP screen).
 * Darkan and Novite never had it; it came from the rsmod base and was built out on 10-01.
 */
class AssistRemovedTests {
    private val content = File("src/main/kotlin")

    @Test
    fun `the Assist tab of the chat filter bar is hidden on every mount of 751`() {
        TabEvents.install()
        val player = mockk<Player>(relaxed = true)
        // N240: the mount also sizes Report through N108Ui, which keeps its per-player cache in the attributes.
        io.mockk.every { player.attr } returns gg.rsmod.game.model.attr.AttributeMap()
        PaneMountHooks.afterMount(player, 751)
        verify(exactly = 1) { player.write(IfSetHideMessage(hash = (751 shl 16) or 12, hidden = true)) }
    }

    @Test
    fun `no player option Req Assist and no Assist System code is left`() {
        val sources = content.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "kts") }.toList()
        assertEquals(emptyList(), sources.filter { "AssistSystem" in it.readText() }.map { it.name })
        assertFalse(File(content, "gg/rsmod/plugins/content/mechanics/assist").exists())
        assertFalse("Req Assist\"" in File(content, "gg/rsmod/plugins/content/runetek5.plugin.kts").readText())
    }

    @Test
    fun `no assisting lock exists any more`() {
        assertFalse(LockState.values().any { it.name == "ASSISTING" })
    }
}
