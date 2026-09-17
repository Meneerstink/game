package gg.rsmod.plugins.content.mechanics.stairs

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
import kotlin.test.assertNull

class GenericStairsTests {
    @Test
    fun `an exact opposite staircase proves the adjacent-floor destination`() {
        val up = StaticObject(1742, 10, 0, Tile(3200, 3200, 0))
        val down = StaticObject(1744, 10, 0, Tile(3200, 3200, 1))
        val world = worldWith(up, down)

        assertEquals(
            Tile(3201, 3200, 1),
            GenericStairs.destination(world, up, Tile(3201, 3200, 0), GenericStairs.Direction.UP),
        )
    }

    @Test
    fun `a nearby staircase without an exact counterpart is rejected`() {
        val up = StaticObject(1742, 10, 0, Tile(3200, 3200, 0))
        val down = StaticObject(1744, 10, 0, Tile(3201, 3200, 1))
        val world = worldWith(up, down)

        assertNull(GenericStairs.destination(world, up, Tile(3200, 3201, 0), GenericStairs.Direction.UP))
    }

    private fun worldWith(vararg objects: GameObject): World {
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
        every { collision.isClipped(any()) } returns false
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
        }
    }
}
