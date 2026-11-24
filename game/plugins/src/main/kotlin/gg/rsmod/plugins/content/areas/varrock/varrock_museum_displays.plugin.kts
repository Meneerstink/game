package gg.rsmod.plugins.content.areas.varrock

// Five Dig Site finds, not Fossil Island displays: OSRS Wiki Kudos gives ten Kudos each, no fixed lamp.
VarrockMuseum.EXHIBITS.forEach { display ->
    listOf(display.root, display.filled).forEach { objId ->
        on_item_on_obj(obj = objId, item = display.item) {
            val slot = player.getInteractingItemSlot()
            val held = player.attr[gg.rsmod.game.model.attr.INTERACTING_ITEM]?.get() ?: return@on_item_on_obj
            VarrockMuseum.place(player, player.getInteractingGameObj(), slot, held)
        }
        on_obj_option(objId, "Study") { VarrockMuseum.study(player, player.getInteractingGameObj()) }
    }
}
// Before completion pottery already occupies its case (unlike the four other finds).
on_item_on_obj(obj = 24543, item = Items.POTTERY) {
    val slot = player.getInteractingItemSlot()
    val held = player.attr[gg.rsmod.game.model.attr.INTERACTING_ITEM]?.get() ?: return@on_item_on_obj
    VarrockMuseum.place(player, player.getInteractingGameObj(), slot, held)
}
on_obj_option(24543, "Study") { VarrockMuseum.study(player, player.getInteractingGameObj()) }

on_obj_option(24452, "Look-at") { VarrockMuseum.showKudos(player) }
on_button(600, 12) { player.closeMainInterface() }
on_button(528, 1) { player.closeMainInterface() }
