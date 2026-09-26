package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.model.ExamineEntityType
import gg.rsmod.game.model.queue.TaskPriority
import gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool
import gg.rsmod.plugins.content.areas.deathsoffice.GravestoneWorld

/*
 * Buttons of the OSRS death screens (mechanics/death: GraveInterface, OfficeInterface, CofferInterface, ItemsKeptOnDeath).
 * Op opcodes from data/packets.yml, the same reading loot_key_chest.plugin.kts uses: op1 = 61, op10 = 91.
 */
private val OP1 = 61
private val OP10 = 91

private val Grave = DeathsOfficeInterfaceImportTool.Grave
private val Office = DeathsOfficeInterfaceImportTool.Office
private val Coffer = DeathsOfficeInterfaceImportTool.Coffer
private val Kept = DeathsOfficeInterfaceImportTool.Kept

fun examine(player: gg.rsmod.game.model.entity.Player, itemId: Int) {
    world.sendExamine(player, itemId, ExamineEntityType.ITEM)
}

// ---- gravestone ----

/** After any change: refresh the window, or close it (and remove the gravestone) once it is empty. */
fun afterGraveChange(player: gg.rsmod.game.model.entity.Player) {
    if (!Gravestone.exists(player)) {
        player.closeInterface(GraveInterface.INTERFACE_ID)
        GravestoneWorld.despawn(player)
    } else {
        GraveInterface.refresh(player)
    }
}

on_button(interfaceId = GraveInterface.INTERFACE_ID, component = GraveInterface.CLOSE) {
    player.closeInterface(GraveInterface.INTERFACE_ID)
}

for (i in 0 until Grave.SLOTS) {
    on_button(interfaceId = GraveInterface.INTERFACE_ID, component = Grave.FREE_SLOT_FIRST + i) {
        val slot = GraveInterface.freeSlot(player, i) ?: return@on_button
        val item = player.gravestone[slot] ?: return@on_button
        if (player.getInteractingOpcode() == OP10) {
            examine(player, item.id)
            return@on_button
        }
        GraveInterface.report(player, Gravestone.take(player, slot))
        afterGraveChange(player)
    }
}

// Owner 2026-09-26: the gravestone is free - there is no Unlock button, no fee section and no incinerator any more.
on_button(interfaceId = GraveInterface.INTERFACE_ID, component = Grave.FREE_BUTTON) {
    GraveInterface.report(player, Gravestone.takeAll(player))
    afterGraveChange(player)
}
on_interface_close(interfaceId = GraveInterface.INTERFACE_ID) {
    GraveInterface.close(player)
}

// ---- Death's Office ----

on_button(interfaceId = OfficeInterface.INTERFACE_ID, component = OfficeInterface.CLOSE) {
    player.closeInterface(OfficeInterface.INTERFACE_ID)
}

for (i in 0 until Office.SLOTS) {
    on_button(interfaceId = OfficeInterface.INTERFACE_ID, component = Office.SLOT_FIRST + i) {
        val slot = OfficeInterface.slotAt(player, i) ?: return@on_button
        val item = player.deathRecovery[slot] ?: return@on_button
        if (player.getInteractingOpcode() == OP10) {
            examine(player, item.id)
        } else {
            OfficeInterface.select(player, slot)
        }
    }
}

on_button(interfaceId = OfficeInterface.INTERFACE_ID, component = Office.SELECTED) {
    val slot = OfficeInterface.selected(player) ?: return@on_button
    if (player.getInteractingOpcode() == OP10) examine(player, player.deathRecovery[slot]!!.id)
}

on_button(interfaceId = OfficeInterface.INTERFACE_ID, component = Office.BUTTON_1) { OfficeInterface.take(player, 1) }
on_button(interfaceId = OfficeInterface.INTERFACE_ID, component = Office.BUTTON_5) { OfficeInterface.take(player, 5) }
on_button(interfaceId = OfficeInterface.INTERFACE_ID, component = Office.BUTTON_ALL) { OfficeInterface.take(player, Int.MAX_VALUE) }
on_button(interfaceId = OfficeInterface.INTERFACE_ID, component = Office.BUTTON_X) {
    player.queue(TaskPriority.WEAK) {
        val amount = inputInt("Enter amount:")
        if (amount > 0 && OfficeInterface.isOpen(player)) OfficeInterface.take(player, amount)
    }
}
on_button(interfaceId = OfficeInterface.INTERFACE_ID, component = Office.BUTTON_TAKE_ALL) { OfficeInterface.takeAll(player) }

on_interface_close(interfaceId = OfficeInterface.INTERFACE_ID) {
    OfficeInterface.close(player)
}

// ---- Death's Coffer ----

on_button(interfaceId = CofferInterface.INTERFACE_ID, component = CofferInterface.CLOSE) {
    player.closeInterface(CofferInterface.INTERFACE_ID)
}

on_button(interfaceId = CofferInterface.SIDE_ID, component = DeathsOfficeInterfaceImportTool.CofferSide.ROOT) {
    CofferInterface.select(player, player.getInteractingSlot())
}

on_button(interfaceId = CofferInterface.INTERFACE_ID, component = Coffer.BUTTON_1) { CofferInterface.setQuantity(player, 1) }
on_button(interfaceId = CofferInterface.INTERFACE_ID, component = Coffer.BUTTON_5) { CofferInterface.setQuantity(player, 5) }
on_button(interfaceId = CofferInterface.INTERFACE_ID, component = Coffer.BUTTON_ALL) { CofferInterface.setQuantity(player, CofferInterface.ALL) }
on_button(interfaceId = CofferInterface.INTERFACE_ID, component = Coffer.BUTTON_X) {
    player.queue(TaskPriority.WEAK) {
        val amount = inputInt("Enter amount:")
        if (amount > 0 && CofferInterface.isOpen(player)) CofferInterface.setQuantity(player, amount)
    }
}
on_button(interfaceId = CofferInterface.INTERFACE_ID, component = Coffer.CONFIRM) { CofferInterface.confirm(player) }

on_interface_close(interfaceId = CofferInterface.INTERFACE_ID) {
    CofferInterface.close(player)
}

// ---- Items Kept on Death ----

on_button(interfaceId = ItemsKeptOnDeath.INTERFACE_ID, component = ItemsKeptOnDeath.CLOSE) {
    player.closeInterface(ItemsKeptOnDeath.INTERFACE_ID)
}

for (t in 0 until 4) {
    on_button(interfaceId = ItemsKeptOnDeath.INTERFACE_ID, component = Kept.TOGGLE_FIRST + t * Kept.TOGGLE_STRIDE) {
        ItemsKeptOnDeath.toggle(player, t)
    }
}

for (i in 0 until Kept.SLOTS) {
    on_button(interfaceId = ItemsKeptOnDeath.INTERFACE_ID, component = Kept.SLOT_FIRST + i) {
        ItemsKeptOnDeath.check(player, i)
    }
}

on_interface_close(interfaceId = ItemsKeptOnDeath.INTERFACE_ID) {
    ItemsKeptOnDeath.close(player)
}
