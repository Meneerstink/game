package gg.rsmod.plugins.content.mechanics.traversal.batch2

import gg.rsmod.plugins.content.skills.agility.shortcut

/**
 * Kalphite Queen lair wall tunnel 23596 (3515,9520,2), obstacle batch 2 (owner priority #1 2026-10-03). The tunnel is not a passage:
 * Void KalphiteHive.kt `objectOperate("Enter", "kalphite_hive_wall_tunnel")` only answers
 * "It looks like the tunnel is blocked at the other end." - the player does not move.
 */
shortcut(23596, "Enter") {
    player.message("It looks like the tunnel is blocked at the other end.")
}
