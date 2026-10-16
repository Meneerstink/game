package gg.rsmod.plugins.content.mechanics.travel

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * N49 / N50 (Keurmeester LC19 2026-10-16): the Brimhaven <-> Shilo cart and the Rellekka <-> Waterbirth boat took the fare and left
 * the player FULL-locked on a black screen for good. Both ran the trip in a STANDARD task (`player.lockingQueue {}` / `queue {}`
 * without a priority), opened their own fade 115 in the main-screen modal slot and then `wait(3)`ed. A STANDARD task is paused
 * while a main-screen menu is open (PawnQueueTaskSet.isPaused + runetek5 set_menu_open_check = MAIN_SCREEN or MAIN_SCREEN_FULL),
 * so the task waited on its own fade and never resumed (ROOT_CAUSES #92 / #164). Donors keep a travel fade out of anything a
 * task waits on (Void fade.ifaces.toml "overlay", Darkan FadingScreen + InterfaceManager Sub.FADING); our working boats
 * (charter.plugin.kts, void_outpost_boat.plugin.kts) run the trip as a TaskPriority.WEAK task.
 *
 * Class guard over every plugin source: no STANDARD player task may open an interface in MAIN_SCREEN / MAIN_SCREEN_FULL and
 * then wait(n) ticks with it still open - that wait never ends.
 */
class StandardTaskOwnModalWaitTests {
    private val roots = listOf(File("src/main/kotlin/gg/rsmod/plugins"))

    private val taskStart = Regex("""(?<![A-Za-z0-9_])(queue|lockingQueue)\s*(\(([^(){}]*)\))?\s*\{""")
    private val modalOpen = Regex("""(?<![A-Za-z0-9_])openInterface\s*\(""")
    private val timedWait = Regex("""(?<![A-Za-z0-9_.])wait\s*\(\s*\d""")
    private val modalClose = Regex("""closeMainInterface\s*\(|closeInterface\s*\(\s*(dest\s*=\s*)?InterfaceDestination\.MAIN_SCREEN(_FULL)?\b(?!_)|closeInterfaceModal\s*\(|closeModalInterface\s*\(""")
    private val modalSlot = Regex("""InterfaceDestination\.MAIN_SCREEN(_FULL)?\b(?!_)""")

    @Test
    fun `no standard player task waits on its own main-screen modal`() {
        val sources = roots.flatMap { root -> root.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "kts") }.toList() }
        assertTrue(sources.size > 500, "plugin sources not found from ${File(".").absolutePath} (${sources.size})")
        val offenders = mutableListOf<String>()
        for (file in sources) {
            val text = stripCommentsAndStrings(file.readText())
            for (start in taskStart.findAll(text)) {
                val receiver = text.substring(maxOf(0, start.range.first - 12), start.range.first)
                // Npc and world queues are never paused by a player's menu.
                if (receiver.endsWith("world.") || receiver.endsWith("npc.") || receiver.endsWith("world.queues.")) continue
                val args = start.groupValues[3]
                if (args.contains("WEAK") || args.contains("STRONG")) continue
                val open = start.range.last
                val close = matchingBrace(text, open) ?: continue
                val body = text.substring(open + 1, close)
                val offence = ownModalWait(body) ?: continue
                val line = text.substring(0, open).count { it == '\n' } + 1
                offenders += "${file.relativeTo(roots[0]).path.replace('\\', '/')}:$line ${start.groupValues[1]}(${args.trim()}) -> $offence"
            }
        }
        if (offenders.isNotEmpty()) {
            fail(
                "STANDARD task opens a main-screen modal and then wait()s on it - it is paused by its own window and never resumes " +
                    "(N49/N50; use TaskPriority.WEAK like charter/void_outpost_boat or keep the fade out of the modal slot):\n" +
                    offenders.joinToString("\n"),
            )
        }
    }

    @Test
    fun `the guard sees the N49 and N50 shape and accepts the working boats`() {
        val broken = "player.lockingQueue {\n player.openInterface(115, InterfaceDestination.MAIN_SCREEN_FULL)\n wait(3)\n player.moveTo(t)\n}"
        val weak = "player.lockingQueue(TaskPriority.WEAK) {\n player.openInterface(115, InterfaceDestination.MAIN_SCREEN_FULL)\n wait(3)\n}"
        val overlay = "player.lockingQueue {\n player.openInterface(dest = InterfaceDestination.MAIN_SCREEN_OVERLAY, interfaceId = 115)\n wait(3)\n}"
        val closed = "player.queue {\n player.openInterface(1, InterfaceDestination.MAIN_SCREEN)\n player.closeMainInterface()\n wait(3)\n}"
        assertTrue(scan(broken).isNotEmpty(), "STANDARD lockingQueue + own MAIN_SCREEN_FULL fade + wait must be caught")
        assertTrue(scan(weak).isEmpty(), "a WEAK task is not paused by menus")
        assertTrue(scan(overlay).isEmpty(), "MAIN_SCREEN_OVERLAY is not a menu (runetek5 set_menu_open_check)")
        assertTrue(scan(closed).isEmpty(), "the window is closed before the wait")
    }

    private fun scan(source: String): List<String> {
        val text = stripCommentsAndStrings(source)
        return taskStart.findAll(text).mapNotNull { start ->
            val args = start.groupValues[3]
            if (args.contains("WEAK") || args.contains("STRONG")) return@mapNotNull null
            val close = matchingBrace(text, start.range.last) ?: return@mapNotNull null
            ownModalWait(text.substring(start.range.last + 1, close))
        }.toList()
    }

    /** The first openInterface(..MAIN_SCREEN / MAIN_SCREEN_FULL..) followed by wait(n) with no close of that window in between. */
    private fun ownModalWait(body: String): String? {
        for (open in modalOpen.findAll(body)) {
            val argsEnd = matchingParen(body, open.range.last) ?: continue
            val args = body.substring(open.range.last + 1, argsEnd)
            if (!modalSlot.containsMatchIn(args)) continue
            val rest = body.substring(argsEnd)
            val wait = timedWait.find(rest) ?: continue
            val closeBefore = modalClose.find(rest)
            if (closeBefore != null && closeBefore.range.first < wait.range.first) continue
            return "openInterface(${args.trim()}) then ${rest.substring(wait.range.first, minOf(rest.length, wait.range.first + 8)).trim()}"
        }
        return null
    }

    private fun matchingBrace(text: String, open: Int): Int? = matching(text, open, '{', '}')

    private fun matchingParen(text: String, open: Int): Int? = matching(text, open, '(', ')')

    private fun matching(text: String, open: Int, a: Char, b: Char): Int? {
        var depth = 0
        for (i in open until text.length) {
            when (text[i]) {
                a -> depth++
                b -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return null
    }

    /** Blanks comments and string literal contents (keeps offsets and line numbers), so braces inside text never count. */
    private fun stripCommentsAndStrings(src: String): String {
        val out = StringBuilder(src.length)
        var i = 0
        while (i < src.length) {
            val c = src[i]
            when {
                src.startsWith("//", i) -> while (i < src.length && src[i] != '\n') { out.append(' '); i++ }
                src.startsWith("/*", i) -> {
                    val end = src.indexOf("*/", i + 2).let { if (it < 0) src.length else it + 2 }
                    while (i < end) { out.append(if (src[i] == '\n') '\n' else ' '); i++ }
                }
                src.startsWith("\"\"\"", i) -> {
                    val end = src.indexOf("\"\"\"", i + 3).let { if (it < 0) src.length else it + 3 }
                    while (i < end) { out.append(if (src[i] == '\n') '\n' else ' '); i++ }
                }
                c == '"' -> {
                    out.append(' '); i++
                    while (i < src.length && src[i] != '"' && src[i] != '\n') {
                        if (src[i] == '\\' && i + 1 < src.length) { out.append(' '); i++ }
                        out.append(' '); i++
                    }
                    if (i < src.length) { out.append(' '); i++ }
                }
                c == '\'' && i + 2 < src.length && (src[i + 1] == '\\' || src[i + 2] == '\'') -> {
                    val end = src.indexOf('\'', i + 2).let { if (it < 0) src.length else it + 1 }
                    while (i < end) { out.append(' '); i++ }
                }
                else -> { out.append(c); i++ }
            }
        }
        return out.toString()
    }
}
