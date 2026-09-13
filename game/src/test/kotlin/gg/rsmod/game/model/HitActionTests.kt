package gg.rsmod.game.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression for the live `Pawn.hitsCycle` ConcurrentModificationException (launcher log
 * game-server-20260912-222345: 45 occurrences at inlined `hit.actions.forEach`). `dealHit`
 * registers `addAction { onHit(pawnHit) }` and onHit callbacks register further actions on the
 * same hit; the killing-hit variant aborted before NpcDeathAction ran.
 */
class HitActionTests {
    private fun hit(): Hit = Hit.Builder().addHit(damage = 1, type = 0).build()

    @Test
    fun `actions registered by a running action are invoked in the same pass and in order`() {
        val order = mutableListOf<String>()
        val hit = hit()
        hit.addAction {
            order += "onHit"
            addAction {
                order += "spell-effect"
                addAction { order += "nested" }
            }
        }
        hit.addAction { order += "postDamage" }

        hit.invokeActions()

        assertEquals(listOf("onHit", "postDamage", "spell-effect", "nested"), order)
    }

    @Test
    fun `each action runs exactly once`() {
        var count = 0
        val hit = hit()
        repeat(3) { hit.addAction { count++ } }

        hit.invokeActions()

        assertEquals(3, count)
    }
}
