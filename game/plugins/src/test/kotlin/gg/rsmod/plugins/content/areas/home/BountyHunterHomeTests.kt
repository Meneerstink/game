package gg.rsmod.plugins.content.areas.home

import gg.rsmod.game.model.Tile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ferox Enclave safe polygon: main compound x 3123..3155 z 3617..3646, eastern annex/garden
 * x 3156..3187 z 3603..3646, ground floor only. Every tile below is the real imported geometry.
 */
class BountyHunterHomeTests {
    private val home = Tile(3137, 3629, 0)

    @Test
    fun `configured home, arrival, bank chest, pools and altar are safe`() {
        assertTrue(BountyHunterHome.isSafe(home, home))
        assertTrue(BountyHunterHome.isSafe(HomeLayout.arrival.tile(home), home))
        assertTrue(BountyHunterHome.isSafe(HomeLayout.bank.tile(home), home))
        assertTrue(BountyHunterHome.isSafe(HomeLayout.pool.tile(home), home))
        assertTrue(BountyHunterHome.isSafe(HomeLayout.poolNorth.tile(home), home))
        assertTrue(BountyHunterHome.isSafe(HomeLayout.altar.tile(home), home))
    }

    @Test
    fun `the enclave wall lines are safe and one tile beyond each is dangerous Wilderness`() {
        assertTrue(BountyHunterHome.isSafe(Tile(3123, 3629, 0), home))
        assertTrue(BountyHunterHome.isDangerousWilderness(Tile(3122, 3629, 0), home))
        assertTrue(BountyHunterHome.isSafe(Tile(3134, 3617, 0), home))
        assertTrue(BountyHunterHome.isDangerousWilderness(Tile(3134, 3616, 0), home))
        assertTrue(BountyHunterHome.isSafe(Tile(3140, 3646, 0), home))
        assertTrue(BountyHunterHome.isDangerousWilderness(Tile(3140, 3647, 0), home))
        assertTrue(BountyHunterHome.isSafe(Tile(3187, 3620, 0), home))
        assertTrue(BountyHunterHome.isDangerousWilderness(Tile(3188, 3620, 0), home))
        assertTrue(BountyHunterHome.isSafe(Tile(3170, 3603, 0), home))
        assertTrue(BountyHunterHome.isDangerousWilderness(Tile(3170, 3602, 0), home))
    }

    @Test
    fun `the south-west pocket outside the walls is Wilderness, the eastern annex is safe`() {
        assertTrue(BountyHunterHome.isDangerousWilderness(Tile(3140, 3610, 0), home))
        assertTrue(BountyHunterHome.isSafe(Tile(3175, 3610, 0), home))
        assertTrue(BountyHunterHome.isSafe(Tile(3160, 3630, 0), home))
    }

    @Test
    fun `every Wilderness exit barrier has a safe inner landing and a dangerous outer landing`() {
        BountyHunterHome.gates(home).forEach { gate ->
            assertTrue("barrier ${gate.tile}", BountyHunterHome.isSafe(gate.tile, home))
            assertTrue("inner ${gate.innerLanding}", BountyHunterHome.isSafe(gate.innerLanding, home))
            if (gate.exitsToWilderness) {
                assertTrue("outer ${gate.outerLanding}", BountyHunterHome.isDangerousWilderness(gate.outerLanding, home))
                assertFalse("two out ${gate.outerLanding}", BountyHunterHome.isSafe(gate.outerLanding.step(gate.direction), home))
            } else {
                assertTrue("internal far side ${gate.outerLanding}", BountyHunterHome.isSafe(gate.outerLanding, home))
            }
        }
    }

    @Test
    fun `different height is never protected by the ground-floor hub`() {
        val upstairs = Tile(home.x, home.z, 1)
        assertFalse(BountyHunterHome.isSafe(upstairs, home))
        assertTrue(BountyHunterHome.isDangerousWilderness(upstairs, home))
    }

    @Test
    fun `safe and unsafe boundary pairs cannot both be PvP eligible`() {
        val unsafe = Tile(3122, 3629, 0)
        assertFalse(BountyHunterHome.isDangerousWilderness(home, home) && BountyHunterHome.isDangerousWilderness(unsafe, home))
    }
}
