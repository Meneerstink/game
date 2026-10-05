package gg.rsmod.plugins.content.skills.summoning

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.MovementQueue
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.collision.CollisionFlag
import gg.rsmod.game.model.collision.CollisionManager
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.path.PathRequest
import gg.rsmod.game.model.path.strategy.BFSPathFindingStrategy
import gg.rsmod.game.model.region.ChunkSet
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.nio.file.Paths
import java.util.ArrayDeque
import java.util.Queue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Phase L - familiar following, pathing and collision (owner requirement H16).
 *
 * The owner's retests have twice reported familiars walking through scenery and standing inside
 * their owner, against a subsystem whose routing already used a real breadth-first search with
 * real collision. These tests exist because "the pathfinder respects collision" was never the
 * property that needed proving - what mattered was whether the code that *executes* a route
 * respects it too, and whether a familiar that is already mispositioned ever tries to correct
 * itself.
 *
 * Both are tested here by moving a real [Npc] through a real [CollisionManager] and asserting the
 * tile it ends up on, rather than by asserting that a strategy was constructed.
 */
class FamiliarPathingTests {
    /**
     * The heart of the noclip fault. A wall on the north edge of the *northern* tile of a size-2
     * npc blocks nothing on its south-west corner tile, so a corner-only collision test lets the
     * whole body walk straight through it.
     *
     * 43 of the 78 familiars in this cache are `size=2`, so this was the majority case.
     */
    @Test
    fun `a size-2 npc cannot step through a wall that only its far tile touches`() {
        val world = newWorld()
        val origin = Tile(3200, 3200, 0)
        val npc = newNpc(world, LARGE_FAMILIAR, origin)

        // Wall on the north side of the npc's north-west tile only. Its south-west corner tile,
        // the one the old check looked at, is completely unobstructed.
        block(world, origin.transform(0, 1), Direction.NORTH)

        npc.walkPath(pathOf(origin.transform(0, 1)), MovementQueue.StepType.FORCED_WALK, detectCollision = true)
        npc.movementQueue.cycle()

        assertEquals(origin, npc.tile, "a size-2 npc walked through a wall its far tile touches")
    }

    /** The same fault on the second, running step - a familiar mirrors a running owner. */
    @Test
    fun `a running size-2 npc cannot run through a wall that only its far tile touches`() {
        val world = newWorld()
        val origin = Tile(3200, 3200, 0)
        val npc = newNpc(world, LARGE_FAMILIAR, origin)

        // The first step north is clear; the wall sits one tile further on, on the far tile of
        // where the npc will be standing after that first step.
        block(world, origin.transform(0, 2), Direction.NORTH)

        npc.walkPath(
            pathOf(origin.transform(0, 1), origin.transform(0, 2)),
            MovementQueue.StepType.FORCED_RUN,
            detectCollision = true,
        )
        npc.movementQueue.cycle()

        assertEquals(
            origin.transform(0, 1),
            npc.tile,
            "a size-2 npc ran through a wall on the second step of a running cycle",
        )
    }

    /**
     * Owner 2026-09-27 "breachmonsters kunnen allemaal noclippen": solid scenery (a tree, rock or table) flags only its own
     * tile, so the tile being left never says the step is blocked. The mover has to ask the tile being entered too, for a
     * 1x1 and for a large body whose leading edge meets the object.
     */
    @Test
    fun `no npc steps into solid scenery that flags only its own tile`() {
        for (familiar in listOf(SMALL_FAMILIAR, LARGE_FAMILIAR)) {
            val world = newWorld()
            val origin = Tile(3200, 3200, 0)
            val npc = newNpc(world, familiar, origin)
            val size = npc.getSize().coerceAtLeast(1)
            // A rock right in front of the npc's north edge, on its north-east-most column.
            val rock = origin.transform(size - 1, size)
            Direction.NESW.forEach { block(world, rock, it) }

            npc.walkPath(pathOf(origin.transform(0, 1)), MovementQueue.StepType.FORCED_WALK, detectCollision = true)
            npc.movementQueue.cycle()

            assertEquals(origin, npc.tile, "a size-$size npc stepped into a solid object at $rock")
        }
    }

    /** A 1x1 npc must still be able to walk where nothing blocks it - the fix is not a freeze. */
    @Test
    fun `a size-1 npc still walks freely through open ground`() {
        val world = newWorld()
        val origin = Tile(3200, 3200, 0)
        val npc = newNpc(world, SMALL_FAMILIAR, origin)

        npc.walkPath(pathOf(origin.transform(0, 1)), MovementQueue.StepType.FORCED_WALK, detectCollision = true)
        npc.movementQueue.cycle()

        assertEquals(origin.transform(0, 1), npc.tile)
    }

    /** ...and neither is it a freeze for a large one on open ground. */
    @Test
    fun `a size-2 npc still walks freely through open ground`() {
        val world = newWorld()
        val origin = Tile(3200, 3200, 0)
        val npc = newNpc(world, LARGE_FAMILIAR, origin)

        npc.walkPath(pathOf(origin.transform(0, 1)), MovementQueue.StepType.FORCED_WALK, detectCollision = true)
        npc.movementQueue.cycle()

        assertEquals(origin.transform(0, 1), npc.tile)
    }

    /**
     * The wall that stops a size-2 npc must not stop a size-1 one standing on the clear tile -
     * otherwise the footprint loop would just be a blanket "everything is blocked".
     */
    @Test
    fun `the same wall does not block a size-1 npc on the unobstructed tile`() {
        val world = newWorld()
        val origin = Tile(3200, 3200, 0)
        val npc = newNpc(world, SMALL_FAMILIAR, origin)

        block(world, origin.transform(0, 1), Direction.NORTH)

        npc.walkPath(pathOf(origin.transform(0, 1)), MovementQueue.StepType.FORCED_WALK, detectCollision = true)
        npc.movementQueue.cycle()

        assertEquals(origin.transform(0, 1), npc.tile, "a 1x1 npc was blocked by a wall it does not touch")
    }

    // --- BFS route planning for multi-tile bodies (the real cause behind the reported freeze) -

    /**
     * This is what actually froze a Pack yak (size 2) in the live client: walked one tile from
     * Lumbridge's fountain, it stopped dead in fully open ground and never moved again - every one
     * of the 8 directions came back blocked, from a tile whose own collision flags were empty.
     *
     * [BFSPathFindingStrategy.isStepBlocked] used to compare every footprint tile of the *source*
     * against the single-tile step destination via `Direction.between(transform, link)`, a
     * direction derived purely from the sign of the coordinate delta, with no adjacency check. For
     * a size-2 body's leading corner in the direction of travel, `transform` and `link` are the
     * *same tile* - `Direction.between` has no "identical tile" case, so it falls through to its
     * final branch and silently returns `NORTH`. A real wall on the north side of that one corner
     * tile - ordinary, unremarkable collision data - then vetoed every direction whose leading
     * corner happened to be that tile, including directions that have nothing to do with north at
     * all.
     *
     * Here that wall sits on the north side of the body's south-east corner - the same shape as a
     * short wall stub or a building corner would leave. East is the direction whose leading corner
     * is exactly that tile, so the old code asked "can this tile go north?" instead of "can this
     * tile go east?" and wrongly answered no.
     */
    @Test
    fun `a size-2 npc is not blocked eastward by an unrelated north wall on its leading corner`() {
        val world = newWorld()
        val origin = Tile(3200, 3200, 0)

        // A three-sided room open only to the east, so there is exactly one possible route out -
        // a real BFS is otherwise perfectly capable of routing around a single bad direction, which
        // would make this assertion pass even against the old, broken comparison.
        listOf(0, 1).forEach { x -> block(world, origin.transform(x, 1), Direction.NORTH) }
        listOf(0, 1).forEach { x -> block(world, origin.transform(x, 0), Direction.SOUTH) }
        listOf(0, 1).forEach { z -> block(world, origin.transform(0, z), Direction.WEST) }

        // The unrelated wall this test is really about: a stub on the north side of the body's own
        // south-east corner, the same shape a short wall or a building corner would leave. It has
        // nothing to do with the room's one real exit, which is due east.
        val southEastCorner = origin.transform(1, 0)
        block(world, southEastCorner, Direction.NORTH)

        val east =
            BFSPathFindingStrategy(world.collision).calculateRoute(
                requestFor(origin, size = 2, target = origin.transform(3, 0)),
            )
        assertTrue(
            east.success,
            "a size-2 npc could not step out of its room's one real exit, blocked by an unrelated " +
                "north-facing wall on its own leading corner: $east",
        )
    }

    /**
     * A control against the fix having gone too far and stopped [BFSPathFindingStrategy] from
     * clipping anything at all: a body walled in on all four sides has no legal first step in any
     * direction, so no route can leave, however far the search is allowed to look.
     */
    @Test
    fun `a fully walled-in size-2 npc still finds no route out`() {
        val world = newWorld()
        val origin = Tile(3200, 3200, 0)
        listOf(0, 1).forEach { x ->
            block(world, origin.transform(x, 1), Direction.NORTH)
            block(world, origin.transform(x, 0), Direction.SOUTH)
        }
        listOf(0, 1).forEach { z ->
            block(world, origin.transform(1, z), Direction.EAST)
            block(world, origin.transform(0, z), Direction.WEST)
        }

        val route =
            BFSPathFindingStrategy(world.collision).calculateRoute(
                requestFor(origin, size = 2, target = origin.transform(5, 0)),
            )
        assertFalse(route.success, "a size-2 npc walled in on all four sides still found a route out: $route")
    }

    /** Builds the exact [PathRequest] shape [Familiar.follow] uses, for a direct BFS assertion. */
    /**
     * Owner 2026-09-30: with walls or scenery around the owner a familiar teleported again and again (blue GFX and teleport
     * sound every cycle). The placement is decided before anything is committed: no tile the familiar can stay on = no
     * teleport and no effect, however many cycles pass; one free follow slot = exactly one teleport, and none after it.
     */
    @Test
    fun `a recovery without a free follow slot never teleports, a free slot teleports once`() {
        for (familiar in listOf(SMALL_FAMILIAR, LARGE_FAMILIAR)) {
            val world = newWorld()
            val owner = Tile(3200, 3200, 1)
            val size = sizeOf(familiar)
            // Every tile around the owner that any footprint could use is solid scenery.
            for (dx in -size - 1..size + 1) {
                for (dz in -size - 1..size + 1) {
                    if (dx == 0 && dz == 0) continue
                    Direction.NESW.forEach { block(world, owner.transform(dx, dz), it) }
                }
            }
            val player = io.mockk.mockk<gg.rsmod.game.model.entity.Player>(relaxed = true)
            every { player.tile } answers { owner }
            every { player.getSize() } returns 1
            every { player.world } returns world
            every { player.username } returns "tester"
            // Owner went upstairs: the familiar on the ground floor must follow (plane recovery).
            val start = Tile(3200, 3200, 0)
            val npc = newNpc(world, familiar, start)
            assertEquals(null, Familiar.recoveryPlacement(player, npc), "size-$size: a walled-in owner has no placement")
            var teleports = 0
            repeat(20) { Familiar.follow(player, npc) { _, _ -> teleports++ } }
            assertEquals(0, teleports, "size-$size: no free tile must never produce a teleport / arrival effect")
            assertEquals(start, npc.tile, "size-$size: the familiar stays where it was")

            // Free one side: the familiar arrives there once and is then in its follow slot (no second teleport).
            for (dx in 1..size) {
                for (dz in 0 until size) {
                    val tile = owner.transform(dx, dz)
                    world.chunks.get(tile, createIfNeeded = true)!!.getMatrix(tile.height).reset(tile.x % CHUNK_SIZE, tile.z % CHUNK_SIZE)
                }
            }
            val placed = Familiar.recoveryPlacement(player, npc)
            assertTrue(placed != null && Familiar.isInFollowSlot(placed, size, owner, 1), "size-$size: the freed side is a follow slot ($placed)")
            Familiar.follow(player, npc) { _, _ -> teleports++ }
            assertEquals(1, teleports, "size-$size: one teleport onto the free slot")
            assertEquals(placed, npc.tile)
        }
    }

    /**
     * B1/A4 (owner: a loud hit / the call sound on every familiar teleport; log 10-02: sound 188 six times in 10 s). The automatic
     * follow recovery is silent - Novite 667 `call(false)` shows only the 1314/1315 graphic, Void `Follow.tick` nothing; only the
     * player's own summon / "Call follower" plays the arrival cue. Positive control: the same matcher sees a real familiar sound.
     */
    @Test
    fun `an automatic recovery teleport plays no sound`() {
        val world = newWorld()
        val owner = Tile(3200, 3200, 1)
        val player = io.mockk.mockk<gg.rsmod.game.model.entity.Player>(relaxed = true)
        every { player.tile } answers { owner }
        every { player.getSize() } returns 1
        every { player.world } returns world
        every { player.username } returns "tester"
        val npc = newNpc(world, LARGE_FAMILIAR, Tile(3200, 3200, 0))
        Familiar.follow(player, npc, runs = true)
        assertEquals(1, npc.tile.height, "the plane recovery really teleported the familiar")
        io.mockk.verify(exactly = 0) { world.spawn(any<gg.rsmod.game.model.entity.AreaSound>()) }
        FamiliarAudio.play(player, Familiar.ARRIVAL_SOUND, at = owner)
        io.mockk.verify(exactly = 1) { world.spawn(any<gg.rsmod.game.model.entity.AreaSound>()) }
    }

    @Test
    fun `footprint gap measures body to body`() {
        assertEquals(1, Familiar.footprintGap(Tile(3201, 3200), 2, Tile(3200, 3200), 1))
        assertEquals(0, Familiar.footprintGap(Tile(3199, 3199), 2, Tile(3200, 3200), 1))
        assertEquals(4, Familiar.footprintGap(Tile(3196, 3200), 1, Tile(3200, 3201), 2))
    }

    /**
     * E18, donor rule (Void `Follow.tick`, Novite 667 `Familiar.processNPC`/`sendFollow`): a follower its owner can not be reached
     * from - here a wall longer than the 20-tile search - is never teleported while it is within 15 tiles (RS: "Call Follower"),
     * and it does not wander off along the wall either: it waits at the wall, as close to its owner as it can get.
     */
    @Test
    fun `a familiar walled off within 15 tiles waits at the wall and never teleports`() {
        for (familiar in listOf(SMALL_FAMILIAR, LARGE_FAMILIAR)) {
            val world = newWorld()
            // A wall between z=3202 and z=3203 from x=3170 to x=3230: beyond the search's 20 tiles either way.
            for (x in 3170..3230) {
                block(world, Tile(x, 3202, 0), Direction.NORTH)
                block(world, Tile(x, 3203, 0), Direction.SOUTH)
            }
            val owner = Tile(3200, 3200, 0)
            val player = io.mockk.mockk<gg.rsmod.game.model.entity.Player>(relaxed = true)
            every { player.tile } answers { owner }
            every { player.getSize() } returns 1
            every { player.world } returns world
            every { player.username } returns "tester"
            val npc = newNpc(world, familiar, Tile(3203, 3208, 0))
            npc.ignoresEntityCollision = true
            val startGap = Familiar.footprintGap(npc.tile, npc.getSize(), owner, 1)
            var teleports = 0
            var worstGap = startGap
            repeat(30) {
                Familiar.follow(player, npc, runs = true) { _, _ -> teleports++ }
                npc.movementQueue.cycle()
                worstGap = maxOf(worstGap, Familiar.footprintGap(npc.tile, npc.getSize(), owner, 1))
            }
            val size = sizeOf(familiar)
            assertEquals(0, teleports, "size-$size: no teleport within 15 tiles")
            assertEquals(startGap, worstGap, "size-$size: never further from its owner than it started (${npc.tile})")
            assertEquals(3203, npc.tile.z, "size-$size: waits right at the wall (${npc.tile})")
            assertEquals(3, Familiar.footprintGap(npc.tile, npc.getSize(), owner, 1), "size-$size: as close as the wall allows (${npc.tile})")
        }
    }

    /**
     * F6 / E18 on open ground: a familiar of each size follows an owner who walks (never runs then) and one who runs (keeps up),
     * never more than 2 tiles from its owner's body and never teleported.
     */
    @Test
    fun `on open ground a familiar walks with a walking owner and keeps up with a running one`() {
        for (familiar in listOf(SMALL_FAMILIAR, LARGE_FAMILIAR)) {
            for (running in listOf(false, true)) {
                val world = newWorld()
                var owner = Tile(3200, 3200, 0)
                val pending = java.util.ArrayDeque<Tile>()
                (1..24).forEach { pending.add(Tile(3200 + it, 3200, 0)) }
                (1..10).forEach { pending.add(Tile(3224, 3200 + it, 0)) }
                (1..8).forEach { pending.add(Tile(3224 - it, 3210 + it, 0)) }
                val type = if (running) MovementQueue.StepType.FORCED_RUN else MovementQueue.StepType.FORCED_WALK
                val queue = io.mockk.mockk<MovementQueue>(relaxed = true)
                every { queue.peekSteps(any()) } answers { pending.take(firstArg<Int>()).map { MovementQueue.Step(it, type, true) } }
                val player = io.mockk.mockk<gg.rsmod.game.model.entity.Player>(relaxed = true)
                every { player.tile } answers { owner }
                every { player.getSize() } returns 1
                every { player.world } returns world
                every { player.username } returns "tester"
                every { player.movementQueue } returns queue
                every { player.isRunning() } returns running
                every { player.isResting() } returns false
                every { player.runEnergy } returns 100.0
                val npc = newNpc(world, familiar, Familiar.placementTile(player, familiar))
                npc.ignoresEntityCollision = true
                var teleports = 0
                var ranWhileWalking = 0
                var worstGap = 0
                val log = StringBuilder()
                repeat(pending.size + 6) { tick ->
                    Familiar.follow(player, npc, runs = true) { _, _ -> teleports++ }
                    repeat(if (running) 2 else 1) { pending.pollFirst()?.let { owner = it } }
                    val before = npc.tile
                    npc.movementQueue.cycle()
                    if (!running && maxOf(Math.abs(npc.tile.x - before.x), Math.abs(npc.tile.z - before.z)) > 1) ranWhileWalking++
                    val gap = Familiar.footprintGap(npc.tile, npc.getSize(), owner, 1)
                    worstGap = maxOf(worstGap, gap)
                    log.append("t$tick owner=${owner.x},${owner.z} npc=${npc.tile.x},${npc.tile.z} gap=$gap\n")
                }
                val what = "size-${sizeOf(familiar)} running=$running\n$log"
                assertEquals(0, teleports, what)
                assertEquals(0, ranWhileWalking, what)
                // Darkan EntityFollow (npcs move before players, route to the owner's start-of-tick tile): one empty tile behind a
                // walking owner, two behind a running one - a constant gap, never growing.
                assertTrue(worstGap <= if (running) 3 else 2, "worst gap $worstGap $what")
                assertTrue(Familiar.footprintGap(npc.tile, npc.getSize(), owner, 1) <= 1, what)
            }
        }
    }

    private fun requestFor(
        start: Tile,
        size: Int,
        target: Tile,
    ): PathRequest =
        PathRequest.Builder()
            .setPoints(start, target)
            .setSourceSize(size, size)
            .setTargetSize(1, 1)
            .setTouchRadius(1)
            .clipPathNodes(node = true, link = true)
            .clipOverlapTiles()
            .build()

    // --- the follow slot, all 78 -------------------------------------------------------------

    /**
     * For every one of the 78 canonical familiars, at its real size from the production cache:
     * no placement whose body covers the owner may count as "already following". This is the
     * predicate that decides whether [Familiar.follow] bothers to route at all, so a familiar for
     * which it wrongly returns true is a familiar that stays embedded in its owner forever.
     */
    @Test
    fun `no familiar counts as in position while overlapping its owner, all 78`() {
        val owner = Tile(3222, 3218, 0)
        SummoningPouchData.values().forEach { pouch ->
            val size = sizeOf(pouch.npc)
            // Every south-west corner from which a familiar of this size covers the owner's tile.
            for (dx in -(size - 1)..0) {
                for (dz in -(size - 1)..0) {
                    val corner = owner.transform(dx, dz)
                    assertFalse(
                        Familiar.isInFollowSlot(corner, size, owner, ownerSize = 1),
                        "${pouch.name} (npc ${pouch.npc}, size $size) counts as in position at $corner, " +
                            "which covers the owner tile $owner",
                    )
                }
            }
        }
    }

    /**
     * The other half: a familiar standing correctly beside its owner must count as in position,
     * or it would re-route every single cycle and jitter. Asserted for all 78 at their real size,
     * from a corner that is guaranteed clear of the owner for any size.
     */
    @Test
    fun `every familiar counts as in position when placed beside its owner, all 78`() {
        val owner = Tile(3222, 3218, 0)
        SummoningPouchData.values().forEach { pouch ->
            val size = sizeOf(pouch.npc)
            // Directly east of the owner: the whole footprint starts one tile past them.
            val beside = owner.transform(1, 0)
            assertTrue(
                Familiar.isInFollowSlot(beside, size, owner, ownerSize = 1),
                "${pouch.name} (npc ${pouch.npc}, size $size) does not count as in position at $beside, " +
                    "immediately east of its owner at $owner",
            )
        }
    }

    /** A familiar far away from its owner is never in position, at any size. */
    @Test
    fun `no familiar counts as in position when it is not touching its owner, all 78`() {
        val owner = Tile(3222, 3218, 0)
        SummoningPouchData.values().forEach { pouch ->
            val size = sizeOf(pouch.npc)
            val away = owner.transform(size + 2, 0)
            assertFalse(
                Familiar.isInFollowSlot(away, size, owner, ownerSize = 1),
                "${pouch.name} (npc ${pouch.npc}, size $size) counts as in position at $away, " +
                    "which does not touch its owner at $owner",
            )
        }
    }

    /**
     * Every canonical familiar resolves a real footprint out of the production cache. A familiar
     * whose size failed to decode would silently fall back to 1x1 and reintroduce exactly the
     * clipping these tests exist to prevent, so the roster is pinned rather than assumed.
     */
    @Test
    fun `every familiar resolves a real size from the production cache, all 78`() {
        SummoningPouchData.values().forEach { pouch ->
            val size = DEFINITIONS.get(NpcDef::class.java, pouch.npc).size
            assertTrue(
                size >= 1,
                "${pouch.name} (npc ${pouch.npc}) has no usable footprint in the cache: size=$size",
            )
        }
    }

    /**
     * The two npcs the movement tests above use are only meaningful if the cache really gives
     * them the sizes those tests assume - a wrong constant here would turn a noclip test into a
     * test of nothing, which is exactly what happened while this file was being written.
     */
    @Test
    fun `the movement tests use a genuinely size-1 and a genuinely size-2 familiar`() {
        assertEquals(1, sizeOf(SMALL_FAMILIAR), "the small-familiar constant is not size 1 in this cache")
        assertEquals(2, sizeOf(LARGE_FAMILIAR), "the large-familiar constant is not size 2 in this cache")
    }

    /**
     * Owner 2026-10-04 (bug 155035, priority 2): a pack yak walked straight through solid room walls in the POH. Every house entry
     * is a teleport, and the placement only asked whether each footprint tile was clipped, so a 2x2 body was put straddling a room
     * wall. Darkan World.findAdjacentFreeSpace places it only where the whole body can step from the owner's tile.
     */
    @Test
    fun `a big familiar is never placed straddling a wall or behind one`() {
        val world = newWorld()
        val owner = Tile(3200, 3200, 0)
        val player = io.mockk.mockk<gg.rsmod.game.model.entity.Player>(relaxed = true)
        every { player.tile } answers { owner }
        every { player.getSize() } returns 1
        every { player.world } returns world
        fun wallX(x: Int) = (3190..3210).forEach { z -> block(world, Tile(x, z, 0), Direction.EAST); block(world, Tile(x + 1, z, 0), Direction.WEST) }
        fun wallZ(z: Int) = (3190..3210).forEach { x -> block(world, Tile(x, z, 0), Direction.NORTH); block(world, Tile(x, z + 1, 0), Direction.SOUTH) }
        // A wall through the middle of each side's 2x2 body (west 3198-3199, east 3201-3202, south 3198-3199, north 3201-3202).
        wallX(3198); wallX(3201); wallZ(3198); wallZ(3201)
        repeat(20) { assertEquals(owner, Familiar.placementTile(player, LARGE_FAMILIAR), "every side straddles a wall: last resort only") }

        val open = newWorld()
        every { player.world } returns open
        // A wall right between the owner and his west side: that side is behind a wall, the others are open.
        (3190..3210).forEach { z -> block(open, Tile(3199, z, 0), Direction.EAST); block(open, Tile(3200, z, 0), Direction.WEST) }
        repeat(20) {
            val placed = Familiar.placementTile(player, LARGE_FAMILIAR)
            assertTrue(placed != Tile(3198, 3200, 0) && placed != owner, "never behind the wall ($placed)")
            assertTrue(gg.rsmod.game.model.path.MeleeReach.bordersWithoutWall(open.collision, placed, 2, 2, owner, 1, 1), "$placed")
        }
        assertTrue(!gg.rsmod.game.model.path.MeleeReach.bordersWithoutWall(open.collision, Tile(3198, 3200, 0), 2, 2, owner, 1, 1), "a wall in between is not 'beside'")
    }

    /** Owner 2026-10-04 (bug 154831): familiars faced where the player looked. Darkan EntityFollow keeps faceEntity(owner) while following. */
    @Test
    fun `a following familiar holds its owner as face target`() {
        val world = newWorld()
        val owner = Tile(3200, 3200, 0)
        val player = io.mockk.mockk<gg.rsmod.game.model.entity.Player>(relaxed = true)
        every { player.tile } answers { owner }
        every { player.getSize() } returns 1
        every { player.world } returns world
        val npc = newNpc(world, LARGE_FAMILIAR, Tile(3195, 3200, 0))
        Familiar.follow(player, npc, runs = true)
        assertEquals(true, npc.attr[gg.rsmod.game.model.attr.HOLD_FACING_ATTR], "Npc.cycle must not drop the owner face target at range")
    }

    // --- harness ------------------------------------------------------------------------------

    private fun sizeOf(npcId: Int): Int = DEFINITIONS.get(NpcDef::class.java, npcId).size.coerceAtLeast(1)

    private fun pathOf(vararg tiles: Tile): Queue<Tile> = ArrayDeque(tiles.toList())

    /**
     * A world with a genuine [ChunkSet] and [CollisionManager] over it, so collision flags set by
     * a test are the same flags the movement code reads.
     *
     * The [DefinitionSet] is a mock that forwards only npc lookups to the real cache. That split
     * is deliberate: the footprint under test has to be the familiar's genuine cache size, but
     * [ChunkSet.get] asks the definitions to build the region the first time a chunk is created,
     * and a real region would drop its own map collision into the very chunks the test is trying
     * to control. Forwarding one lookup keeps the data real and the terrain empty.
     */
    private fun newWorld(): World {
        val world = mockk<World>(relaxed = true)
        // Real npc update-block table: a relaxed mock returns Objects that break Npc.addBlock.
        every { world.npcUpdateBlocks } returns SummoningTestCache.npcUpdateBlocks
        val definitions = mockk<DefinitionSet>(relaxed = true)
        every { definitions.get(NpcDef::class.java, any()) } answers {
            DEFINITIONS.get(NpcDef::class.java, secondArg())
        }
        every { world.definitions } returns definitions
        val chunks = ChunkSet(world)
        every { world.chunks } returns chunks
        every { world.collision } returns CollisionManager(chunks)
        return world
    }

    private fun newNpc(
        world: World,
        npcId: Int,
        tile: Tile,
    ): Npc = Npc(npcId, tile, world)

    private fun block(
        world: World,
        tile: Tile,
        direction: Direction,
    ) {
        val flag =
            when (direction) {
                Direction.NORTH -> CollisionFlag.PAWN_NORTH
                Direction.SOUTH -> CollisionFlag.PAWN_SOUTH
                Direction.EAST -> CollisionFlag.PAWN_EAST
                Direction.WEST -> CollisionFlag.PAWN_WEST
                else -> throw IllegalArgumentException("only cardinal walls are used here: $direction")
            }
        world.chunks
            .get(tile, createIfNeeded = true)!!
            .getMatrix(tile.height)
            .addFlag(tile.x % CHUNK_SIZE, tile.z % CHUNK_SIZE, flag)
    }

    companion object {
        private const val CHUNK_SIZE = 8

        /** Beaver - one of the 35 familiars this cache gives `size=1`. */
        private const val SMALL_FAMILIAR = 6808

        /** Pack yak - `size=2` in this cache, and one of the 43 familiars that are. */
        private const val LARGE_FAMILIAR = 6873

        private val DEFINITIONS = DefinitionSet()
        private lateinit var store: CacheLibrary

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            store = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            DEFINITIONS.loadAll(store)
            assertNotEquals(DEFINITIONS.getCount(ItemDef::class.java), 0)
        }
    }
}
