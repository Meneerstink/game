package gg.rsmod.plugins.content.mechanics.travel

import gg.rsmod.game.model.Tile
import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.teleport
import gg.rsmod.plugins.content.mechanics.pvp.SevenSecondAction
import gg.rsmod.plugins.content.mechanics.pvp.DeadmanTimerGate

/**
 * R02.3: the Rellekka<->Miscellania boat/charter route, part of the "charter/boat NPC" network.
 * Same discipline as the Gnome Glider/Magic Carpet: [Npcs.SAILOR] (real cache option
 * `"Travel-Miscellania"`) and [Npcs.SAILOR_1385] (real cache option `"Travel-Rellekka"`) each
 * had a genuine dead option, confirmed via `NpcCensus`. Destination tiles are each sailor's own
 * real, already-verified spawn location - SAILOR sits at real Rellekka coordinates and offers
 * travel TO Miscellania, SAILOR_1385 sits at real Miscellania coordinates and offers travel TO
 * Rellekka, a coherent real two-way route, not invented.
 *
 * A single fixed destination per sailor (unlike the 5-way glider/carpet choice), so no dialog
 * is needed. [TeleportType.MODERN] used for the same undocumented-animation reason as the
 * other two networks this session.
 */
val rellekkaTile = Tile(2629, 3693, 0)
val miscellaniaTile = Tile(2581, 3847, 0)

// Deadman (OSRS Wiki): non-teleport transport always opens the 7-second timer interface first.
on_npc_option(Npcs.SAILOR, "Travel-Miscellania") {
    DeadmanTimerGate.requestRoute(player, SevenSecondAction.Kind.TRANSPORT) {
        player.teleport(miscellaniaTile.transform(1, 0), TeleportType.MODERN)
    }
}

on_npc_option(Npcs.SAILOR_1385, "Travel-Rellekka") {
    DeadmanTimerGate.requestRoute(player, SevenSecondAction.Kind.TRANSPORT) {
        player.teleport(rellekkaTile.transform(1, 0), TeleportType.MODERN)
    }
}
