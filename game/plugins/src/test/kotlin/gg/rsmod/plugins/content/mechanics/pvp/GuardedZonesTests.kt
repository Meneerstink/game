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
            "Warriors' Guild" to Tile(2856, 3546, 1),
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
    fun `every stationed guard post lies inside its own zone`() {
        GuardPosts.ALL.forEach { post ->
            assertEquals(post.city, GuardedZones.zoneAt(post.tile)?.name, "guard post ${post.tile} of ${post.city} is outside its zone")
        }
    }
}
