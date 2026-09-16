package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.Tile

/**
 * Deadman Mode guarded (safe) zones - owner instruction 2026-09-16: "our rsps server needs to have
 * the same mechanics safe and deathzone" as the OSRS Deadman Mode map, i.e. whole guarded cities
 * are safe, everything else (including every bank outside these cities: Edgeville, Draynor,
 * Al Kharid, Ferox, ...) is a death zone. This supersedes the earlier "only bank rooms are safe"
 * model ([BankZones] is kept only as a bank-object locator, not as the PvP safety rule).
 *
 * OSRS Wiki "Deadman Mode" guarded areas that exist in this revision-667 world: Varrock (incl. the
 * Grand Exchange), Falador, Lumbridge, Catherby bank, Seers' Village (Camelot) bank, East Ardougne,
 * Rellekka, Tree Gnome Stronghold, Yanille; plus the Warriors' Guild, which the owner explicitly
 * named/pinned (and which has 15 Deadman guards over its floors on the wiki). Wiki: "No dungeons or
 * Rooftop Agility Courses are considered safezones, even if accessible from safe city limits" -
 * dungeons live at z >= 6400 in this map data and are outside every rectangle below; upper floors
 * (height 1-3) of buildings inside a city are treated as part of the city.
 *
 * Boundaries are rectangles in real 667 tile coordinates. They were derived from the owner's own
 * map screenshots (`C:\RSPS\foto`) calibrated against known bank/landmark tiles; they are
 * PENDING_HUMAN_RETEST - walk each city edge with the `zone` command and adjust here if a road
 * outside a wall reads as guarded or a street inside a wall reads as dangerous.
 */
object GuardedZones {
    data class Zone(
        val name: String,
        val minX: Int,
        val maxX: Int,
        val minZ: Int,
        val maxZ: Int,
        /** Heights considered part of the zone; upper floors count as the city. */
        val heights: IntRange = 0..3,
    ) {
        fun contains(tile: Tile): Boolean =
            tile.height in heights && tile.x in minX..maxX && tile.z in minZ..maxZ
    }

    val ZONES: List<Zone> =
        listOf(
            Zone("Varrock", minX = 3136, maxX = 3295, minZ = 3372, maxZ = 3521),
            Zone("Falador", minX = 2934, maxX = 3062, minZ = 3305, maxZ = 3397),
            Zone("Lumbridge", minX = 3203, maxX = 3256, minZ = 3202, maxZ = 3252),
            Zone("Catherby bank", minX = 2803, maxX = 2814, minZ = 3435, maxZ = 3448),
            Zone("Seers' Village bank", minX = 2717, maxX = 2732, minZ = 3486, maxZ = 3499),
            Zone("East Ardougne", minX = 2560, maxX = 2690, minZ = 3262, maxZ = 3346),
            Zone("Rellekka", minX = 2598, maxX = 2705, minZ = 3636, maxZ = 3720),
            Zone("Tree Gnome Stronghold", minX = 2368, maxX = 2505, minZ = 3378, maxZ = 3525),
            Zone("Yanille", minX = 2528, maxX = 2624, minZ = 3068, maxZ = 3113),
            Zone("Warriors' Guild", minX = 2836, maxX = 2878, minZ = 3531, maxZ = 3560),
        )

    fun zoneAt(tile: Tile): Zone? = ZONES.firstOrNull { it.contains(tile) }

    fun contains(tile: Tile): Boolean = zoneAt(tile) != null
}
