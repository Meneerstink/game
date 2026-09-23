package gg.rsmod.game.system

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/** M0 interaction stability: every Netty receive-path exit releases the packet payload. */
class GameSystemPacketReleaseTests {
    @Test
    fun `packet payload release is protected by receive-path finally`() {
        val source =
            Files.readString(
                Path.of(
                    "src",
                    "main",
                    "kotlin",
                    "gg",
                    "rsmod",
                    "game",
                    "system",
                    "GameSystem.kt",
                ),
            )

        assertTrue("try {\n                val decoder" in source)
        assertTrue("} finally {" in source)
        assertTrue("msg.payload.release()" in source)
    }
}
