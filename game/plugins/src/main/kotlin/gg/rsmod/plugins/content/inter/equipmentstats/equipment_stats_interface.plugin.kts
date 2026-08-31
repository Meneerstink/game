package gg.rsmod.plugins.content.inter.equipmentstats

import gg.rsmod.game.action.EquipAction
import gg.rsmod.plugins.content.mechanics.practicepvp.PracticePvp

val EQUIPMENT_BONUS_INTERFACE_ID = 667
val INVENTORY_INTERFACE_ID = 670
val EQUIP_ITEM_SOUND = 2238

on_button(interfaceId = 387, component = 39) {
    when (player.getInteractingOpcode()) {
        61 -> openEquipmentBonuses(player, false)
    }
}

on_interface_close(interfaceId = EQUIPMENT_BONUS_INTERFACE_ID) {
    player.closeInterface(interfaceId = INVENTORY_INTERFACE_ID)
    player.openInterface(dest = InterfaceDestination.INVENTORY_TAB)
    player.inventory.dirty = true
}

fun openEquipmentBonuses(
    player: Player,
    bank: Boolean,
) {
    player.queue {
        player.openInterface(interfaceId = EQUIPMENT_BONUS_INTERFACE_ID, dest = InterfaceDestination.MAIN_SCREEN)
        player.setVarbit(Varbits.IS_BANK_EQUIPMENT_INTERFACE, if (bank) 1 else 0)
        player.openInterface(INVENTORY_INTERFACE_ID, dest = InterfaceDestination.TAB_AREA)
        // Audit finding 8: these 3 lines (plus a 4th, `runClientScript(787, 1)//unknown`, already
        // removed) are not an unfinished guess - they were real code, disabled 3+ years ago in
        // commit 077ddfc4 ("chore: disable some equipment bonus screen configs, caused crash").
        // No decoded interface-667/670 component layout exists in this environment (same
        // interface-cache blocker as R03.4/GE - see OWNER_TASK_STATUS.md) to verify what actually
        // changed between "crashes" and "correct", so re-enabling them here would be reintroducing
        // a historically real crash on a guess, not a fix. Needs a live client to test safely.
        // player.setInterfaceEvents(interfaceId = INVENTORY_INTERFACE_ID, component = 0, from = 0, to = 27, 1538)
        // player.runClientScript(150, INVENTORY_INTERFACE_ID shl 16, 93, 0, 1, 2, 3)
        // player.setInterfaceEvents(interfaceId = EQUIPMENT_BONUS_INTERFACE_ID, component = 7, from = 0, to = 15, 1538)
        player.refreshBonuses()
    }
}

// R04.7 fix: component 7 on interface 667 is the WORN-ITEMS grid, not the inventory - it was
// previously routed through the same handler as the 670 embedded inventory below, which always
// looked the clicked item up in `player.inventory[slot]`. Clicking a worn item to remove it via
// this screen therefore silently did nothing (the item isn't in the inventory container, it's
// in equipment) - "checking the bonus math is correct proves nothing about this click handler."
// Confirmed real bug, not a hypothetical: EquipAction.unequip() takes an equipment-slot id
// (0=head, 1=cape, ... matching EquipmentType), which `slot` here already is once you're
// clicking inside the 667 worn-items component, not an inventory index.
on_button(interfaceId = EQUIPMENT_BONUS_INTERFACE_ID, component = 7) {
    val opcode = player.getInteractingOpcode()
    val item = player.getInteractingItemId()
    val slot = player.getInteractingSlot()
    when (opcode) {
        61 -> {
            val worn = player.equipment[slot]
            if (worn != null && worn.id == item) {
                // Finding 9 (audit): same Practice PvP temp-gear leak as the ordinary
                // equipment tab (equipment.plugin.kts) - this is the other real call site
                // of EquipAction.unequip, so it needs the same guard.
                if (PracticePvp.isHoldingTempGear(player)) {
                    player.filterableMessage("You can't remove free Practice PvP gear - it's cleared automatically when the match ends.")
                    return@on_button
                }
                val result = EquipAction.unequip(player, slot)
                if (result == EquipAction.Result.SUCCESS) {
                    player.sendWeaponComponentInformation()
                    player.refreshBonuses()
                } else if (result == EquipAction.Result.UNHANDLED && world.devContext.debugItemActions) {
                    player.message("Unhandled unequip action: [item=$item, slot=$slot]", type = ChatMessageType.CONSOLE)
                }
            }
        }
        20 -> showStats(player, item)
        25 -> world.sendExamine(player, item, ExamineEntityType.ITEM)
        else -> player.message("Unhandled Equipment Stats interface opcode: $opcode", type = ChatMessageType.CONSOLE)
    }
}

on_button(interfaceId = INVENTORY_INTERFACE_ID, component = 0) {
    val opcode = player.getInteractingOpcode()
    val item = player.getInteractingItemId()
    val slot = player.getInteractingSlot()
    when (opcode) {
        61 -> {
            // Same underlying action as the normal inventory's Wear/Wield (op held 2) - this
            // component is the screen's own embedded inventory (interface 670), so `slot` is a
            // real inventory index, unlike component 7 above.
            val inventoryItem = player.inventory[slot]
            if (inventoryItem != null && inventoryItem.id == item) {
                val result = EquipAction.equip(player, inventoryItem, slot)
                if (result == EquipAction.Result.UNHANDLED && world.devContext.debugItemActions) {
                    player.message("Unhandled equip action: [item=$item, slot=$slot]", type = ChatMessageType.CONSOLE)
                }
            }
        }
        20 -> showStats(player, item)
        25 -> world.sendExamine(player, item, ExamineEntityType.ITEM)
        else -> player.message("Unhandled Equipment Stats interface opcode: $opcode", type = ChatMessageType.CONSOLE)
    }
}

// Same bonus-name/order convention as Player.refreshBonuses (PlayerExt.kt) - kept local since
// that one is private to its own function and this is a different rendering (chat, not
// interface text). R04.7: this replaces a hard TODO() crash - clicking "Stats" on any item in
// the equip-bonus screen previously threw NotImplementedError. A real hover/popup stats panel
// needs a verified interface component id this codebase doesn't have yet (see R04.7 ledger
// note); a chat summary is a working, non-crashing simplification in the meantime.
// Indices match BonusSlot (BonusSlot.kt) exactly: 0-4 attack, 5-9 defence, 10 summoning,
// 11-13 absorb (not in BonusSlot but used by NpcCombatDsl/refreshBonuses), 14-17 the rest.
private val BONUS_NAMES =
    listOf(
        "Attack Stab", "Attack Slash", "Attack Crush", "Attack Magic", "Attack Ranged",
        "Defence Stab", "Defence Slash", "Defence Crush", "Defence Magic", "Defence Ranged",
        "Summoning", "Absorb Melee", "Absorb Magic", "Absorb Ranged",
        "Strength", "Ranged Strength", "Prayer", "Magic Damage",
    )

fun showStats(
    player: Player,
    item: Int,
) {
    val def = player.world.definitions.get(ItemDef::class.java, item)
    if (def.equipSlot == -1) {
        return
    }
    val lines =
        def.bonuses
            .toList()
            .mapIndexedNotNull { i, bonus -> if (bonus != 0) "${BONUS_NAMES[i]}: ${if (bonus >= 0) "+" else ""}$bonus" else null }
    player.message(
        if (lines.isEmpty()) "${def.name} has no bonuses." else "${def.name}: ${lines.joinToString(", ")}",
        type = ChatMessageType.CONSOLE,
    )
}
