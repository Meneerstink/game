package gg.rsmod.plugins.content.items.jewellery

import gg.rsmod.game.model.Tile
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.canTeleport
import gg.rsmod.plugins.content.magic.teleport


val RING_OF_WEALTH =
    intArrayOf(
        // O26 PROJECT48: (5) from the Fountain of Rune (OSRS import), the only place a ring of wealth is charged.
        gg.rsmod.plugins.content.areas.wilderness.FountainOfRune.WEALTH_5,
        Items.RING_OF_WEALTH_4,
        Items.RING_OF_WEALTH_3,
        Items.RING_OF_WEALTH_2,
        Items.RING_OF_WEALTH_1,
    )

private val LOCATIONS = gg.rsmod.plugins.content.magic.teleports.JewelleryDestinations.RING_OF_WEALTH

on_equipment_option(item = Items.RING_OF_WEALTH, option = "Rub") {
    player.message(
        "You will need to recharge your ring of wealth before you can use it again.",
        type = ChatMessageType.GAME_MESSAGE,
    )
}
on_item_option(item = Items.RING_OF_WEALTH, option = "Rub") {
    player.message("The ring has lost its charge.", type = ChatMessageType.GAME_MESSAGE)
    player.message("It will need to be recharged before you can use it again.", type = ChatMessageType.GAME_MESSAGE)
}

RING_OF_WEALTH.forEach { item ->
    on_item_option(item = item, option = "Rub") {
        player.queue {
            when (options("Miscellania.", "Grand Exchange.", "Nowhere.")) {
                1 -> player.teleport(LOCATIONS["Miscellania"]!!, isEquipped = false)
                2 -> player.teleport(LOCATIONS["Grand Exchange"]!!, isEquipped = false)
            }
        }
    }
}

RING_OF_WEALTH.forEach { item ->
    LOCATIONS.forEach { (location, endTile) ->
        on_equipment_option(item, option = location) {
            player.queue(TaskPriority.STRONG) {
                player.teleport(endTile, isEquipped = true)
            }
        }
    }
    // N537 stap 3a (census 25-11): the OSRS ring (5) also lists Falador / Dondakan / Boss Log worn and Features in the backpack - dead.
    val def = world.definitions.get(ItemDef::class.java, item)
    gg.rsmod.plugins.content.magic.teleports.JewelleryDestinations.RING_OF_WEALTH_WORN_EXTRA.forEach { (location, endTile) ->
        if (def.equipmentMenu.none { it.equals(location, ignoreCase = true) }) return@forEach
        on_equipment_option(item, option = location) {
            gg.rsmod.plugins.content.magic.teleports.JewelleryDestinations.wealthRefusal(player, location)?.let {
                player.message(it)
                return@on_equipment_option
            }
            player.queue(TaskPriority.STRONG) {
                player.teleport(endTile, isEquipped = true)
            }
        }
    }
    if (def.equipmentMenu.any { it.equals("Boss Log", ignoreCase = true) }) {
        // ADAPTED: OSRS opens the Boss Kill Log; the kill counts per boss here are on the collection log pages.
        on_equipment_option(item, option = "Boss Log") { gg.rsmod.plugins.content.quests.tab.CollectionLogUi.open(player) }
    }
    if (def.inventoryMenu.any { it.equals("Features", ignoreCase = true) }) {
        on_item_option(item, option = "Features") {
            player.message("Rub or operate the ring to teleport (one charge each). Boss Log opens the collection log with your kill counts.")
        }
    }
}

/**
 * Teleports the player using jewellery and updates the item's charges.
 *
 * @param endTile The destination tile for the player after teleportation.
 * @param isEquipped Indicates if the item is equipped on the player (true) or in the inventory (false).
 */
fun Player.teleport(
    endTile: Tile,
    isEquipped: Boolean,
) {
    // Check if the player can teleport using items
    // Deadman PvP guards plan (2026-09-16): two-arg canTeleport - a skulled player's 7-second
    // countdown completes this action automatically.
    canTeleport(TeleportType.JEWELRY) {
        // Start/land sounds come from TeleportType.JEWELRY in the shared teleport (one rule, no second copy here).

        // Get the replacement ring with updated charges
        val replacement = gg.rsmod.plugins.content.mechanics.deadman.sigils.SigilJeweller.keep(this, getInteractingItemId(), replacement(getInteractingItemId()))

        if (isEquipped) {
            // Update the equipped item, or remove it if there are no charges left
            equipment[EquipmentType.RING.id] = if (replacement > -1) Item(replacement) else null
        } else {
            // Remove the item from the inventory and add the replacement if there are charges left
            inventory.remove(getInteractingItem())
            if (replacement > -1) {
                inventory.add(replacement)
            }
        }

        // Teleport the player to the destination tile
        this@teleport.teleport(endTile, TeleportType.JEWELRY)

        // Send a message to the player about the remaining ring charges
        message(message(getInteractingItemId()))
    }
}

/**
 * Returns the replacement item with updated charges.
 *
 * @param original The original item ID.
 * @return The replacement item ID, or -1 if the original item is out of charges.
 */
fun replacement(original: Int): Int {
    if (original == Items.RING_OF_WEALTH_1) {
        return Items.RING_OF_WEALTH
    }
    if (original == gg.rsmod.plugins.content.areas.wilderness.FountainOfRune.WEALTH_5) return Items.RING_OF_WEALTH_4
    return original - 2
}

/**
 * Returns a message with the remaining charges for the specified item.
 *
 * @param original The original item ID.
 * @return A message with the remaining charges, or a message indicating the item has crumbled to dust.
 */
fun message(original: Int): String {
    return when (original) {
        gg.rsmod.plugins.content.areas.wilderness.FountainOfRune.WEALTH_5 -> "Your ring of wealth has four charges left."
        Items.RING_OF_WEALTH_4 -> "Your ring of wealth has three charges left."
        Items.RING_OF_WEALTH_3 -> "Your ring of wealth has two charges left."
        Items.RING_OF_WEALTH_2 -> "Your ring of wealth has one charge left."
        else -> "You use your ring of wealth's last charge."
    }
}
