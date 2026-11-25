package gg.rsmod.plugins.content.skills.cooking

listOf(Items.STEW, Items.SPICY_STEW).forEach { stew ->
    SpicyStew.Colour.values().forEach { colour ->
        (colour.fourDose..colour.fourDose + 3).forEach { spice ->
            on_item_on_item(item1 = stew, item2 = spice) {
                // One spicy stew at a time (Wiki). Do not reset a previous recipe by creating a second stew.
                if (stew == Items.STEW && player.bank.contains(Items.SPICY_STEW)) return@on_item_on_item
                val slots = listOf(player.getInteractingItemSlot(), player.getInteractingOtherItemSlot())
                val stewSlot = slots.firstOrNull { player.inventory[it]?.id == stew } ?: return@on_item_on_item
                val spiceSlot = slots.firstOrNull { player.inventory[it]?.id == spice } ?: return@on_item_on_item
                SpicyStew.addDose(player.inventory, stewSlot, spiceSlot)
            }
        }
    }
}

on_item_option(item = Items.SPICY_STEW, option = "Smell") {
    val stew = player.inventory[player.getInteractingItemSlot()] ?: return@on_item_option
    if (stew.id == Items.SPICY_STEW) SpicyStew.smell(stew).forEach { player.message(it) }
}
