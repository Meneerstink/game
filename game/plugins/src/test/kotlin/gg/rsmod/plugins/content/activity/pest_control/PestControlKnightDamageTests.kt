package gg.rsmod.plugins.content.activity.pest_control

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * N232 (owner 2026-10-23): solo Pest Control was too hard - the Void Knight died too fast. The knight takes a share of the pests'
 * damage in a small team: a third solo, players / 5 for 2..4, everything from 5 players on; and one knight shifter per player.
 */
class PestControlKnightDamageTests {
    /** The average over every roll: what the knight loses per [damage] hit on average. */
    private fun average(
        players: Int,
        damage: Int,
    ): Double = (0 until 1000).map { PestControlHandler.knightDamage(players, damage, (it + 0.5) / 1000) }.average()

    @Test
    fun `a solo knight takes a third, a team of 5 or more everything`() {
        assertEquals(10.0, average(1, 30), 0.01)
        assertEquals(12.0, average(2, 30), 0.01)
        assertEquals(18.0, average(3, 30), 0.01)
        assertEquals(24.0, average(4, 30), 0.01)
        listOf(5, 10, 25).forEach { assertEquals(30.0, average(it, 30), 0.0, "$it players") }
        assertEquals(0, PestControlHandler.knightDamage(1, 0))
    }

    @Test
    fun `small hits are not rounded up or down to always the same number`() {
        assertEquals(1.0 / 3, average(1, 1), 0.01, "a 1-hit on a solo knight lands a third of the time")
        assertEquals(4.0 / 3, average(1, 4), 0.01)
        (1..12).forEach { d -> (0..9).forEach { r -> assertTrue(PestControlHandler.knightDamage(1, d, r / 10.0) in 0..d) } }
    }

    @Test
    fun `the knight brings one shifter per player, at most Darkan's 4`() {
        assertEquals(listOf(1, 1, 2, 3, 4, 4, 4), listOf(0, 1, 2, 3, 4, 5, 25).map { PestControlHandler.knightShifterCap(it) })
        val root = listOf(File("src/main/kotlin"), File("plugins/src/main/kotlin")).first { it.isDirectory }
        val handler = root.resolve("gg/rsmod/plugins/content/activity/pest_control/PestControlHandler.kt").readText()
        assertTrue("knightShifters.size >= PestControlHandler.knightShifterCap(players.size)" in handler)
    }

    @Test
    fun `every hit on a pawn passes the knight scaling`() {
        val root = listOf(File("src/main/kotlin"), File("plugins/src/main/kotlin")).first { it.isDirectory }
        val dealHit = root.resolve("gg/rsmod/plugins/content/combat/PawnExt.kt").readText()
        assertTrue("PestControlHandler.modifyIncomingDamage(target, damage.toInt())" in dealHit)
        val handler = root.resolve("gg/rsmod/plugins/content/activity/pest_control/PestControlHandler.kt").readText()
        assertTrue("it.knight === target" in handler.substringAfter("fun modifyIncomingDamage("), "only the knight of a running game")
    }
}
