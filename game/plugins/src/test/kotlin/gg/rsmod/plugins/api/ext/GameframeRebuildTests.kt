package gg.rsmod.plugins.api.ext

import gg.rsmod.game.model.entity.Player
import io.mockk.mockk
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * RCV-010 A4: every top-level gameframe restore re-arms the server-sent tab-strip state.
 */
class GameframeRebuildTests {
    @AfterTest
    fun reset() = GameframeRebuild.clearForTests()

    @Test
    fun `closing a fullscreen interface runs every registered restore`() {
        val calls = mutableListOf<String>()
        GameframeRebuild.register("a") { calls += "a" }
        GameframeRebuild.register("b") { calls += "b" }
        GameframeRebuild.register("a") { calls += "a2" } // script reload replaces, never duplicates
        val player = mockk<Player>(relaxed = true)
        player.closeFullscreenInterface()
        assertEquals(listOf("a2", "b"), calls, "immediate pass (the delayed pass is queued)")
        assertEquals(setOf("a", "b"), GameframeRebuild.registeredKeys())
    }

    @Test
    fun `every gameframe restore write site in plugin sources re-arms`() {
        // Roster over the source: any function that writes IF_OPENTOP in restore mode (2) must call GameframeRebuild.
        val sources = File("src/main/kotlin").walkTopDown().filter { it.isFile && (it.name.endsWith(".kt") || it.name.endsWith(".kts")) }.toList()
        val offenders = mutableListOf<String>()
        sources.forEach { file ->
            val text = file.readText()
            Regex("""IfOpenTopMessage\([^)]*,\s*2\)""").findAll(text).forEach { m ->
                val start = text.lastIndexOf("fun ", m.range.first)
                val end = text.indexOf("\n}", m.range.last).let { if (it < 0) text.length else it }
                if (!text.substring(start.coerceAtLeast(0), end).contains("GameframeRebuild.rearm")) {
                    offenders += "${file.name}:${text.substring(0, m.range.first).count { it == '\n' } + 1}"
                }
            }
        }
        assertTrue(offenders.isEmpty(), "IF_OPENTOP restore without GameframeRebuild.rearm: $offenders")
        // The Summoning restore is registered from familiar.plugin.kts.
        val familiar = File("src/main/kotlin/gg/rsmod/plugins/content/skills/summoning/familiar.plugin.kts").readText()
        assertTrue(familiar.contains("GameframeRebuild.register(\"summoning\")") && familiar.contains("FollowerDetailsTab.install(p)"))
    }
}
