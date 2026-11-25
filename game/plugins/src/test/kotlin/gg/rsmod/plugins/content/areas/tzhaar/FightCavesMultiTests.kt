package gg.rsmod.plugins.content.areas.tzhaar

import gg.rsmod.game.model.Area
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.instance.InstancedChunkSet
import gg.rsmod.game.model.instance.InstancedMap
import gg.rsmod.plugins.api.ext.isMulti
import gg.rsmod.plugins.content.areas.tzhaar.fightcaves.FightCaves
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * N603: the Fight Caves instance is multi-combat through its copy source (region 9551 in data/cfg/areas/multi.yml), so the activity
 * no longer registers - and leaks - its instance regions as multi.
 */
class FightCavesMultiTests {
    /** COMBATENGAGE-FLAKY: multi comes from mocked multi.yml chunks here; a class that loaded the plugins (multiway_combat) leaves the real surface map in this JVM. */
    @kotlin.test.BeforeTest
    fun unloadMultiZones() = gg.rsmod.plugins.content.mechanics.multi.MultiCombatZones.unload()

    private fun world(multiRegions: Set<Int>): World {
        val chunks = InstancedChunkSet.Builder()
        for (x in 0 until 8) for (z in 0 until 8) for (height in 0..3) {
            chunks.set(x, z, height, 0, Tile(FightCaves.REGION_BASE_X + x * 8, FightCaves.REGION_BASE_Z + z * 8, height))
        }
        val map = mockk<InstancedMap>(relaxed = true)
        every { map.area } returns Area(6400, 0, 6400 + 63, 63)
        every { map.chunks } returns chunks.build()
        val world = mockk<World>(relaxed = true)
        every { world.instanceAllocator.getMap(any()) } returns map
        every { world.getMultiCombatRegions() } returns multiRegions
        every { world.getMultiCombatChunks() } returns emptySet()
        return world
    }

    @Test
    fun `a Fight Caves instance tile is multi through region 9551 alone`() {
        val source = (FightCaves.REGION_BASE_X shr 6 shl 8) or (FightCaves.REGION_BASE_Z shr 6)
        assertTrue(source == 9551)
        val world = world(setOf(9551))
        for (dx in 0 until 64 step 7) for (dz in 0 until 64 step 7) {
            assertTrue("instance tile ${6400 + dx},$dz", Tile(6400 + dx, dz, 0).isMulti(world))
        }
    }

    @Test
    fun `without 9551 the same instance tile is single - the source decides, not a leaked instance region`() {
        assertFalse(Tile(6420, 20, 0).isMulti(world(emptySet())))
    }
}
