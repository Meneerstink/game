package gg.rsmod.game.model.entity

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Audit T-13: the window in which a non-priority animation (a block animation) may not replace the
 * previous one is counted in game cycles. It was `currentTimeMillis() + ticks`, i.e. a few milliseconds,
 * so a block animation overwrote the attack animation of the same tick. Source-level, because a [Pawn]
 * cannot be built in this module's tests without a full World.
 */
class AnimationPriorityTests {
    private val pawn = File("src/main/kotlin/gg/rsmod/game/model/entity/Pawn.kt").readText()
    private val animate = pawn.substringAfter("    fun animate(").substringBefore("\n    }")

    @Test
    fun `the protection window is measured in cycles`() {
        assertTrue("internal var lastAnimation = 0\n" in pawn)
        assertTrue("if (!priority && lastAnimation > world.currentCycle)" in animate)
        assertTrue("world.currentCycle + world.getAnimationDelay(id)" in animate)
        assertFalse("currentTimeMillis" in animate)
    }

    @Test
    fun `resetting the animation also ends its protection window`() {
        assertTrue("else 0" in animate, "animate(-1) clears the window")
        val reset = pawn.substringAfter("fun resetAnimation() {").substringBefore("\n    }")
        assertTrue("lastAnimation = 0" in reset)
    }
}
