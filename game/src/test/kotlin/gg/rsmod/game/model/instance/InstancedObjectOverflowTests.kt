package gg.rsmod.game.model.instance

import gg.rsmod.game.DevContext
import gg.rsmod.game.GameContext
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.StaticObject
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * CoX 15-11 (dossier COX.txt ENGINE-PUNT): a 3x3 loc at chunk-local (3,6) in a chunk turned 3 (west) has its rotated south-west tile at
 * x = 7 - 6 - (3 - 1) = -1, outside its chunk - the last floor's hole 29735 in a room turned west. The OSRS client places it in the
 * neighbour chunk; OBJECTS_OVERFLOW_INTO_NEIGHBOUR_CHUNK does the same server-side. Without any flag the allocator still refuses (old
 * behaviour), with bypassObjectChunkBounds (Pest Control, POH) the loc is still left out.
 */
class InstancedObjectOverflowTests {
    private val big = 7777

    private fun world(): World {
        val context = GameContext(false, "CoX overflow", 667, 600, 8, Tile(3200, 3200), 25, 10, false, 100, 100, false, false, 0)
        val world = World(context, DevContext(false, false, false, false, false, false, false))
        val definitions = mockk<DefinitionSet>(relaxed = true)
        every { definitions.get(ObjectDef::class.java, any()) } answers {
            ObjectDef(secondArg<Int>()).apply { if (secondArg<Int>() == big) { width = 3; length = 3 } }
        }
        every { definitions.get(NpcDef::class.java, any()) } answers { NpcDef(secondArg()) }
        World::class.java.getDeclaredField("definitions").apply { isAccessible = true }.set(world, definitions)
        val source = Tile(4096 + 3, 6656 + 6)
        world.chunks.getOrCreate(source).addEntity(world, StaticObject(big, 10, 0, source), source)
        return world
    }

    private fun allocate(world: World, config: InstancedMapConfiguration.Builder): InstancedMap {
        val b = InstancedChunkSet.Builder()
        b.set(0, 0, 0, copy = Tile(4200, 6656))
        b.set(1, 0, 0, rot = 3, copy = Tile(4096, 6656))
        return world.instanceAllocator.allocate(world, b.build(), config.setExitTile(Tile(3222, 3222)).build())!!
    }

    private fun objectsAt(world: World, tile: Tile) =
        world.chunks.getOrCreate(tile).getEntities<StaticObject>(EntityType.STATIC_OBJECT).filter { it.id == big }

    @Test
    fun `without a flag the allocator refuses the overflowing loc as before`() {
        assertFailsWith<IllegalStateException> { allocate(world(), InstancedMapConfiguration.Builder()) }
    }

    @Test
    fun `the plain bypass still leaves the loc out (Pest Control, POH unchanged)`() {
        val world = world()
        val map = allocate(world, InstancedMapConfiguration.Builder().setBypassObjectChunkBounds(true))
        val expected = map.area.bottomLeft.transform(8 - 1, 3, 0)
        assertTrue(objectsAt(world, expected).isEmpty())
    }

    @Test
    fun `overflow flag places the turned loc in the neighbour chunk like the OSRS client`() {
        val world = world()
        val map = allocate(world, InstancedMapConfiguration.Builder().addAttribute(InstancedMapAttribute.OBJECTS_OVERFLOW_INTO_NEIGHBOUR_CHUNK))
        // chunk (1,0) starts at x+8; rotated (3,6) with 3x3 under rotation 3 -> (7-6-2, 3) = (-1, 3) -> x+7 in chunk (0,0).
        val expected = map.area.bottomLeft.transform(8 - 1, 3, 0)
        val placed = objectsAt(world, expected)
        assertEquals(1, placed.size)
        assertEquals(expected, placed.single().tile)
        assertEquals(3, placed.single().rot)
    }
}
