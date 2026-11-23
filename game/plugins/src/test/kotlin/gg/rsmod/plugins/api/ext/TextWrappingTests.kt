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

    @Test
    fun `DIALOOG-WEES-REST an over-long line leaves no lone word`() {
        val cook = balanceDialogueLines(arrayOf("I don't have anything for you to do right now, sorry."), DIALOG_LINE_CHARS)
        assertEquals(listOf("I don't have anything for", "you to do right now, sorry."), cook)
        val tutor =
            balanceDialogueLines(
                arrayOf("Welcome to the hall, adventurer. I keep the records", "of every quest in Gielinor - and of the paths each", "new hero chooses."),
                DIALOG_LINE_CHARS,
            )
        assertEquals(3, tutor.size)
        tutor.forEach { assertTrue(it.length <= DIALOG_LINE_CHARS && it.split(' ').size > 1, "'$it'") }
        val pick = wrapMessageBox(arrayOf("You need a pickaxe to mine this rock. You do not have a pickaxe<br><br>which you have the Mining level to use."))
        assertEquals(2, pick.size)
        pick.forEach { assertTrue(it.length <= MESSAGE_BOX_LINE_CHARS && it.split(' ').size > 3, "'$it'") }
    }

    @Test
    fun `lines that fit keep the caller's own breaks`() {
        val lines = arrayOf("Short one.", "<col=ff0000>A coloured line that is long in tags only</col>", "two<br>pieces")
        assertEquals(lines.toList(), balanceDialogueLines(lines, DIALOG_LINE_CHARS))
    }
}
