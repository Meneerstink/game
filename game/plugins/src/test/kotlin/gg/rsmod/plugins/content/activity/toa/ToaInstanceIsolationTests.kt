package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.instance.InstancedChunkSet
import kotlin.test.*

class ToaInstanceIsolationTests {
    @Test fun `every raid source square copies all planes from local shifted bank`() {
        val chunks = ToaMap.chunks()
        assertEquals(7, chunks.regionSize)
        assertEquals(12 * 8 * 8 * 4, chunks.values.size)
        for (region in ToaMap.REGIONS) for (plane in 0..3) {
            val x = ((region shr 8) * 64 - ToaMap.SOURCE_BASE_X) / 8
            val z = ((region and 255) * 64 - ToaMap.SOURCE_BASE_Z) / 8
            val source = Tile.fromRotatedHash(chunks.values.getValue(InstancedChunkSet.getCoordinates(x, z, plane)).packed)
            assertEquals((region shr 8) * 64 + ToaMap.CACHE_OFFSET_X, source.x)
            assertEquals((region and 255) * 64 + ToaMap.CACHE_OFFSET_Z, source.z)
            assertEquals(plane, source.height)
        }
    }

    @Test fun `each party owns independent chunk maps and each boss landing uses correct plane`() {
        val a = ToaMap.chunks()
        val b = ToaMap.chunks()
        assertNotSame(a.values, b.values)
        assertEquals(a.values.keys, b.values.keys)
        assertEquals(1, ToaPath.HET.bossEntry.height)
        // Cache2686 15700 plane 1 carries the bridge flag over the Zebak entry, so the game plane is 0 (ToaRooms CRONDIS, 07-11 14:2x).
        assertEquals(0, ToaPath.CRONDIS.bossEntry.height)
        assertTrue(ToaPath.values().all { it.challengeEntry.height == 0 })
    }
}
