package gg.rsmod.plugins.content.inter.pricecheck

/*
 * The client sends the op it was clicked with as one of ten opcodes rather than as an op number,
 * and `getInteractingOption()` reports the opcode's index in the IfButtonMessage opcode list of
 * data/packets.yml plus one, which is not the same order. Matching on the opcode itself is the one
 * reading that cannot drift if that list is ever reordered.
 */
private val OP1 = 61
private val OP2 = 64
private val OP3 = 4
private val OP4 = 52
private val OP5 = 81
private val OP6 = 91

/**
 * "Show Price-checker" on the worn-equipment tab. The component bakes op1 itself
 * (`387:42 events=0x000002`), so the click was always arriving - nothing was listening for it.
 */
on_button(interfaceId = 387, component = 42) {
    when (player.getInteractingOpcode()) {
        OP1 -> PriceChecker.open(player)
    }
}

/*
 * The inventory overlay (336:0) is NOT bound here. The trade screen uses the same bare overlay and
 * already owns that button hash, and the engine allows exactly one plugin per hash - binding it a
 * second time throws "Button hash already bound to a plugin" at boot and the server does not start.
 * The single binding lives in trading.plugin.kts and routes to PriceChecker.checkFromOverlay when a
 * price-check session is open.
 */

/*
 * The grid on the price checker itself. Clientscript 2184 labels these ops "Remove-1", "Remove-5",
 * "Remove-10", "Remove-All", "Remove-X" and "Examine", in that order.
 */
on_button(interfaceId = PriceChecker.INTERFACE_ID, component = PriceChecker.GRID_COMPONENT) {
    val container = PriceChecker.container(player) ?: return@on_button
    val slot = player.getInteractingSlot()
    val opcode = player.getInteractingOpcode()
    val item = container[slot] ?: return@on_button

    if (opcode == OP6) {
        world.sendExamine(player, item.id, ExamineEntityType.ITEM)
        return@on_button
    }

    player.queue(TaskPriority.WEAK) {
        val amount =
            when (opcode) {
                OP1 -> 1
                OP2 -> 5
                OP3 -> 10
                OP4 -> container.getItemCount(item.id)
                OP5 -> inputInt("Enter amount:")
                else -> return@queue
            }
        PriceChecker.uncheck(player, slot, amount)
    }
}

on_button(interfaceId = PriceChecker.INTERFACE_ID, component = PriceChecker.CLOSE_COMPONENT) {
    player.closeInterface(interfaceId = PriceChecker.INTERFACE_ID)
}

/*
 * Every way out of the screen goes through here - the Close button, walking away, or anything else
 * that closes a main-screen interface - so the checked items always come back.
 */
on_interface_close(interfaceId = PriceChecker.INTERFACE_ID) {
    PriceChecker.close(player)
}

on_logout {
    PriceChecker.close(player)
}
