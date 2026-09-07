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
