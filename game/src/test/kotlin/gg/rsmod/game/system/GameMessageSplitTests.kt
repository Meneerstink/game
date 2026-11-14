package gg.rsmod.game.system

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** N609 14-11: game messages over the variable-byte limit are split at send time instead of being dropped by GamePacketEncoder. */
class GameMessageSplitTests {
    @Test
    fun `a short message stays one part`() {
        assertEquals(listOf("hello there"), GameSystem.splitGameMessage("hello there"))
    }

    @Test
    fun `a long botstate line is split into parts under the limit without losing words`() {
        val text = "botstate " + (1..60).joinToString(" ") { "field$it=${it * 7}/99" }
        val parts = GameSystem.splitGameMessage(text)
        assertTrue(parts.size > 1)
        assertTrue(parts.all { it.toByteArray().size <= GameSystem.MAX_GAME_MESSAGE_BYTES })
        assertEquals(text.split(' '), parts.joinToString(" ").split(' '))
    }

    @Test
    fun `a colour tag is never cut in half`() {
        val text = "a".repeat(215) + "<col=ff0000>red</col> tail"
        val parts = GameSystem.splitGameMessage(text)
        assertTrue(parts.all { it.count { c -> c == '<' } == it.count { c -> c == '>' } })
        assertEquals(text.replace(" ", ""), parts.joinToString("").replace(" ", ""))
    }

    @Test
    fun `text without spaces is still split under the limit`() {
        val parts = GameSystem.splitGameMessage("x".repeat(700))
        assertEquals(700, parts.sumOf { it.length })
        assertTrue(parts.all { it.length <= GameSystem.MAX_GAME_MESSAGE_BYTES })
    }
}
