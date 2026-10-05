package gg.rsmod.plugins.content.activity.pest_control

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * OSRS Wiki "Pest Control": "The first shield will always drop 15 seconds into the game, and the remaining shields deactivate every
 * 30 seconds thereafter. All shields will be down by 1 minute and 45 seconds into the game." One game tick = 0.6 s.
 */
class PestControlShieldTests {
    @Test
    fun `shields drop at 15 s and then every 30 s, all down by 1 min 45 s`() {
        val seconds = PC_SHIELD_DROP_TICKS.map { it * 0.6 }
        assertEquals(listOf(15.0, 45.0, 75.0, 105.0), seconds)
    }
}
