package gg.rsmod.plugins.content.mechanics.traversal.batch13

import gg.rsmod.plugins.content.skills.agility.*

/*
 * Obstacle batch 13 (owner priority #1, 2026-10-03): The Adventurous at the Mos Le'Harmless docks - gangplanks 11211 (quay end,
 * 3684,2952 level 0) and 11212 (deck end, 3684,2951 level 1). RS3 Wiki "The Adventurous": Bill Teach's ship, "found in Port Phasmatys
 * docks and Mos Le'Harmless" (players board it; Bill Teach can be found on it). Behaviour = 2009scape PortsObjectPlugin "cross" for a
 * moored ship's gangplank: a move across the plank (cross(player, location) = teleport) with "You board the ship." / "You disembark the
 * ship.". Landings (MapProbe 2026-10-03): the deck tile 3684,2950 level 1 right behind the deck end, the quay tile 3684,2953 level 0 in
 * front of the quay end (32 tiles of quay reachable). The deck's ladders (11289/11290, 11308/11309) climb through the shared ladder
 * fallback; the climbing net 11244 is data (batch-13.json).
 */
val ADVENTUROUS_DECK = Tile(3684, 2950, 1)
val ADVENTUROUS_QUAY = Tile(3684, 2953, 0)

shortcut(11211, "Cross") {
    obstacle {
        wait(1)
        player.moveTo(ADVENTUROUS_DECK)
        player.message("You board the ship.")
    }
}

shortcut(11212, "Cross") {
    obstacle {
        wait(1)
        player.moveTo(ADVENTUROUS_QUAY)
        player.message("You disembark the ship.")
    }
}
