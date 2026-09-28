package gg.rsmod.plugins.content.cmd

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.priv.Privilege

/*
 * Debug toolkit phase 4 - visual regression (manual: C:\RSPS\TRACES.md). `visualtour` moves the admin through fixed places,
 * waits for the map to load and asks the RSPS client to save the game canvas (the `screenshot` marker) as
 * C:\RSPS\screens\<stamp>_tour_<place>_<w>x<h>.png. `tools\visual\Compare-Screens.ps1` compares the newest tour with the
 * baseline (tools\visual\baseline) and marks what changed, black holes (missing terrain/models) and flat missing-texture
 * colours. `visualtour <place>` takes one place. The player ends where he started.
 */

val TOUR_PREFIX = "__RSPS_SHOT__:"

val TOUR: List<Pair<String, Tile>> =
    listOf(
        "grand_exchange" to Tile(3165, 3487, 0),
        "varrock_square" to Tile(3212, 3428, 0),
        "lumbridge_courtyard" to Tile(3222, 3218, 0),
        "draynor_village" to Tile(3093, 3244, 0),
        "falador_park" to Tile(2994, 3376, 0),
        "edgeville_bank" to Tile(3094, 3491, 0),
        "barbarian_village" to Tile(3082, 3420, 0),
        "port_sarim" to Tile(3012, 3218, 0),
        "al_kharid" to Tile(3293, 3174, 0),
        "seers_village" to Tile(2725, 3485, 0),
        "catherby" to Tile(2809, 3436, 0),
        "ardougne_market" to Tile(2662, 3305, 0),
        "yanille" to Tile(2605, 3093, 0),
        "burthorpe" to Tile(2899, 3544, 0),
        "canifis" to Tile(3493, 3488, 0),
    )

on_command("visualtour", Privilege.ADMIN_POWER) {
    val only = player.getCommandArgs().getOrNull(0)?.lowercase()
    val places = TOUR.filter { only == null || it.first.contains(only) }
    if (places.isEmpty()) {
        player.message("No tour place matches '$only'. Places: ${TOUR.joinToString(" ") { it.first }}")
        return@on_command
    }
    val start = player.tile
    player.message("Visual tour: ${places.size} place(s). Keep the game window visible; about ${places.size * 5} seconds.")
    player.queue {
        for ((name, tile) in places) {
            player.moveTo(tile)
            wait(8)
            player.message("${TOUR_PREFIX}tour_$name", type = ChatMessageType.CONSOLE)
            wait(1)
        }
        player.moveTo(start)
        player.message("<col=178000>Visual tour done.</col> Run tools\\visual\\Compare-Screens.ps1 (first time: -SetBaseline).")
    }
}
