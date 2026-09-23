package gg.rsmod.game.service

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class GameServiceCycleIsolationTests {
    @Test
    fun `world cycle failure is isolated from the fixed-rate scheduler`() {
        val source =
            File("src/main/kotlin/gg/rsmod/game/service/GameService.kt")
                .readText()

        val worldCycle = source.indexOf("world.cycle()")
        assertTrue(worldCycle >= 0, "GameService must invoke the world cycle")

        val boundary = source.substring(worldCycle.coerceAtLeast(0), (worldCycle + 220).coerceAtMost(source.length))
        assertTrue(boundary.contains("catch (e: Exception)"), "world cycle lacks an exception boundary")
        assertTrue(boundary.contains("Error with world cycle"), "world cycle failure is not logged")
    }
}
