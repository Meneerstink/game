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
        // Void PlayerDeath: retribution / wrath before the death animation, the items after it.
        val start = source.indexOf("runDeathHook(player, \"player-death-start\")")
        val anim = source.indexOf("player.animate(deathAnim.id)")
        assertTrue(start in 0 until anim)
        assertTrue(source.indexOf("runDeathHook(player, \"player-pre-death\")") > anim)
        assertTrue("runDeathHook(player, \"player-death\")" in source)
        assertTrue("player.unlock()" in source)
        assertTrue("player.attr.removeIf { it.resetOnDeath }" in source)
    }
}
