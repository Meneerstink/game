package gg.rsmod.plugins.content.inter.equipmentstats

import gg.rsmod.game.action.EquipAction
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.combat.CombatConfigs
import gg.rsmod.plugins.content.mechanics.practicepvp.PracticePvp

val EQUIPMENT_BONUS_INTERFACE_ID = 667
val INVENTORY_INTERFACE_ID = 670
val EQUIP_ITEM_SOUND = 2238
val EQUIPMENT_STATS_NAME_VARCSTR = 321
val EQUIPMENT_STATS_TITLES_VARCSTR = 322
val EQUIPMENT_STATS_NAMES_VARCSTR = 323
val EQUIPMENT_STATS_VALUES_VARCSTR = 324
val EQUIPMENT_STATS_COMPARISON_VARCSTR = 325
val EQUIPMENT_STATS_DONE_COMPONENT = 65

on_button(interfaceId = 387, component = 39) {
    when (player.getInteractingOpcode()) {
        61 -> openEquipmentBonuses(player, false)
    }
}

on_interface_close(interfaceId = EQUIPMENT_BONUS_INTERFACE_ID) {
    clearEquipmentStats(player)
    if (player.interfaces.isVisible(INVENTORY_INTERFACE_ID)) {
        player.closeInterface(interfaceId = INVENTORY_INTERFACE_ID)
    }
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
        // Audit finding 8 / R05.1: two of these three lines were real code, disabled 3+ years ago
        // in commit 077ddfc4 ("chore: disable some equipment bonus screen configs, caused crash").
        // The interface-cache blocker that previously stopped us re-enabling them is gone: the
        // `layout`/`compref` modes of InterfaceHookProbeTool now decode the real 667/670 component
        // layout straight out of the production cache, and they prove:
        //   * 667:7 (worn grid) and 670:0 (embedded inventory) both have a baked events mask of 0,
        //     so InterfaceManager.ifButtonXSend() returns before transmitting anything - the CS2
        //     onOp hook still draws the menu entry, which is exactly the reported symptom
        //     ("menu appears, clicking does nothing").
        //   * 667:0's onLoad (CS2 787 -> 2373) builds the worn grid over inv 94 with
        //     cc_setop(1,"Remove"), cc_setop(9,"Stats"), cc_setop(10,"Examine"); 670:0's onLoad
        //     (CS2 2737 -> 2739) builds the embedded inventory over inv 93 with
        //     cc_setop(1,"Equip"), cc_setop(9,"Stats"), cc_setop(10,"Examine").
        //   * cc_create gives each dynamic child `slot = parent.slot`, `id = child index`, and
        //     IF_SETEVENTS keys on (idAndSlot << 32) + component - so these two calls address
        //     exactly those children, and the delivered slot is the equipment-slot id (667:7) or
        //     inventory index (670:0) the handlers below already assume.
        // 1538 = 0b110_0000_0010 = bits 1|9|10 = ops 1, 9, 10 = IF_BUTTON1(61), IF_BUTTON9(20),
        // IF_BUTTON10(25), matching the `when (opcode)` branches of both handlers below.
        // The third line stays deleted: CS2 150 is CC_CREATE, not a callable script here, and
        // 670:0's own onLoad already builds the grid - that call is the likely origin of the crash.
        player.setInterfaceEvents(interfaceId = INVENTORY_INTERFACE_ID, component = 0, range = 0 until player.inventory.capacity, setting = 1538)
        player.setInterfaceEvents(interfaceId = EQUIPMENT_BONUS_INTERFACE_ID, component = 7, range = 0 until player.equipment.capacity, setting = 1538)
        player.refreshBonuses()
        // Resend inv 94 so the equipment display hook shows/hides the Dizana's quiver slot 667:14 for this interface too.
        player.equipment.dirty = true
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
    // Dizana's quiver second ammo slot: display-only slot 14 of inv 94 (dizanas_quiver.plugin.kts), not a real equipment slot.
    if (slot == gg.rsmod.plugins.content.items.osrs.DizanasQuiver.DISPLAY_SLOT && player.equipment[slot] == null) {
        when (opcode) {
            61 -> if (gg.rsmod.plugins.content.items.osrs.DizanasQuiver.removeWornStoredToInventory(player)) player.refreshBonuses()
            25 -> world.sendExamine(player, item, ExamineEntityType.ITEM)
        }
        return@on_button
    }
    when (opcode) {
        61 -> {
            // Owner 2026-09-25 "interfaces blijven hangen" (live log: 667:7 clicked 3 times, nothing removed): the clicked grid
            // index did not always name the equipment slot holding the clicked item, and a mismatch was silently ignored. The
            // clicked item decides; the delivered index is used when it matches, otherwise the slot that really holds the item.
            val wornSlot =
                if (player.equipment[slot]?.id == item) slot
                else (0 until player.equipment.capacity).firstOrNull { player.equipment[it]?.id == item } ?: -1
            if (wornSlot != slot) {
                gg.rsmod.game.model.AvTrace.log { "equipment stats remove: clicked slot=$slot item=$item -> worn slot=$wornSlot" }
            }
            val worn = if (wornSlot >= 0) player.equipment[wornSlot] else null
            if (worn != null) {
                val slot = wornSlot
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

// 667:65 is the cache's baked "Done" button for the item-statistics layer.  The old route
// handled the item menu but never handled this button, leaving the varc strings alive after
// closing the popup and causing the next item to inherit stale text.
on_button(interfaceId = EQUIPMENT_BONUS_INTERFACE_ID, component = EQUIPMENT_STATS_DONE_COMPONENT) {
    if (player.getInteractingOpcode() == 61) {
        clearEquipmentStats(player)
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

fun showStats(
    player: Player,
    item: Int,
) {
    val def = player.world.definitions.get(ItemDef::class.java, item)
    if (def.equipSlot == -1) {
        clearEquipmentStats(player)
        return
    }

    val textLayout = EquipmentStatsTextLayout()

    fun appendTitle(title: String) {
        textLayout.title(title)
    }

    fun appendRow(label: String, value: String) {
        textLayout.row(label, value)
    }

    fun bonusValue(index: Int): String {
        val bonus = def.bonuses[index]
        val sign = if (bonus >= 0) "+" else ""
        return "$sign$bonus"
    }

    fun percentValue(tenths: Int): String {
        val value = tenths / 10.0
        return String.format(java.util.Locale.ROOT, "%+.1f%%", value)
    }

    appendTitle("Attack bonus")
    listOf("Stab" to 0, "Slash" to 1, "Crush" to 2, "Magic" to 3, "Ranged" to 4).forEach { (label, index) ->
        appendRow(label, bonusValue(index))
    }

    appendTitle("Defence bonus")
    listOf("Stab" to 5, "Slash" to 6, "Crush" to 7, "Magic" to 8, "Ranged" to 9).forEach { (label, index) ->
        appendRow(label, bonusValue(index))
    }

    appendTitle("Other bonuses")
    appendRow("Melee Str.", bonusValue(14))
    appendRow("Ranged Str.", bonusValue(15))
    appendRow("Magic Dmg.", percentValue(def.bonuses[17]))
    appendRow("Prayer", bonusValue(16))

    appendTitle("Target-specific")
    val targetSpecific = targetSpecificBonuses(item)
    appendRow("Undead", targetSpecific.first)
    appendRow("Slayer", targetSpecific.second)

    val baseSpeed = def.attackSpeed.takeIf { it > 0 }
    if (baseSpeed != null) {
        appendTitle("Weapon speed")
        val actualSpeed =
            if (player.equipment[EquipmentType.WEAPON.id]?.id == item) {
                CombatConfigs.getAttackDelay(player)
            } else {
                baseSpeed
            }
        appendRow("Base", formatWeaponSpeed(baseSpeed))
        appendRow("Actual", formatWeaponSpeed(actualSpeed))
    }

    val columns = textLayout.columns()
    player.setVarcString(EQUIPMENT_STATS_NAME_VARCSTR, def.name)
    player.setVarcString(EQUIPMENT_STATS_TITLES_VARCSTR, columns.titles)
    player.setVarcString(EQUIPMENT_STATS_NAMES_VARCSTR, columns.names)
    player.setVarcString(EQUIPMENT_STATS_VALUES_VARCSTR, columns.values)
    player.setVarcString(EQUIPMENT_STATS_COMPARISON_VARCSTR, "")
}

fun clearEquipmentStats(player: Player) {
    player.setVarcString(EQUIPMENT_STATS_NAME_VARCSTR, "")
    player.setVarcString(EQUIPMENT_STATS_TITLES_VARCSTR, "")
    player.setVarcString(EQUIPMENT_STATS_NAMES_VARCSTR, "")
    player.setVarcString(EQUIPMENT_STATS_VALUES_VARCSTR, "")
    player.setVarcString(EQUIPMENT_STATS_COMPARISON_VARCSTR, "")
}

fun formatWeaponSpeed(ticks: Int): String =
    String.format(java.util.Locale.ROOT, "%.1fs", ticks * 0.6)

fun targetSpecificBonuses(item: Int): Pair<String, String> =
    when (item) {
        Items.SALVE_AMULET -> "16.7%" to "0%"
        Items.SALVE_AMULET_E -> "20%" to "0%"
        Items.BLACK_MASK,
        Items.BLACK_MASK_1,
        Items.BLACK_MASK_2,
        Items.BLACK_MASK_3,
        Items.BLACK_MASK_4,
        Items.BLACK_MASK_5,
        Items.BLACK_MASK_6,
        Items.BLACK_MASK_7,
        Items.BLACK_MASK_8,
        Items.BLACK_MASK_9,
        Items.BLACK_MASK_10,
        Items.SLAYER_HELMET,
        Items.SLAYER_HELMET_E,
        Items.SLAYER_HELMET_CHARGED,
        Items.FULL_SLAYER_HELMET,
        Items.FULL_SLAYER_HELMET_E,
        Items.FULL_SLAYER_HELMET_CHARGED -> "0%" to "16.7%"
        else -> "0%" to "0%"
    }
