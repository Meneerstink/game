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
        // RCV-012 B5 roster: every IF_OPENTOP mode (0 fullscreen, 1 overlay, 2 restore) and every tab-area open/close
        // helper in PlayerExt re-arms, so no caller (bank, shop, GE, trade, duel, price checker, BoB) needs its own patch.
        val ext = File("src/main/kotlin/gg/rsmod/plugins/api/ext/PlayerExt.kt").readText()
        val topWrites = Regex("""IfOpenTopMessage\(""").findAll(ext).toList()
        assertTrue(topWrites.size >= 3, "expected the three IF_OPENTOP helpers")
        topWrites.forEach { m ->
            val body = ext.substring(ext.lastIndexOf("fun ", m.range.first), ext.indexOf("\n}", m.range.last))
            assertTrue(body.contains("GameframeRebuild.rearm"), "IF_OPENTOP without re-arm in: ${body.lineSequence().first()}")
        }
        listOf("fun Player.openInterface(\n    interfaceId: Int,\n    dest: InterfaceDestination,", "fun Player.closeInterface(dest: InterfaceDestination)").forEach { sig ->
            val start = ext.replace("\r\n", "\n").indexOf(sig)
            assertTrue(start >= 0, "missing $sig")
            val normalized = ext.replace("\r\n", "\n")
            val body = normalized.substring(start, normalized.indexOf("\n}", start))
            assertTrue(body.contains("InterfaceDestination.TAB_AREA") && body.contains("GameframeRebuild.rearm"), "tab-area re-arm missing in $sig")
        }
        // No per-path tab re-arm patches left in plugin content (the shared helpers own it).
        val patched = sources.filter { it.name == "FamiliarInventoryInterface.kt" }.filter { it.readText().contains("FollowerDetailsTab.install") }
        assertTrue(patched.isEmpty(), "per-path FollowerDetailsTab.install patch still present: $patched")
        // The Summoning restore is registered from familiar.plugin.kts.
        val familiar = File("src/main/kotlin/gg/rsmod/plugins/content/skills/summoning/familiar.plugin.kts").readText()
        assertTrue(familiar.contains("GameframeRebuild.register(\"summoning\")") && familiar.contains("FollowerDetailsTab.install(p)"))
    }
}
