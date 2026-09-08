package gg.rsmod.plugins.content.inter.notes

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.getVarp
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setInterfaceEvents
import gg.rsmod.plugins.api.ext.setVarbit
import gg.rsmod.plugins.api.ext.setVarcString
import gg.rsmod.plugins.api.ext.setVarp

/**
 * The Notes tab: thirty notes per player, each with a colour, persisted with the player.
 *
 * The server owns the note data outright. That is not a design choice - it is forced by the client.
 * Interface 34's own scripts keep the notes in varcstrs and varbits, and neither ever travels back
 * up the connection:
 *
 *  - `ClientProt` (the client's whole client-to-server opcode table) has no notes packet, and the
 *    only components of interface 34 that reach the server at all are the ones whose ops are baked
 *    into the cache;
 *  - `POP_VARCSTR` in the client's `ScriptRunner` writes `Static37.varcstrs` and stops there, and
 *    varcstrs are not among the values the client saves locally - only the int varcs flagged
 *    `permVarcs` are, and those go to a file on the player's own machine;
 *  - client-side varp writes land in `TimedVarDomain.updates`, which nothing in the client reads.
 *
 * So if the server does not remember a note, nobody does, and the notes vanish the moment the tab
 * is redrawn. That is the defect this fixes.
 *
 * Cache contract, from
 * `./gradlew :game:runInterfaceHookProbeTool --args="../data/cache layout 34"` and the clientscripts
 * it names:
 *
 *  - clientscript 2452 is "text of note n": a switch over thirty cases, each pushing one of
 *    varcstr 149..178 - so [TEXT_VARCSTR] + slot holds the note's text;
 *  - clientscript 2453 is "colour of note n": the same switch shape, each case reading one of
 *    varbit 6316..6345 and passing it to clientscript 2454, which maps 0 to white (0xFFFFFF),
 *    1 to green (0x00FF00), 2 to amber (0xD69700) and 3 to red (0xFF3F3F) - the four colours
 *    components 35, 37, 39 and 41 are labelled with;
 *  - clientscript 2445 labels each row's ops "Select"/"Unselect", "Edit", "Colour" and "Delete",
 *    choosing between "Select" and "Unselect" by comparing varp 1439 to the row index - so varp
 *    1439 is the selected note, and the server is the only thing that can set it;
 *  - clientscript 2442, the list's `onLoad`, blanks all thirty varcstrs. Anything the server sent
 *    before the interface opened is therefore lost, which is why [refresh] runs a tick *after*
 *    the tab opens rather than during it.
 */
object Notes {
    const val NOTES_INTERFACE_ID = 34

    /** Thirty slots; clientscript 2444 renders the header as "Notes (n/30)". */
    const val MAX_NOTES = 30

    const val ADD_NOTE_COMPONENT = 3
    const val DELETE_COMPONENT = 8
    const val LIST_COMPONENT = 11
    const val COLOUR_PICKER_COMPONENT = 16

    /** varcstr 149..178, one per note, read by clientscript 2452. */
    private const val TEXT_VARCSTR = 149

    /** varbit 6316..6345, one per note, read by clientscript 2453. */
    private const val COLOUR_VARBIT = 6316

    /** varp 1439, the selected note, compared against the row index by clientscript 2445. */
    private const val SELECTED_VARP = 1439

    /**
     * Nothing selected. Zero cannot mean this: clientscript 2445 would then label the first note
     * "Unselect" on a freshly opened tab.
     */
    const val NOTHING_SELECTED = -1

    /**
     * Enables ops 1-4 on the note rows, which the client creates as children of [LIST_COMPONENT]
     * and gives its own labels to. The cache only bakes ops 1 and 2 onto the row template, so
     * "Colour" and "Delete" would never be sent without this.
     */
    private const val ROW_OPS = (1 shl 1) or (1 shl 2) or (1 shl 3) or (1 shl 4)

    /**
     * A server-side bound on how much text one note may keep. The cache does not state a limit, so
     * this is a deliberate storage bound rather than a claim about the original game: it keeps a
     * single note from filling a player's save file.
     */
    const val MAX_TEXT_LENGTH = 80

    val NOTES_ATTR = AttributeKey<String>(persistenceKey = "notes")

    enum class NoteColour(
        val component: Int,
        val varbitValue: Int,
    ) {
        WHITE(35, 0),
        GREEN(37, 1),
        AMBER(39, 2),
        RED(41, 3),
        ;

        companion object {
            fun forComponent(component: Int): NoteColour? = values().firstOrNull { it.component == component }

            fun forVarbitValue(value: Int): NoteColour = values().firstOrNull { it.varbitValue == value } ?: WHITE
        }
    }

    data class Note(
        val text: String,
        val colour: NoteColour,
    )

    fun notes(player: Player): List<Note> = decode(player.attr[NOTES_ATTR])

    fun selected(player: Player): Int {
        val selected = player.getVarp(SELECTED_VARP)
        return if (selected in 0 until MAX_NOTES) selected else NOTHING_SELECTED
    }

    /**
     * Selects [index], or clears the selection when it is already selected - which is what makes
     * the row's single op read "Select" one moment and "Unselect" the next.
     */
    fun toggleSelection(
        player: Player,
        index: Int,
    ) {
        val current = selected(player)
        player.setVarp(SELECTED_VARP, if (current == index) NOTHING_SELECTED else index)
    }

    fun select(
        player: Player,
        index: Int,
    ) {
        player.setVarp(SELECTED_VARP, if (index in 0 until MAX_NOTES) index else NOTHING_SELECTED)
    }

    fun clearSelection(player: Player) {
        player.setVarp(SELECTED_VARP, NOTHING_SELECTED)
    }

    fun add(
        player: Player,
        text: String,
    ) {
        val cleaned = sanitise(text)
        if (cleaned.isEmpty()) {
            return
        }
        val notes = notes(player)
        if (notes.size >= MAX_NOTES) {
            player.message("You can only have $MAX_NOTES notes.")
            return
        }
        store(player, notes + Note(cleaned, NoteColour.WHITE))
        refresh(player)
    }

    fun edit(
        player: Player,
        index: Int,
        text: String,
    ) {
        val cleaned = sanitise(text)
        val notes = notes(player)
        val note = notes.getOrNull(index) ?: return
        if (cleaned.isEmpty()) {
            delete(player, index)
            return
        }
        store(player, notes.toMutableList().also { it[index] = note.copy(text = cleaned) })
        refresh(player)
    }

    fun colour(
        player: Player,
        index: Int,
        colour: NoteColour,
    ) {
        val notes = notes(player)
        val note = notes.getOrNull(index) ?: return
        store(player, notes.toMutableList().also { it[index] = note.copy(colour = colour) })
        refresh(player)
    }

    /**
     * Deleting shifts everything below the note up a slot, so the selection has to follow: the
     * client only knows the row index, and leaving varp 1439 where it was would silently move the
     * selection onto whichever note took the deleted one's place.
     */
    fun delete(
        player: Player,
        index: Int,
    ) {
        val notes = notes(player)
        if (index !in notes.indices) {
            return
        }
        store(player, notes.toMutableList().also { it.removeAt(index) })
        val selected = selected(player)
        player.setVarp(
            SELECTED_VARP,
            when {
                selected == index -> NOTHING_SELECTED
                selected > index -> selected - 1
                else -> selected
            },
        )
        refresh(player)
    }

    fun deleteAll(player: Player) {
        store(player, emptyList())
        player.setVarp(SELECTED_VARP, NOTHING_SELECTED)
        refresh(player)
    }

    fun openColourPicker(player: Player) {
        player.setComponentHidden(NOTES_INTERFACE_ID, COLOUR_PICKER_COMPONENT, hidden = false)
    }

    fun closeColourPicker(player: Player) {
        player.setComponentHidden(NOTES_INTERFACE_ID, COLOUR_PICKER_COMPONENT, hidden = true)
    }

    /**
     * Sends the whole notes state to the client. Every slot is written, including the empty ones,
     * because clientscript 2442 only blanks the varcstrs when the interface loads - a note that is
     * deleted while the tab is open has to be blanked by us.
     */
    fun refresh(player: Player) {
        val notes = notes(player)
        for (slot in 0 until MAX_NOTES) {
            val note = notes.getOrNull(slot)
            player.setVarcString(TEXT_VARCSTR + slot, note?.text ?: "")
            player.setVarbit(COLOUR_VARBIT + slot, note?.colour?.varbitValue ?: NoteColour.WHITE.varbitValue)
        }
        if (selected(player) >= notes.size) {
            player.setVarp(SELECTED_VARP, NOTHING_SELECTED)
        }
        player.setInterfaceEvents(NOTES_INTERFACE_ID, LIST_COMPONENT, 0 until MAX_NOTES, ROW_OPS)
    }

    private fun store(
        player: Player,
        notes: List<Note>,
    ) {
        player.attr[NOTES_ATTR] = encode(notes)
    }

    /**
     * One line per note, the colour as a single leading digit and the rest of the line as the text.
     * A note can therefore hold any character a player can type except a newline, which [sanitise]
     * removes anyway.
     */
    internal fun encode(notes: List<Note>): String = notes.joinToString("\n") { "${it.colour.varbitValue}${it.text}" }

    internal fun decode(stored: String?): List<Note> {
        if (stored.isNullOrEmpty()) {
            return emptyList()
        }
        return stored
            .split("\n")
            .filter { it.isNotEmpty() }
            .take(MAX_NOTES)
            .map { line ->
                val colour = NoteColour.forVarbitValue(line[0].digitToIntOrNull() ?: 0)
                Note(line.substring(1), colour)
            }
    }

    /** Strips what the encoding and the client cannot carry, and bounds the length. */
    internal fun sanitise(text: String): String =
        text
            .filter { it.code in 32..126 || it.code > 160 }
            .trim()
            .take(MAX_TEXT_LENGTH)
}
