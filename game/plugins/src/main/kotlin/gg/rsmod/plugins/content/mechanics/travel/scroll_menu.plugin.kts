package gg.rsmod.plugins.content.mechanics.travel

// The OSRS parchment menu's lines 864:10-25 (ScrollMenu: spirit trees, obelisks).
for (row in 0 until ScrollMenu.ROWS) {
    on_button(interfaceId = ScrollMenu.INTERFACE, component = ScrollMenu.FIRST_ROW + row) {
        ScrollMenu.pick(player, row)
    }
}
