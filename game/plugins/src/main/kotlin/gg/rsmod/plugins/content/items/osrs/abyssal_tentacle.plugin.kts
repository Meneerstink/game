package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.INTERACTING_ITEM_SLOT
import gg.rsmod.game.model.attr.OTHER_ITEM_SLOT_ATTR

/**
 * Abyssal tentacle: creation, Check, Dissolve and combining charges (rules and sources in [AbyssalTentacle]). The OSRS menus come
 * from AbyssalTentacleMenuTool; each binding is skipped while the cache does not carry its option yet. Messages are ADAPTED.
 */

fun tentacleHas(
    option: String,
    worn: Boolean = false,
): Boolean {
    val def = world.definitions.get(ItemDef::class.java, Items.ABYSSAL_TENTACLE)
    return (if (worn) def.equipmentMenu else def.inventoryMenu).any { it.equals(option, true) }
}

if (tentacleHas("Check")) {
    on_item_option(item = Items.ABYSSAL_TENTACLE, option = "Check") {
        val tentacle = player.inventory[player.getInteractingItemSlot()] ?: return@on_item_option
        player.message(AbyssalTentacle.checkMessage(tentacle))
    }
}

if (tentacleHas("Check", worn = true)) {
    on_equipment_option(item = Items.ABYSSAL_TENTACLE, option = "Check") {
        val tentacle = player.getEquipment(EquipmentType.WEAPON)?.takeIf { AbyssalTentacle.isTentacle(it.id) } ?: return@on_equipment_option
        player.message(AbyssalTentacle.checkMessage(tentacle))
    }
}

// OSRS destroy option: "Dissolve - This will dissolve your whip immediately. The whip will be permanently lost." The kraken tentacle
// is what remains (the whip is the part that is lost).
if (tentacleHas("Dissolve")) {
    on_item_option(item = Items.ABYSSAL_TENTACLE, option = "Dissolve") {
        val slot = player.getInteractingItemSlot()
        if (!AbyssalTentacle.isTentacle(player.inventory[slot]?.id)) return@on_item_option
        player.queue {
            if (options("Yes, dissolve the whip.", "No.", title = "Dissolve your whip? It will be permanently lost.") != 1) {
                return@queue
            }
            if (!AbyssalTentacle.isTentacle(player.inventory[slot]?.id)) return@queue
            player.inventory[slot] = Item(Items.KRAKEN_TENTACLE, 1)
            player.message("The abyssal whip dissolves, leaving the kraken tentacle.")
        }
    }
}

// "The abyssal tentacle can be created by using a kraken tentacle on an abyssal whip" - with a warning that this cannot be undone
// and that the tentacle is not tradeable.
on_item_on_item(item1 = Items.KRAKEN_TENTACLE, item2 = Items.ABYSSAL_WHIP) {
    player.queue {
        if (options(
                "Yes, make the abyssal tentacle.",
                "No.",
                title = "This cannot be undone, and the abyssal tentacle cannot be traded.",
            ) != 1
        ) {
            return@queue
        }
        if (!player.inventory.contains(Items.KRAKEN_TENTACLE) || !player.inventory.contains(Items.ABYSSAL_WHIP)) return@queue
        player.inventory.remove(Items.KRAKEN_TENTACLE, 1)
        val whipSlot = player.inventory.getItemIndex(Items.ABYSSAL_WHIP, false)
        if (whipSlot < 0) return@queue
        player.inventory[whipSlot] = AbyssalTentacle.created()
        // The shared attach rule (owner 2026-10-03, every attach makes a sound; ItemAttachSoundsTests).
        gg.rsmod.plugins.content.items.combine.ItemAttachSounds.attached(player, Items.KRAKEN_TENTACLE, Items.ABYSSAL_WHIP)
        player.message("You attach the kraken tentacle to the abyssal whip.")
    }
}

// "Players can combine the charges of abyssal tentacles, up to a maximum of 20,000 charges. This action will destroy one abyssal
// tentacle."
on_item_on_item(item1 = Items.ABYSSAL_TENTACLE, item2 = Items.ABYSSAL_TENTACLE) {
    val first = player.attr[INTERACTING_ITEM_SLOT] ?: return@on_item_on_item
    val second = player.attr[OTHER_ITEM_SLOT_ATTR] ?: return@on_item_on_item
    if (first == second) return@on_item_on_item
    val a = player.inventory[first]?.takeIf { AbyssalTentacle.isTentacle(it.id) } ?: return@on_item_on_item
    val b = player.inventory[second]?.takeIf { AbyssalTentacle.isTentacle(it.id) } ?: return@on_item_on_item
    if (AbyssalTentacle.charges(a) >= AbyssalTentacle.MAX_CHARGES) {
        player.message("Your abyssal tentacle cannot hold any more charges.")
        return@on_item_on_item
    }
    player.queue {
        if (options("Yes, combine them.", "No.", title = "One abyssal tentacle will be destroyed. Combine the charges?") != 1) return@queue
        val latestA = player.inventory[first]?.takeIf { AbyssalTentacle.isTentacle(it.id) } ?: return@queue
        val latestB = player.inventory[second]?.takeIf { AbyssalTentacle.isTentacle(it.id) } ?: return@queue
        val total = minOf(AbyssalTentacle.MAX_CHARGES, AbyssalTentacle.charges(latestA) + AbyssalTentacle.charges(latestB))
        player.inventory[second] = null
        player.inventory[first] = AbyssalTentacle.withCharges(latestA, total)
        player.message(AbyssalTentacle.checkMessage(player.inventory[first]!!))
    }
}
