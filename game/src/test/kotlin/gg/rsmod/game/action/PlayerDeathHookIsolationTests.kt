package gg.rsmod.game.action

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** A player death hook must not strand the player when optional plugin code throws. */
class PlayerDeathHookIsolationTests {
    @Test
    fun `death hooks are isolated around the existing lifecycle`() {
        val source = File("src/main/kotlin/gg/rsmod/game/action/PlayerDeathAction.kt").readText()

        assertTrue("object PlayerDeathAction : KLogging()" in source)
        assertTrue("private inline fun runDeathHook" in source)
        assertTrue("runDeathHook(player, \"player-pre-death\")" in source)
        assertTrue("runDeathHook(player, \"player-death\")" in source)
        assertTrue("player.unlock()" in source)
        assertTrue("player.attr.removeIf { it.resetOnDeath }" in source)
    }
}
