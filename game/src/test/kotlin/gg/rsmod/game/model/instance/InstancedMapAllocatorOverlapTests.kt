package gg.rsmod.game.model.instance

import gg.rsmod.game.model.Area
import gg.rsmod.game.model.Tile
import java.util.EnumSet
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * N159: the allocator only checked the two corners of a new map against the maps already placed, so a smaller map inside a
 * bigger candidate square without touching its corners was missed. Darkan InstanceBuilder.findEmptyChunkBound checks every
 * region plus an empty one-region border; the border also keeps an edge wall's outside flag off an active neighbour.
 */
class InstancedMapAllocatorOverlapTests {
    private fun allocatorWith(vararg areas: Area): InstancedMapAllocator {
        val allocator = InstancedMapAllocator()
        @Suppress("UNCHECKED_CAST")
        val maps = InstancedMapAllocator::class.java.getDeclaredField("maps").apply { isAccessible = true }.get(allocator) as MutableList<InstancedMap>
        areas.forEach { maps += InstancedMap(it, InstancedChunkSet.Builder().build(), Tile(3222, 3222), null, EnumSet.noneOf(InstancedMapAttribute::class.java)) }
        return allocator
    }

    private fun region(x: Int, z: Int, regions: Int = 1) = Area(x, z, x + regions * 64 - 1, z + regions * 64 - 1)

    @Test
    fun `a three-region map never covers a one-region map lying inside it`() {
        // An active house in the middle region of the candidate square: both corners of the candidate are free.
        val allocator = allocatorWith(region(6464, 64))
        assertFalse(allocator.isFree(6400, 0, 3 * 64), "the old corner check said free")
    }

    @Test
    fun `a new map keeps one empty region between itself and every other map`() {
        val allocator = allocatorWith(region(6400, 0))
        assertFalse(allocator.isFree(6400, 64, 64), "directly beside: an edge wall would flag the neighbour's edge row")
        assertFalse(allocator.isFree(6464, 64, 64), "diagonally beside")
        assertTrue(allocator.isFree(6400, 128, 64), "one empty region between")
        assertTrue(allocator.isFree(6528, 0, 64))
    }

    @Test
    fun `an empty allocator has room everywhere`() {
        assertTrue(allocatorWith().isFree(6400, 0, 3 * 64))
        assertTrue(allocatorWith().hasFreeSpace(3))
    }
}
