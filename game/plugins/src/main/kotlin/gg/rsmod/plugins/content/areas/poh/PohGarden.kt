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

    /**
     * The fairy-ring network, code and destination as 2009scape's `FairyRing` enum records them. The codes whose
     * destination that table leaves null (BJR Realm of the Fisher King, BLQ Yu'biusk, AIS/AIP/AKP) are not listed:
     * they have no reachable destination in this cache either.
     */
    val FAIRY_RINGS: List<Destination> =
        listOf(
            Destination("AIQ - Mudskipper Point", Tile(2996, 3114, 0)),
            Destination("AIR - South of Witchaven", Tile(2700, 3247, 0)),
            Destination("AJQ - Cave south of Dorgesh-Kaan", Tile(2735, 5221, 0)),
            Destination("AJR - Slayer cave near Rellekka", Tile(2780, 3613, 0)),
            Destination("AJS - Penguins near Miscellania", Tile(2500, 3896, 0)),
            Destination("AKQ - Piscatoris Hunter area", Tile(2319, 3619, 0)),
            Destination("AKS - Feldip jungle Hunter area", Tile(2571, 2956, 0)),
            Destination("ALQ - Haunted Woods east of Canifis", Tile(3597, 3495, 0)),
            Destination("ALR - Abyssal Area", Tile(3059, 4875, 0)),
            Destination("ALS - McGrubor's Wood", Tile(2644, 3495, 0)),
            Destination("BIP - River Salve", Tile(3410, 3324, 0)),
            Destination("BIQ - Near the Kalphite hive", Tile(3251, 3095, 0)),
            Destination("BIS - Ardougne Zoo unicorns", Tile(2635, 3266, 0)),
            Destination("BKP - South of Castle Wars", Tile(2385, 3035, 0)),
            Destination("BKQ - Enchanted Valley", Tile(3041, 4532, 0)),
            Destination("BKR - Mort Myre, south of Canifis", Tile(3469, 3431, 0)),
            Destination("BLP - TzHaar area", Tile(2437, 5126, 0)),
            Destination("BLR - Legends' Guild", Tile(2740, 3351, 0)),
            Destination("CIP - Miscellania", Tile(2513, 3884, 0)),
            Destination("CIQ - North-west of Yanille", Tile(2528, 3127, 0)),
            Destination("CJR - Sinclair Mansion", Tile(2705, 3576, 0)),
            Destination("CKP - Cosmic Entity's plane", Tile(2075, 4848, 0)),
            Destination("CKR - South of Tai Bwo Wannai", Tile(2801, 3003, 0)),
            Destination("CKS - Canifis", Tile(3447, 3470, 0)),
            Destination("CLP - South of Draynor Village", Tile(3082, 3206, 0)),
            Destination("CLS - Jungle spiders near Yanille", Tile(2682, 3081, 0)),
            Destination("DIR - Goraks' Plane", Tile(3038, 5348, 0)),
            Destination("DIS - Wizards' Tower", Tile(3108, 3149, 0)),
            Destination("DJP - Tower of Life", Tile(2658, 3230, 0)),
            Destination("DJR - Sinclair Mansion (west)", Tile(2676, 3587, 0)),
            Destination("DKP - South of Musa Point", Tile(2900, 3111, 0)),
            Destination("DKR - Edgeville", Tile(3129, 3496, 0)),
            Destination("DKS - Snowy Hunter area", Tile(2744, 3719, 0)),
            Destination("DLQ - North of Nardah", Tile(3423, 3016, 0)),
            Destination("DLR - Poison Waste south of Isafdar", Tile(2213, 3099, 0)),
            Destination("Zanaris", Tile(2412, 4434, 0)),
        )
}
