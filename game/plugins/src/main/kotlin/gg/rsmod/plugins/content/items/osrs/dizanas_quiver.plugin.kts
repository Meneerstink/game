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
        player.message("Your quiver now has ${DizanasQuiver.charges(charge.result)} charges.")
    }
}

/*
 * Second ammunition slot (rules and sources in DizanasQuiver / RangedAmmo). Fill is a Worn Equipment option (667 worn
 * menu param 528+, written by the import tool); Open and Empty are the quiver's own inventory options. Messages other
 * than the sourced "nothing to fill" text are ADAPTED.
 */
fun ammoName(id: Int) = world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, id).name

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
        on_item_option(item = quiverId, option = "Open") {
            val slot = player.attr[INTERACTING_ITEM_SLOT] ?: return@on_item_option
            val stored = DizanasQuiver.storedAmmo(player.inventory[slot])
            player.message(if (stored == null) "Your quiver holds no ammunition." else "Your quiver holds ${stored.amount} x ${ammoName(stored.id)}.")
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
}
