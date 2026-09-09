package gg.rsmod.plugins.content.mechanics.ladders

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.collision.CollisionManager
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.StaticObject
import gg.rsmod.game.model.region.Chunk
import gg.rsmod.game.model.region.ChunkSet
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [GenericLadders] against real ladder definitions (1747 "Climb-up", 1746 "Climb-down" - the same
 * pair the hand-written handlers use) on a mocked map, so the evidence rule can be pinned without
 * loading regions: a destination exists only where a counterpart ladder exists.
 */
class GenericLaddersTests {
    private val up = 1747
    private val down = 1746

    @Test
    fun `the real ladder pair advertises the expected directions`() {
        assertTrue(GenericLadders.isLadder(definitions.get(ObjectDef::class.java, up)))
        assertEquals(GenericLadders.Direction.UP, definitions.get(ObjectDef::class.java, up).options.firstNotNullOf { GenericLadders.optionDirection(it) })
        assertEquals(GenericLadders.Direction.DOWN, definitions.get(ObjectDef::class.java, down).options.firstNotNullOf { GenericLadders.optionDirection(it) })
    }

    @Test
    fun `climbing up lands on the same tile one floor higher when a climb-down ladder is there`() {
        val ladder = obj(up, Tile(3200, 3200, 0))
        val counterpart = obj(down, Tile(3200, 3200, 1))
        val world = worldWith(ladder, counterpart)

        val destination = GenericLadders.destination(world, ladder, from = Tile(3201, 3200, 0), direction = GenericLadders.Direction.UP)

        assertEquals(Tile(3201, 3200, 1), destination)
    }

    @Test
    fun `climbing down a surface ladder lands beside its mirrored dungeon ladder`() {
        val ladder = obj(down, Tile(2594, 3086, 0))
        val counterpart = obj(up, Tile(2594, 9486, 0)) // Wizards' Tower basement: z + 6400
        val world = worldWith(ladder, counterpart)

        val destination = GenericLadders.destination(world, ladder, from = Tile(2594, 3087, 0), direction = GenericLadders.Direction.DOWN)

        assertEquals(Tile(2594, 9487, 0), destination)
    }

    @Test
    fun `climbing up from a dungeon ladder mirrors back to the surface`() {
        val ladder = obj(up, Tile(2594, 9486, 0))
        val counterpart = obj(down, Tile(2594, 3086, 0))
        val world = worldWith(ladder, counterpart)

        val destination = GenericLadders.destination(world, ladder, from = Tile(2595, 9486, 0), direction = GenericLadders.Direction.UP)

        assertEquals(Tile(2595, 3086, 0), destination)
    }

    @Test
    fun `a ladder with no counterpart anywhere yields no destination`() {
        val ladder = obj(down, Tile(3000, 3000, 0))
        val world = worldWith(ladder)

        assertNull(GenericLadders.destination(world, ladder, from = Tile(3001, 3000, 0), direction = GenericLadders.Direction.DOWN))
    }

    @Test
    fun `a clipped landing tile falls back to a walkable tile beside the counterpart`() {
        val ladder = obj(up, Tile(3200, 3200, 0))
        val counterpart = obj(down, Tile(3200, 3200, 1))
        val world = worldWith(ladder, counterpart, clipped = setOf(Tile(3201, 3200, 1), Tile(3200, 3199, 1)))

        val destination = GenericLadders.destination(world, ladder, from = Tile(3201, 3200, 0), direction = GenericLadders.Direction.UP)

        assertNotEquals(Tile(3201, 3200, 1), destination)
        assertEquals(Tile(3200, 3201, 1), destination)
    }

    private fun obj(
        id: Int,
        tile: Tile,
    ): GameObject = StaticObject(id, 10, 0, tile)

    private fun worldWith(
        vararg objects: GameObject,
        clipped: Set<Tile> = emptySet(),
    ): World {
        val world = mockk<World>(relaxed = true)
        val chunks = mockk<ChunkSet>()
        val chunk = mockk<Chunk>()
        val collision = mockk<CollisionManager>()
        every { world.definitions } returns definitions
        every { world.chunks } returns chunks
        every { world.collision } returns collision
        every { chunks.get(any<Tile>(), any()) } returns chunk
        every { chunk.getEntities<GameObject>(any<Tile>(), *anyVararg<EntityType>()) } answers {
            val tile = firstArg<Tile>()
            objects.filter { it.tile == tile }
        }
        every { collision.isClipped(any()) } answers { firstArg<Tile>() in clipped }
        return world
    }

    companion object {
        private val definitions = DefinitionSet()

        private lateinit var store: CacheLibrary

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            store = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            definitions.loadAll(store)
            assertNotEquals(0, definitions.getCount(ObjectDef::class.java))
        }
    }
}
