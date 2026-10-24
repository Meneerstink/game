package gg.rsmod.plugins.api.ext

import gg.rsmod.util.TextWrapping
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextWrappingTests {
    @Test
    fun `N357 no wrapped dialogue line is longer than the wrap length`() {
        // The Bloodhound transcript lines (OSRS Wiki) whose end ran past the box.
        listOf(
            "*Bark! Woof bark bark.* Whatever is left, however improbable, must be the answer.",
            "*Woof woof bark!* Elementary, it's due to the influence of the -SQUIRREL-!",
            "*Woof bark bark woof* I have teeth too you know, that joke was not funny.",
        ).forEach { text ->
            val lines = TextWrapping.wrap(text)!!.toList()
            lines.forEach { assertTrue(it.length <= 50, "'$it' (${it.length})") }
            assertEquals(text, lines.joinToString(" "))
        }
    }

    @Test
    fun `a short line stays whole and one long word is not split`() {
        assertEquals(listOf("Hello there."), TextWrapping.wrap("Hello there.")!!.toList())
        val word = "x".repeat(60)
        assertEquals(listOf(word, "end"), TextWrapping.wrap("$word end")!!.toList())
    }
}
