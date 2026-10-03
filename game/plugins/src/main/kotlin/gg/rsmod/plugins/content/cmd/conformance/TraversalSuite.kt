package gg.rsmod.plugins.content.cmd.conformance

import gg.rsmod.game.fs.def.AnimDef
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.message.impl.MoveGameClickMessage
import gg.rsmod.game.message.impl.OpLoc1Message
import gg.rsmod.game.message.impl.OpLoc2Message
import gg.rsmod.game.message.impl.OpLoc3Message
import gg.rsmod.game.message.impl.OpLoc4Message
import gg.rsmod.game.message.impl.OpLoc5Message
import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.collision.Landing
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.content.skills.agility.isUsableLanding
import java.io.File

/**
 * `conform traversal [id|name] [limit=N] [lanes=8]` - owner priority #1 2026-10-03 (obstacles and noclip, "in heel Gielinor"): every
 * traversal placement of this 667 map that a handler is bound for, crossed by a bot from every side it can be approached from,
 * measured per placement + side (the coverage unit of the plan, not the object id):
 *
 *  - the click is handled (no "Nothing interesting happens") and has a defined result: the player crosses, or is refused with a
 *    message and stays where he was;
 *  - a crossing ends on usable ground ([isUsableLanding]) on the other side of the loc, with the lock released, no looping
 *    animation still running on the client ([AnimDef.loops] - the Trollheim 740 case) and the client on the server tile
 *    (Pawn.clientDesyncs);
 *  - one ordinary step afterwards works at once (a real walk click, server-side route).
 *
 * The placements come from the read-only cache census `tools\conformance\traversal\placements_667.tsv` (TraversalCacheAuditTool),
 * so a loc nothing is bound for is listed in the catalogue as OPEN rather than hidden. The run writes `catalogue.tsv` with one
 * row per placement + side next to its report. `pursuit`: the Deadman two-player case on the Trollheim rocks (escape + pursuit,
 * requirement refusal for the pursuer).
 */
object TraversalSuite : Conformance.Suite {
    private val verbs =
        listOf(
            "climb", "jump", "squeeze", "cross", "walk-across", "walk-on", "balance", "swing", "grapple", "crawl", "enter",
            "go-through", "pass", "walk-through", "push", "lift", "step", "leap", "vault", "slide", "descend", "ascend", "scale",
        )

    private data class Placement(
        val id: Int,
        val name: String,
        val x: Int,
        val z: Int,
        val plane: Int,
        val type: Int,
        val rot: Int,
    )

    private val census: File get() = File("../../tools/conformance/traversal/placements_667.tsv")

    /**
     * Owner decision 2026-10-03 04:3x: content this server does not have (Dungeoneering floors, minigames that do not exist here)
     * gets the verdict NOT_APPLICABLE with what was checked, instead of being built. One line per entry, tab separated:
     * `box <x0> <z0> <x1> <z1> <reason>` (inclusive, every plane) or `id <id> <reason>`. `#` starts a comment.
     */
    private val notApplicableFile: File get() = File("../../reference/traversal/not_applicable.tsv")

    private class NotApplicable(val x0: Int, val z0: Int, val x1: Int, val z1: Int, val id: Int, val reason: String) {
        fun matches(p: Placement) = if (id >= 0) p.id == id else p.x in x0..x1 && p.z in z0..z1
    }

    private fun notApplicable(): List<NotApplicable> {
        // not_applicable.tsv plus one not_applicable-<family batch>.tsv per audited batch, same format.
        val files = notApplicableFile.parentFile?.listFiles { f -> f.name.startsWith("not_applicable") && f.name.endsWith(".tsv") }?.sortedBy { it.name }.orEmpty()
        return files.flatMap { it.readLines() }.filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull { line ->
            val c = line.split('\t')
            runCatching {
                when (c[0]) {
                    "box" -> NotApplicable(c[1].toInt(), c[2].toInt(), c[3].toInt(), c[4].toInt(), -1, c[5])
                    "id" -> NotApplicable(0, 0, 0, 0, c[1].toInt(), c[2])
                    else -> null
                }
            }.getOrNull()
        }
    }

    /** Failing crossings that get a timeline file per run (disk: each is the one bot's events, at most 200 kB). */
    private const val MAX_TIMELINES = 300
    private val NL = System.lineSeparator()
    private val timelines = java.util.concurrent.atomic.AtomicInteger(0)

    override suspend fun QueueTask.run(
        run: Conformance.Run,
        args: List<String>,
    ) {
        val filter = args.filter { !it.startsWith("limit=") && !it.startsWith("lanes=") && it != "pursuit" && it != "placements" }.joinToString(" ").lowercase()
        val limit = args.firstOrNull { it.startsWith("limit=") }?.substringAfter('=')?.toIntOrNull() ?: Int.MAX_VALUE
        val lanes = args.firstOrNull { it.startsWith("lanes=") }?.substringAfter('=')?.toIntOrNull() ?: 8
        val only = args.filter { it == "pursuit" || it == "placements" }
        if (only.isEmpty() || "pursuit" in only) pursuit(run)
        if (only.isEmpty() || "placements" in only) placements(run, filter, limit, lanes)
    }

    // ---- placements ------------------------------------------------------------------------------------------------------

    private fun load(run: Conformance.Run): List<Placement> {
        val file = census
        if (!file.exists()) {
            run.note("census ${file.absolutePath} missing: run gradlew :game:runTraversalCacheAuditTool first")
            return emptyList()
        }
        return file.readLines().filter { it.startsWith("PLACEMENT\t") }.mapNotNull { line ->
            val f = line.split('\t').drop(1).associate { it.substringBefore('=') to it.substringAfter('=', "") }
            runCatching {
                Placement(f.getValue("id").toInt(), f["name"] ?: "", f.getValue("x").toInt(), f.getValue("z").toInt(), f.getValue("plane").toInt(), f.getValue("type").toInt(), f.getValue("rotation").toInt())
            }.getOrNull()
        }
    }

    /** The option slot (1-5) of [def] whose text is a traversal verb and that a plugin binds; null when none. */
    private fun boundVerb(
        run: Conformance.Run,
        id: Int,
        def: ObjectDef,
    ): Pair<Int, String>? {
        val bound = run.world.plugins.boundObjectOptions(id)
        def.options.forEachIndexed { i, option ->
            val text = option?.lowercase()?.trim() ?: return@forEachIndexed
            if (verbs.any { text == it || text.startsWith("$it-") || text.startsWith("$it ") } && (i + 1) in bound) return (i + 1) to option
        }
        return null
    }

    /** The option slot (1-5) with a traversal verb that the sourced teleport table handles on exactly this tile; null when none. */
    private fun tableSlot(
        def: ObjectDef,
        id: Int,
        tile: Tile,
    ): Pair<Int, String>? {
        def.options.forEachIndexed { i, option ->
            val text = option?.lowercase()?.trim() ?: return@forEachIndexed
            if (!verbs.any { text == it || text.startsWith("$it-") || text.startsWith("$it ") }) return@forEachIndexed
            // Bridge tiles: the census keeps the raw plane, the server loads the loc one plane lower (batch 1 report: 17400, 17396).
            val tiles = listOfNotNull(tile, if (tile.height > 0) Tile(tile.x, tile.z, tile.height - 1) else null)
            val found =
                tiles.any { t ->
                    gg.rsmod.plugins.content.mechanics.objteleports.ObjectTeleports.find(t, setOf(id), option) != null ||
                        (text == "climb" && listOf("climb-up", "climb-down").any { gg.rsmod.plugins.content.mechanics.objteleports.ObjectTeleports.find(t, setOf(id), it) != null })
                }
            if (found) return (i + 1) to option
        }
        return null
    }

    /** One placement of a bound traversal loc, ready to cross. */
    private class Work(
        val p: Placement,
        val def: ObjectDef,
        val verb: Pair<Int, String>,
        val obj: gg.rsmod.game.model.entity.GameObject,
    )

    private suspend fun QueueTask.placements(
        run: Conformance.Run,
        filter: String,
        limit: Int,
        lanes: Int,
    ) {
        val all = load(run)
        timelines.set(0)
        val defs = run.world.definitions
        val rows = java.util.Collections.synchronizedList(ArrayList<String>())
        var open = 0
        var na = 0
        val skip = notApplicable()
        val work = ArrayList<Work>()
        for ((id, all_) in all.groupBy { it.id }.toSortedMap()) {
            val def = defs.getNullable(ObjectDef::class.java, id) ?: continue
            if (filter.isNotBlank() && filter != id.toString() && !def.name.lowercase().contains(filter)) continue
            val hasAnyVerb = def.options.any { o -> o != null && verbs.any { o.lowercase().startsWith(it) } }
            val placements =
                all_.filter { p ->
                    val reason = skip.firstOrNull { it.matches(p) }?.reason ?: return@filter true
                    if (hasAnyVerb) {
                        na++
                        rows += "$id@${p.x},${p.z},${p.plane}\t${def.name}\t${def.options.filterNotNull().joinToString("|")}\t-\t-\t-\t-\tNOT_APPLICABLE\t$reason"
                    }
                    false
                }
            val verb = boundVerb(run, id, def)
            if (verb == null) {
                // Handled per placement by the object fallback: the sourced teleport table (data/cfg/object-teleports/*.json) has an
                // entry for this exact id + tile + option. Those are tested like any bound placement (batch 2 report 2026-10-03).
                val (tabled, rest) =
                    placements.partition { p ->
                        tableSlot(def, id, Tile(p.x, p.z, p.plane)) != null
                    }
                for (p in tabled) {
                    val obj = (p.plane downTo maxOf(0, p.plane - 1)).asSequence().mapNotNull { h ->
                        run.world.chunks.get(Tile(p.x, p.z, h), createIfNeeded = true)
                            ?.getEntities<gg.rsmod.game.model.entity.GameObject>(Tile(p.x, p.z, h), gg.rsmod.game.model.EntityType.STATIC_OBJECT)
                            ?.firstOrNull { it.id == id }
                    }.firstOrNull()
                    val slot = tableSlot(def, id, Tile(p.x, p.z, p.plane))!!
                    if (obj == null) {
                        rows += "$id@${p.x},${p.z},${p.plane}\t${def.name}\t${slot.second}\ttable\t-\t-\t-\tNOT_TESTED\tnot on the live map (removed or replaced)"
                    } else {
                        work += Work(p, def, slot, obj)
                    }
                }
                if (rest.isEmpty()) continue
                // Listed, not hidden: the catalogue keeps every unhandled traversal loc as OPEN.
                val hasVerb = def.options.any { o -> o != null && verbs.any { o.lowercase().startsWith(it) } }
                if (hasVerb) {
                    open += rest.size
                    rest.forEach { p -> rows += "$id@${p.x},${p.z},${p.plane}\t${def.name}\t${def.options.filterNotNull().joinToString("|")}\tno\t-\t-\t-\tOPEN\tno handler bound" }
                }
                continue
            }
            for (p in placements) {
                val obj = run.world.chunks.get(Tile(p.x, p.z, p.plane), createIfNeeded = true)
                    ?.getEntities<gg.rsmod.game.model.entity.GameObject>(Tile(p.x, p.z, p.plane), gg.rsmod.game.model.EntityType.STATIC_OBJECT)
                    ?.firstOrNull { it.id == id }
                if (obj == null) {
                    rows += "$id@${p.x},${p.z},${p.plane}\t${def.name}\t${verb.second}\tyes\t-\t-\t-\tNOT_TESTED\tnot on the live map (removed or replaced)"
                    continue
                }
                work += Work(p, def, verb, obj)
            }
        }
        // Lanes work on contiguous blocks of the map (sorted by region), so two lanes are never at the same obstacle.
        val ordered = work.sortedWith(compareBy({ (it.p.x shr 6) * 256 + (it.p.z shr 6) }, { it.p.plane }, { it.p.x }, { it.p.z })).take(limit)
        val laneCount = lanes.coerceIn(1, 16).coerceAtMost(maxOf(1, ordered.size))
        val block = (ordered.size + laneCount - 1) / laneCount
        val tested = java.util.concurrent.atomic.AtomicInteger(0)
        val done = java.util.concurrent.atomic.AtomicInteger(0)
        run.note("traversal: ${ordered.size} bound placements in $laneCount lanes")
        for (lane in 0 until laneCount) {
            val share = ordered.drop(lane * block).take(block)
            run.world.queue {
                try {
                    for (w in share) {
                        if (Conformance.stopRequested) break
                        try {
                            placement(run, w, lane, rows, tested)
                        } catch (e: Throwable) {
                            run.expectTrue("${w.p.id}@${w.p.x},${w.p.z},${w.p.plane}", "scenario runs to the end", false, "threw $e")
                        }
                    }
                } finally {
                    done.incrementAndGet()
                }
            }
        }
        while (done.get() < laneCount) wait(5)
        File(run.dir, "catalogue.tsv").writeText("key\tname\toption\tbound\tside\tstart\tend\tverdict\tdetail\n" + rows.sorted().joinToString("\n") + "\n")
        run.note("traversal catalogue: ${all.size} census placements, ${tested.get()} placement-sides tested, $open placements with a traversal option and no handler (OPEN), $na NOT_APPLICABLE (reference/traversal/not_applicable.tsv)")
    }

    private suspend fun QueueTask.placement(
        run: Conformance.Run,
        w: Work,
        lane: Int,
        rows: MutableList<String>,
        tested: java.util.concurrent.atomic.AtomicInteger,
    ) {
        val p = w.p
        val def = w.def
        val obj = w.obj
        val wd = def.getRotatedWidth(obj)
        val l = def.getRotatedLength(obj)
        val sides =
            listOf(
                "W" to { d: Int -> Tile(p.x - d, p.z + l / 2, p.plane) },
                "E" to { d: Int -> Tile(p.x + wd - 1 + d, p.z + l / 2, p.plane) },
                "S" to { d: Int -> Tile(p.x + wd / 2, p.z - d, p.plane) },
                "N" to { d: Int -> Tile(p.x + wd / 2, p.z + l - 1 + d, p.plane) },
            )
        var crossedFromSomeSide = false
        var sidesTried = 0
        val refusals = ArrayList<String>()
        for ((side, at) in sides) {
            val start = (1..3).map(at).firstOrNull { t -> t.height in 0..3 && Landing.pocket(run.world.collision, t).size >= Landing.OPEN_GROUND } ?: continue
            val n = tested.incrementAndGet()
            sidesTried++
            if (n % 25 == 0) run.progress("traversal $n: ${p.id} ${def.name} at ${p.x},${p.z} from $side")
            val key = "${p.id}@${p.x},${p.z},${p.plane}/$side"
            val from = run.world.currentCycle
            run.laneStarts[lane] = from
            val (verdict, end, detail) = cross(run, key, obj.id, obj.tile, w.verb.first, start)
            run.laneStarts.remove(lane)
            if (!end.sameAs(start)) crossedFromSomeSide = true else refusals += "$side: ${detail.substringAfter("ticks; ").take(60)}"
            rows += "${p.id}@${p.x},${p.z},${p.plane}\t${def.name}\t${w.verb.second}\tyes\t$side\t${start.x},${start.z}\t${end.x},${end.z},${end.height}\t$verdict\t$detail"
            run.expectTrue(key, "${def.name} '${w.verb.second}' from $side", verdict == "PASS", detail.take(250))
            // The recorder is pruned either way; a failing crossing's own timeline is written by cross() (bot-only, capped - the
            // 2026-10-03 sweeps wrote every lane's events per failure, 0.5-0.7 GB a run, and filled C:).
            run.prune()
        }
        // A placement nobody can use from any side is a dead obstacle even when every side answers politely
        // ("I can't reach that!" from everywhere = the object cannot be routed to), unless a requirement refused it.
        if (sidesTried > 0 && !crossedFromSomeSide) {
            val requirement = refusals.any { it.contains("level") || it.contains("need") || it.contains("quest", ignoreCase = true) }
            run.expectTrue("${p.id}@${p.x},${p.z},${p.plane}", "${def.name} '${w.verb.second}' is usable from at least one side", requirement, refusals.joinToString(" | ").take(250))
        }
    }

    private fun opLoc(
        slot: Int,
        id: Int,
        tile: Tile,
    ): gg.rsmod.game.message.Message =
        when (slot) {
            1 -> OpLoc1Message(0, tile.x, id, tile.z)
            2 -> OpLoc2Message(id, tile.x, tile.z, 0)
            3 -> OpLoc3Message(id, tile.x, tile.z, 0)
            4 -> OpLoc4Message(id, tile.x, tile.z, 0)
            else -> OpLoc5Message(id, tile.x, tile.z, 0)
        }

    /**
     * One bot, one click, one verdict: PASS when the player crossed to usable ground with everything cleaned up and can walk on,
     * or was refused with a message and stayed put; FAIL otherwise. Returns (verdict, end tile, detail).
     */
    private suspend fun QueueTask.cross(
        run: Conformance.Run,
        label: String,
        id: Int,
        objTile: Tile,
        slot: Int,
        start: Tile,
    ): Triple<String, Tile, String> {
        val bot = run.bot(start, label.replace(Regex("[^A-Za-z0-9]"), "").take(12))
        wait(2)
        val c0 = run.world.currentCycle
        Bots.inject(bot, opLoc(slot, id, objTile))
        var waited = 0
        // Approach + crossing: until the obstacle task has released the lock, the walk is done and no transit is pending - for three
        // ticks in a row, so a handler that waits before it locks (a run-up, a dialogue) is not judged as "did nothing".
        var idle = 0
        var answered = 0
        do {
            wait(1)
            waited++
            idle = if (bot.lock != LockState.NONE || bot.movementQueue.hasDestination() || bot.inTransit()) 0 else idle + 1
            // A middle-floor "Climb" (Void Stairs.kt) asks "What would you like to do?" - the bot picks "Go up the stairs."
            // like a player would; any other options dialogue gets its first line too. Answering counts as the click acting.
            if (answered < 2 && Bots.answerOption(bot, 1)) {
                answered++
                idle = 0
            }
        } while (waited < 80 && idle < 3)
        val end = Tile(bot.tile)
        val msgs = run.recording.of(bot.username, "msg").filter { it.cycle >= c0 }.map { it.text }.filter { it.contains("MessageGame") && !it.contains("__RSPS_HUD__") }
        val nothing = msgs.any { it.contains("Nothing interesting happens") }
        val moved = !end.sameAs(start)
        val problems = ArrayList<String>()
        if (nothing) problems += "unhandled option"
        if (bot.lock != LockState.NONE) problems += "lock still ${bot.lock} after $waited ticks"
        val anim = bot.lastAnimationId
        if (anim != -1 && run.world.definitions.getNullable(AnimDef::class.java, anim)?.loops == true) problems += "looping animation $anim still running on the client"
        if (bot.clientDesyncs > 0) problems += "client off the server tile (${bot.clientDesyncs})"
        if (bot.sceneryNoclips > 0) problems += "noclip: ${bot.lastCrossingProblem}"
        if (bot.plainWalksThroughScenery > 0) problems += "walked through the loc without a climb (OB7): ${bot.lastCrossingProblem}"
        // A stop on a chain - the stepping stone just jumped onto, or the next traversal loc of the chain - is not a pocket: the
        // player goes on with the next jump, not with a walk.
        val chainStop = end.sameAs(objTile) || traversalLocAt(run, end)
        // Evidence the obstacle itself did something: an animation, a teleport or a forced movement sent for the bot (a walk to the
        // loc that then stands still is a dead click, not a crossing).
        val acted =
            run.recording.of(bot.username, "out").filter { it.cycle >= c0 }.any { e ->
                (e.text.startsWith("anim id=") && !e.text.startsWith("anim id=-1")) || e.text == "teleport" || e.text.contains("FORCE", ignoreCase = true)
            } || end.height != start.height || answered > 0 ||
                // A Void climb-render walk (walkOverTile with Render.CLIMBING: Trollheim "up", stiles, handholds) has no sequence and
                // no forced move: the evidence is that the player ended on the other side of the loc. A walk through the loc WITHOUT
                // the climbing render is caught separately as OB7 (Player.plainWalksThroughScenery) and fails below.
                crossedLoc(start, end, objTile) ||
                // A short moveTo (Chaos Tunnels portal 3266,5552 -> 3262,5552, Void chaos_tunnels.teles.toml) goes out as a
                // movement-type jump, not a teleport block: a tile change of more than one tile in a single tick is the action.
                run.recording.of(bot.username, "state").filter { it.cycle >= c0 && it.text.startsWith("tile: ") }.any { e -> jumped(e.text) }
        if (moved && !acted && msgs.isEmpty()) {
            problems += "click did nothing: walked to the loc, then no action and no message"
        } else if (moved) {
            if (!chainStop) {
                if (!isUsableLanding(run.world, end)) problems += "landed in a pocket at ${end.x},${end.z}"
                // One ordinary step afterwards: a real walk click to a neighbouring tile the server can route to.
                val next = Direction.NESW.map { end.step(it) }.firstOrNull { run.world.collision.canStep(end, Direction.between(end, it)) }
                if (next != null) {
                    Bots.inject(bot, MoveGameClickMessage(next.x, next.z, 0))
                    repeat(3) { wait(1) }
                    if (bot.tile.sameAs(end)) problems += "cannot walk on after the crossing (click to ${next.x},${next.z} did nothing)"
                }
            }
        } else if (msgs.isEmpty()) {
            problems += "click did nothing: no movement and no message"
        }
        val detail = "from ${start.x},${start.z} to ${end.x},${end.z} in $waited ticks; ${msgs.joinToString(" / ").take(120)}" + (if (problems.isEmpty()) "" else "; PROBLEM " + problems.joinToString("; "))
        if (problems.isNotEmpty() && timelines.incrementAndGet() <= MAX_TIMELINES) {
            runCatching {
                val file = File(File(run.dir, "timelines").also { it.mkdirs() }, label.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".txt")
                file.writeText(run.recording.of(bot.username).filter { it.cycle >= c0 - 5 }.joinToString(NL) { it.toString() }.take(200_000) + NL)
            }
        }
        run.retire(bot)
        return Triple(if (problems.isEmpty()) "PASS" else "FAIL", end, detail)
    }

    /** [start] and [end] lie on opposite sides of the loc's anchor along x or z. */
    private fun crossedLoc(
        start: Tile,
        end: Tile,
        loc: Tile,
    ): Boolean = (start.x < loc.x && end.x > loc.x) || (start.x > loc.x && end.x < loc.x) || (start.z < loc.z && end.z > loc.z) || (start.z > loc.z && end.z < loc.z)

    /** `tile: x,z,h -> x,z,h` with more than one tile (or a plane change) between the two: a jump, not a walk step. */
    private fun jumped(text: String): Boolean {
        val parts = text.removePrefix("tile: ").split(" -> ")
        if (parts.size != 2) return false
        val a = parts[0].trim().split(',').mapNotNull { it.trim().toIntOrNull() }
        val b = parts[1].trim().split(',').mapNotNull { it.trim().toIntOrNull() }
        if (a.size < 3 || b.size < 3) return false
        return maxOf(Math.abs(a[0] - b[0]), Math.abs(a[1] - b[1])) > 2 || a[2] != b[2]
    }

    /** True when a loc with a traversal option stands on [tile] (a stepping stone, the next rock of a chain). */
    private fun traversalLocAt(
        run: Conformance.Run,
        tile: Tile,
    ): Boolean =
        run.world.chunks.get(tile, createIfNeeded = true)
            ?.getEntities<gg.rsmod.game.model.entity.GameObject>(tile, gg.rsmod.game.model.EntityType.STATIC_OBJECT, gg.rsmod.game.model.EntityType.DYNAMIC_OBJECT)
            ?.any { o -> run.world.definitions.getNullable(ObjectDef::class.java, o.id)?.options?.any { opt -> opt != null && verbs.any { opt.lowercase().startsWith(it) } } == true } == true

    // ---- Deadman: escape and pursuit on the Trollheim rocks --------------------------------------------------------------

    private suspend fun QueueTask.pursuit(run: Conformance.Run) {
        // 3803 at 2876,3671: east side (down) -> west landing; the owner's 23:58 route. Fleeing player F, pursuer P two tiles behind.
        val rock = Tile(2876, 3671)
        val f = run.bot(Tile(2883, 3669), "PF")
        val p = run.bot(Tile(2885, 3669), "PP")
        wait(2)
        Bots.inject(f, OpLoc1Message(0, rock.x, 3803, rock.z))
        wait(2)
        Bots.inject(p, OpLoc1Message(0, rock.x, 3803, rock.z))
        var waited = 0
        do {
            wait(1)
            waited++
        } while (waited < 40 && listOf(f, p).any { it.lock != LockState.NONE || it.movementQueue.hasDestination() || it.inTransit() })
        run.expectTrue("pursuit", "fleeing player crosses 3803 to usable ground", f.tile.x < rock.x && isUsableLanding(run.world, f.tile), "F at ${f.tile}")
        run.expectTrue("pursuit", "pursuer crosses the same rock to usable ground", p.tile.x < rock.x && isUsableLanding(run.world, p.tile), "P at ${p.tile}")
        run.expectTrue("pursuit", "both clients on their server tiles", f.clientDesyncs == 0 && p.clientDesyncs == 0, "F ${f.clientDesyncs} P ${p.clientDesyncs}")
        run.expectTrue("pursuit", "no looping climb animation left on either", listOf(f, p).none { b -> b.lastAnimationId != -1 && run.world.definitions.getNullable(AnimDef::class.java, b.lastAnimationId)?.loops == true }, "F anim ${f.lastAnimationId} P anim ${p.lastAnimationId}")
        // The fleeing player walks on at once; the pursuer follows through open ground, not through the rock.
        val fEnd = Tile(f.tile)
        Bots.inject(f, MoveGameClickMessage(2878, 3678, 0))
        repeat(6) { wait(1) }
        run.expectTrue("pursuit", "fleeing player walks on after landing", !f.tile.sameAs(fEnd), "F ${fEnd} -> ${f.tile}")
        run.expectTrue("pursuit", "the walk after the landing stays on open ground", Landing.pocket(run.world.collision, f.tile).size >= Landing.OPEN_GROUND, "F at ${f.tile}")
        // Requirement gate: 9305 needs 47 Agility - a pursuer below it is refused with the sourced message and stays on his side.
        val low = run.bot(Tile(2893, 3675), "PL")
        low.skills.setBaseLevel(Skills.AGILITY, 1)
        low.skills.setCurrentLevel(Skills.AGILITY, 1)
        wait(2)
        val before = Tile(low.tile)
        val c0 = run.world.currentCycle
        Bots.inject(low, OpLoc1Message(0, 2894, 9305, 3672))
        repeat(8) { wait(1) }
        val msgs = run.recording.of("PL", "msg").filter { it.cycle >= c0 }.map { it.text }
        run.expectTrue("pursuit", "a pursuer without the Agility level is refused and stays", low.tile.sameAs(before) && msgs.any { it.contains("Agility level") }, "at ${low.tile}; ${msgs.filter { it.contains("MessageGame") }.joinToString(" / ").take(150)}")
        run.expectTrue("pursuit", "a refused pursuer is not locked or animating", low.lock == LockState.NONE && low.lastAnimationId == -1, "lock ${low.lock} anim ${low.lastAnimationId}")
    }
}
