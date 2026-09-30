package gg.rsmod.plugins.content.items.osrs

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
    on_item_option(item = scroll, option = "Teleport") { scrollTeleport(scroll, destination, TeleportType.TAB) }
}

CasketTeleportScrolls.READ_SCROLLS.forEach { (scroll, destination) ->
    on_item_option(item = scroll, option = "Read") { scrollTeleport(scroll, destination, TeleportType.SCROLL) }
}

fun Plugin.scrollTeleport(
    scroll: Int,
    destination: CasketTeleportScrolls.Destination,
    type: TeleportType,
) {
    // Deadman PvP guards plan (2026-09-16): the two-arg canTeleport overload makes a skulled
    // player's 7-second countdown complete this action automatically.
    player.canTeleport(type) {
        val slot = player.getInteractingItemSlot()
        if (!player.inventory.remove(item = scroll, amount = 1, beginSlot = slot).hasSucceeded()) return@canTeleport
        val area = Area(destination.centreX - destination.radius, destination.centreZ - destination.radius, destination.centreX + destination.radius, destination.centreZ + destination.radius)
        // Walkable ground of the destination, never a tile inside scenery (Landing, owner 2026-09-30 Trollheim).
        val tile = gg.rsmod.game.model.collision.Landing.pick(world.collision, area)
        player.teleport(tile, type)
    }
}
