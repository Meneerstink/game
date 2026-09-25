package gg.rsmod.plugins.content.combat.scripts

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Audit T-03: plugins must change the world only on the game thread, on ticks. The Canifis citizens'
 * werewolf transformation ran on two `java.util.Timer` threads per fight (150 ms / 2000 ms later),
 * racing the game thread on the npc list and chunks and leaking a thread each time.
 */
class GameThreadOnlyPluginTests {
    private val pluginsMain = File("src/main/kotlin/gg/rsmod/plugins")

    /**
     * Files that start their own threads on purpose and hand the result back through
     * `GameService.submitGameThreadJob`. Adding a file here needs that same hand-off.
     */
    private val allowed = setOf("PasswordChange.kt")

    private val forbidden =
        listOf(
            Regex("""\bjava\.util\.Timer\b"""),
            Regex("""\bTimerTask\b"""),
            Regex("""\bThread\("""),
            Regex("""\bthread\s*\{"""),
            Regex("""\bthread\("""),
            Regex("""\bExecutors\."""),
        )

    @Test
    fun `plugins do not start timers or threads outside the allowed hand-off files`() {
        val offenders =
            pluginsMain
                .walk()
                .filter { it.isFile && (it.name.endsWith(".kt") || it.name.endsWith(".kts")) && it.name !in allowed }
                .flatMap { file ->
                    file.readLines()
                        .filter { line -> line.trim().let { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") } }
                        .filter { line -> forbidden.any { it.containsMatchIn(line) } }
                        .map { "${file.name}: ${it.trim()}" }
                }.toList()
        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun `the Canifis transformation runs on ticks`() {
        val script = File(pluginsMain, "content/combat/scripts/impl/CanifisCitizensCombatScript.kt").readText()
        assertFalse("import java.util.Timer" in script)
        assertFalse(".schedule(" in script)
        val swap = script.substringAfter("npc.animate(Anims.START_HUMAN_TO_WEREWOLF, priority = true)").substringBefore("werewolf.attack(player)")
        assertTrue("it.wait(1)" in swap.substringBefore("world.spawn(werewolf)"), "the swap happens a tick after the start animation")
        assertTrue("world.queue {" in swap && "wait(WEREWOLF_ANIMATION_RESET_TICKS)" in swap, "the animation reset is a world task")
    }
}
