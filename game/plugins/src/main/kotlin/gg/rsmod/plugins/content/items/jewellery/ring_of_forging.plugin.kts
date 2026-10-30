package gg.rsmod.plugins.content.items.jewellery

import gg.rsmod.game.model.attr.RING_OF_FORGING_CHARGES

// K12 N11: this worn "Check" was bound but empty, so the census counted it as working while it did nothing. The charges are the
// ones SmeltingAction spends (140 per ring, OSRS Wiki); wording from Darkan (Smelting.checkChargesRingOfForging).
on_equipment_option(item = Items.RING_OF_FORGING, option = "Check") {
    val charges = player.attr[RING_OF_FORGING_CHARGES] ?: 140
    player.message("You have $charges charges remaining in your ring of forging.")
}
