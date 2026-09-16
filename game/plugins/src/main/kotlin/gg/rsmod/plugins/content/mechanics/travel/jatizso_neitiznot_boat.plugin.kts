package gg.rsmod.plugins.content.mechanics.travel

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.teleport
import gg.rsmod.plugins.content.mechanics.pvp.SevenSecondAction

/**
 * R02.3: the Rellekka<->Jatizso and Rellekka<->Neitiznot boat legs, extending the same
 * charter/boat NPC network as [rellekka_boat.plugin.kts]. Same discipline: each option below is
 * a genuine dead cache option (`"Travel-Jatizso"`/`"Travel-Neitiznot"`/`"Travel-Rellekka"`), and
 * each destination tile is the other end's own real, already-verified spawn tile from the
 * spawns_*.plugin.kts census - never invented:
 * - [Npcs.MORD_GUNNARS] (5481) sits at the real Rellekka dock spawn (2644,3709) and offers
 *   travel TO Jatizso; [Npcs.MORD_GUNNARS_5482] sits at the real Jatizso spawn (2421,3781) and
 *   offers travel back TO Rellekka.
 * - [Npcs.MARIA_GUNNARS_5508] sits at the real Rellekka dock spawn (2644,3710) and offers
 *   travel TO Neitiznot; [Npcs.MARIA_GUNNARS] sits at the real Neitiznot spawn (2311,3781) and
 *   offers travel back TO Rellekka.
 *
 * [Npcs.JARVALD] (2438, real spawn 2544,3761, also offers a dead `"Travel-Rellekka"` option) is
 * neither of the two Rellekka-dock tiles above nor either island - a distinct real location this
 * file does not wire, left as an open item rather than guessed at.
 */
val jatizsoTile = Tile(2421, 3781, 0)
val neitiznotTile = Tile(2311, 3781, 0)
val rellekkaDockTile = Tile(2644, 3709, 0)

// Deadman (OSRS Wiki): non-teleport transport always opens the 7-second timer interface first.
fun sail(
    player: Player,
    dest: Tile,
) {
    SevenSecondAction.start(player, SevenSecondAction.Kind.TRANSPORT) {
        player.teleport(dest.transform(1, 0), TeleportType.MODERN)
    }
}

on_npc_option(Npcs.MORD_GUNNARS, "Travel-Jatizso") {
    sail(player, jatizsoTile)
}

on_npc_option(Npcs.MORD_GUNNARS_5482, "Travel-Rellekka") {
    sail(player, rellekkaDockTile)
}

on_npc_option(Npcs.MARIA_GUNNARS_5508, "Travel-Neitiznot") {
    sail(player, neitiznotTile)
}

on_npc_option(Npcs.MARIA_GUNNARS, "Travel-Rellekka") {
    sail(player, rellekkaDockTile)
}
