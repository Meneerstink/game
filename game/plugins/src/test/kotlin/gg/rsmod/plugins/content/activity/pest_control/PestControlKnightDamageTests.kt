package gg.rsmod.plugins.content.activity.pest_control

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * N232 (owner 2026-10-23): solo Pest Control was too hard - the Void Knight died too fast. The knight takes a share of the pests'
 * damage in a small team (N232 2026-11-01: PestControlBalance); every boat uses the same team profile.
 */
class PestControlKnightDamageTests {
    /** The average over every roll: what the knight loses per [damage] hit on average. */
    private fun average(
        players: Int,
        damage: Int,
    ): Double = (0 until 1000).map { PestControlHandler.knightDamage(players, damage, (it + 0.5) / 1000) }.average()

    @Test
    fun `a solo knight takes a sixth, a team of 5 or more everything`() {
        assertEquals(5.0, average(1, 30), 0.01)
        assertEquals(9.0, average(2, 30), 0.01)
        assertEquals(15.0, average(3, 30), 0.01)
        assertEquals(21.0, average(4, 30), 0.01)
        listOf(5, 10, 25).forEach { assertEquals(30.0, average(it, 30), 0.0, "$it players") }
        assertEquals(0, PestControlHandler.knightDamage(1, 0))
    }

    @Test
    fun `small hits are not rounded up or down to always the same number`() {
        assertEquals(1.0 / 6, average(1, 1), 0.01, "a 1-hit on a solo knight lands a sixth of the time")
        assertEquals(4.0 / 6, average(1, 4), 0.01)
        (1..12).forEach { d -> (0..9).forEach { r -> assertTrue(PestControlHandler.knightDamage(1, d, r / 10.0) in 0..d) } }
    }

    @Test
    fun `small teams have fewer knight shifters, at most Darkan's 4`() {
        assertEquals(listOf(1, 1, 1, 2, 3, 4, 4), listOf(0, 1, 2, 3, 4, 5, 25).map { PestControlHandler.knightShifterCap(it) })
        val root = listOf(File("src/main/kotlin"), File("plugins/src/main/kotlin")).first { it.isDirectory }
        val handler = root.resolve("gg/rsmod/plugins/content/activity/pest_control/PestControlHandler.kt").readText()
        assertTrue("knightShifters.size >= PestControlHandler.knightShifterCap(players.size)" in handler)
    }

    @Test
    fun `damage budget renews only at the next window and counts damage spent before a departure`() {
        val budget = PestControlBalance.KnightDamageBudget()
        assertEquals(8, budget.apply(1, 8, Int.MAX_VALUE))
        assertEquals(0, budget.apply(2, 100, 4))
        assertEquals(0, budget.apply(9, 100, 4))
        assertEquals(4, budget.apply(10, 100, 4))
        assertEquals(0, budget.apply(10, 100, 4))
        assertEquals(3, budget.apply(20, 3, 4))
        assertEquals(1, budget.apply(20, 100, 4))
    }
}
