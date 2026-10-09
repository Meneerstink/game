package gg.rsmod.plugins.content.areas.grandexchange

import gg.rsmod.game.model.Direction
import gg.rsmod.plugins.content.activity.duel_arena.DuelArenaLocations
import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.canTeleport
import gg.rsmod.plugins.content.magic.teleport

/*
 * The Royal Hall's top storey is the casino (owner 2026-10-09: one open gambling room). Mubariz, the Duel Arena's own host
 * (npc 957), takes you straight into the Duel Arena lobby - the lobby tiles the arena itself uses after a duel
 * (DuelArenaLocations.LOBBY, Novite LOBBY_TELEPORTS) - through the normal teleport rules (Tele Block, the Deadman countdown,
 * Landing). The bar's barman (Bartender 737, no shop anywhere else) only talks.
 */

/** The casino storey of the Royal Hall. */
fun inCasino(tile: gg.rsmod.game.model.Tile) = tile.height == 2 && tile.x in 3158..3171 && tile.z in 3486..3497

spawn_npc(npc = Npcs.MUBARIZ, x = 3170, z = 3489, height = 2, direction = Direction.WEST)
spawn_npc(npc = Npcs.BARTENDER_737, x = 3159, z = 3489, height = 2, direction = Direction.EAST)

on_npc_option(npc = Npcs.MUBARIZ, option = "talk-to") {
    val inHall = inCasino(player.getInteractingNpc().tile)
    player.queue {
        if (!inHall) {
            chatNpc("Welcome to the Duel Arena! Challenge another player in the lobby and fight for glory.")
            return@queue
        }
        chatNpc("Fancy a duel? I can take you straight to the Duel Arena lobby.")
        when (options("Take me to the Duel Arena.", "No thanks.")) {
            1 -> {
                chatPlayer("Take me to the Duel Arena.")
                player.canTeleport(TeleportType.MODERN) { player.teleport(DuelArenaLocations.LOBBY.random(), TeleportType.MODERN) }
            }
            else -> chatPlayer("No thanks.")
        }
    }
}

// Spoken to across the unbroken counter (owner 2026-10-09: the flap gap is closed), as Death across his desk.
on_npc_option(npc = Npcs.BARTENDER_737, option = "talk-to", lineOfSightDistance = 2) {
    player.queue {
        chatNpc("Welcome to the casino bar. Good luck at the tables!")
        chatPlayer("Thanks!")
    }
}
