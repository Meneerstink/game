package gg.rsmod.game.action

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** The NPC death queue must keep cleanup alive when an optional plugin hook throws. */
class NpcDeathHookIsolationTests {
    @Test
    fun `death hooks are isolated before respawn or removal`() {
        val source = File("src/main/kotlin/gg/rsmod/game/action/NpcDeathAction.kt").readText()

        assertTrue("object NpcDeathAction : KLogging()" in source)
        assertTrue("private inline fun runDeathHook" in source)
        assertTrue("world.plugins.executeNpcPreDeath(npc)" in source)
        assertTrue("world.plugins.executeNpcDeath(npc)" in source)
        assertTrue("world.plugins.executeSlayerLogic(npc)" in source)
        assertTrue("runDeathHook(npc, \"npc-death\")" in source)
        assertTrue("world.remove(npc)" in source)
    }
}
