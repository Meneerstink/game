package gg.rsmod.plugins.content.items.jewellery

import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.canTeleport
import gg.rsmod.plugins.content.magic.teleport

/**
 * Ring of kinship "Teleport to Daemonheim", from the inventory and while worn.
 *
 * 2026-09-22 item-option census: the handler was bound to inventory slot 4 - the ring's "Customise" option - so the
 * real Teleport option did nothing and Customise teleported; the worn option had no handler at all. It also dropped
 * the random offset (`tile.transform` result unused). Now bound by option name; the landing square is the Void donor's
 * `daemonheim_teleport` area (3444-3448, 3693-3698).
 */
fun Player.kinshipTeleport() {
    // Deadman PvP guards plan (2026-09-16): the two-arg canTeleport overload makes a skulled
    // player's 7-second countdown complete this action automatically.
    canTeleport(TeleportType.RING_OF_KINSHIP) {
        val tile = Tile(x = world.random(3444..3448), z = world.random(3693..3698))
        queue(TaskPriority.STRONG) {
            teleport(tile, TeleportType.RING_OF_KINSHIP)
        }
    }
}

on_item_option(item = Items.RING_OF_KINSHIP, option = "Teleport to Daemonheim") {
    player.kinshipTeleport()
}

on_equipment_option(item = Items.RING_OF_KINSHIP, option = "Teleport to Daemonheim") {
    player.kinshipTeleport()
}
