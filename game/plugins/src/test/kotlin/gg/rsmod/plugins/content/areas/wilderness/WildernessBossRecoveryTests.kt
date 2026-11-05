package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.plugins.content.areas.wilderness.bosses.WildernessBossLife
import kotlin.test.*

class WildernessBossRecoveryTests {
    @Test fun `a failed phase insert retries and commits one replacement without duplicate setup`() {
        var attempts = 0
        var ready = 0
        val pending = WildernessBossLife.PendingSpawn({ ++attempts >= 2 }, { ready++ })
        assertFalse(pending.attempt())
        assertEquals(0, ready, "failure must not mark the next phase as ready")
        assertTrue(pending.attempt())
        repeat(5) { assertTrue(pending.attempt()) }
        assertEquals(2, attempts)
        assertEquals(1, ready)
    }
}
