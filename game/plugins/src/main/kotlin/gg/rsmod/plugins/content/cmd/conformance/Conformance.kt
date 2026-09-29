package gg.rsmod.plugins.content.cmd.conformance

import gg.rsmod.game.Server.Companion.logger
import gg.rsmod.game.message.Message
import gg.rsmod.game.message.impl.IfButtonMessage
import gg.rsmod.game.message.impl.MoveGameClickMessage
import gg.rsmod.game.message.impl.OpNpc1Message
import gg.rsmod.game.message.impl.OpNpc2Message
import gg.rsmod.game.message.impl.OpNpcTMessage
import gg.rsmod.game.message.impl.ResumePauseButtonMessage
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.Trace
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.service.GameService
import gg.rsmod.game.system.GameSystem
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.message
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.util.ReferenceCountUtil
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Conformance suite (P8, owner 2026-09-28: summoning, curses, combat PvP/PvM and every OSRS weapon "op alles getest").
 * Catalogue of what is tested: C:\RSPS\docs\conformance_catalogue.md.
 *
 *   conform <domain> [filter]     domains: summoning (more follow: curses, combat, weapons)
 *   conform stop
 *
 * Headless bots (never saved) act only through the real input path - [Bots.inject] queues the same message objects the
 * packet decoders produce, so every handler, gate and per-cycle timing is the live one. A [Recording] captures everything
 * measurable per tick. Each check compares a measured value with the value of one or more independent references (never
 * the server's own tables) and gets a verdict:
 *
 *   PASS        measured == every reference
 *   FAIL        measured differs from agreeing references
 *   CONFLICT    the references disagree with each other (the owner decides; the measured value is shown with each)
 *   UNSOURCED   no reference has a value; the measured value is recorded so a later source can judge it
 *   NOT_TESTED  a catalogue line with no scenario yet (listed, never silently skipped)
 *
 * Output: C:\RSPS\bugs\conformance\<domain>-<stamp>\report.txt (FAILs first, NEW ones vs the previous run on top),
 * timelines\<entry>.txt (every recorded event of that entry, tick by tick), and a PROBLEM line per FAIL for Show-Problems.
 */
object Conformance {
    enum class Verdict { FAIL, CONFLICT, UNSOURCED, NOT_TESTED, PASS }

    data class Result(
        val entry: String,
        val check: String,
        val verdict: Verdict,
        val expected: String,
        val measured: String,
    ) {
        /** Stable across runs (no values): used to find NEW failures. */
        val key get() = "$entry | $check"

        override fun toString() = "${verdict.name.padEnd(10)} $entry | $check | expected $expected | measured $measured"
    }

    @Volatile
    var running: String? = null

    @Volatile
    var stopRequested = false

    private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    private val ROOT = File("C:/RSPS/bugs/conformance")

    fun start(
        world: World,
        admin: Player,
        domain: String,
        args: List<String>,
    ) = admin.message(start(world, admin.username, admin.tile, domain, args))

    /** Default arena search origin for runs started from the command port (no admin in game). */
    val DEFAULT_ORIGIN = Tile(3222, 3222, 0)

    /**
     * Starts [domain] with the arena searched from [origin]; returns the reply line. `at x z [h]` in [args] overrides the
     * origin (command port: `conform summoning at 3100 3500`).
     */
    fun start(
        world: World,
        adminName: String?,
        origin: Tile,
        domain: String,
        rawArgs: List<String>,
    ): String {
        if (running != null) return "Conformance '$running' is already running. conform stop first."
        val suite: Suite =
            when (domain) {
                "summoning" -> SummoningSuite
                "curses", "prayers" -> CursesSuite
                "equipment", "weapons", "armour" -> EquipmentSuite
                "combat" -> CombatSuite
                "hud" -> HudSuite
                else -> return "Unknown conformance domain '$domain' (summoning, curses, equipment, combat, hud)."
            }
        val at = rawArgs.indexOf("at")
        val from =
            if (at >= 0 && rawArgs.size >= at + 3) {
                Tile(rawArgs[at + 1].toInt(), rawArgs[at + 2].toInt(), rawArgs.getOrNull(at + 3)?.toIntOrNull() ?: 0)
            } else {
                origin
            }
        val args = if (at >= 0) rawArgs.take(at) else rawArgs
        running = domain
        stopRequested = false
        val run = Run(world, domain, adminName ?: "", from)
        world.queue {
            run.recording.attach()
            try {
                with(suite) { run(run, args) }
            } catch (e: Throwable) {
                Trace.crash("conformance $domain", e)
                run.note("suite aborted: $e")
            } finally {
                run.recording.detach()
                run.cleanup()
                val dir = run.write()
                running = null
                lastSummary =
                    "Conformance '$domain' done: ${run.count(Verdict.PASS)} pass, ${run.count(Verdict.FAIL)} fail, " +
                    "${run.count(Verdict.CONFLICT)} conflict, ${run.count(Verdict.UNSOURCED)} unsourced, ${run.count(Verdict.NOT_TESTED)} not tested - ${dir.path}"
                world.getPlayerForName(run.adminName)?.message("<col=178000>$lastSummary</col>")
                logger.info("CONFORMANCE done: {}", lastSummary)
                // Headless agent run (tools\conformance\Run-Conformance.ps1): the private test server ends itself, saving nothing.
                if (System.getProperty("$PROPERTY.exit") == "true") Runtime.getRuntime().halt(0)
            }
        }
        return "Conformance '$domain' started near ${from.x},${from.z},${from.height}. Report: C:\\RSPS\\bugs\\conformance\\ (conform stop ends it)."
    }

    /** Headless agent run: -Drsps.conformance=<domain> [-Drsps.conformance.args="a b"] [-Drsps.conformance.at=x,z,h] -Drsps.conformance.exit=true */
    const val PROPERTY = "rsps.conformance"

    fun startHeadless(world: World) {
        val domain = System.getProperty(PROPERTY) ?: return
        val args = System.getProperty("$PROPERTY.args")?.split(' ')?.filter { it.isNotBlank() } ?: emptyList()
        val at = System.getProperty("$PROPERTY.at")?.split(',')?.mapNotNull { it.trim().toIntOrNull() }
        val origin = if (at != null && at.size >= 2) Tile(at[0], at[1], at.getOrElse(2) { 0 }) else DEFAULT_ORIGIN
        // The recorder measures by itself; the world-wide traces (on through the owner's toggle files) only cost cycle time
        // here. Switched off in this test server's memory only - the toggle files are never touched.
        Trace.Cat.values().forEach { Trace.set(it, it == Trace.Cat.SWEEP) }
        gg.rsmod.game.model.AvTrace.enabled = false
        gg.rsmod.game.model.BreachTrace.enabled = false
        world.queue {
            wait(5)
            logger.info("CONFORMANCE headless: {}", start(world, null, origin, domain, args))
        }
    }

    @Volatile
    var lastSummary: String = "no run finished yet"

    /** Command-port form: `conform <domain> [args] [at x z h]`, `conform stop`, `conform status`. */
    fun command(
        world: World,
        args: List<String>,
    ): String =
        when (val d = args.getOrNull(0)?.lowercase()) {
            null -> "conform summoning|curses|equipment|combat|hud [name] [at x z h] | stop | status"
            "stop" -> {
                stopRequested = true
                "The conformance run stops after its current entry."
            }
            "status" -> if (running != null) "running: $running (${progressText})" else lastSummary
            else -> start(world, null, DEFAULT_ORIGIN, d, args.drop(1))
        }

    @Volatile
    var progressText: String = ""

    /** One domain's scenarios. */
    interface Suite {
        suspend fun QueueTask.run(
            run: Run,
            args: List<String>,
        )
    }

    /** State of one conformance run: results, the recording, the bots and npcs it spawned, timeline windows. */
    class Run(
        val world: World,
        val domain: String,
        val adminName: String,
        val origin: Tile,
    ) {
        val recording = Recording(world)
        val results = ArrayList<Result>()
        private val notes = ArrayList<String>()
        private val bots = ArrayList<Client>()
        private val npcs = ArrayList<Npc>()
        /** The report folder, created at the start so every entry's timeline is written as soon as the entry ends. */
        val dir = File(ROOT, "$domain-${LocalDateTime.now().format(STAMP)}").also { File(it, "timelines").mkdirs() }

        /** Start cycle of the entry each parallel lane is working on; events before the earliest are pruned. */
        val laneStarts = java.util.concurrent.ConcurrentHashMap<Int, Int>()

        fun note(text: String) {
            notes += text
        }

        fun count(v: Verdict) = results.count { it.verdict == v }

        fun bot(
            tile: Tile,
            name: String,
        ): Client = Bots.create(world, tile, name).also { bots += it; recording.track(it, name) }

        fun npc(
            id: Int,
            tile: Tile,
            label: String,
        ): Npc {
            val npc = Npc(id, tile, world)
            npc.respawnOverride = false
            npc.walkRadius = 0
            world.spawn(npc)
            npcs += npc
            recording.track(npc, label)
            return npc
        }

        fun remove(npc: Npc) {
            recording.untrack(npc)
            if (world.npcs.contains(npc)) world.remove(npc)
        }

        /** Marks the recorded events of [entry] (for its timeline file) from [fromCycle] up to now. */
        fun window(
            entry: String,
            fromCycle: Int,
        ) {
            // Written now (the recorder is pruned afterwards), from every pawn's events of this entry's cycles.
            runCatching {
                val file = File(File(dir, "timelines"), entry.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".txt")
                file.writeText(recording.slice(fromCycle, world.currentCycle).joinToString("\n") + "\n")
            }
            val floor = laneStarts.values.minOrNull() ?: world.currentCycle
            recording.prune(minOf(floor, world.currentCycle))
        }

        fun progress(text: String) {
            progressText = text
            world.getPlayerForName(adminName)?.message("<col=7f7f7f>conform: $text</col>")
        }

        // ---- verdicts ---------------------------------------------------------------------------------------------------

        fun add(result: Result) {
            results += result
            if (result.verdict == Verdict.FAIL) Trace.problem(Trace.Cat.SWEEP) { "conformance $result".take(300) }
        }

        /**
         * Compares [measured] with every non-null reference in [refs] ("void" to 6, "novite" to 6). Values are compared by
         * [same] (default: equal as text, so 6 == 6.0 does not fail on type).
         */
        fun check(
            entry: String,
            check: String,
            measured: Any?,
            vararg refs: Pair<String, Any?>,
            same: (Any?, Any?) -> Boolean = { a, b -> norm(a) == norm(b) },
        ) {
            val known = refs.filter { it.second != null }
            val m = norm(measured)
            if (known.isEmpty()) {
                add(Result(entry, check, Verdict.UNSOURCED, "-", m))
                return
            }
            // One source always agrees with itself (it may list several valid values, e.g. "55% (50% imbued)").
            val agree = known.size == 1 || known.all { same(it.second, known.first().second) }
            val exp = known.joinToString(" / ") { "${norm(it.second)} (${it.first})" }
            val verdict =
                when {
                    !agree -> Verdict.CONFLICT
                    same(measured, known.first().second) -> Verdict.PASS
                    else -> Verdict.FAIL
                }
            add(Result(entry, check, verdict, exp, m))
        }

        /** A yes/no rule that must hold (invariants, refusals): PASS or FAIL with [detail]. */
        fun expectTrue(
            entry: String,
            check: String,
            ok: Boolean,
            detail: String,
            rule: String = "true",
        ) = add(Result(entry, check, if (ok) Verdict.PASS else Verdict.FAIL, rule, detail))

        fun notTested(
            entry: String,
            check: String,
            why: String,
        ) = add(Result(entry, check, Verdict.NOT_TESTED, why, "-"))

        private fun norm(v: Any?): String =
            when (v) {
                null -> "null"
                is Double -> if (v == Math.floor(v)) v.toLong().toString() else "%.3f".format(java.util.Locale.ENGLISH, v)
                is Float -> norm(v.toDouble())
                is Collection<*> -> v.joinToString(",", "[", "]") { norm(it) }
                else -> v.toString()
            }

        // ---- cleanup and report ------------------------------------------------------------------------------------------

        fun cleanup() {
            bots.forEach { b -> runCatching { gg.rsmod.plugins.content.skills.summoning.Familiar.dismiss(b) }; runCatching { world.unregister(b) } }
            npcs.forEach { n -> runCatching { if (world.npcs.contains(n)) world.remove(n) } }
        }

        fun write(): File {
            val previous = previousFailKeys()
            val order = results.sortedWith(compareBy<Result>({ it.verdict.ordinal }, { it.entry }, { it.check }))
            val newFails = order.filter { it.verdict == Verdict.FAIL && it.key !in previous }
            val sb = StringBuilder()
            sb.append("RSPS conformance '$domain' ${LocalDateTime.now()}\n")
            Verdict.values().forEach { v -> sb.append("  ${v.name}: ${count(v)}\n") }
            sb.append("  entries: ${results.map { it.entry }.distinct().size}, checks: ${results.size}\n")
            notes.forEach { sb.append("  note: $it\n") }
            if (previous.isNotEmpty() || newFails.isNotEmpty()) {
                sb.append("\n== NEW FAILS (not failing in the previous run) : ${newFails.size} ==\n")
                newFails.forEach { sb.append("$it\n") }
                val fixed = previous - order.filter { it.verdict == Verdict.FAIL }.map { it.key }.toSet()
                sb.append("\n== FIXED since the previous run : ${fixed.size} ==\n")
                fixed.sorted().forEach { sb.append("$it\n") }
            }
            sb.append("\n== ALL RESULTS ==\n")
            order.forEach { sb.append("$it\n") }
            File(dir, "report.txt").writeText(sb.toString())
            File(dir, "fail-keys.txt").writeText(order.filter { it.verdict == Verdict.FAIL }.joinToString("\n") { it.key })
            logger.info("Conformance {} written to {}", domain, dir)
            return dir
        }

        private fun previousFailKeys(): Set<String> {
            val last = ROOT.listFiles { f -> f.isDirectory && f.name.startsWith("$domain-") && f != dir }?.maxByOrNull { it.name } ?: return emptySet()
            return File(last, "fail-keys.txt").takeIf { it.exists() }?.readLines()?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        }
    }
}

/** Headless test players and their real-input clicks. */
object Bots {
    fun create(
        world: World,
        tile: Tile,
        name: String,
    ): Client {
        val channel =
            EmbeddedChannel(
                object : ChannelOutboundHandlerAdapter() {
                    override fun write(
                        ctx: ChannelHandlerContext,
                        msg: Any,
                        promise: ChannelPromise,
                    ) {
                        ReferenceCountUtil.release(msg)
                        promise.setSuccess()
                    }
                },
            )
        val bot = Client(channel, world)
        bot.loginUsername = name
        bot.username = name
        bot.passwordHash = ""
        bot.uid = gg.rsmod.game.model.PlayerUID(name)
        bot.tile = tile
        bot.gameSystem = GameSystem(channel, world, bot, world.getService(GameService::class.java)!!)
        for (skill in 0..Skills.DUNGEONEERING) bot.skills.setBaseLevel(skill, 99)
        check(world.register(bot)) { "conformance bot not registered" }
        bot.login()
        // Never saved: the autosave timer would write a save file for the test player, and a death / trade saves at once.
        bot.timers.remove(gg.rsmod.game.model.timer.SAVE_TIMER)
        bot.attr[gg.rsmod.plugins.api.ext.NEVER_SAVE_ATTR] = true
        gg.rsmod.plugins.content.mechanics.pvp.BeginnerProtection.forfeit(bot)
        return bot
    }

    /** Queues [message] as if [bot]'s client had sent it (real handler, gates and cycle). */
    fun inject(
        bot: Client,
        message: Message,
    ): Boolean {
        // A message type the server has no decoder for is a harness bug (the real client never sends it): fail loudly.
        check(bot.gameSystem.inject(message)) { "the server has no handler for ${message.javaClass.simpleName} - the 667 client does not send it" }
        return true
    }

    /** IfButton opcodes of the 667 client per menu option (IfButton1Handler: 61, 64, 4, 18, 10, .., 25 for option 8). */
    private val OPTION_OPCODES = mapOf(1 to 61, 2 to 64, 3 to 4, 4 to 18, 5 to 10, 8 to 25)

    fun button(
        bot: Client,
        parent: Int,
        component: Int,
        option: Int = 1,
        slot: Int = -1,
        item: Int = -1,
    ) = inject(bot, IfButtonMessage((parent shl 16) or component, option - 1, slot, item, OPTION_OPCODES[option] ?: 61))

    /** The inventory item's menu option named [option] ("Summon"), clicked on the item in [slot]. False when absent. */
    fun itemOption(
        bot: Client,
        slot: Int,
        option: String,
    ): Boolean {
        val item = bot.inventory[slot] ?: return false
        val menu = item.getDef(bot.world.definitions).inventoryMenu
        val index = menu.indexOfFirst { it?.equals(option, true) == true }
        if (index < 0) return false
        return button(bot, 679, 0, index + 1, slot, item.id)
    }

    fun walk(
        bot: Client,
        to: Tile,
        run: Boolean = false,
    ) = inject(bot, MoveGameClickMessage(to.x, to.z, if (run) 1 else 0))

    fun npcOption(
        bot: Client,
        npc: Npc,
        option: Int,
    ) = when (option) {
        1 -> inject(bot, OpNpc1Message(npc.index, 0))
        2 -> inject(bot, OpNpc2Message(npc.index, 0))
        3 -> inject(bot, gg.rsmod.game.message.impl.OpNpc3Message(npc.index, 0))
        4 -> inject(bot, gg.rsmod.game.message.impl.OpNpc4Message(npc.index, 0))
        5 -> inject(bot, gg.rsmod.game.message.impl.OpNpc5Message(npc.index, 0))
        else -> false
    }

    /** "Use" an inventory item on an npc: the 667 client sends it as an interface-target packet from inventory 679:0. */
    fun itemOnNpc(
        bot: Client,
        slot: Int,
        npc: Npc,
    ): Boolean {
        if (bot.inventory[slot] == null) return false
        return inject(bot, OpNpcTMessage(npc.index, (679 shl 16), slot, 0))
    }

    /** A targeted interface action (spell, familiar special, familiar attack) from [parent]:[component] on [npc]. */
    fun targetNpc(
        bot: Client,
        parent: Int,
        component: Int,
        npc: Npc,
        slot: Int = -1,
    ) = inject(bot, OpNpcTMessage(npc.index, (parent shl 16) or component, slot, 0))

    /** Answers an open options dialogue (interfaces 228..240) with option [n] (1-based), like a click on that line. */
    fun answerOption(
        bot: Client,
        n: Int,
    ): Boolean {
        val open = (228..240 step 2).firstOrNull { bot.interfaces.isVisible(it) } ?: return false
        return inject(bot, ResumePauseButtonMessage(open, n + 1, n + 1))
    }
}
