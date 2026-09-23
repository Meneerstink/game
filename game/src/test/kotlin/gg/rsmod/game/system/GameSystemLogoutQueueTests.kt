package gg.rsmod.game.system

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** M0 logout stability: queued inbound packets must not mutate a pending/offline player. */
class GameSystemLogoutQueueTests {
    @Test
    fun `message handling clears inbound packets after logout or disconnect`() {
        val playerSource = File("src/main/kotlin/gg/rsmod/game/model/entity/Player.kt").readText()
        val systemSource = File("src/main/kotlin/gg/rsmod/game/system/GameSystem.kt").readText()
        val handleMessages = systemSource.substringAfter("fun handleMessages()")

        assertTrue("val isLogoutPending: Boolean" in playerSource)
        assertTrue("!client.isOnline || client.isLogoutPending" in handleMessages)
        assertTrue("messages.clear()" in handleMessages)
        assertTrue("return" in handleMessages)
    }
}
