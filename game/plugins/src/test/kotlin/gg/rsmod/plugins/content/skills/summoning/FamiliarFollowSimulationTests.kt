package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.MovementQueue
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.path.PathRequest
import gg.rsmod.game.model.path.strategy.BFSPathFindingStrategy
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.testutil.RealMapWorld
import gg.rsmod.util.ServerProperties
import io.mockk.every
import io.mockk.mockk
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * E18 / A4 / F6 (owner, weeks of reports): "familiars RUN, don't FOLLOW correctly and TELEPORT EVERY SECOND". These tests drive the
 * real [Familiar.follow] against the real map of the game cache, tick by tick in the server's own order (familiar follow in the
 * player cycle, then the owner's movement, then the npc's movement), with the owner walking or running a route a player can
 * really walk. Measured per tick: recovery teleports, run steps while the owner walks, and the familiar's distance behind.
 */
class FamiliarFollowSimulationTests {
    private val world get() = RealMapWorld.world.also { ensureBlocks() }

    /** One tick of the owner's movement: a queued step, a forced move (shortcut / moveTo), or a tick spent in an animation. */
    private sealed class Move {
        data class Step(val tile: Tile) : Move()

        data class Jump(val tile: Tile) : Move()

        object Busy : Move()
    }

    private data class Run(
        val teleports: Int,
        val teleportTicks: List<Int>,
        val runStepsWhileOwnerWalks: Int,
        val maxGap: Int,
        val finalGap: Int,
        val gaps: List<Int>,
        val log: List<String>,
    ) {
        fun summary() = copy(gaps = emptyList(), log = emptyList()).toString() + "\n" + log.joinToString("\n")
    }

    /**
     * The owner's real route from [from] to [to] for a 1x1 player. The server's BFS searches only 20 tiles around its start
     * ([gg.rsmod.game.model.path.PathFindingStrategy.MAX_DISTANCE]) while the client's own search covers the whole scene, so a
     * long leg is walked as the client does it: to the closest reachable tile, then on from there.
     */
    private fun ownerRoute(
        from: Tile,
        to: Tile,
    ): List<Tile> {
        val path = mutableListOf<Tile>()
        var cursor = from
        repeat(8) {
            if (cursor.sameAs(to)) return path
            val request =
                PathRequest.Builder()
                    .setPoints(Tile(cursor), Tile(to))
                    .setSourceSize(1, 1)
                    .setTargetSize(0, 0)
                    .clipPathNodes(node = true, link = true)
                    .build()
            val route = BFSPathFindingStrategy(world.collision).calculateRoute(request)
            val leg = route.path.toList()
            path += leg
            if (route.success || leg.isEmpty()) return path
            cursor = leg.last()
        }
        return path
    }

    private fun walk(
        from: Tile,
        vararg waypoints: Tile,
    ): List<Move> {
        val moves = mutableListOf<Move>()
        var cursor = from
        waypoints.forEach { wp ->
            val leg = ownerRoute(cursor, wp)
            leg.forEach { moves += Move.Step(it) }
            cursor = leg.lastOrNull() ?: cursor
        }
        return moves
    }

    /**
     * [ownerStepType] is the type of the owner's queued steps; whether he moves two tiles a tick follows from it exactly as in
     * [MovementQueue.cycle] (NORMAL = the run toggle, FORCED_RUN = run, FORCED_WALK = walk).
     */
    private fun simulate(
        npcId: Int,
        start: Tile,
        moves: List<Move>,
        ownerStepType: MovementQueue.StepType,
        runToggle: Boolean = false,
        idleTicksAtEnd: Int = 10,
    ): Run {
        val ownerRuns =
            when (ownerStepType) {
                MovementQueue.StepType.NORMAL -> runToggle
                MovementQueue.StepType.FORCED_RUN -> true
                MovementQueue.StepType.FORCED_WALK -> false
            }
        var ownerTile = start
        val pending = ArrayDeque(moves)
        fun queued(limit: Int) =
            pending.takeWhile { it is Move.Step }.take(limit).map { MovementQueue.Step((it as Move.Step).tile, ownerStepType, true) }
        val queue = mockk<MovementQueue>(relaxed = true)
        every { queue.peekSteps(any()) } answers { queued(firstArg()) }
        every { queue.peekFirstStep() } answers { queued(1).firstOrNull() }
        every { queue.hasDestination() } answers { queued(1).isNotEmpty() }
        val player = mockk<Player>(relaxed = true)
        every { player.tile } answers { ownerTile }
        every { player.getSize() } returns 1
        every { player.world } returns world
        every { player.username } returns "owner"
        every { player.movementQueue } returns queue
        every { player.isRunning() } returns runToggle
        every { player.isResting() } returns false
        every { player.runEnergy } returns 100.0

        // The familiar arrives where a summon puts it: beside its owner, on tiles its whole body fits on.
        val npc = Npc(npcId, Familiar.placementTile(player, npcId), world)
        npc.ignoresEntityCollision = true
        var teleports = 0
        val teleportTicks = mutableListOf<Int>()
        var runWhileWalking = 0
        var maxGap = 0
        val gaps = mutableListOf<Int>()
        val log = mutableListOf<String>()
        log += "start owner=${start.x},${start.z} npc=${npc.tile.x},${npc.tile.z} size=${npc.getSize()}"
        val total = moves.size + idleTicksAtEnd
        for (tick in 0 until total) {
            val ownerMoving = pending.isNotEmpty()
            val ownerWalksThisTick = !ownerRuns && pending.firstOrNull() is Move.Step
            Familiar.follow(player, npc, runs = true) { _, _ ->
                teleports++
                teleportTicks += tick
            }
            // Owner moves (PlayerPreSynchronizationTask runs before NpcPreSynchronizationTask).
            when (val head = pending.firstOrNull()) {
                is Move.Jump -> {
                    pending.removeFirst()
                    ownerTile = head.tile
                }
                is Move.Busy -> pending.removeFirst()
                is Move.Step ->
                    repeat(if (ownerRuns) 2 else 1) {
                        (pending.firstOrNull() as? Move.Step)?.let {
                            pending.removeFirst()
                            ownerTile = it.tile
                        }
                    }
                null -> Unit
            }
            val before = npc.tile
            npc.movementQueue.cycle()
            val ran = maxOf(Math.abs(npc.tile.x - before.x), Math.abs(npc.tile.z - before.z)) > 1
            if (ran && ownerWalksThisTick) runWhileWalking++
            val gap = Familiar.footprintGap(npc.tile, npc.getSize(), ownerTile, 1)
            if (ownerMoving) maxGap = maxOf(maxGap, gap)
            gaps += gap
            log += "t$tick owner=${ownerTile.x},${ownerTile.z} npc=${npc.tile.x},${npc.tile.z} gap=$gap ran=$ran tp=$teleports"
        }
        return Run(teleports, teleportTicks, runWhileWalking, maxGap, Familiar.footprintGap(npc.tile, npc.getSize(), ownerTile, 1), gaps, log)
    }

    private fun sizeOf(npcId: Int) = world.definitions.get(NpcDef::class.java, npcId).size.coerceAtLeast(1)

    /** Varrock square and its streets, the owner walking: a Steel titan (size 2 in this cache) never teleports, never runs, ends beside him. */
    @Test
    fun `a steel titan follows a walking owner through varrock without teleporting or running`() {
        // The 667 cache gives the Steel titan size 2 (the test used to assume 3); FamiliarPathingTests: 35 familiars are 1x1, 43 are 2x2.
        assertEquals(2, sizeOf(Npcs.STEEL_TITAN))
        val moves = walk(VARROCK_START, *VARROCK)
        assertTrue(moves.size >= 40, "the owner's varrock walk is real: ${moves.size} steps")
        val run = simulate(Npcs.STEEL_TITAN, VARROCK_START, moves, MovementQueue.StepType.NORMAL, runToggle = false)
        assertEquals(0, run.teleports, run.summary())
        assertEquals(0, run.runStepsWhileOwnerWalks, run.summary())
        assertTrue(run.finalGap <= 1, run.summary())
    }

    /** The run toggle is on but the owner ctrl-click walks (FORCED_WALK): the familiar walks too (F6). */
    @Test
    fun `a familiar walks beside an owner who walks with the run toggle on`() {
        val moves = walk(VARROCK_START, *VARROCK)
        val run = simulate(Npcs.STEEL_TITAN, VARROCK_START, moves, MovementQueue.StepType.FORCED_WALK, runToggle = true)
        assertEquals(0, run.teleports, run.summary())
        assertEquals(0, run.runStepsWhileOwnerWalks, run.summary())
        assertTrue(run.finalGap <= 1, run.summary())
    }

    /**
     * A running owner (run toggle on, normal clicks): the titan never teleports, is never left far enough behind to be, and ends
     * beside him. This route has stretches a 2x2 body cannot take (the Varrock general store's one-tile door at 3217,3419-3420;
     * a one-tile gap near 3230,3419 the titan walks round to the north - sim 2026-10-04), so how close it stays is asserted on open
     * ground instead (FamiliarPathingTests "on open ground a familiar walks with a walking owner and keeps up with a running one").
     */
    @Test
    fun `a steel titan keeps up with a running owner without teleporting`() {
        val moves = walk(VARROCK_START, *VARROCK)
        val run = simulate(Npcs.STEEL_TITAN, VARROCK_START, moves, MovementQueue.StepType.NORMAL, runToggle = true)
        assertEquals(0, run.teleports, run.summary())
        assertTrue(run.maxGap < Familiar.FOLLOW_RECOVERY_DISTANCE, "max gap ${run.maxGap}\n" + run.summary())
        assertTrue(run.finalGap <= 1, run.summary())
    }

    /**
     * Live log 2026-10-03 20:56:40-20:57:47: the owner walked the Edgeville side of the Grand Exchange wall (3128,3516 -> 3135,3517
     * -> 3136,3513) and his War tortoise followed without a single no-route trace. Every familiar size, walking and running.
     */
    @Test
    fun `familiars of every size follow the owner's walk by the grand exchange wall without teleporting`() {
        val largest = SummoningPouchData.values.map { it.npc }.maxByOrNull { sizeOf(it) }!!
        val bad = mutableListOf<String>()
        val moves = walk(EDGEVILLE_SIDE_START, Tile(3135, 3517), Tile(3136, 3513), Tile(3128, 3516))
        assertTrue(moves.size >= 12, "the owner's walk is real: ${moves.size} steps")
        for (id in listOf(Npcs.BEAVER, Npcs.WAR_TORTOISE, Npcs.STEEL_TITAN, largest).distinct()) {
            for (type in listOf(MovementQueue.StepType.FORCED_WALK, MovementQueue.StepType.FORCED_RUN)) {
                val run = simulate(id, EDGEVILLE_SIDE_START, moves, type)
                if (run.teleports > 0 || run.runStepsWhileOwnerWalks > 0 || run.finalGap > 1) {
                    bad += "npc $id (size ${sizeOf(id)}) $type ${run.summary()}"
                }
            }
        }
        assertTrue(bad.isEmpty(), bad.joinToString("\n\n"))
    }

    /**
     * Live log 2026-10-03 20:57:47-20:58:28: the owner crawled through the Grand Exchange underwall tunnel (9311: moveTo 3138,3516 ->
     * 3139,3516 -> 3143,3514 -> 3144,3514, ShortcutSupport) and stayed by the wall. Donor rule (Void `Follow.tick`, Novite 667
     * `Familiar.processNPC`): the familiar left on the other side is not teleported while it is within 15 tiles - no "every second"
     * teleport, no sound; RS gives "Call Follower" for this - and it does not wander off along the wall while it waits (its body never
     * gets further from its standing owner). When the owner crawls back it simply follows again.
     */
    @Test
    fun `a familiar left behind a wall by a shortcut waits without teleporting and follows again`() {
        for (id in listOf(Npcs.WAR_TORTOISE, Npcs.STEEL_TITAN, Npcs.BEAVER)) {
            val moves = mutableListOf<Move>()
            moves += walk(Tile(3136, 3513), Tile(3138, 3516))
            moves += Move.Jump(Tile(3139, 3516))
            moves += Move.Busy
            moves += Move.Jump(Tile(3143, 3514))
            repeat(4) { moves += Move.Busy }
            moves += Move.Jump(Tile(3144, 3514))
            val waitFrom = moves.size + 3
            repeat(12) { moves += Move.Busy }
            val waitTo = moves.size
            moves += Move.Jump(Tile(3143, 3514))
            repeat(2) { moves += Move.Busy }
            moves += Move.Jump(Tile(3139, 3516))
            moves += Move.Busy
            moves += Move.Jump(Tile(3138, 3516))
            moves += walk(Tile(3138, 3516), Tile(3136, 3513))
            val run = simulate(id, Tile(3136, 3513), moves, MovementQueue.StepType.FORCED_WALK, idleTicksAtEnd = 15)
            assertEquals(0, run.teleports, "npc $id: no teleport within 15 tiles\n" + run.summary())
            val waiting = run.gaps.subList(waitFrom, waitTo)
            assertTrue(waiting.zipWithNext().all { (a, b) -> b <= a }, "npc $id: never walks away from its waiting owner $waiting\n" + run.summary())
            assertTrue(run.finalGap <= 1, "npc $id: follows again once the owner is back\n" + run.summary())
            assertEquals(0, run.runStepsWhileOwnerWalks, "npc $id\n" + run.summary())
        }
    }

    companion object {
        private val VARROCK_START = Tile(3218, 3428)
        private val VARROCK = arrayOf(Tile(3218, 3415), Tile(3232, 3418), Tile(3236, 3432), Tile(3210, 3437))
        private val EDGEVILLE_SIDE_START = Tile(3128, 3516)

        private fun ensureBlocks() {
            val blocks = RealMapWorld.world.npcUpdateBlocks
            if (blocks.updateBlocks.isEmpty()) {
                val properties = ServerProperties().loadYaml(Paths.get("..", "..", "data", "blocks.yml").toFile())
                blocks.load(properties.extract("npcs"))
            }
        }
    }
}
