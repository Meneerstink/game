package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.Tile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Deadman zone contract: guarded cities are safe, everything else (Ferox, hotspot banks, Wilderness) is a death zone. */
class AreaStateTests {
    private val home = Tile(3165, 3487, 0)

    @Test
    fun `Ferox tile outside the bank is not a safe zone`() {
        val feroxNonBankTile = Tile(3140, 3646, 0)

        assertTrue(AreaState.isPvpAllowed(feroxNonBankTile, home))
        assertFalse(AreaState.isSafe(feroxNonBankTile, home))
    }

    @Test
    fun `hotspot banks outside the Deadman cities are death zones`() {
        listOf(
            Tile(3094, 3491, 0), // Edgeville
            Tile(3092, 3245, 0), // Draynor
            Tile(3269, 3167, 0), // Al Kharid
            Tile(3139, 3629, 0), // Ferox bank
        ).forEach { tile ->
            assertTrue(AreaState.isPvpAllowed(tile, home), "$tile must be a death zone")
        }
    }

    @Test
    fun `every Deadman city and the Grand Exchange home are safe`() {
        listOf(
            Tile(3165, 3487, 0), // Grand Exchange (Varrock)
            Tile(3212, 3428, 0), // Varrock square
            Tile(2965, 3380, 0), // Falador
            Tile(3222, 3218, 0), // Lumbridge castle
            Tile(2809, 3441, 0), // Catherby bank
            Tile(2725, 3491, 0), // Seers' Village bank
            Tile(2661, 3305, 0), // East Ardougne market
            Tile(2660, 3665, 0), // Rellekka
            Tile(2465, 3495, 0), // Grand Tree
            Tile(2612, 3093, 0), // Yanille bank
            Tile(2856, 3546, 0), // Warriors' Guild ground floor
            Tile(2856, 3546, 1), // Warriors' Guild first floor
        ).forEach { tile ->
            assertTrue(AreaState.isSafe(tile, home), "$tile must be guarded")
            assertFalse(AreaState.isPvpAllowed(tile, home))
        }
    }

    @Test
    fun `dungeons under a city are never safe`() {
        assertFalse(AreaState.isSafe(Tile(3212, 3428 + 6400, 0), home), "Varrock sewers must be a death zone")
    }

    @Test
    fun `every wiki Deadman area that exists in this world plus the Warriors' Guild is present`() {
        assertEquals(
            listOf(
                "Varrock", "Falador", "Lumbridge", "Catherby bank", "Seers' Village bank", "East Ardougne", "Rellekka",
                "Tree Gnome Stronghold", "Yanille", "Jatizso", "Neitiznot", "Port Phasmatys", "Sophanem", "Tutorial Island",
                "Void Knights' Outpost", "Warriors' Guild",
            ),
            GuardedZones.ZONES.map { it.name },
        )
    }

    @Test
    fun `streets outside the Varrock wall are death zones even though the old rectangle covered them`() {
        assertTrue(AreaState.isPvpAllowed(Tile(3300, 3500, 0), home), "Lumber Yard must be a death zone")
        assertTrue(AreaState.isPvpAllowed(Tile(3190, 3360, 0), home), "Champions' Guild must be a death zone")
    }

    @Test
    fun `each city has both melee and ranged guards where it has more than one post`() {
        GuardPosts.ALL.groupBy { it.city }.forEach { (city, posts) ->
            if (posts.size > 1) {
                assertTrue(posts.any { it.ranged } && posts.any { !it.ranged }, "$city must mix melee and ranged guards")
            }
        }
    }
}
