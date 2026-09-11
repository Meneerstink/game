package gg.rsmod.plugins.content.items

import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.canTeleport
import gg.rsmod.plugins.content.magic.teleport

/**
 * Q-016 (RSPS_2DAY_DONOR_IMPORT_PLAN.md): the Ectophial (Ghosts Ahoy quest reward) had its
 * [TeleportType.ECTOPHIAL] animation/graphic already defined in `TeleportType.kt` but no
 * `on_item_option` ever used it - "Empty" did nothing. Destination tile sourced from Void's
 * `data/area/morytania/port_phasmatys/port_phasmatys.areas.toml` `[ectophial_teleport]` area
 * (x=3654-3665, y=3521-3524, just outside the Ectofuntus) - real 2011 overworld coordinates,
 * unchanged between the 634/667 cache revisions. `Items.ECTOPHIAL_4252` (the empty variant) is
 * this cache's own generated id, adjacent to `Items.ECTOPHIAL` (4251), matching this project's
 * standard degrade-pair naming; not sourced from either donor.
 *
 * Not in scope here (separate follow-up, no Ectofuntus/Ghosts Ahoy quest content exists in this
 * codebase to gate or refill against): refilling the empty ectophial at the Ectofuntus.
 */
private val DESTINATION = Area(3654, 3521, 3665, 3524)

on_item_option(item = Items.ECTOPHIAL, option = "empty") {
    if (!player.canTeleport(TeleportType.ECTOPHIAL)) {
        return@on_item_option
    }
    player.inventory.remove(Items.ECTOPHIAL)
    player.inventory.add(Items.ECTOPHIAL_4252)
    player.teleport(DESTINATION.randomTile, TeleportType.ECTOPHIAL)
    player.message("You pour some ectoplasm from the vial, and it explodes in a rush of magical energy...")
}
