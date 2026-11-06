package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.model.PlayerUID
import kotlin.test.*

class ToaCheckpointsTests {
    @Test fun `two personal claims with full inventory retry and advance forfeiture`() {
        val state = ToaCheckpoints()
        val a = PlayerUID(1); val b = PlayerUID(2)
        assertFalse(state.pathsCleared(1)); assertFalse(state.offered(a))
        assertTrue(state.pathsCleared(2)); assertTrue(state.offered(a)); assertTrue(state.offered(b))
        assertFalse(state.claim(a) { false }); assertTrue(state.offered(a))
        assertTrue(state.claim(a) { true }); assertFalse(state.claim(a) { error("double delivery") })
        state.forfeit(b); assertFalse(state.offered(b))
        assertFalse(state.pathsCleared(2)); assertFalse(state.pathsCleared(3))
        assertTrue(state.pathsCleared(4)); assertTrue(state.claim(a) { true }); assertTrue(state.claim(b) { true })
        assertFalse(state.pathsCleared(4)); assertFalse(state.offered(b))
    }
}
