package gg.rsmod.plugins.content.mechanics.pvp

/*
 * Buttons of the OSRS "Loot keys" chest screen (LootKeyChest, interface 1149). The client sends the
 * clicked op as one of ten opcodes; these are the opcode ids of op1..op5 and op10 in
 * data/packets.yml (the same reading pricecheck.plugin.kts uses).
 */
private val OP1 = 61
private val OP2 = 64
private val OP3 = 4
private val OP4 = 52
private val OP5 = 81
private val OP10 = 91

on_button(interfaceId = LootKeyChest.INTERFACE_ID, component = LootKeyChest.CLOSE) {
    player.closeInterface(interfaceId = LootKeyChest.INTERFACE_ID)
}

for (tab in 0 until LootKeys.MAX_KEYS) {
    on_button(interfaceId = LootKeyChest.INTERFACE_ID, component = LootKeyChest.TAB_FIRST + tab * LootKeyChest.TAB_STRIDE) {
        LootKeyChest.select(player, tab)
    }
}

for (slot in 0 until LootKeyChest.SLOT_COUNT) {
    on_button(interfaceId = LootKeyChest.INTERFACE_ID, component = LootKeyChest.SLOT_FIRST + slot) {
        val item = LootKeyChest.storedAt(player, slot) ?: return@on_button
        val opcode = player.getInteractingOpcode()
        if (opcode == OP10) {
            val shown = LootKeyChest.shown(world.definitions, item, LootKeyChest.noteMode(player))
            world.sendExamine(player, shown.id, ExamineEntityType.ITEM)
            return@on_button
        }
        player.queue(TaskPriority.WEAK) {
            val amount =
                when (opcode) {
                    OP1 -> 1
                    OP2 -> 5
                    OP3 -> 10
                    OP4 -> item.amount
                    OP5 -> inputInt("Enter amount:")
                    else -> return@queue
                }
            if (LootKeyChest.isOpen(player)) {
                LootKeyChest.withdraw(player, slot, amount)
            }
        }
    }
}

on_button(interfaceId = LootKeyChest.INTERFACE_ID, component = LootKeyChest.DESTROY_BUTTON) {
    LootKeyChest.promptDestroy(player)
}

on_button(interfaceId = LootKeyChest.INTERFACE_ID, component = LootKeyChest.CONFIRM_BUTTON) {
    LootKeyChest.confirmDestroy(player)
}

on_button(interfaceId = LootKeyChest.INTERFACE_ID, component = LootKeyChest.CANCEL_BUTTON) {
    LootKeyChest.cancelDestroy(player)
}

on_button(interfaceId = LootKeyChest.INTERFACE_ID, component = LootKeyChest.ITEM_LAYER) {
    LootKeyChest.setNoteMode(player, noted = false)
}

on_button(interfaceId = LootKeyChest.INTERFACE_ID, component = LootKeyChest.NOTE_LAYER) {
    LootKeyChest.setNoteMode(player, noted = true)
}

on_button(interfaceId = LootKeyChest.INTERFACE_ID, component = LootKeyChest.INVENTORY_BUTTON) {
    LootKeyChest.withdrawAllToInventory(player)
}

on_button(interfaceId = LootKeyChest.INTERFACE_ID, component = LootKeyChest.BANK_BUTTON) {
    LootKeyChest.withdrawAllToBank(player)
}

/* Every way out of the screen (Close, walking away, another interface) drops the view state; the loot stays stored. */
on_interface_close(interfaceId = LootKeyChest.INTERFACE_ID) {
    LootKeyChest.close(player)
}

on_logout {
    LootKeyChest.close(player)
}
