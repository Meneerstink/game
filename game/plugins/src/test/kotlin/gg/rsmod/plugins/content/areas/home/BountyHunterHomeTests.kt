package gg.rsmod.plugins.content.areas.home

import gg.rsmod.game.model.Tile
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BountyHunterHomeTests {
    private val home = Tile(3140, 3640, 0)

    @Test
    fun `home centre and every boundary edge are safe`() {
        assertTrue(BountyHunterHome.isSafe(home, home))
        assertTrue(BountyHunterHome.isSafe(home.transform(-5, -5), home))
        assertTrue(BountyHunterHome.isSafe(home.transform(5, 5), home))
        assertTrue(BountyHunterHome.isSafe(home.transform(-5, 5), home))
        assertTrue(BountyHunterHome.isSafe(home.transform(5, -5), home))
    }

    @Test
    fun `one tile beyond each home edge is dangerous Wilderness`() {
        assertTrue(BountyHunterHome.isDangerousWilderness(home.transform(-6, 0), home))
        assertTrue(BountyHunterHome.isDangerousWilderness(home.transform(6, 0), home))
        assertTrue(BountyHunterHome.isDangerousWilderness(home.transform(0, -6), home))
        assertTrue(BountyHunterHome.isDangerousWilderness(home.transform(0, 6), home))
    }

    @Test
    fun `different height is never protected by ground-floor hub`() {
        val upstairs = Tile(home.x, home.z, 1)
        assertFalse(BountyHunterHome.isSafe(upstairs, home))
        assertTrue(BountyHunterHome.isDangerousWilderness(upstairs, home))
    }

    @Test
    fun `safe and unsafe boundary pairs cannot both be PvP eligible`() {
        val safe = home
        val unsafe = home.transform(6, 0)

        assertFalse(
            BountyHunterHome.isDangerousWilderness(safe, home) &&
                BountyHunterHome.isDangerousWilderness(unsafe, home),
        )
        assertFalse(
            BountyHunterHome.isDangerousWilderness(unsafe, home) &&
                BountyHunterHome.isDangerousWilderness(safe, home),
        )
    }
}
