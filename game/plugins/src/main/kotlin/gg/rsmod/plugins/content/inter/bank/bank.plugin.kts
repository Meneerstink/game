package gg.rsmod.plugins.content.inter.bank

import gg.rsmod.game.model.attr.INTERACTING_ITEM_SLOT
import gg.rsmod.game.model.attr.OTHER_ITEM_SLOT_ATTR

on_interface_open(Bank.BANK_INTERFACE_ID) {
    Bank.cleanEmptySlots(player)
}

on_interface_close(Bank.BANK_INTERFACE_ID) {
    player.closeInterface(dest = InterfaceDestination.TAB_AREA)
    player.closeInputDialog()
    gg.rsmod.plugins.content.mechanics.pvp.BankSecurity.onBankClosed(player)
}

on_interface_close(Bank.DEPOSIT_BOX_INTERFACE_ID) {
    gg.rsmod.plugins.content.mechanics.pvp.BankSecurity.onBankClosed(player)
}

on_button(interfaceId = Bank.BANK_INTERFACE_ID, component = 44) {
    player.openInterface(Bank.BANK_HELP_INTERFACE_ID, InterfaceDestination.MAIN_SCREEN)

    player.setComponentHidden(Bank.BANK_HELP_INTERFACE_ID, 54, false)
}

on_button(interfaceId = Bank.BANK_HELP_INTERFACE_ID, component = 54) {
    Bank.open(player)
}

/*
 * 'Set a Bank PIN'. The cache bakes op1=IF_BUTTON1 into this component, so the click was already
 * arriving here - there was simply nothing listening to it.
 */
on_button(interfaceId = Bank.BANK_INTERFACE_ID, component = 40) {
    BankPin.manage(player)
}

/*
 * RCV-012.B17: the PIN keypad's digit cells are interface 759 (mounted into 13:5) and its Exit is 13:25, both op1 = IF_BUTTON1 in
 * the cache - not pause buttons (BankPin cache contract).
 */
BankPin.DIGIT_COMPONENTS.forEach { component ->
    on_button(interfaceId = BankPin.DIGITS_INTERFACE_ID, component = component) {
        BankPin.pressDigit(player, component)
    }
}

on_button(interfaceId = BankPin.PIN_INTERFACE_ID, component = BankPin.EXIT_COMPONENT) {
    BankPin.pressExit(player)
}

on_button(interfaceId = Bank.BANK_INTERFACE_ID, component = 33) {
    Bank.depositInventory(player)
}

on_button(interfaceId = Bank.BANK_INTERFACE_ID, component = 35) {
    Bank.depositEquipment(player)
}

/* E7: 762:37 'Deposit beast of burden inventory' had no handler. */
on_button(interfaceId = Bank.BANK_INTERFACE_ID, component = Bank.DEPOSIT_BOB_COMPONENT) {
    Bank.depositBeastOfBurden(player)
}

on_button(interfaceId = Bank.DEPOSIT_BOX_INTERFACE_ID, component = 18) {
    Bank.depositInventory(player)
}

on_button(interfaceId = Bank.DEPOSIT_BOX_INTERFACE_ID, component = 20) {
    Bank.depositEquipment(player)
}

/* E7: 11:22 'Deposit beast of burden inventory' had no handler. */
on_button(interfaceId = Bank.DEPOSIT_BOX_INTERFACE_ID, component = Bank.DEPOSIT_BOX_BOB_COMPONENT) {
    Bank.depositBeastOfBurden(player)
}

/*
 * 762:17 'Search': the client runs the search itself (onOp CS2 1471) and clears varc 190; re-arm it so the button works
 * again. This used to set varp 190 (a different variable) and run 1472 while varc 190 was still 0, so 1472 did nothing.
 */
on_button(interfaceId = Bank.BANK_INTERFACE_ID, component = Bank.SEARCH_COMPONENT) {
    Bank.armSearch(player)
}

/*
 * E7: 762:117 'Show Equipment Stats' (OSRS 'Show worn items') and the way back, 667:49 'Show bank'.
 */
on_button(interfaceId = Bank.BANK_INTERFACE_ID, component = Bank.EQUIPMENT_STATS_COMPONENT) {
    if (player.getInteractingOpcode() == 61) {
        Bank.showEquipmentStats(player)
    }
}

on_button(interfaceId = Bank.EQUIPMENT_BONUS_INTERFACE_ID, component = Bank.SHOW_BANK_COMPONENT) {
    if (player.getInteractingOpcode() == 61) {
        Bank.showBank(player)
    }
}

on_button(interfaceId = Bank.DEPOSIT_BOX_INTERFACE_ID, component = 17) p@{
    val slot = player.getInteractingSlot()
    val opcode = player.getInteractingOpcode()
    val item = player.inventory[slot] ?: return@p

    if (opcode == 25) {
        world.sendExamine(player, item.id, ExamineEntityType.ITEM)
        return@p
    }

    val amount = Bank.depositBoxAmount(opcode, player.inventory.getItemCount(item.id)) ?: return@p
    if (amount == -1) {
        player.queue(TaskPriority.WEAK) {
            val input = inputInt(Bank.AMOUNT_PROMPT)
            if (input > 0) {
                player.setVarp(Bank.LAST_X_INPUT, input)
                Bank.deposit(player, player.inventory, slot, input)
            }
        }
        return@p
    }
    if (amount > 0) {
        Bank.deposit(player, player.inventory, slot, amount)
    }
}

on_button(interfaceId = Bank.INV_INTERFACE_ID, component = Bank.INV_INTERFACE_CHILD) p@{
    val slot = player.getInteractingSlot()
    val opcode = player.getInteractingOpcode()
    val item = player.inventory[slot] ?: return@p

    if (opcode == 25) {
        world.sendExamine(player, item.id, ExamineEntityType.ITEM)
        return@p
    }

    val amount = Bank.bankInventoryDepositAmount(player, opcode, player.inventory.getItemCount(item.id)) ?: return@p
    if (amount == -1) {
        player.queue(TaskPriority.WEAK) {
            val input = inputInt(Bank.AMOUNT_PROMPT)
            if (input > 0) {
                player.setVarp(Bank.LAST_X_INPUT, input)
                Bank.deposit(player, player.inventory, slot, input)
            }
        }
        return@p
    }
    if (amount > 0) {
        Bank.deposit(player, player.inventory, slot, amount)
    }
}

on_button(interfaceId = Bank.BANK_INTERFACE_ID, component = 93) p@{
    val slot = player.getInteractingSlot()
    val opcode = player.getInteractingOpcode()

    val item = player.bank[slot] ?: return@p

    if (opcode == 25) {
        world.sendExamine(player, item.id, ExamineEntityType.ITEM)
        return@p
    }

    val amount = Bank.withdrawAmount(player, opcode, item.amount) ?: return@p

    if (amount == -1) {
        /**
         * Placeholders' "release" option uses the same option
         * as "withdraw-x" would.
         */
        if (item.amount == 0) {
            player.bank[slot] = null
            return@p
        }
        player.queue(TaskPriority.WEAK) {
            val input = inputInt(Bank.AMOUNT_PROMPT)
            if (input > 0) {
                player.setVarp(Bank.LAST_X_INPUT, input)
                Bank.withdraw(player, item.id, input, slot)
            }
        }
        return@p
    }

    if (opcode == 18 && amount <= 0) {
        player.filterableMessage(Bank.ALL_BUT_ONE_MESSAGE)
        return@p
    }
    if (amount > 0) {
        Bank.withdraw(player, item.id, amount, slot)
    }
}

on_button(interfaceId = Bank.BANK_INTERFACE_ID, component = 15) {
    player.toggleVarp(Bank.REARRANGE_MODE_VARP)
}

on_button(interfaceId = Bank.BANK_INTERFACE_ID, component = 19) {
    player.toggleVarp(Bank.WITHDRAW_AS_VARB)
}

/**
 * Swap items in bank inventory interface.
 */
on_component_to_component_item_swap(
    srcInterfaceId = Bank.INV_INTERFACE_ID,
    srcComponent = Bank.INV_INTERFACE_CHILD,
    dstInterfaceId = Bank.INV_INTERFACE_ID,
    dstComponent = Bank.INV_INTERFACE_CHILD,
) {
    val srcSlot = player.attr[INTERACTING_ITEM_SLOT]!!
    val dstSlot = player.attr[OTHER_ITEM_SLOT_ATTR]!!

    val container = player.inventory

    if (srcSlot in 0 until container.capacity && dstSlot in 0 until container.capacity) {
        container.swap(srcSlot, dstSlot)
    }
    container.dirty = true
}

/**
 * Swap items in main bank tab.
 */
on_component_to_component_item_swap(
    srcInterfaceId = Bank.BANK_INTERFACE_ID,
    srcComponent = Bank.BANK_MAINTAB_COMPONENT,
    dstInterfaceId = Bank.BANK_INTERFACE_ID,
    dstComponent = Bank.BANK_MAINTAB_COMPONENT,
) {
    val srcSlot = player.attr[INTERACTING_ITEM_SLOT]!!
    val dstSlot = player.attr[OTHER_ITEM_SLOT_ATTR]!!

    val container = player.bank

    if (srcSlot in 0 until container.occupiedSlotCount && dstSlot in 0 until container.occupiedSlotCount) {
        val insertMode = player.getVarp(Bank.REARRANGE_MODE_VARP) == 1
        if (!insertMode) {
            Bank.swap(player, srcSlot, dstSlot)
        } else {
            Bank.tabSafeInsert(player, srcSlot, dstSlot)
        }
    } else {
        // Sync the container on the client
        container.dirty = true
    }
}
