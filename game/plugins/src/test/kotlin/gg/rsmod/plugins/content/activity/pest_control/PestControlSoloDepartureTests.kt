package gg.rsmod.plugins.content.activity.pest_control

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * N204 (owner 2026-10-22): with one player on the lander only "Next departure: 30 seconds" showed and the game never started; every
 * lander may depart with 1 player, and 30 seconds before departure a red chat line says so.
 */
class PestControlSoloDepartureTests {
    @Test
    fun `a lander departs with one player`() {
        assertEquals(1, PC_MIN_TEAM_SIZE)
    }

    @Test
    fun `a red chat line 30 seconds (50 ticks) before departure`() {
        assertEquals("<col=ff0000>Pest Control will start in 30 seconds.</col>", PC_START_SOON_MESSAGE)
        val handler = File("src/main/kotlin/gg/rsmod/plugins/content/activity/pest_control/PestControlHandler.kt").readText()
        assertTrue("if (lobby.ticks == PC_LOBBY_DEPARTURE_TICKS - 50) lobby.waiting.forEach { it.message(PC_START_SOON_MESSAGE) }" in handler)
    }
}
