package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.collision.ObjectType
import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.canTeleport
import gg.rsmod.plugins.content.magic.teleport

/**
 * OSRS-IMPORT casket-teleports: "Teleport" on a Treasure Trail teleport scroll (rules and sources in [CasketTeleportScrolls]). The scroll is
 * consumed only once the teleport is allowed.
 */

CasketTeleportScrolls.REFUSALS.forEach { (scroll, message) ->
    on_item_option(item = scroll, option = "Teleport") {
        player.message(message)
    }
}

CasketTeleportScrolls.DESTINATIONS.forEach { (scroll, destination) ->
    on_item_option(item = scroll, option = "Teleport") {
        if (!player.canTeleport(TeleportType.TAB)) return@on_item_option
        val slot = player.getInteractingItemSlot()
        if (!player.inventory.remove(item = scroll, amount = 1, beginSlot = slot).hasSucceeded()) return@on_item_option
        val area = Area(destination.centreX - destination.radius, destination.centreZ - destination.radius, destination.centreX + destination.radius, destination.centreZ + destination.radius)
        var tile = area.randomTile
        var attempts = 1
        while (world.getObject(tile, ObjectType.INTERACTABLE) != null && attempts < CasketTeleportScrolls.LANDING_ATTEMPTS) {
            tile = area.randomTile
            attempts++
        }
        if (world.getObject(tile, ObjectType.INTERACTABLE) != null) tile = Tile(destination.centreX, destination.centreZ, 0)
        player.teleport(tile, TeleportType.TAB)
    }
}
