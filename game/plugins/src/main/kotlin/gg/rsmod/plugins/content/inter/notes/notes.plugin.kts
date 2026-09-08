package gg.rsmod.plugins.content.inter.notes

import gg.rsmod.plugins.content.inter.notes.Notes.NoteColour

/*
 * The client sends the op it was clicked with as one of ten opcodes rather than as an op number,
 * and `getInteractingOption()` reports the opcode's index in the IfButtonMessage opcode list of
 * data/packets.yml plus one, which is not the same order. Matching on the opcode itself is the one
 * reading that cannot drift if that list is ever reordered.
 */
private val OP1 = 61
private val OP2 = 64
private val OP3 = 4
private val OP4 = 52

/*
 * Clientscript 2442 - the list's onLoad - blanks all thirty note varcstrs, and it runs when the
 * client processes the IfOpenSub for this interface. The interface-open hook fires *before* that
 * message is written, so a refresh sent from inside it would be blanked a moment later; waiting a
 * cycle puts it safely after.
 */
on_interface_open(Notes.NOTES_INTERFACE_ID) {
    player.queue {
        wait(1)
        Notes.refresh(player)
    }
}

/*
 * Selection is per-session: it says which row the header's 'Delete' and the colour swatches act on,
 * and carrying yesterday's row index into a new login would only mislabel a row 'Unselect'.
 */
on_login {
    Notes.clearSelection(player)
}

on_button(interfaceId = Notes.NOTES_INTERFACE_ID, component = Notes.ADD_NOTE_COMPONENT) {
    player.queue {
        Notes.add(player, inputString("Enter note:"))
    }
}

/*
 * The header's own 'Delete' acts on the selected note; 'Delete all' needs no selection.
 */
on_button(interfaceId = Notes.NOTES_INTERFACE_ID, component = Notes.DELETE_COMPONENT) {
    when (player.getInteractingOpcode()) {
        OP1 -> {
            val selected = Notes.selected(player)
            if (selected == Notes.NOTHING_SELECTED) {
                player.message("Select a note to delete first.")
            } else {
                Notes.delete(player, selected)
            }
        }
        OP2 -> Notes.deleteAll(player)
    }
}

/*
 * The rows themselves. Clientscript 2445 labels their ops 'Select'/'Unselect', 'Edit', 'Colour' and
 * 'Delete' in that order, and the client sends the row index as the slot.
 */
on_button(interfaceId = Notes.NOTES_INTERFACE_ID, component = Notes.LIST_COMPONENT) {
    val slot = player.getInteractingSlot()
    when (player.getInteractingOpcode()) {
        OP1 -> Notes.toggleSelection(player, slot)
        OP2 ->
            player.queue {
                Notes.edit(player, slot, inputString("Enter note:"))
            }
        OP3 -> {
            Notes.select(player, slot)
            Notes.openColourPicker(player)
        }
        OP4 -> Notes.delete(player, slot)
    }
}

/*
 * The four colour swatches on the picker overlay. Clientscript 2454 turns the note's colour varbit
 * into the colour the row is drawn in, so setting the varbit is the whole of the change.
 */
NoteColour.values().forEach { colour ->
    on_button(interfaceId = Notes.NOTES_INTERFACE_ID, component = colour.component) {
        val selected = Notes.selected(player)
        if (selected != Notes.NOTHING_SELECTED) {
            Notes.colour(player, selected, colour)
        }
        Notes.closeColourPicker(player)
    }
}
