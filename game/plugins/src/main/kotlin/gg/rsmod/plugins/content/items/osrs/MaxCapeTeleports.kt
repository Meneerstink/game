package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.Tile

/**
 * The OSRS max cape's teleports (OSRS Wiki "Max cape", "Teleportation options" table - each destination with its map pin and the
 * skillcape it comes from). Destinations this revision-667 world does not have are left out: Farming Guild, Hunter Guild (Kourend /
 * Varlamore), The Pandemonium and the boat teleports (Sailing), and the Hosidius, Aldarin and Prifddinas house portals.
 */
object MaxCapeTeleports {
    class Destination(
        val name: String,
        val tile: Tile,
        /** Hunter cape: "Five teleports per day to the black or carnivorous chinchompa Hunter areas". */
        val dailyHunterLimit: Boolean = false,
    )

    val WARRIORS_GUILD = Destination("Warriors' Guild", Tile(2865, 3546, 0)) // Strength cape
    val FISHING_GUILD = Destination("Fishing Guild", Tile(2604, 3401, 0)) // Fishing cape
    val CRAFTING_GUILD = Destination("Crafting Guild", Tile(2931, 3286, 0)) // Crafting cape
    val OTTOS_GROTTO = Destination("Otto's Grotto", Tile(2504, 3484, 0)) // Fishing cape
    val FELDIP_HUNTER = Destination("Feldip Hunter area", Tile(2556, 2916, 0), dailyHunterLimit = true) // Hunter cape
    val WILDERNESS_HUNTER = Destination("Wilderness Hunter area", Tile(3144, 3772, 0), dailyHunterLimit = true) // Hunter cape

    /** "Guild Teleports" (worn option). */
    val GUILDS = listOf(WARRIORS_GUILD, FISHING_GUILD, CRAFTING_GUILD)

    /** "Skilling Areas" (worn option). */
    val SKILLING_AREAS = listOf(OTTOS_GROTTO, FELDIP_HUNTER, WILDERNESS_HUNTER)

    /** "POH Portals": outside each town's house portal (Construction cape). "Home" is the player's own house. */
    val POH_PORTALS =
        listOf(
            Destination("Rimmington", Tile(2954, 3224, 0)),
            Destination("Taverley", Tile(2894, 3465, 0)),
            Destination("Pollnivneach", Tile(3340, 3004, 0)),
            Destination("Rellekka", Tile(2670, 3632, 0)),
            Destination("Brimhaven", Tile(2758, 3178, 0)),
            Destination("Yanille", Tile(2544, 3095, 0)),
        )

    /** Every destination of the inventory "Teleports" menu, in the wiki's order. */
    val ALL = GUILDS + SKILLING_AREAS

    const val HUNTER_TELEPORTS_PER_DAY = 5
    const val SPELLBOOK_SWAPS_PER_DAY = 5
    const val STAMINA_PER_DAY = 1
    const val SEARCHES_PER_DAY = 3
}
