package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.plugins.content.mechanics.pvp.DeadmanHud

/**
 * Starts the Deadman zone/skull HUD ([DeadmanHud]) for the session - Wilderness level inside the Wilderness,
 * "Guarded"/"Deadman" everywhere else, and the PK-skull countdown. The HUD is driven at the end of every cycle from
 * ClientHud.tick (client_hud.plugin.kts), not from a timer: an exception in a timer removed it for good (research
 * 2026-10-03 R2). The overlay is server-authoritative: the client never derives danger or a level from raw coordinates.
 */
on_login {
    DeadmanHud.reset(player)
}
