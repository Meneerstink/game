package gg.rsmod.plugins.content.skills.hunter

HunterRelease.options.forEach { (id, option) ->
    if (!world.plugins.hasItemOption(id, option)) on_item_option(item = id, option = option) {
        if (option == 4 && player.timers.has(HunterRelease.BUTTERFLY_DELAY)) return@on_item_option
        val slot = player.getInteractingItemSlot()
        val held = player.inventory[slot]
        if (held != null && held.id == id) {
            val amount = held.amount
            if (id in HunterRelease.chinchompas && amount > 1) {
                player.queue {
                    val name = if (id == Items.CHINCHOMPA_10033) "chinchompas" else "red chinchompas"
                    if (options("Yes", "No", title = "Drop all of your $name?") == 1) {
                        HunterRelease.release(player, slot, held, amount)
                    }
                }
            } else if (HunterRelease.release(player, slot, held, amount) && option == 4) {
                player.timers[HunterRelease.BUTTERFLY_DELAY] = 3
            }
        }
    }
}
