package gg.rsmod.plugins.content.inter.notes

import gg.rsmod.plugins.content.inter.notes.Notes.Note
import gg.rsmod.plugins.content.inter.notes.Notes.NoteColour
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Coverage for the part of the Notes tab that has to survive a logout: the stored form of the note
 * list, and what is accepted into it.
 *
 * The interface flow itself needs a client and is not exercised here. What is exercised is the
 * reason the tab was broken in the first place - that a note has to round-trip through the player's
 * save, because the client never sends one back.
 */
class NotesTests {
    @Test
    fun `notes survive a round trip through the stored form`() {
        val notes =
            listOf(
                Note("meet the wise old man", NoteColour.WHITE),
                Note("bring 5 nature runes", NoteColour.GREEN),
                Note("clue scroll is in the bank", NoteColour.RED),
            )

        assertEquals(notes, Notes.decode(Notes.encode(notes)))
    }

    @Test
    fun `a player with no stored notes has no notes`() {
        assertEquals(emptyList(), Notes.decode(null))
        assertEquals(emptyList(), Notes.decode(""))
    }

    @Test
    fun `the colour is not part of the text`() {
        val stored = Notes.encode(listOf(Note("3 sharks", NoteColour.AMBER)))

        assertEquals(listOf(Note("3 sharks", NoteColour.AMBER)), Notes.decode(stored))
    }

    @Test
    fun `a note whose text begins with a digit keeps its own colour`() {
        // The encoding puts the colour in the leading character, so this is the case that would
        // silently eat the first character of the text if the two were ever confused.
        val notes = listOf(Note("0 coins left", NoteColour.RED))

        assertEquals(notes, Notes.decode(Notes.encode(notes)))
    }

    @Test
    fun `a stored note with an unknown colour reads as white rather than failing`() {
        assertEquals(listOf(Note("hello", NoteColour.WHITE)), Notes.decode("9hello"))
    }

    @Test
    fun `newlines cannot be typed into a note, because they would split it in two`() {
        val cleaned = Notes.sanitise("first\nsecond")

        assertTrue(!cleaned.contains('\n'))
        assertEquals(1, Notes.decode(Notes.encode(listOf(Note(cleaned, NoteColour.WHITE)))).size)
    }

    @Test
    fun `a note is bounded in length`() {
        val cleaned = Notes.sanitise("a".repeat(500))

        assertEquals(Notes.MAX_TEXT_LENGTH, cleaned.length)
    }

    @Test
    fun `surrounding whitespace is not kept`() {
        assertEquals("buy a whip", Notes.sanitise("   buy a whip   "))
    }

    @Test
    fun `a save holding more than thirty notes is truncated rather than overflowing the slots`() {
        val stored = Notes.encode((1..40).map { Note("note $it", NoteColour.WHITE) })

        assertEquals(Notes.MAX_NOTES, Notes.decode(stored).size)
    }
}
