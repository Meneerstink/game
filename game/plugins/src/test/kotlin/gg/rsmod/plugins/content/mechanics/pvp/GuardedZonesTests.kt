package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.Tile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The guarded zones are the OSRS Deadman Mode map polygons (wiki map data, 2026-09-17). These tests
 * pin the contract that made the owner's "guards teleport onto you in a dangerous zone" bug
 * impossible: tiles outside a city wall are outside the zone even when they sit inside the old
 * bounding rectangle.
 */
class GuardedZonesTests {
    @Test
    fun `known safe tiles inside every wiki polygon are guarded`() {
        listOf(
            "Varrock" to Tile(3165, 3487, 0), // Grand Exchange
            "Varrock" to Tile(3212, 3428, 0), // Varrock square
            "Varrock" to Tile(3253, 3420, 0), // east bank
            "Varrock" to Tile(3185, 3436, 0), // west bank
            "Falador" to Tile(2965, 3380, 0),
            "Falador" to Tile(3013, 3355, 0), // east bank
            "Lumbridge" to Tile(3222, 3218, 0),
            "Lumbridge" to Tile(3208, 3220, 2), // castle bank, top floor
            "Catherby bank" to Tile(2809, 3441, 0),
            "Seers' Village bank" to Tile(2725, 3491, 0),
            "East Ardougne" to Tile(2661, 3305, 0),
            "Rellekka" to Tile(2660, 3665, 0),
            "Tree Gnome Stronghold" to Tile(2465, 3495, 0),
            "Yanille" to Tile(2612, 3093, 0),
            "Jatizso" to Tile(2405, 3810, 0),
            "Neitiznot" to Tile(2340, 3805, 0),
            "Port Phasmatys" to Tile(3670, 3480, 0),
            "Sophanem" to Tile(3300, 2780, 0),
            "Tutorial Island" to Tile(3100, 3100, 0),
            "Void Knights' Outpost" to Tile(2660, 2650, 0),
        ).forEach { (name, tile) ->
            assertEquals(name, GuardedZones.zoneAt(tile)?.name, "$tile must be inside $name")
        }
    }

    @Test
    fun `tiles just outside a city wall are dangerous even inside the old bounding rectangle`() {
        listOf(
            Tile(3300, 3500, 0), // Lumber Yard, east of Varrock (old rectangle reached x 3295)
            Tile(3190, 3360, 0), // Champions' Guild, south of Varrock
            Tile(3280, 3495, 0), // Jolly Boar Inn, north-east of Varrock
            Tile(3235, 3515, 0), // north of the Varrock wall
            Tile(3130, 3500, 0), // west of the Grand Exchange
            Tile(3094, 3491, 0), // Edgeville bank
            Tile(3092, 3245, 0), // Draynor bank
            Tile(3269, 3167, 0), // Al Kharid bank
            Tile(3139, 3629, 0), // Ferox bank
            Tile(2856, 3546, 0), // Warriors' Guild is not an OSRS Deadman safe zone
            Tile(2970, 3300, 0), // south of Falador wall
            Tile(3212, 3428 + 6400, 0), // Varrock sewers
        ).forEach { tile ->
            assertFalse(GuardedZones.contains(tile), "$tile must be a death zone")
        }
    }

    @Test
    fun `polygon containment follows the wiki edge exactly at the Varrock west gate`() {
        // Wiki edge x = 3174 between z 3399 and 3448: x 3174 is the wall line (inside), 3173 the road outside.
        assertTrue(GuardedZones.contains(Tile(3174, 3420, 0)))
        assertFalse(GuardedZones.contains(Tile(3173, 3420, 0)))
    }

    @Test
    fun `every zone has an interior sample tile and dungeons never count`() {
        GuardedZones.ZONES.forEach { zone ->
            assertTrue(zone.contains(zone.sample), "${zone.name} sample must be inside")
            assertFalse(zone.contains(Tile(zone.sample.x, zone.sample.z + 6400, 0)), "${zone.name} dungeon offset must be outside")
        }
    }

    @Test
    fun `the stationed posts are the 108 distinct OSRS wiki guard pins, on every guarded zone that has guards`() {
        assertEquals(108, GuardPosts.ALL.size)
        assertEquals(GuardPosts.ALL.size, GuardPosts.ALL.map { it.tile }.toSet().size, "no duplicate tiles")
        val expected =
            mapOf(
                "Varrock" to 26, "Falador" to 6, "Lumbridge" to 9, "Catherby bank" to 1, "Seers' Village bank" to 2,
                "East Ardougne" to 13, "Rellekka" to 7, "Tree Gnome Stronghold" to 16, "Yanille" to 6, "Jatizso" to 4,
                "Neitiznot" to 3, "Port Phasmatys" to 6, "Sophanem" to 6, "Void Knights' Outpost" to 3,
            )
        assertEquals(expected, GuardPosts.ALL.groupingBy { it.city }.eachCount())
        assertEquals(5, GuardPosts.ALL.count { it.tile.height > 0 }, "Lumbridge Castle x3 and Gnome Stronghold first floor x2")
    }

    @Test
    fun `every stationed guard post lies inside its own zone`() {
        // The wiki's own Yanille pin 2614,3104 lies ~2 tiles outside the wiki's own Yanille polygon (the
        // diagonal south-east wall edge 2620,3097 -> 2608,3109); CityGuards.spawnStationedGuards snaps it to
        // the nearest walkable tile inside the zone. Any other pin outside its zone is a data error.
        val outside = GuardPosts.ALL.filter { GuardedZones.zoneAt(it.tile)?.name != it.city }
        assertEquals(listOf(Tile(2614, 3104, 0)), outside.map { it.tile }, "guard posts outside their own zone")
        val yanille = GuardedZones.ZONES.first { it.name == "Yanille" }
        assertTrue((-2..2).any { dx -> (-2..2).any { dz -> yanille.contains(Tile(2614 + dx, 3104 + dz, 0)) } }, "snap target within 2 tiles")
    }
}
