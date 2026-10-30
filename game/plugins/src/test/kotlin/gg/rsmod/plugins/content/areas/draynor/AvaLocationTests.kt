package gg.rsmod.plugins.content.areas.draynor

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.collision.CollisionManager
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.region.ChunkSet
import gg.rsmod.game.service.xtea.XteaKeyService
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.util.ArrayDeque
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** N461: read the actual 667 landscape and collision; no game server or client is started. */
class AvaLocationTests {
    private val sourceRoot = File("src/main/kotlin/gg/rsmod/plugins/content")

    @Test
    fun `Ava has exactly one spawn at the owner chosen upper laboratory and the orb directions follow it`() {
        val spawns = sourceRoot.resolve("areas/spawns").walkTopDown().filter { it.extension == "kts" }
            .flatMap { it.readLines().asSequence() }.filter { "spawn_npc(npc = Npcs.AVA," in it }.toList()
        assertEquals(1, spawns.size)
        assertTrue("x = 3109, z = 3367, height = 2" in spawns.single())
        assertFalse("walkRadius" in spawns.single(), "stay on the verified lab tile")
        assertTrue("top floor of Draynor Manor" in sourceRoot.resolve("items/osrs/LocatorOrb.kt").readText())
    }

    @Test
    fun `667 manor stairs and ladder reach the upper floor and the lab tile is walkable through its open door`() {
        val defs = AvaShardRuntimeTests.world.definitions
        val world = mockk<World>(relaxed = true)
        val chunks = ChunkSet(world)
        // Scenery on a map-square edge updates adjacent chunks. This test only loads the Manor square.
        val active = ChunkSet::class.java.getDeclaredField("activeRegions").apply { isAccessible = true }
            .get(chunks) as it.unimi.dsi.fastutil.ints.IntOpenHashSet
        for (x in 47..49) for (z in 51..53) if (x != 48 || z != 52) active.add((x shl 8) or z)
        val collision = CollisionManager(chunks)
        val xtea = mockk<XteaKeyService>()
        every { xtea.get(12340) } returns IntArray(4) // This map square is unencrypted in the 667 cache.
        every { world.getService(XteaKeyService::class.java, any()) } returns xtea
        every { world.definitions } returns defs
        every { world.chunks } returns chunks
        every { world.collision } returns collision
        val cache = CacheLibrary(System.getenv("RSPS_TEST_CACHE") ?: "../../data/cache")
        try {
            every { world.filestore } returns cache
            chunks.getOrCreate(Tile(3105, 3363, 2))
            fun objectAt(id: Int, x: Int, z: Int, height: Int): GameObject {
                val tile = Tile(x, z, height)
                return chunks.getOrCreate(tile).getEntities<GameObject>(tile, EntityType.STATIC_OBJECT)
                    .first { it.id == id }
            }
            objectAt(47364, 3107, 3362, 0)
            objectAt(47657, 3108, 3364, 1)
            objectAt(47574, 3105, 3363, 1)
            objectAt(47575, 3105, 3363, 2)
            val route = sourceRoot.resolve("areas/draynor/draynor_manor.plugin.kts").readText()
            assertTrue("player.handleStairs(x, 3366, 1)" in route)
            assertTrue("player.handleStairs(x, 3361, 0)" in route)
            assertTrue("player.handleLadder(3105, 3362, 2)" in route)
            assertTrue("player.handleLadder(3105, 3364, 1)" in route)
            fun reachable(start: Tile, end: Tile): Boolean {
                val seen = hashSetOf(start)
                val todo = ArrayDeque<Tile>().apply { add(start) }
                while (todo.isNotEmpty()) {
                    val tile = todo.removeFirst()
                    if (tile == end) return true
                    Direction.NESW.forEach { dir ->
                        val next = tile.step(dir)
                        if (next.x in 3090..3125 && next.z in 3350..3375 && collision.canStep(tile, dir) && seen.add(next)) todo.add(next)
                    }
                }
                return false
            }
            assertTrue(reachable(Tile(3108, 3366, 1), Tile(3105, 3364, 1)), "stairs landing to ladder")
            val door = objectAt(47512, 3108, 3364, 2)
            assertTrue(defs.get(ObjectDef::class.java, door.id).options.any { it.equals("open", true) })
            // Model opening the existing lab door; client verification must still exercise the real door handler.
            chunks.getOrCreate(door.tile).removeEntity(world, door, door.tile)
            assertFalse(collision.isClipped(Tile(3109, 3367, 2)))
            assertTrue(reachable(Tile(3105, 3362, 2), Tile(3109, 3367, 2)), "upper ladder landing through lab doorway to Ava")
        } finally {
            cache.close()
        }
    }
}
