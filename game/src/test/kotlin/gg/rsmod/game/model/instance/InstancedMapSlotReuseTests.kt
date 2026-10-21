package gg.rsmod.game.model.instance

import com.displee.cache.CacheLibrary
import com.google.gson.JsonParser
import gg.rsmod.game.DevContext
import gg.rsmod.game.GameContext
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.service.xtea.XteaKeyService
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * N122 (new player's intro froze in its first PK scene: "Deadman Pker ... no route", all three actors "cannot step in any
 * direction" at their start tiles). An instance's collision was written for `Chunk.CHUNKS_PER_REGION` (13 - the client's
 * 104-tile view) chunks per region instead of the 8 chunks a 64-tile region has, so every map also fully blocked a band of
 * chunks OUTSIDE its own area (a one-region house: 13x13 chunks, the two-region intro: 26x26) that its release never
 * removes. A house released in slot 6400,0 (the intro's own "your house" stop, or ::house) left the next intro's start tiles
 * solid on every plane. Darkan (InstanceBuilder.copyChunk/destroyMap) and Void (DynamicZones.copy/clear) touch only the
 * instance's own chunks and reset their collision before a copy.
 *
 * Class test: every map shape in use (the player house, the intro scene, a four-plane one-region map like the Fight Cave, a
 * three-region map), on the real cache. A map writes collision only inside its own area, leaves nothing behind when released,
 * and a reused slot gives the next map exactly the collision a fresh world gives it - in every order.
 */
class InstancedMapSlotReuseTests {
    private val cache = CacheLibrary(File("../data/cache").path, false, null)

    @AfterTest
    fun close() = cache.close()

    private enum class Shape(val bypass: Boolean) {
        /** PlayerHouse.build: 8x8 chunks on plane 0 only, the whitewashed-stone lawn and the five rooms (portal chamber turned 180). */
        HOUSE(true),

        /** IntroGuardScene.setUp: 16x16 chunks x 4 planes of the Edgeville wilderness (3072,3456). */
        INTRO(false),

        /** FightCaves-like: one region, every plane copied. */
        ONE_REGION_FOUR_PLANES(false),

        /** A three-region map (24x24 chunks, plane 0) - the largest shape the allocator's 64-step scan is asked for here. */
        THREE_REGIONS(false),
        ;

        fun chunks(): InstancedChunkSet {
            val b = InstancedChunkSet.Builder()
            when (this) {
                HOUSE -> {
                    val sx = 1856
                    val sz = 5056
                    val plane = 2
                    for (x in 0 until 8) for (z in 0 until 8) {
                        val dx = x - 1
                        val dz = z - 1
                        var rot = 0
                        val copy =
                            when {
                                dx == 0 && dz == 0 -> Tile(sx, sz + 8, plane)
                                dx == 0 && dz == 1 -> Tile(sx + 48, sz + 8, plane)
                                dx == 0 && dz == -1 -> Tile(sx + 8, sz + 32, plane).also { rot = 2 }
                                dx == 1 && dz == 0 -> Tile(sx + 16, sz + 40, plane)
                                dx == -1 && dz == 0 -> Tile(sx + 32, sz + 56, plane)
                                else -> Tile(sx + 8, sz, plane)
                            }
                        b.set(x, z, 0, rot, copy)
                    }
                }
                INTRO -> for (x in 0 until 16) for (z in 0 until 16) for (h in 0..3) b.set(x, z, h, 0, Tile(3072 + x * 8, 3456 + z * 8, h))
                ONE_REGION_FOUR_PLANES -> for (x in 0 until 8) for (z in 0 until 8) for (h in 0..3) b.set(x, z, h, 0, Tile(3072 + x * 8, 3456 + z * 8, h))
                THREE_REGIONS -> for (x in 0 until 24) for (z in 0 until 24) b.set(x, z, 0, 0, Tile(3072 + x * 8, 3392 + z * 8, 0))
            }
            return b.build()
        }

        fun config(): InstancedMapConfiguration =
            InstancedMapConfiguration.Builder().setExitTile(Tile(3162, 3489, 0)).setBypassObjectChunkBounds(bypass).build()
    }

    private fun world(): World {
        val context = GameContext(false, "N122 slot reuse", 667, 600, 8, Tile(3072, 3456, 0), 25, 10, false, 100, 100, false, false, 0)
        val world = World(context, DevContext(false, false, false, false, false, false, false))
        world.filestore = cache
        world.definitions.load(cache, ObjectDef::class.java, emptyList())
        // Real XTEA keys, so the copied locs (and their collision) are the ones the game server copies.
        val keys = XteaKeyService()
        val keyField = XteaKeyService::class.java.getDeclaredField("keys").apply { isAccessible = true }

        @Suppress("UNCHECKED_CAST")
        val keyMap = keyField.get(keys) as Int2ObjectOpenHashMap<IntArray>
        JsonParser().parse(File("../data/xteas/xteas.json").readText()).asJsonArray.forEach { entry ->
            val o = entry.asJsonObject
            keyMap.put(o.get("mapsquare").asInt, IntArray(4) { o.getAsJsonArray("key").get(it).asInt })
        }
        DefinitionSet::class.java.getDeclaredField("xteaService").apply { isAccessible = true }.set(world.definitions, keys)
        return world
    }

    /** Every collision word of [area] (all four planes); a missing chunk reads as 0, like a fresh one. */
    private fun snapshot(
        world: World,
        area: gg.rsmod.game.model.Area,
    ): IntArray {
        val w = area.topRightX - area.bottomLeftX
        val out = IntArray(4 * w * w)
        var i = 0
        for (h in 0..3) for (x in area.bottomLeftX until area.topRightX) for (z in area.bottomLeftZ until area.topRightZ) {
            out[i++] = world.chunks.get(Tile(x, z, h), createIfNeeded = false)?.getMatrix(h)?.get(x and 7, z and 7) ?: 0
        }
        return out
    }

    /** Tiles of the instance space (x >= 6400) beyond the chunk ring around [inside] that carry any collision flag. */
    private fun flaggedOutside(
        world: World,
        inside: gg.rsmod.game.model.Area?,
    ): List<String> {
        val out = ArrayList<String>()
        world.chunks.allChunks().forEach { chunk ->
            val base = chunk.coords.toTile()
            if (base.x < 6400 || base.z < 0 || base.z >= 6400) return@forEach
            for (h in 0..3) for (lx in 0..7) for (lz in 0..7) {
                val t = Tile(base.x + lx, base.z + lz, h)
                // A wall or a large loc on the map's edge also flags the tiles just across it (its footprint and two-sided wall
                // collision, as in the source map): that edge spill stays inside the ring of directly adjacent chunks and is reset
                // by InstancedMapAllocator.clearCollision when a later map takes the slot (separate finding 20261021-1230). The
                // N122 defect is the band of whole chunks beyond it - up to 80 tiles out - that a map blocked and never released.
                val ring = gg.rsmod.game.model.region.Chunk.CHUNK_SIZE
                val inArea =
                    inside != null && t.x >= inside.bottomLeftX - ring && t.x < inside.topRightX + ring &&
                        t.z >= inside.bottomLeftZ - ring && t.z < inside.topRightZ + ring
                if (!inArea && chunk.getMatrix(h).get(lx, lz) != 0) {
                    out += "${t.x},${t.z},$h"
                    if (out.size >= 5) return out
                }
            }
        }
        return out
    }

    @Test
    fun `every map shape writes no collision beyond its own area's edge and leaves none behind when released`() {
        val failures = ArrayList<String>()
        Shape.values().forEach { shape ->
            val world = world()
            val map = world.instanceAllocator.allocate(world, shape.chunks(), shape.config())!!
            flaggedOutside(world, map.area).takeIf { it.isNotEmpty() }?.let { failures += "$shape allocated at ${map.area}: collision outside its area at $it" }
            world.instanceAllocator.release(world, map)
            flaggedOutside(world, map.area).takeIf { it.isNotEmpty() }?.let { failures += "$shape released: collision left at $it" }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun `a reused slot gives every map the same collision as a fresh world, in every order`() {
        val fresh =
            Shape.values().associateWith { shape ->
                val world = world()
                val map = world.instanceAllocator.allocate(world, shape.chunks(), shape.config())!!
                map.area to snapshot(world, map.area)
            }
        val failures = ArrayList<String>()
        Shape.values().forEach { first ->
            val world = world()
            val before = world.instanceAllocator.allocate(world, first.chunks(), first.config())!!
            world.instanceAllocator.release(world, before)
            val history = mutableListOf(first)
            Shape.values().forEach { next ->
                val map = world.instanceAllocator.allocate(world, next.chunks(), next.config())!!
                val (freshArea, freshWords) = fresh.getValue(next)
                assertEquals(freshArea, map.area, "$next after $history: a released slot is reused")
                val words = snapshot(world, map.area)
                val differing = words.indices.count { words[it] != freshWords[it] }
                if (differing > 0) failures += "$next after $history (each allocated and released in this slot): $differing collision words differ from a fresh world"
                world.instanceAllocator.release(world, map)
                history += next
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun `the intro's actors can step off their start tiles after a player house used the slot`() {
        val world = world()
        val house = world.instanceAllocator.allocate(world, Shape.HOUSE.chunks(), Shape.HOUSE.config())!!
        world.instanceAllocator.release(world, house)
        val intro = world.instanceAllocator.allocate(world, Shape.INTRO.chunks(), Shape.INTRO.config())!!
        // IntroGuardScene: PKER_START 3141,3555, ESCAPER_START 3140,3549 and the watcher between them, copied from base 3072,3456.
        listOf(Tile(3141, 3555), Tile(3140, 3549), Tile(3140, 3552)).forEach { real ->
            val tile = Tile(intro.area.bottomLeftX + real.x - 3072, intro.area.bottomLeftZ + real.z - 3456, 0)
            val open = gg.rsmod.game.model.Direction.NESW.count { world.collision.canTraverse(tile, it, projectile = false, water = false) }
            assertTrue(open > 0, "actor start ${tile.x},${tile.z} cannot step in any direction after a released house")
        }
    }
}
