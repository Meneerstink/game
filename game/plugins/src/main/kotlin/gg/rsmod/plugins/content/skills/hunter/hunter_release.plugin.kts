package gg.rsmod.plugins.content.skills.hunter

HunterRelease.options.forEach { (id, option) ->
    if (!world.plugins.hasItemOption(id, option)) on_item_option(item = id, option = option) {
        val slot = player.getInteractingItemSlot()
        val held = player.inventory[slot]
        if (held != null && held.id == id) {
            val amount = held.amount
            if (id == Items.RED_CHINCHOMPA_10034 && amount > 1) {
                player.queue {
                    if (options("Yes", "No", title = "Drop all of your red chinchompas?") == 1) {
                        HunterRelease.release(player, slot, held, amount)
                    }
                }
            } else HunterRelease.release(player, slot, held, amount)
        }
    }
}
