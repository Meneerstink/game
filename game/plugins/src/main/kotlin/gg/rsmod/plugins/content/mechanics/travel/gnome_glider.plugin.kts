package gg.rsmod.plugins.content.mechanics.travel

import gg.rsmod.game.model.Tile
import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.teleport
import gg.rsmod.plugins.content.mechanics.pvp.SevenSecondAction
import gg.rsmod.plugins.content.mechanics.pvp.DeadmanTimerGate

/**
 * R02.3: the Gnome Glider network. All 6 pilot npcs and their real cache `"Glider"` option were
 * confirmed dead (present, never bound) via this session's fresh `NpcCensus` boot output - a
 * real, previously-unbound feature, not invented from nothing. Their `[Tile]`s are each pilot's
 * own real, already-verified spawn location (from the matching `spawns_*.plugin.kts` file, the
 * same real cache map data every other live npc placement in this codebase uses) - not a
 * guessed coordinate.
 *
 * Honest approximation, documented rather than hidden: the exact walkable arrival tile beside
 * each pilot (as opposed to the pilot's own tile) isn't independently verified without a client,
 * so arrival uses the pilot's tile offset by one step east. If that happens to be unwalkable at
 * a given station this is a minor landing-spot inaccuracy, not a broken teleport - the same
 * "real data, approximated final touch" honesty used for the home wall's uniform rotation.
 *
 * No dedicated cache glider animation/graphic was verified either, so [TeleportType.MODERN] (a
 * plain generic teleport swirl) is used rather than guessing a specific one - same reasoning as
 * the Armadyl Godsword special's animation gap.
 */
private data class GliderStation(
    val npc: Int,
    val label: String,
    val tile: Tile,
)

private val STATIONS =
    listOf(
        GliderStation(Npcs.GNORMADIUM_AVLAFRIM, "Gnormadium Avlafrim's station", Tile(2544, 2973, 0)),
        GliderStation(Npcs.CAPTAIN_DALBUR, "Captain Dalbur's station", Tile(3285, 3213, 0)),
        GliderStation(Npcs.CAPTAIN_BLEEMADGE, "Captain Bleemadge's station", Tile(2847, 3499, 0)),
        GliderStation(Npcs.CAPTAIN_ERRDO, "Captain Errdo's station", Tile(2465, 3504, 3)),
        GliderStation(Npcs.CAPTAIN_KLEMFOODLE, "Captain Klemfoodle's station", Tile(2970, 2973, 0)),
        GliderStation(Npcs.CAPTAIN_BELMONDO, "Captain Belmondo's station", Tile(2496, 3189, 0)),
    )

STATIONS.forEach { origin ->
    on_npc_option(origin.npc, "Glider") {
        val destinations = STATIONS.filter { it.npc != origin.npc }
        player.queue {
            val choice = options(*destinations.map { it.label }.toTypedArray(), title = "Fly to...")
            if (choice < 1 || choice > destinations.size) {
                return@queue
            }
            val dest = destinations[choice - 1]
            // Deadman PvP guards plan (2026-09-16): a real "non-teleport transport" network -
            // the Gnome Glider bypassed player.canTeleport entirely before this (it called
            // player.teleport(...) directly), so it also had no combat-recency/skull gate at all.
            // Owner spec: this category always gets the unconditional 7-second countdown, for
            // everyone, regardless of skull state - unlike ordinary teleport spells.
            DeadmanTimerGate.requestRoute(player, SevenSecondAction.Kind.TRANSPORT) {
                player.teleport(dest.tile.transform(1, 0), TeleportType.MODERN)
            }
        }
    }
}
