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

    /**
     * Owner 2026-09-20: "in some part of the ge is dangerous this is a big bug ! 3164, 3516, 0 is the location
     * only this but check the whole grand ex should be safe !".
     *
     * The wiki polygon's north edge dipped to z 3515 between x 3162 and x 3167, which left a wedge of dangerous
     * tiles standing inside the Grand Exchange courtyard. This sweeps the whole enclosure rather than the one
     * tile the owner happened to stand on, so a future edit to the Varrock polygon cannot re-open a hole here.
     */
    @Test
    fun `every tile of the Grand Exchange enclosure is inside the Varrock zone`() {
        val dangerous =
            (3143..3189).flatMap { x ->
                (3470..3517).map { z -> Tile(x, z, 0) }
            }.filterNot { GuardedZones.zoneAt(it)?.name == "Varrock" }
        assertEquals(emptyList(), dangerous, "Grand Exchange tiles outside the Varrock safe zone")
        assertTrue(GuardedZones.contains(Tile(3164, 3516, 0)), "the tile the owner reported")
        // Upper floors of the Grand Exchange count as the city, the sewers below it never do.
        assertTrue(GuardedZones.contains(Tile(3164, 3516, 1)))
        assertFalse(GuardedZones.contains(Tile(3164, 3516 + 6400, 0)))
    }

    /**
     * Owner 2026-09-23: "in grand exchange mag nooit dangerous zijn" (3158,3465 was Dangerous). The strip inside the
     * south wall and the whole gate passage are Guarded; the Dangerous side starts at the gatehouse front (z 3461).
     */
    @Test
    fun `the Grand Exchange south wall strip and gate passage are guarded, outside the gate is not`() {
        val inside = (3151..3178).map { Tile(it, 3465, 0) } + (3151..3178).map { Tile(it, 3466, 0) } +
            (3159..3170).flatMap { x -> (3462..3464).map { z -> Tile(x, z, 0) } }
        assertEquals(emptyList(), inside.filterNot { GuardedZones.contains(it) }, "GE tiles still Dangerous")
        assertTrue(GuardedZones.contains(Tile(3158, 3465, 0)), "the tile the owner reported")
        listOf(Tile(3164, 3461, 0), Tile(3162, 3461, 0), Tile(3167, 3461, 0), Tile(3155, 3463, 0), Tile(3175, 3463, 0))
            .forEach { assertFalse(GuardedZones.contains(it), "$it is outside the GE wall and stays Dangerous") }
    }

    /**
     * The zone is a polygon, not a bounding box, so filling the Grand Exchange notch must not have widened
     * Varrock past its own wall: the wilderness ditch north of the Grand Exchange stays dangerous.
     */
    @Test
    fun `filling the Grand Exchange notch did not reach past the north wall`() {
        listOf(
            Tile(3164, 3518, 0), // the wall line itself
            Tile(3164, 3521, 0), // the wilderness ditch
            Tile(3164, 3530, 0), // level 1 wilderness
            Tile(3137, 3500, 0), // one tile west of the Grand Exchange wall
        ).forEach { assertFalse(GuardedZones.contains(it), "$it must stay a death zone") }
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
