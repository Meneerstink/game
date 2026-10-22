package gg.rsmod.plugins.content.combat

import kotlin.test.Test
import kotlin.test.assertEquals

/** N97 (owner VRAGEN 54 = B, 2026-10-21): the single-way PJ window is 17 ticks (10.2 s) for everyone, his choice of 2026-09-25. */
class PjTimerTests {
    @Test
    fun `the PJ window is 17 ticks`() {
        assertEquals(17, Combat.PJ_TICKS)
    }
}
