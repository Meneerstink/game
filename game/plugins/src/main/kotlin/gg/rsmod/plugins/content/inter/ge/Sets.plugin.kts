package gg.rsmod.plugins.content.inter.ge

import gg.rsmod.game.fs.DefinitionSet

val GE_SETS_INTERFACE = 645
val INV_INTERFACE = 644

val geClerk = arrayOf(Npcs.GRAND_EXCHANGE_CLERK, Npcs.GRAND_EXCHANGE_CLERK_2240, Npcs.GRAND_EXCHANGE_CLERK_2241, Npcs.GRAND_EXCHANGE_CLERK_2593)

geClerk.forEach {
    on_npc_option(npc = it, option = "sets", lineOfSightDistance = 2) {
        openGESets(player)
    }
}

on_interface_close(GE_SETS_INTERFACE) {
    player.closeInterface(interfaceId = INV_INTERFACE)
    player.openInterface(dest = InterfaceDestination.INVENTORY_TAB)
    player.inventory.dirty = true
}

fun openGESets(p: Player) {
    p.openInterface(GE_SETS_INTERFACE, InterfaceDestination.MAIN_SCREEN)
    p.openInterface(INV_INTERFACE, InterfaceDestination.INVENTORY_TAB)
    p.unlockIComponentOptionSlots(GE_SETS_INTERFACE, 16, 0, 115, 0, 1)
    p.runClientScriptReversed(676)
    p.unlockIComponentOptionSlots(INV_INTERFACE, 0, 0, 27, 0, 1)
    p.runClientScript(150, INV_INTERFACE shl 16, 93, 4, 7, 0, -1, "Components", "Exchange")
}

fun setComponents(p: Player, set: GeItemSet?) {
    if (set != null) {
        // RCV-010 C3-b: the 667 cache carries the real components text per set item (enum 1088).
        val text = p.world.definitions.getNullable(gg.rsmod.game.fs.def.EnumDef::class.java, 1088)?.values?.get(set.id) as? String
        if (text != null) {
            p.message(text)
        }
    } else {
        p.message("No set found.")
    }
}

fun getItemName(definitions: DefinitionSet, itemId: Int): String {
    return definitions.get(ItemDef::class.java, itemId).name
}

fun unpackSet(player: Player, set: GeItemSet?) {
    if (set != null) {
        if (!player.inventory.contains(set.id)) {
            return
        }
        if (player.inventory.freeSlotCount < set.items.size) {
            player.message("You don't have enough space for that.")
            return
        }
        set.items.forEach { itemId ->
            player.inventory.add(itemId, 1)
        }
        player.inventory.remove(set.id, 1)
    }
}

fun packSet(player: Player, set: GeItemSet?) {
    if (set != null) {
        for (itemId in set.items) {
            if (!player.inventory.contains(itemId)) {
                val itemName = getItemName(player.world.definitions, itemId)
                player.message("You need a $itemName to exchange this set.")
                return
            }
        }
        set.items.forEach { itemId ->
            player.inventory.remove(itemId, 1)
        }
        player.inventory.add(set.id, 1)
    }
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
                INV_INTERFACE -> unpackSet(player, set)
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

on_button(interfaceId = INV_INTERFACE, component = 0) {
    handleButton(player, INV_INTERFACE)
}

on_button(interfaceId = INV_INTERFACE, component = 16) {
    handleButton(player, INV_INTERFACE)
}
