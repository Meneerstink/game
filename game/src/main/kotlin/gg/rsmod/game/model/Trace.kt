package gg.rsmod.game.model

import mu.KLogging
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

/**
 * The RSPS debug toolkit's trace core (owner 2026-09-28: "een hulppakket om onze rsps zsm bugvrij te maken", one command for
 * every trace, "als ik die aanzet en we testen moet je gelijk het issue herkennen"). Manual for every agent: `C:\RSPS\TRACES.md`.
 *
 * - Categories ([Cat]) log to the game-server log as `[trace:<cat>] ...`. A line that names a failure starts with `PROBLEM`.
 * - [AvTrace] (graphics/sounds) and [BreachTrace] (Deadman breaches) keep their own prefixes, feed [record] too and are switched
 *   with the rest by [setAll].
 * - Every traced line is also kept in memory ([recent], the last [RECENT_LINES]) for the `bug <text>` report.
 * - [crash] is always on (traces or not): every caught exception goes to `C:\RSPS\bugs\crashes-<date>.log` with its stack.
 * - [onProblem] (set by the plugins) shows each PROBLEM live in the chat of staff who turned the traces on.
 *
 * On: the file [ALL_FILE] at boot (server and client), or the in-game command `traces` (on / off / status / <category>).
 */
object Trace : KLogging() {
    const val ALL_FILE = "C:/RSPS/trace.on"
    const val BUGS_DIR = "C:/RSPS/bugs"
    const val RECENT_LINES = 600

    enum class Cat(val key: String, val what: String) {
        INPUT("input", "every client action packet the server handled; commands with their arguments"),
        MSG("msg", "every chatbox message sent to a player; PROBLEM for 'Nothing interesting happens.' with the click that caused it"),
        INTERFACE("interface", "interfaces opened/closed/moved and button clicks with handled=true/false"),
        INTERACT("interact", "npc / object / ground-item option clicks with handled=true/false"),
        ITEM("item", "inventory and worn item options with handled=true/false"),
        COMBAT("combat", "every hit: attacker, target, style, max hit, landed, damage"),
        NPCPATH("npcpath", "npc route searches that fail (the npc cannot reach its target)"),
        VAR("var", "varp / varbit values sent to the client"),
        PLUGIN("plugin", "exceptions thrown by packet handlers, plugins, timers, queues and hit actions"),
        TICK("tick", "game ticks that ran late (server lag)"),
        SUMMON("summon", "familiar summon/dismiss, follow recovery teleports, special moves; PROBLEM when a special shows nothing"),
        SWEEP("sweep", "automatic sweeps (weapons, spells, familiars, npcs, prayers) and their verdicts"),
    }

    private val on: MutableSet<Cat> = ConcurrentHashMap.newKeySet()

    private val recentLines = ArrayDeque<String>(RECENT_LINES)

    /** Live PROBLEM sink (the plugins route it to the chat of staff with traces on). Must never throw. */
    @Volatile
    var onProblem: ((String) -> Unit)? = null

    init {
        if (File(ALL_FILE).exists()) on.addAll(Cat.values())
    }

    fun enabled(cat: Cat): Boolean = cat in on

    fun anyOn(): Boolean = on.isNotEmpty()

    fun set(
        cat: Cat,
        value: Boolean,
    ) {
        if (value) on.add(cat) else on.remove(cat)
    }

    /**
     * Every trace on or off at once: these categories, [AvTrace] and [BreachTrace]. The file [ALL_FILE] is written or removed
     * too, so the client (it reads the file at start) and the next server start follow.
     */
    fun setAll(value: Boolean) {
        if (value) on.addAll(Cat.values()) else on.clear()
        AvTrace.enabled = value
        BreachTrace.enabled = value
        runCatching {
            val file = File(ALL_FILE)
            if (value) file.writeText("all traces on (the 'traces' command, see C:/RSPS/TRACES.md)\n") else file.delete()
        }
    }

    fun status(): String =
        Cat.values().joinToString(" ") { "${it.key}=${if (it in on) "on" else "off"}" } +
            " av=${if (AvTrace.enabled) "on" else "off"} breach=${if (BreachTrace.enabled) "on" else "off"}"

    inline fun log(
        cat: Cat,
        crossinline message: () -> String,
    ) {
        if (enabled(cat)) {
            val line = "[trace:${cat.key}] ${message()}"
            record(line)
            logger.info { line }
        }
    }

    /** A line that names a failure: always starts with PROBLEM so a test session's issues can be listed with one search. */
    inline fun problem(
        cat: Cat,
        crossinline message: () -> String,
    ) {
        if (enabled(cat)) {
            val line = "[trace:${cat.key}] PROBLEM ${message()}"
            record(line)
            logger.info { line }
        }
    }

    /** In-engine sweeps ([capture]) receive every traced line while they run, whatever else is on. */
    @Volatile
    private var captureSink: MutableList<String>? = null

    /** Starts collecting every traced line into [into] (a sweep's evidence window); [stopCapture] ends it. */
    fun capture(into: MutableList<String>) {
        captureSink = into
    }

    fun stopCapture() {
        captureSink = null
    }

    /** Sounds this cycle, per key ("p:<player>" for a sound sent to a player, "t:x,z,h" for an area sound at a tile). */
    private val soundsThisCycle = ConcurrentHashMap<String, Int>()

    fun noteSound(
        key: String,
        cycle: Int,
    ) {
        if (!anyOn() && !AvTrace.enabled) return
        if (soundsThisCycle.size > 5000) soundsThisCycle.clear()
        soundsThisCycle[key] = cycle
    }

    /** Whether a sound was noted for [key] in [cycle] (swing audits: did the attack make any sound?). */
    fun soundNoted(
        key: String,
        cycle: Int,
    ): Boolean = soundsThisCycle[key] == cycle

    /** Keeps [line] for the bug report and hands a PROBLEM to [onProblem]. Called by [log], [problem], [AvTrace], [BreachTrace]. */
    fun record(line: String) {
        val stamped = "${LocalDateTime.now().format(TIME)} $line"
        synchronized(recentLines) {
            if (recentLines.size >= RECENT_LINES) recentLines.pollFirst()
            recentLines.addLast(stamped)
        }
        captureSink?.let { sink -> synchronized(sink) { sink += line } }
        if (line.contains("PROBLEM")) {
            runCatching { onProblem?.invoke(line) }
        }
    }

    /** The traced lines kept in memory, oldest first. */
    fun recent(): List<String> = synchronized(recentLines) { recentLines.toList() }

    /**
     * Always on: a caught exception with its [context] goes to `C:\RSPS\bugs\crashes-<date>.log` (full stack) and, with the
     * traces on, becomes a PLUGIN PROBLEM line naming the file and line where it happened.
     */
    fun crash(
        context: String,
        e: Throwable?,
    ) {
        val head = if (e == null) context else "$context: ${e.javaClass.simpleName}: ${e.message} at ${where(e)}"
        problem(Cat.PLUGIN) { if (e == null) "error $head" else "crash $head" }
        runCatching {
            val dir = File(BUGS_DIR)
            dir.mkdirs()
            val stack = e?.stackTrace?.take(25)?.joinToString("\n") { "    at $it" } ?: "    (logged as an error, no exception)"
            val cause = e?.cause?.let { "\n  caused by ${it.javaClass.name}: ${it.message} at ${where(it)}" } ?: ""
            File(dir, "crashes-${LocalDate.now()}.log").appendText("${LocalDateTime.now().format(TIME)} $head$cause\n$stack\n\n")
        }
    }

    /** The first stack frame of [e] inside the game/plugin code, e.g. "bank.plugin.kts:120", for a PROBLEM line. */
    fun where(e: Throwable): String =
        e.stackTrace.firstOrNull { it.className.startsWith("gg.rsmod") || it.fileName?.endsWith(".kts") == true }
            ?.let { "${it.fileName}:${it.lineNumber}" } ?: "?"

    private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
}
