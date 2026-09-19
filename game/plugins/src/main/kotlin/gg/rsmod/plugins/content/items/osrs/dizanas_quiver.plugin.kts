package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.attr.INTERACTING_ITEM_SLOT
import gg.rsmod.game.model.attr.OTHER_ITEM_SLOT_ATTR

/**
 * OSRS-IMPORT Dizana's quiver charging (rules in [DizanasQuiver]): Sunfire splinters used on an uncharged or charged quiver
 * add one charge per splinter, up to 20,000. Messages are ADAPTED (no sourced OSRS text for charging).
 */

val chargeableQuivers = DizanasQuiver.CHARGED_FOR.keys + DizanasQuiver.CHARGED_FOR.values

chargeableQuivers.forEach { quiverId ->
    on_item_on_item(item1 = Items.SUNFIRE_SPLINTERS, item2 = quiverId) {
        val first = player.attr[INTERACTING_ITEM_SLOT] ?: return@on_item_on_item
        val second = player.attr[OTHER_ITEM_SLOT_ATTR] ?: return@on_item_on_item
        val quiverSlot = if (player.inventory[first]?.id in chargeableQuivers) first else second
        val quiver = player.inventory[quiverSlot] ?: return@on_item_on_item
        val splinters = player.inventory.getItemCount(Items.SUNFIRE_SPLINTERS)
        val charge = DizanasQuiver.charge(quiver, splinters)
        if (charge.added <= 0) {
            player.message("Your quiver cannot hold any more charges.")
            return@on_item_on_item
        }
        player.inventory.remove(Items.SUNFIRE_SPLINTERS, charge.added)
        player.inventory[quiverSlot] = charge.result
        player.attr[DizanasQuiver.SPLINTERS_CHARGED] = (player.attr[DizanasQuiver.SPLINTERS_CHARGED] ?: 0) + charge.added
        player.message("Your quiver now has ${DizanasQuiver.charges(charge.result)} charges.")
    }
}

/*
 * Second ammunition slot (rules and sources in DizanasQuiver / RangedAmmo). Fill is a Worn Equipment option (667 worn
 * menu param 528+, written by the import tool); Open and Empty are the quiver's own inventory options. Messages other
 * than the sourced "nothing to fill" text are ADAPTED.
 */
fun ammoName(id: Int) = world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, id).name

val QuiverUi = gg.rsmod.game.tools.importer.DizanasQuiverInterfaceImportTool

/** The inventory slot of the quiver whose interface is open. */
val OPEN_QUIVER_SLOT = gg.rsmod.game.model.attr.AttributeKey<Int>()

/** OSRS 592 "Charges: ..." text: a number while charged (OSRS default "Charges: 0"), "None" uncharged (owner picture). */
fun chargesText(quiver: Item): String =
    when {
        quiver.id in DizanasQuiver.BLESSED -> "Charges: Unlimited" // ADAPTED: blessed quivers have permanent Sunfire
        DizanasQuiver.charges(quiver) > 0 -> "Charges: ${"%,d".format(DizanasQuiver.charges(quiver))}"
        else -> "Charges: None"
    }

fun refreshQuiver(player: Player) {
    val slot = player.attr[OPEN_QUIVER_SLOT] ?: return
    val quiver = player.inventory[slot]?.takeIf { it.id in DizanasQuiver.AMMO_HOLDERS } ?: return
    val stored = DizanasQuiver.storedAmmo(quiver)
    player.setComponentItem(QuiverUi.INTERFACE_ID, QuiverUi.SLOT, stored?.id ?: -1, stored?.amount ?: 0)
    player.setComponentText(QuiverUi.INTERFACE_ID, QuiverUi.CHARGES, chargesText(quiver))
}

/*
 * Owner 2026-09-19 (picture "quiver i"): while the quiver window is open the inventory offers "Store" (OSRS: ammunition is stored
 * from the inventory side panel). The side panel is 667 interface 1125 - a plain inventory panel byte-identical to the GE sets
 * panel 644 and used nowhere else - whose grid over inv 93 is built by CS2 150 with the single op "Store" (IF_BUTTON1, opcode 61).
 */
val QUIVER_INVENTORY = 1125

fun openQuiver(
    player: Player,
    slot: Int,
) {
    player.attr[OPEN_QUIVER_SLOT] = slot
    player.openInterface(QuiverUi.INTERFACE_ID, InterfaceDestination.MAIN_SCREEN)
    player.setInterfaceEvents(QuiverUi.INTERFACE_ID, QuiverUi.CLOSE, -1..-1, 0x2)
    player.setInterfaceEvents(QuiverUi.INTERFACE_ID, QuiverUi.SLOT, -1..-1, 0x2 or 0x400)
    player.openInterface(QUIVER_INVENTORY, InterfaceDestination.INVENTORY_TAB)
    // Owner 2026-09-19 picture "store into diz": Store, Store-X and Store-All (ops 1-3 = IF_BUTTON1-3, opcodes 61/64/4).
    player.unlockIComponentOptionSlots(QUIVER_INVENTORY, 0, 0, 27, 0, 1, 2)
    player.runClientScript(150, QUIVER_INVENTORY shl 16, 93, 4, 7, 0, -1, "Store", "Store-X", "Store-All")
    refreshQuiver(player)
}

fun restoreInventoryTab(player: Player) {
    player.attr.remove(OPEN_QUIVER_SLOT)
    player.closeInterface(QUIVER_INVENTORY)
    player.openInterface(dest = InterfaceDestination.INVENTORY_TAB)
    player.inventory.dirty = true
}

on_button(interfaceId = QuiverUi.INTERFACE_ID, component = QuiverUi.CLOSE) {
    player.closeInterface(QuiverUi.INTERFACE_ID)
    restoreInventoryTab(player)
}

on_interface_close(interfaceId = QuiverUi.INTERFACE_ID) {
    restoreInventoryTab(player)
}

/** Stores up to [requested] of the item in inventory slot [invSlot] into the open quiver (ammunition) or charges it (splinters). */
fun storeIntoOpenQuiver(
    player: Player,
    invSlot: Int,
    itemId: Int,
    requested: Int,
) {
    val quiverSlot = player.attr[OPEN_QUIVER_SLOT] ?: return
    if (invSlot == quiverSlot || requested <= 0) return
    val quiver = player.inventory[quiverSlot]?.takeIf { it.id in DizanasQuiver.AMMO_HOLDERS } ?: return
    val item = player.inventory[invSlot]?.takeIf { it.id == itemId } ?: return
    if (item.id == Items.SUNFIRE_SPLINTERS && quiver.id in chargeableQuivers) {
        val charge = DizanasQuiver.charge(quiver, minOf(requested, player.inventory.getItemCount(item.id)))
        if (charge.added <= 0) {
            player.message("Your quiver cannot hold any more charges.")
            return
        }
        player.inventory.remove(item.id, charge.added)
        player.inventory[quiverSlot] = charge.result
        player.attr[DizanasQuiver.SPLINTERS_CHARGED] = (player.attr[DizanasQuiver.SPLINTERS_CHARGED] ?: 0) + charge.added
        player.message("Your quiver now has ${DizanasQuiver.charges(charge.result)} charges.")
        refreshQuiver(player)
        return
    }
    val offered = Item(item.id, minOf(requested, item.amount))
    when (val result = DizanasQuiver.fill(quiver, offered)) {
        is DizanasQuiver.FillResult.Filled -> {
            player.inventory.remove(item.id, result.moved, beginSlot = invSlot)
            player.inventory[quiverSlot] = result.quiver
            refreshQuiver(player)
        }
        DizanasQuiver.FillResult.NothingWorn -> Unit
        DizanasQuiver.FillResult.NotArrowOrBolt -> player.message("Dizana's quiver can only hold arrows or bolts.")
        DizanasQuiver.FillResult.DifferentAmmo -> player.message("Empty your quiver before filling it with a different type of ammunition.")
        DizanasQuiver.FillResult.Full -> player.message("Your quiver cannot hold any more ammunition.")
    }
}

on_button(interfaceId = QUIVER_INVENTORY, component = 0) {
    val invSlot = player.getInteractingSlot()
    val itemId = player.getInteractingItemId()
    when (player.getInteractingOpcode()) {
        61 -> storeIntoOpenQuiver(player, invSlot, itemId, 1)
        64 ->
            player.queue {
                val amount = inputInt("How many would you like to store?")
                storeIntoOpenQuiver(player, invSlot, itemId, amount)
            }
        4 -> storeIntoOpenQuiver(player, invSlot, itemId, Int.MAX_VALUE)
    }
}

on_button(interfaceId = QuiverUi.INTERFACE_ID, component = QuiverUi.SLOT) {
    val slot = player.attr[OPEN_QUIVER_SLOT] ?: return@on_button
    val quiver = player.inventory[slot]?.takeIf { it.id in DizanasQuiver.AMMO_HOLDERS } ?: return@on_button
    val stored = DizanasQuiver.storedAmmo(quiver) ?: return@on_button
    if (player.getInteractingOpcode() != 61) {
        world.sendExamine(player, stored.id, gg.rsmod.game.model.ExamineEntityType.ITEM)
        return@on_button
    }
    val added = player.inventory.add(stored.id, stored.amount).completed
    if (added <= 0) {
        player.message("You don't have enough inventory space.")
        return@on_button
    }
    player.inventory[slot] = DizanasQuiver.withStored(quiver, stored.id, stored.amount - added)
    refreshQuiver(player)
}

DizanasQuiver.AMMO_HOLDERS.forEach { quiverId ->
    val def = world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, quiverId)
    if (def.equipmentMenu.any { it.equals("Fill", ignoreCase = true) }) {
        on_equipment_option(item = quiverId, option = "Fill") {
            val quiver = player.getEquipment(EquipmentType.CAPE) ?: return@on_equipment_option
            when (val result = DizanasQuiver.fill(quiver, player.getEquipment(EquipmentType.AMMO))) {
                is DizanasQuiver.FillResult.Filled -> {
                    val ammo = player.getEquipment(EquipmentType.AMMO)!!
                    player.equipment.remove(ammo.id, result.moved)
                    player.equipment[EquipmentType.CAPE.id] = result.quiver
                    val stored = DizanasQuiver.storedAmmo(result.quiver)!!
                    player.message("Your quiver now holds ${stored.amount} x ${ammoName(stored.id)}.")
                }
                DizanasQuiver.FillResult.NothingWorn -> player.message(DizanasQuiver.NOTHING_TO_FILL_MESSAGE)
                DizanasQuiver.FillResult.NotArrowOrBolt -> player.message("Dizana's quiver can only hold arrows or bolts.")
                DizanasQuiver.FillResult.DifferentAmmo -> player.message("Empty your quiver before filling it with a different type of ammunition.")
                DizanasQuiver.FillResult.Full -> player.message("Your quiver cannot hold any more ammunition.")
            }
        }
    }
    if (def.equipmentMenu.any { it.equals("Check", ignoreCase = true) }) {
        on_equipment_option(item = quiverId, option = "Check") {
            val quiver = player.getEquipment(EquipmentType.CAPE) ?: return@on_equipment_option
            val stored = DizanasQuiver.storedAmmo(quiver)
            val ammo = if (stored == null) "no ammunition" else "${stored.amount} x ${ammoName(stored.id)}"
            player.message("Your quiver has ${DizanasQuiver.charges(quiver)} charges and holds $ammo.")
        }
    }
    if (def.inventoryMenu.any { it.equals("Open", ignoreCase = true) }) {
        // Owner 2026-09-18 (picture "dizana interface"): Open shows the OSRS quiver interface (OSRS 592, built as 667 interface 1150
        // by DizanasQuiverInterfaceImportTool) instead of a chat line.
        on_item_option(item = quiverId, option = "Open") {
            val slot = player.attr[INTERACTING_ITEM_SLOT] ?: return@on_item_option
            if (player.inventory[slot]?.id != quiverId) return@on_item_option
            openQuiver(player, slot)
        }
    }
    if (def.inventoryMenu.any { it.equals("Empty", ignoreCase = true) }) {
        on_item_option(item = quiverId, option = "Empty") {
            val slot = player.attr[INTERACTING_ITEM_SLOT] ?: return@on_item_option
            val quiver = player.inventory[slot] ?: return@on_item_option
            val stored = DizanasQuiver.storedAmmo(quiver)
            if (stored == null) {
                player.message("Your quiver holds no ammunition.")
                return@on_item_option
            }
            val added = player.inventory.add(stored.id, stored.amount).completed
            if (added <= 0) {
                player.message("You don't have enough inventory space.")
                return@on_item_option
            }
            player.inventory[slot] = DizanasQuiver.withStored(quiver, stored.id, stored.amount - added)
            player.message("You empty ${added} x ${ammoName(stored.id)} from your quiver.")
        }
    }
    // Owner answer 3 (OSRS_IMPORT_MASTER.yml "Owner answers 2026-09-14 ~19:00"): Uncharge -> Sunfire splinters.
    if (def.inventoryMenu.any { it.equals("Uncharge", ignoreCase = true) }) {
        on_item_option(item = quiverId, option = "Uncharge") {
            val slot = player.attr[INTERACTING_ITEM_SLOT] ?: return@on_item_option
            val quiver = player.inventory[slot] ?: return@on_item_option
            val result = DizanasQuiver.uncharge(quiver)
            if (result.added <= 0) {
                player.message("Your quiver has no charges to uncharge.")
                return@on_item_option
            }
            if (!player.inventory.add(Items.SUNFIRE_SPLINTERS, result.added, assureFullInsertion = true).hasSucceeded()) {
                player.message("You don't have enough inventory space to uncharge your quiver.")
                return@on_item_option
            }
            player.inventory[slot] = result.result
            player.message("You uncharge your quiver, recovering ${result.added} Sunfire splinters.")
        }
    }
}

/*
 * Worn Equipment second ammunition slot (owner 2026-09-18, picture "dizana slot"; OSRS Wiki: "The extra ammunition slot above the
 * original slot, only appearing if the quiver is ... equipped"). The client draws it with the 667 worn tab's spare slot 387:48
 * (the old aura slot, container slot 14), moved above the ammo slot (EquipmentInterfaceLayout). The server sends the stored
 * ammunition as display-only slot 14 of inv 94 and shows the slot only while a quiver (or Dizana's max cape) is worn; bonuses,
 * weight and appearance keep reading the real equipment. Remove puts the stored ammunition in the inventory, Examine examines it.
 */
val QUIVER_SLOT_COMPONENT = 48
val QUIVER_DISPLAY_SLOT = DizanasQuiver.DISPLAY_SLOT

/** Equipment Bonuses (667) slot background of the same display slot; the client moves it above the ammo slot (owner 2026-09-19). */
val BONUSES_QUIVER_SLOT_COMPONENT = 14

gg.rsmod.game.model.entity.Player.equipmentDisplay = { p, items ->
    val cape = items.getOrNull(EquipmentType.CAPE.id)
    val holder = cape != null && cape.id in DizanasQuiver.AMMO_HOLDERS
    p.setComponentHidden(interfaceId = 387, component = QUIVER_SLOT_COMPONENT, hidden = !holder)
    if (p.isInterfaceVisible(667)) {
        p.setComponentHidden(interfaceId = 667, component = BONUSES_QUIVER_SLOT_COMPONENT, hidden = !holder)
    }
    if (!holder) {
        items
    } else {
        Array(maxOf(items.size, QUIVER_DISPLAY_SLOT + 1)) { i -> if (i == QUIVER_DISPLAY_SLOT) DizanasQuiver.storedAmmo(cape) else items.getOrNull(i) }
    }
}

on_login {
    // The tab is built after the first equipment sync: resend so the slot's visibility and contents are right.
    player.equipment.dirty = true
}

on_button(interfaceId = 387, component = QUIVER_SLOT_COMPONENT) {
    val quiver = player.getEquipment(EquipmentType.CAPE)?.takeIf { it.id in DizanasQuiver.AMMO_HOLDERS } ?: return@on_button
    val stored = DizanasQuiver.storedAmmo(quiver) ?: return@on_button
    when (player.getInteractingOpcode()) {
        61 -> DizanasQuiver.removeWornStoredToInventory(player)
        25 -> world.sendExamine(player, stored.id, gg.rsmod.game.model.ExamineEntityType.ITEM)
    }
}

/*
 * Owner live report 2026-09-17c: "u cannot store arrows bolts ... in the dizana quiver". Filling only worked through the worn Fill
 * option (and only when the cache lists it); OSRS also takes ammunition used on the quiver in the inventory. Every arrow and bolt the
 * ranged engine knows can now be used on any quiver / Dizana's max cape: same rules as Fill (arrows or bolts only, one type at a time).
 */
val storableAmmo: Set<Int> =
    gg.rsmod.plugins.content.combat.strategy.ranged.RangedProjectile.values
        .filter { it.type == ProjectileType.ARROW || it.type == ProjectileType.BOLT }
        .flatMap { it.items.toList() }
        .toSet()

DizanasQuiver.AMMO_HOLDERS.forEach { quiverId ->
    storableAmmo.forEach { ammoId ->
        on_item_on_item(item1 = ammoId, item2 = quiverId) {
            val first = player.attr[INTERACTING_ITEM_SLOT] ?: return@on_item_on_item
            val second = player.attr[OTHER_ITEM_SLOT_ATTR] ?: return@on_item_on_item
            val quiverSlot = if (player.inventory[first]?.id == quiverId) first else second
            val ammoSlot = if (quiverSlot == first) second else first
            val quiver = player.inventory[quiverSlot]?.takeIf { it.id == quiverId } ?: return@on_item_on_item
            val ammo = player.inventory[ammoSlot]?.takeIf { it.id == ammoId } ?: return@on_item_on_item
            when (val result = DizanasQuiver.fill(quiver, ammo)) {
                is DizanasQuiver.FillResult.Filled -> {
                    player.inventory.remove(ammoId, result.moved, beginSlot = ammoSlot)
                    player.inventory[quiverSlot] = result.quiver
                    val stored = DizanasQuiver.storedAmmo(result.quiver)!!
                    player.message("Your quiver now holds ${stored.amount} x ${ammoName(stored.id)}.")
                }
                DizanasQuiver.FillResult.NothingWorn -> Unit
                DizanasQuiver.FillResult.NotArrowOrBolt -> player.message("Dizana's quiver can only hold arrows or bolts.")
                DizanasQuiver.FillResult.DifferentAmmo -> player.message("Empty your quiver before filling it with a different type of ammunition.")
                DizanasQuiver.FillResult.Full -> player.message("Your quiver cannot hold any more ammunition.")
            }
        }
    }
}
