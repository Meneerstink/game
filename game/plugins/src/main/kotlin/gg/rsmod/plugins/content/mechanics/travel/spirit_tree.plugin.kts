package gg.rsmod.plugins.content.mechanics.travel

import gg.rsmod.game.model.Tile
import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.canTeleport
import gg.rsmod.plugins.content.magic.teleport

/**
 * Q-017: the spirit tree network. Names + tile coordinates were decoded directly from this
 * project's own 667 game cache via `ConfigDefProbeTool enum 2535` (real menu text) and
 * `enum 2536` (packed tile: plane<<28 | x<<14 | z) - an authoritative source, not a guess.
 * Cross-checked against the independently-found real static object placements
 * (`ObjectPlacementProbeTool name "spirit tree"`), which agree within a few tiles on all 5
 * always-present stations below. The four real "Spirit tree" world objects (ids
 * 1293/1294/1295/1317, real cache option "Teleport" confirmed via `ObjectDefProbeTool`) are not
 * each tied to one destination - the same ids repeat across multiple physical trees - so the
 * station the player is standing at is resolved by nearest-tile match instead, mirroring the
 * STATIONS pattern already used by gnome_glider.plugin.kts/magic_carpet.plugin.kts in this
 * package.
 *
 * Only the 5 permanent, always-present destinations (cache enum indices 0-4) are wired here.
 * Indices 5-7 (Port Sarim/Etceteria/Brimhaven) require a player-grown spirit tree farming patch
 * and index 8 (Mountains east of the Poison Waste) requires a separate unlock counter - neither
 * exists in this target's Farming subsystem yet (`Patch.kt` has no spirit tree patch entries), so
 * wiring them now would mean inventing availability state this server doesn't actually have.
 * Real, named gap - left for the Farming skill batch (R6/R12): do not mark these as always
 * available, that would be wrong.
 */
private data class SpiritTreeStation(
    val label: String,
    val tile: Tile,
)

private val SPIRIT_TREE_OBJECTS =
    intArrayOf(
        Objs.SPIRIT_TREE_1293, Objs.SPIRIT_TREE_1294, Objs.SPIRIT_TREE_1295, Objs.SPIRIT_TREE_1317,
        // 8355 is the 3x3 spirit tree, and it carries the same "Teleport" option. The player-owned house's Superior
        // Garden uses it because the 4x4 trees above cannot stand in an 8x8 POH room without covering a doorway
        // (PlayerHouse.furnishGarden); it is the same network from the same tree, so it belongs on the same list.
        Objs.SPIRIT_TREE_8355,
    )

private val STATIONS =
    listOf(
        SpiritTreeStation("Tree Gnome Village", Tile(2542, 3169, 0)),
        SpiritTreeStation("Tree Gnome Stronghold", Tile(2462, 3444, 0)),
        SpiritTreeStation("Battlefield of Khazard", Tile(2557, 3259, 0)),
        SpiritTreeStation("North-east of the Grand Exchange in Varrock", Tile(3185, 3511, 0)),
        SpiritTreeStation("Mobilising Armies", Tile(2416, 2851, 0)),
    )

private val NEAREST_STATION_RADIUS = 30

SPIRIT_TREE_OBJECTS.forEach { obj ->
    on_obj_option(obj = obj, option = "Teleport") {
        player.queue { travel(this, player) }
    }
}

/*
 * The 3x3 spirit tree (8355, the player-owned house's Superior Garden tree) also carries "Talk-to", "Inspect" and "Guide"; none
 * had a route, so each click answered "Nothing interesting happens" (owner 2026-09-24: "poh alle objecten werken niet").
 * Talk-to uses the spirit tree's own greeting (RuneScape Wiki "Spirit tree": "If you are a friend of the gnome people, you are a
 * friend of mine. Do you want to travel?"); Guide lists the same destinations; Inspect is ADAPTED flavour text.
 */
on_obj_option(obj = Objs.SPIRIT_TREE_8355, option = "Talk-to") {
    player.queue {
        messageBox("If you are a friend of the gnome people, you are a friend of mine. Do you want to travel?")
        travel(this, player)
    }
}

on_obj_option(obj = Objs.SPIRIT_TREE_8355, option = "Guide") {
    player.queue { travel(this, player) }
}

on_obj_option(obj = Objs.SPIRIT_TREE_8355, option = "Inspect") {
    player.message("The spirit tree can carry you to the other spirit trees of Gielinor.")
}

suspend fun travel(
    task: gg.rsmod.game.model.queue.QueueTask,
    player: Player,
) {
    val origin = STATIONS.minByOrNull { it.tile.getDistance(player.tile) }
    val destinations =
        if (origin != null && origin.tile.getDistance(player.tile) <= NEAREST_STATION_RADIUS) {
            STATIONS.filter { it != origin }
        } else {
            STATIONS
        }
    val choice = task.options(*destinations.map { it.label }.toTypedArray(), title = "Where would you like to go?")
    if (choice < 1 || choice > destinations.size) {
        return
    }
    val dest = destinations[choice - 1]
    // Spirit trees are teleports, so Tele Block and the Deadman teleport rule are shared
    // with spells, jewellery, tabs, fairy rings, levers and obelisks.
    player.canTeleport(TeleportType.SPIRIT_TREE) {
        player.teleport(dest.tile, TeleportType.SPIRIT_TREE)
    }
}
