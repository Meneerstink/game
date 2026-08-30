package gg.rsmod.plugins.content.skills.construction

// Numeric option index used instead of guessed option text - see hunter.plugin.kts for why.
// Using the hammer to "Build" doubles as entering/leaving your house: opening the menu is
// entering, closing it (Exit or walking away, which cancels the queued task) is leaving.
on_item_option(item = Items.HAMMER, option = 1) {
    player.queue {
        Construction.openMenu(this)
    }
}

on_command("house") {
    player.queue {
        Construction.openMenu(this)
    }
}
