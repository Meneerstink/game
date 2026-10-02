package gg.rsmod.plugins.content.areas.poh

import gg.rsmod.game.model.Tile

/**
 * The travel catalogues the house garden's three transport objects offer (owner 2026-09-21: "we want nexus teleport
 * and every teleport available in the house put it in spirit tree obelisk fairy ring").
 *
 * The full teleport directory lives in [PohTeleports] and is what the portal-chamber centrepiece opens. The three
 * garden objects each offer their OWN real network instead of a copy of that directory, which is what those objects
 * do everywhere else in the game:
 *  * the spirit tree offers the gnome spirit-tree stations,
 *  * the fairy ring offers the fairy-ring code network,
 *  * the obelisk offers the six Wilderness obelisks.
 *
 * Sources. The spirit-tree stations are the five permanent ones this server already wires in
 * `mechanics/travel/spirit_tree.plugin.kts`, decoded there from the 667 cache's own enums 2535/2536; they are
 * repeated here rather than imported because that file declares them privately inside a plugin script. The fairy-ring
 * codes and destinations are 2009scape's `FairyRingInterface.FairyRing` table. The obelisk destinations are the
 * exact tiles `areas/wilderness/wilderness_obelisk.plugin.kts`'s own `Obelisk` enum teleports to.
 */
object PohGarden {
    data class Destination(
        val label: String,
        val tile: Tile,
    )

    /**
     * The five always-available spirit-tree stations (cache enum 2535/2536 indices 0-4). The farming-patch trees
     * (Port Sarim, Etceteria, Brimhaven) and the Poison Waste tree need unlock state this server's Farming does not
     * have yet, so they are deliberately absent here too.
     */
    val SPIRIT_TREE: List<Destination> =
        listOf(
            Destination("Tree Gnome Village", Tile(2542, 3169, 0)),
            Destination("Tree Gnome Stronghold", Tile(2462, 3444, 0)),
            Destination("Battlefield of Khazard", Tile(2557, 3259, 0)),
            Destination("Grand Exchange", Tile(3185, 3511, 0)),
            Destination("Mobilising Armies", Tile(2416, 2851, 0)),
        )

    /**
     * The six Wilderness obelisks, by the Wilderness level each one stands in - the destinations the house obelisk
     * offers. The tiles are `Obelisk`'s own, not re-derived.
     */
    val OBELISKS: List<Destination> =
        listOf(
            Destination("Level 13 obelisk", Tile(3156, 3620, 0)),
            Destination("Level 19 obelisk", Tile(3219, 3656, 0)),
            Destination("Level 27 obelisk", Tile(3035, 3732, 0)),
            Destination("Level 35 obelisk", Tile(3106, 3794, 0)),
            Destination("Level 44 obelisk", Tile(2980, 3866, 0)),
            Destination("Level 50 obelisk", Tile(3307, 3916, 0)),
        )
}
