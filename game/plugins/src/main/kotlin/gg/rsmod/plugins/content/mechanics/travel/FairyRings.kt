package gg.rsmod.plugins.content.mechanics.travel

import gg.rsmod.game.model.Tile

/**
 * The fairy ring network (owner 2026-09-24: "Check alle donors Void en Novite voor dingen die onze rsps nog niet heeft ... port t
 * dan"). Only the Zanaris ring 12094 worked here (to the Lumbridge shed); the other 43 rings answered nothing.
 *
 * Sources: Void `fairy_ring_codes.toml` (codes, names, tiles - the same rev-634/667 map) and `fairy_ring.ifaces.toml` /
 * `fairy_ring.varbits.toml` (dial 734: Teleport 21, rotate clockwise/anticlockwise 23-28; the three dials are varbits 2341-2343 whose
 * values index the letters a d c b / i l k j / p s r q); OSRS Wiki "Fairy ring": every ring can dial every other ring, which needs a
 * dramen or lunar staff wielded.
 * ADAPTED: quests are not on this server, so the destinations Void gates behind a quest are open; an unknown or empty code lands in
 * Zanaris, as in Void.
 */
object FairyRings {
    const val DIAL_INTERFACE = 734
    const val TRAVEL_LOG_INTERFACE = 735
    const val TELEPORT_BUTTON = 21
    val DIAL_VARBITS = intArrayOf(2341, 2342, 2343)
    val DIAL_LETTERS = listOf(listOf('a', 'd', 'c', 'b'), listOf('i', 'l', 'k', 'j'), listOf('p', 's', 'r', 'q'))
    val ZANARIS: Tile = Tile(2412, 4434, 0)

    class Destination(val name: String, val tile: Tile)

    val CODES: Map<String, Destination> =
        mapOf(
            "aiq" to Destination("Asgarnia: Mudskipper Point", Tile(2996, 3114, 0)),
            "air" to Destination("Islands: South of Witchaven", Tile(2700, 3247, 0)),
            "ajq" to Destination("Dungeons: Cave south of Dorgesh-Kaan", Tile(2735, 5221, 0)),
            "ajr" to Destination("Kandarin: Slayer cave south-east of Rellekka", Tile(2780, 3613, 0)),
            "ajs" to Destination("Islands: Penguins near Miscellania", Tile(2500, 3896, 0)),
            "akq" to Destination("Kandarin: Piscatoris Hunter area", Tile(2319, 3619, 0)),
            "aks" to Destination("Feldip Hills: Feldip Hunter area", Tile(2571, 2956, 0)),
            "alp" to Destination("Kandarin: Feldip Hills", Tile(2468, 4189, 0)),
            "alq" to Destination("Morytania: Haunted Woods east of Canifis", Tile(3597, 3495, 0)),
            "alr" to Destination("Other Realms: Abyssal Area", Tile(3059, 4875, 0)),
            "als" to Destination("Kandarin: McGrubor's Wood", Tile(2644, 3495, 0)),
            "bip" to Destination("Islands: South-west of Mort Myre", Tile(3410, 3324, 0)),
            "biq" to Destination("Kharidian Desert near Kalphite Hive", Tile(3251, 3095, 0)),
            "bir" to Destination("Other Realms: Sparse Plane", Tile(2455, 4396, 0)),
            "bis" to Destination("Kandarin: Ardougne Zoo - Unicorns", Tile(2635, 3266, 0)),
            "bjr" to Destination("Other Realms: Realm of the Fisher King", Tile(2650, 4730, 0)),
            "bkp" to Destination("Feldip Hills: South of Castle Wars", Tile(2385, 3035, 0)),
            "bkq" to Destination("Other Realms: Enchanted Valley", Tile(3041, 4532, 0)),
            "bkr" to Destination("Morytania: Mort Myre Swamp, south of Canifis", Tile(3469, 3431, 0)),
            "bks" to Destination("Other Realms: Zanaris", Tile(2412, 4434, 0)),
            "blp" to Destination("Dungeons: TzHaar area", Tile(2437, 5126, 0)),
            "blq" to Destination("Other Realms: Yu'biusk", Tile(2228, 4244, 1)),
            "blr" to Destination("Kandarin: Legends' Guild", Tile(2740, 3351, 0)),
            "cip" to Destination("Islands: Miscellania", Tile(2513, 3884, 0)),
            "ciq" to Destination("Kandarin: North-west of Yanille", Tile(2528, 3127, 0)),
            "cjr" to Destination("Kandarin: Sinclair Mansion (east)", Tile(2705, 3576, 0)),
            "ckp" to Destination("Other Realms: Cosmic entity's plane", Tile(2075, 4848, 0)),
            "ckr" to Destination("Karamja: South of Tai Bwo Wannai Village", Tile(2801, 3003, 0)),
            "cks" to Destination("Morytania: Canifis", Tile(3447, 3470, 0)),
            "clp" to Destination("Islands: South of Draynor Village", Tile(3082, 3206, 0)),
            "clr" to Destination("Islands: Ape Atoll", Tile(2735, 2742, 0)),
            "cls" to Destination("Islands: Hazelmere's home", Tile(2682, 3081, 0)),
            "dip" to Destination("Mos Le'Harmless: Isle on the coast of Mos Le'Harmless", Tile(3763, 2930, 0)),
            "dir" to Destination("Other Realms: Gorak's Plane", Tile(3038, 5348, 0)),
            "dis" to Destination("Misthalin: Wizards' Tower", Tile(3108, 3149, 0)),
            "djp" to Destination("Kandarin: Tower of Life", Tile(2658, 3230, 0)),
            "djr" to Destination("Kandarin: Sinclair Mansion (west)", Tile(2676, 3587, 0)),
            "dkp" to Destination("Karamja: South of Musa Point", Tile(2900, 3111, 0)),
            "dkr" to Destination("Misthalin: Edgeville", Tile(3129, 3496, 0)),
            "dks" to Destination("Kandarin: Polar Hunter area", Tile(2744, 3719, 0)),
            "dlq" to Destination("Kharidian Desert: North of Nardah", Tile(3423, 3016, 0)),
            "dlr" to Destination("Islands: Poison Waste south of Isafdar", Tile(2213, 3099, 0)),
            "dls" to Destination("Dungeons: Myreque hideout under The Hollows", Tile(3501, 9821, 3)),
        )

    /** The dialled code from the three dial varbits. */
    fun code(dials: IntArray): String = dials.mapIndexed { i, v -> DIAL_LETTERS[i][v and 3] }.joinToString("")

    /** The dial value after one turn clockwise (+1) or anticlockwise (-1). */
    fun turn(value: Int, amount: Int): Int = (value + amount) and 3
}
