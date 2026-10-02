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
 * `conform traversal [id|name|limit=N]` - owner priority #1 2026-10-03 (obstacles and noclip, "in heel Gielinor"): every
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

    override suspend fun QueueTask.run(
        run: Conformance.Run,
        args: List<String>,
    ) {
        val filter = args.filter { !it.startsWith("limit=") && it != "pursuit" && it != "placements" }.joinToString(" ").lowercase()
        val limit = args.firstOrNull { it.startsWith("limit=") }?.substringAfter('=')?.toIntOrNull() ?: Int.MAX_VALUE
        val only = args.filter { it == "pursuit" || it == "placements" }
        if (only.isEmpty() || "pursuit" in only) pursuit(run)
        if (only.isEmpty() || "placements" in only) placements(run, filter, limit)
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

    private suspend fun QueueTask.placements(
        run: Conformance.Run,
        filter: String,
        limit: Int,
    ) {
        val all = load(run)
        val defs = run.world.definitions
        val rows = StringBuilder("key\tname\toption\tbound\tside\tstart\tend\tverdict\tdetail\n")
        var tested = 0
        var open = 0
        val byId = all.groupBy { it.id }
        for ((id, placements) in byId.toSortedMap()) {
            val def = defs.getNullable(ObjectDef::class.java, id) ?: continue
            if (filter.isNotBlank() && filter != id.toString() && !def.name.lowercase().contains(filter)) continue
            val verb = boundVerb(run, id, def)
            if (verb == null) {
                // Listed, not hidden: the catalogue keeps every unhandled traversal loc as OPEN.
                val hasVerb = def.options.any { o -> o != null && verbs.any { o.lowercase().startsWith(it) } }
                if (hasVerb) {
                    open += placements.size
                    placements.forEach { p -> rows.append("$id@${p.x},${p.z},${p.plane}\t${def.name}\t${def.options.filterNotNull().joinToString("|")}\tno\t-\t-\t-\tOPEN\tno handler bound\n") }
                }
                continue
            }
            for (p in placements) {
                if (tested >= limit || Conformance.stopRequested) break
                val obj = run.world.chunks.get(Tile(p.x, p.z, p.plane), createIfNeeded = true)
                    ?.getEntities<gg.rsmod.game.model.entity.GameObject>(Tile(p.x, p.z, p.plane), gg.rsmod.game.model.EntityType.STATIC_OBJECT)
                    ?.firstOrNull { it.id == id }
                if (obj == null) {
                    rows.append("$id@${p.x},${p.z},${p.plane}\t${def.name}\t${verb.second}\tyes\t-\t-\t-\tNOT_TESTED\tnot on the live map (removed or replaced)\n")
                    continue
                }
                val w = def.getRotatedWidth(obj)
                val l = def.getRotatedLength(obj)
                val sides =
                    listOf(
                        "W" to { d: Int -> Tile(p.x - d, p.z + l / 2, p.plane) },
                        "E" to { d: Int -> Tile(p.x + w - 1 + d, p.z + l / 2, p.plane) },
                        "S" to { d: Int -> Tile(p.x + w / 2, p.z - d, p.plane) },
                        "N" to { d: Int -> Tile(p.x + w / 2, p.z + l - 1 + d, p.plane) },
                    )
                var crossedFromSomeSide = false
                var sidesTried = 0
                val refusals = ArrayList<String>()
                for ((side, at) in sides) {
                    val start = (1..3).map(at).firstOrNull { t -> t.height in 0..3 && Landing.pocket(run.world.collision, t).size >= Landing.OPEN_GROUND } ?: continue
                    tested++
                    sidesTried++
                    run.progress("traversal $id ${def.name} at ${p.x},${p.z} from $side ($tested)")
                    val key = "$id@${p.x},${p.z},${p.plane}/$side"
                    val (verdict, end, detail) = cross(run, key, obj.id, obj.tile, verb.first, start)
                    if (!end.sameAs(start)) crossedFromSomeSide = true else refusals += "$side: ${detail.substringAfter("ticks; ").take(60)}"
                    rows.append("$id@${p.x},${p.z},${p.plane}\t${def.name}\t${verb.second}\tyes\t$side\t${start.x},${start.z}\t${end.x},${end.z},${end.height}\t$verdict\t$detail\n")
                    run.expectTrue(key, "${def.name} '${verb.second}' from $side", verdict == "PASS", detail.take(250))
                    run.window(key, run.world.currentCycle - 40)
                }
                // A placement nobody can use from any side is a dead obstacle even when every side answers politely
                // ("I can't reach that!" from everywhere = the object cannot be routed to), unless a requirement refused it.
                if (sidesTried > 0 && !crossedFromSomeSide) {
                    val requirement = refusals.any { it.contains("level") || it.contains("need") || it.contains("quest", ignoreCase = true) }
                    run.expectTrue("$id@${p.x},${p.z},${p.plane}", "${def.name} '${verb.second}' is usable from at least one side", requirement, refusals.joinToString(" | ").take(250))
                }
            }
            if (tested >= limit || Conformance.stopRequested) break
        }
        File(run.dir, "catalogue.tsv").writeText(rows.toString())
        run.note("traversal catalogue: ${all.size} census placements, $tested placement-sides tested, $open placements with a traversal option and no handler (OPEN)")
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
        // Approach + crossing: until the obstacle task has released the lock, the walk is done and no transit is pending.
        do {
            wait(1)
            waited++
        } while (waited < 40 && (bot.lock != LockState.NONE || bot.movementQueue.hasDestination() || bot.inTransit()))
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
        if (moved) {
            if (!isUsableLanding(run.world, end)) problems += "landed in a pocket at ${end.x},${end.z}"
            // One ordinary step afterwards: a real walk click to a neighbouring tile the server can route to.
            val next = Direction.NESW.map { end.step(it) }.firstOrNull { run.world.collision.canStep(end, Direction.between(end, it)) }
            if (next != null) {
                Bots.inject(bot, MoveGameClickMessage(next.x, next.z, 0))
                repeat(3) { wait(1) }
                if (bot.tile.sameAs(end)) problems += "cannot walk on after the crossing (click to ${next.x},${next.z} did nothing)"
            }
        } else if (msgs.isEmpty()) {
            problems += "click did nothing: no movement and no message"
        }
        val detail = "from ${start.x},${start.z} to ${end.x},${end.z} in $waited ticks; ${msgs.joinToString(" / ").take(120)}" + (if (problems.isEmpty()) "" else "; PROBLEM " + problems.joinToString("; "))
        return Triple(if (problems.isEmpty()) "PASS" else "FAIL", end, detail)
    }

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
