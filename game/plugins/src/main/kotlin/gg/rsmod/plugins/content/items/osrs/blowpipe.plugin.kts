package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.INTERACTING_ITEM_SLOT
import gg.rsmod.game.model.attr.OTHER_ITEM_SLOT_ATTR
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Darts

/**
 * OSRS-IMPORT blowpipe item actions for every [Blowpipe.Pipe] (rules and sources in [Blowpipe]). Messages without an OSRS source are
 * recorded as ADAPTED in `C:\RSPS\OSRS_IMPORT_STATUS.md`. Options are bound only where the item definition carries them.
 */

fun Player.itemName(id: Int): String = world.definitions.get(ItemDef::class.java, id).name

fun pipeHasOption(
    itemId: Int,
    option: String,
): Boolean = world.definitions.get(ItemDef::class.java, itemId).inventoryMenu.any { it.equals(option, ignoreCase = true) }

/** The (blowpipe slot, other slot) of an item-on-item interaction, whichever way round it was used. */
fun Player.blowpipeSlots(): Pair<Int, Int>? {
    val first = attr[INTERACTING_ITEM_SLOT] ?: return null
    val second = attr[OTHER_ITEM_SLOT_ATTR] ?: return null
    return when {
        inventory[first]?.id?.let { Blowpipe.isBlowpipe(it) } == true -> first to second
        inventory[second]?.id?.let { Blowpipe.isBlowpipe(it) } == true -> second to first
        else -> null
    }
}

fun Player.checkBlowpipe(pipe: Item) {
    message(Blowpipe.checkMessage(pipe, Blowpipe.dart(pipe)?.let { itemName(it.itemId) }))
}

Blowpipe.Pipe.values().forEach { type ->
    val charged = type.charged
    if (pipeHasOption(charged, "Check")) {
        on_item_option(item = charged, option = "Check") {
            val pipe = player.inventory[player.getInteractingItemSlot()] ?: return@on_item_option
            player.checkBlowpipe(pipe)
        }
    }

    if (pipeHasOption(charged, "Unload")) {
        on_item_option(item = charged, option = "Unload") {
            val slot = player.getInteractingItemSlot()
            val pipe = player.inventory[slot]?.takeIf { it.id == charged } ?: return@on_item_option
            val dart = Blowpipe.dart(pipe)
            if (dart == null) {
                player.message("The blowpipe has no darts in it.")
                return@on_item_option
            }
            if (!player.inventory.add(dart.itemId, Blowpipe.darts(pipe), assureFullInsertion = true).hasSucceeded()) {
                player.message("You don't have space to do that.")
                return@on_item_option
            }
            player.inventory[slot] = Blowpipe.unloadDarts(pipe)
        }
    }

    if (type.usesScales && pipeHasOption(charged, "Uncharge")) {
        on_item_option(item = charged, option = "Uncharge") {
            val slot = player.getInteractingItemSlot()
            player.queue {
                if (options("Uncharge it. All scales and darts will fall out.", "Cancel.") != 1) return@queue
                val pipe = player.inventory[slot]?.takeIf { it.id == charged } ?: return@queue
                val dart = Blowpipe.dart(pipe)
                val darts = Blowpipe.darts(pipe)
                val scales = Blowpipe.scales(pipe)
                if (dart != null && !player.inventory.add(dart.itemId, darts, assureFullInsertion = true).hasSucceeded()) {
                    player.message("You don't have enough inventory space to uncharge the blowpipe.")
                    return@queue
                }
                if (scales > 0 && !player.inventory.add(Items.ZULRAHS_SCALES, scales, assureFullInsertion = true).hasSucceeded()) {
                    if (dart != null) player.inventory.remove(dart.itemId, darts)
                    player.message("You don't have enough inventory space to uncharge the blowpipe.")
                    return@queue
                }
                player.inventory[slot] = Blowpipe.uncharge(pipe)
            }
        }
    }

    listOf(type.charged, type.empty).forEach { pipeId ->
        Blowpipe.Dart.values().forEach { dart ->
            on_item_on_item(item1 = pipeId, item2 = dart.itemId) {
                val (pipeSlot, dartSlot) = player.blowpipeSlots() ?: return@on_item_on_item
                val pipe = player.inventory[pipeSlot] ?: return@on_item_on_item
                val stack = player.inventory[dartSlot] ?: return@on_item_on_item
                val load = Blowpipe.loadDarts(pipe, stack.id, stack.amount)
                when (load.outcome) {
                    Blowpipe.LoadOutcome.LOADED -> {
                        player.inventory.remove(stack.id, load.added)
                        player.inventory[pipeSlot] = load.result
                        player.checkBlowpipe(load.result)
                    }
                    Blowpipe.LoadOutcome.DIFFERENT_DART -> player.message(Blowpipe.DIFFERENT_DART_MESSAGE)
                    Blowpipe.LoadOutcome.FULL -> player.message(Blowpipe.FULL_DARTS_MESSAGE)
                    Blowpipe.LoadOutcome.TOO_STRONG -> player.message(Blowpipe.DART_TOO_STRONG_MESSAGE)
                    Blowpipe.LoadOutcome.NOT_A_DART -> {}
                }
            }
        }
        if (type.usesScales) {
            on_item_on_item(item1 = pipeId, item2 = Items.ZULRAHS_SCALES) {
                val (pipeSlot, scaleSlot) = player.blowpipeSlots() ?: return@on_item_on_item
                val pipe = player.inventory[pipeSlot] ?: return@on_item_on_item
                val stack = player.inventory[scaleSlot] ?: return@on_item_on_item
                val load = Blowpipe.chargeScales(pipe, stack.amount)
                if (load.outcome != Blowpipe.LoadOutcome.LOADED) {
                    player.message(Blowpipe.FULL_SCALES_MESSAGE)
                    return@on_item_on_item
                }
                player.inventory.remove(Items.ZULRAHS_SCALES, load.added)
                player.inventory[pipeSlot] = load.result
                player.checkBlowpipe(load.result)
            }
        }
        Darts.DARTS.filter { Blowpipe.Dart.forItem(it) == null }.distinct().forEach { poisoned ->
            on_item_on_item(item1 = pipeId, item2 = poisoned) {
                player.message(Blowpipe.POISONED_DART_MESSAGE)
            }
        }
    }
}

// Wiki: dismantling the empty blowpipe or the Tanzanite fang gives 20,000 Zulrah's scales.
listOf(Items.TOXIC_BLOWPIPE_EMPTY, Items.TANZANITE_FANG).forEach { id ->
    on_item_option(item = id, option = "Dismantle") {
        val slot = player.getInteractingItemSlot()
        player.queue {
            if (options("Dismantle it for 20,000 Zulrah's scales.", "Cancel.") != 1) return@queue
            if (player.inventory[slot]?.id != id) return@queue
            player.inventory[slot] = null
            if (!player.inventory.add(Items.ZULRAHS_SCALES, Blowpipe.DISMANTLE_SCALES, assureFullInsertion = true).hasSucceeded()) {
                player.inventory[slot] = Item(id)
                player.message("You need some free space to dismantle it.")
            }
        }
    }
}

// Wiki "Tanzanite fang": chisel on the fang, 78 Fletching, 120 XP -> Toxic blowpipe (empty). Message: Kronos (single source).
on_item_on_item(item1 = Items.CHISEL, item2 = Items.TANZANITE_FANG) {
    if (player.skills.getCurrentLevel(Skills.FLETCHING) < Blowpipe.FLETCHING_LEVEL) {
        player.message("You need a Fletching level of ${Blowpipe.FLETCHING_LEVEL} to do that.")
        return@on_item_on_item
    }
    if (player.inventory.remove(Items.TANZANITE_FANG, 1).hasSucceeded()) {
        player.inventory.add(Items.TOXIC_BLOWPIPE_EMPTY, 1)
        player.addXp(Skills.FLETCHING, Blowpipe.FLETCHING_XP)
        player.message("You carve the fang and turn it into a powerful blowpipe.")
    }
}
