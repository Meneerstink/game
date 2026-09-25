package gg.rsmod.plugins.content.inter.ge

import gg.rsmod.plugins.content.mechanics.exchange.GrandExchangeSounds

/*
 * Grand Exchange item sets (645 + side 644). Messages, the component-space rule and the GE_TRADE_OK / GE_TRADE_ERROR
 * sounds are 2009scape `GrandExchangeInterface` (sets branch).
 */
val geClerk = arrayOf(Npcs.GRAND_EXCHANGE_CLERK, Npcs.GRAND_EXCHANGE_CLERK_2240, Npcs.GRAND_EXCHANGE_CLERK_2241, Npcs.GRAND_EXCHANGE_CLERK_2593)

geClerk.forEach {
    on_npc_option(npc = it, option = "sets", lineOfSightDistance = 2) {
        openGeSets(player)
    }
}

on_interface_close(GE_SETS_INTERFACE) {
    player.closeInterface(interfaceId = GE_SETS_SIDE_INTERFACE)
    player.openInterface(dest = InterfaceDestination.INVENTORY_TAB)
    player.inventory.dirty = true
}

fun setComponents(p: Player, set: GeItemSet?) {
    if (set != null) {
        // RCV-010 C3-b: the 667 cache carries the real components text per set item (enum 1088).
        val text = p.world.definitions.getNullable(gg.rsmod.game.fs.def.EnumDef::class.java, 1088)?.values?.get(set.id) as? String
        if (text != null) {
            p.message(text)
        }
    } else {
        p.message("This isn't a set item.")
    }
}

fun unpackSet(player: Player, set: GeItemSet?) {
    if (set == null) {
        player.message("This isn't a set item.")
        return
    }
    if (!player.inventory.contains(set.id)) {
        return
    }
    // The set itself frees one slot.
    if (player.inventory.freeSlotCount < set.items.size - 1) {
        player.playSound(GrandExchangeSounds.TRADE_ERROR)
        player.message("You don't have enough inventory space for the component parts.")
        return
    }
    if (player.inventory.remove(set.id, 1).hasFailed()) {
        return
    }
    set.items.forEach { itemId ->
        player.inventory.add(itemId, 1)
    }
    player.playSound(GrandExchangeSounds.TRADE_OK)
    player.message("You successfully traded your set for its component items!")
}

fun packSet(player: Player, set: GeItemSet?) {
    if (set == null) {
        return
    }
    if (set.items.any { !player.inventory.contains(it) }) {
        player.playSound(GrandExchangeSounds.TRADE_ERROR)
        player.message("You don't have the parts that make up this set.")
        return
    }
    set.items.forEach { itemId ->
        player.inventory.remove(itemId, 1)
    }
    player.inventory.add(set.id, 1)
    player.playSound(GrandExchangeSounds.TRADE_OK)
    player.message("You successfully traded your item components for a set!")
}

fun handleButton(player: Player, interfaceId: Int) {
    val opcode = player.getInteractingOpcode()
    val itemId = player.getInteractingItemId()
    val set = GeItemSet.values().find { it.id == itemId || it.items.contains(itemId) }

    when (opcode) {
        61 -> setComponents(player, set)
        64 -> {
            when (interfaceId) {
                GE_SETS_INTERFACE -> packSet(player, set)
                GE_SETS_SIDE_INTERFACE -> unpackSet(player, set)
            }
        }
    }
}

on_button(interfaceId = GE_SETS_INTERFACE, component = 0) {
    handleButton(player, GE_SETS_INTERFACE)
}

on_button(interfaceId = GE_SETS_INTERFACE, component = 16) {
    handleButton(player, GE_SETS_INTERFACE)
}

on_button(interfaceId = GE_SETS_SIDE_INTERFACE, component = 0) {
    handleButton(player, GE_SETS_SIDE_INTERFACE)
}

on_button(interfaceId = GE_SETS_SIDE_INTERFACE, component = 16) {
    handleButton(player, GE_SETS_SIDE_INTERFACE)
}
