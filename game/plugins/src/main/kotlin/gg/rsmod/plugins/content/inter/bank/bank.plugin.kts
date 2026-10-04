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

    // E7: OSRS layout (script 172) - op1 the default quantity of the bank's quantity toggle, then 1 / 5 / 10 / X / All.
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

    /*
     * E7: the OSRS menu (script 669, patched 1453 in the 78 client): op1 the default quantity, Withdraw-1 / 5 / 10 / <n> /
     * X / All / All-but-1, 'Placeholder'; a placeholder (count 0) only has op8 'Release' and 'Examine' (script 278).
     */
    val action = BankOptions.withdrawAction(opcode, placeholder = Bank.isPlaceholder(item))
    if (action == null) {
        gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.INTERFACE) {
            "bank 762:93 opcode $opcode on ${item.id}x${item.amount} (slot $slot) for ${player.username} maps to no OSRS bank op (old client menu?)"
        }
        return@p
    }
    when (action) {
        BankOptions.WithdrawAction.EXAMINE -> world.sendExamine(player, item.id, ExamineEntityType.ITEM)
        BankOptions.WithdrawAction.RELEASE -> Bank.releasePlaceholder(player, slot)
        BankOptions.WithdrawAction.PLACEHOLDER -> Bank.withdraw(player, item.id, item.amount, slot, placeholder = true)
        BankOptions.WithdrawAction.X ->
            player.queue(TaskPriority.WEAK) {
                val input = inputInt(Bank.AMOUNT_PROMPT)
                if (input > 0) {
                    player.setVarp(Bank.LAST_X_INPUT, input)
                    Bank.withdraw(player, item.id, input, slot)
                }
            }
        else -> {
            val amount = Bank.withdrawAmount(player, opcode, item.amount) ?: return@p
            if (action == BankOptions.WithdrawAction.ALL_BUT_ONE && amount <= 0) {
                player.filterableMessage(Bank.ALL_BUT_ONE_MESSAGE)
                return@p
            }
            if (amount > 0) {
                Bank.withdraw(player, item.id, amount, slot)
            }
        }
    }
}

/*
 * E7: the 78 client's OSRS bank buttons on the root layer 762:0 (BankOptions.buttons): the quantity toggle 1 / 5 / 10 / X /
 * All ('Set custom quantity' on X) and 'Always set placeholders' ('Release all placeholders' as its second op).
 */
on_button(interfaceId = Bank.BANK_INTERFACE_ID, component = 0) p@{
    val pressed = BankOptions.pressed(player.getInteractingSlot(), player.getInteractingOpcode())
    if (pressed == null) {
        gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.INTERFACE) {
            "bank button 762:0 child ${player.getInteractingSlot()} opcode ${player.getInteractingOpcode()} for ${player.username} is no OSRS bank button"
        }
        return@p
    }
    val (press, mode) = pressed
    when (press) {
        BankOptions.ButtonPress.QUANTITY -> BankOptions.setQuantityMode(player, mode)
        BankOptions.ButtonPress.SET_CUSTOM ->
            player.queue(TaskPriority.WEAK) {
                val input = inputInt(Bank.AMOUNT_PROMPT)
                if (input > 0) {
                    player.setVarp(Bank.LAST_X_INPUT, input)
                    BankOptions.setQuantityMode(player, BankOptions.MODE_X)
                }
            }
        BankOptions.ButtonPress.TOGGLE_PLACEHOLDER -> BankOptions.setAlwaysPlaceholder(player, !BankOptions.alwaysPlaceholder(player))
        BankOptions.ButtonPress.RELEASE_ALL ->
            player.queue(TaskPriority.WEAK) {
                if (options("Yes", "No", title = "${BankOptions.RELEASE_ALL_PLACEHOLDERS}?") == 1) {
                    Bank.releaseAllPlaceholders(player)
                }
            }
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
