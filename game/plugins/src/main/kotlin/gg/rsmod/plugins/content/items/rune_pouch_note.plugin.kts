package gg.rsmod.plugins.content.items

on_item_option(item = Items.RUNE_POUCH_NOTE, option = "read") {
    player.message(RunePouchNote.READ)
}
