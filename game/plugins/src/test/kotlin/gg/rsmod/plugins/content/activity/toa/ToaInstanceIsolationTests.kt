package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.instance.InstancedChunkSet
import gg.rsmod.plugins.api.ext.isMulti
import kotlin.test.*

class ToaInstanceIsolationTests {
    /** COMBATENGAGE-FLAKY: multi comes from mocked multi.yml chunks here; a class that loaded the plugins (multiway_combat) leaves the real surface map in this JVM. */
    @kotlin.test.BeforeTest
    fun unloadMultiZones() = gg.rsmod.plugins.content.mechanics.multi.MultiCombatZones.unload()

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

    @Test fun `every raid tile on every plane is multi-combat through its copy source`() {
        val chunks = ToaMap.chunks()
        val baseX = 6400
        val baseZ = 0
        val map = io.mockk.mockk<gg.rsmod.game.model.instance.InstancedMap>(relaxed = true)
        io.mockk.every { map.area } returns gg.rsmod.game.model.Area(baseX, baseZ, baseX + 7 * 64 - 1, baseZ + 7 * 64 - 1)
        io.mockk.every { map.chunks } returns chunks
        val world = io.mockk.mockk<gg.rsmod.game.model.World>(relaxed = true)
        io.mockk.every { world.instanceAllocator.getMap(any()) } returns map
        io.mockk.every { world.getMultiCombatRegions() } returns ToaMap.CACHE_REGIONS.toSet()
        io.mockk.every { world.getMultiCombatChunks() } returns emptySet()
        assertEquals(12, ToaMap.CACHE_REGIONS.toSet().size)
        var checked = 0
        for (region in ToaMap.REGIONS) for (plane in 0..3) for (dx in 0 until 64 step 3) for (dz in 0 until 64 step 3) {
            val source = Tile((region shr 8) * 64 + dx, (region and 255) * 64 + dz, plane)
            val tile = Tile(baseX + source.x - ToaMap.SOURCE_BASE_X, baseZ + source.z - ToaMap.SOURCE_BASE_Z, plane)
            assertTrue(tile.isMulti(world), "raid tile $tile (OSRS $source) must be multi-combat")
            checked++
        }
        assertTrue(checked > 10_000)
        // Kephri arena (N462 11-11 repro): the boss tile and the attacker tile resolve the same way.
        io.mockk.every { world.getMultiCombatRegions() } returns emptySet()
        assertFalse(Tile(baseX + 3546 - ToaMap.SOURCE_BASE_X, baseZ + 5406 - ToaMap.SOURCE_BASE_Z, 0).isMulti(world),
            "without the registration the raid is single-way (the bug)")
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
