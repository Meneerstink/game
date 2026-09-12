package gg.rsmod.plugins.content.areas.godwars

import gg.rsmod.game.model.Tile
import gg.rsmod.plugins.api.cfg.Npcs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GodWarsInteractionTests {
    @Test
    fun `unused altars allow first recharge without integer overflow`() {
        for (cycle in listOf(0, 1, 1000, Int.MAX_VALUE)) {
            assertTrue(GodWars.canRechargeAltar(cycle, null))
        }
        assertFalse(GodWars.canRechargeAltar(1099, 100))
        assertTrue(GodWars.canRechargeAltar(1100, 100))
    }

    @Test
    fun `all four airborne Armadyl encounter npcs reject melee`() {
        for (npc in listOf(Npcs.KREEARRA, Npcs.WINGMAN_SKREE, Npcs.FLOCKLEADER_GEERIN, Npcs.FLIGHT_KILISA)) {
            assertTrue(GodWars.isFlyingArmadylNpc(npc), "NPC $npc")
        }
        for (npc in 6229..6231) assertFalse(GodWars.isFlyingArmadylNpc(npc))
        assertFalse(GodWars.isFlyingArmadylNpc(Npcs.GENERAL_GRAARDOR))
    }

    @Test
    fun `every sourced GWD npc belongs to one faction`() {
        val gods = GodWars.God.values()
        for (index in gods.indices) {
            for (otherIndex in index + 1 until gods.size) {
                assertTrue(
                    gods[index].npcs.intersect(gods[otherIndex].npcs).isEmpty(),
                    "${gods[index]} and ${gods[otherIndex]} share a faction NPC",
                )
            }
        }
        assertTrue(GodWars.God.forNpcId(6255) == GodWars.God.SARADOMIN)
        assertTrue(GodWars.God.forNpcId(6256) == GodWars.God.SARADOMIN)
        assertTrue(GodWars.God.forNpcId(6257) == GodWars.God.SARADOMIN)
        assertTrue(GodWars.God.forNpcId(6229) == GodWars.God.ARMADYL)
    }

    @Test
    fun `wind chill follows the sourced polygon boundaries`() {
        assertTrue(GodWars.inGodWarsChillArea(Tile(2943, 3712, 0)))
        assertTrue(GodWars.inGodWarsChillArea(Tile(2839, 3744, 0)))
        assertTrue(GodWars.inGodWarsChillArea(Tile(2879, 3839, 0)))
        assertFalse(GodWars.inGodWarsChillArea(Tile(2838, 3712, 0)))
        assertFalse(GodWars.inGodWarsChillArea(Tile(2880, 3839, 0)))
        assertFalse(GodWars.inGodWarsChillArea(Tile(2900, 3800, 0)))
    }

    @Test
    fun `ancient prison remains inside the God Wars lifecycle`() {
        assertTrue(GodWars.inDungeon(Tile(2910, 5203, 0)))
        assertTrue(GodWars.inDungeon(Tile(2899, 5203, 0)))
        assertTrue(GodWars.inDungeon(Tile(2864, 5354, 2)))
        assertFalse(GodWars.inDungeon(Tile(2900, 5189, 0)))
        assertFalse(GodWars.inDungeon(Tile(3000, 5203, 0)))
    }

    @Test
    fun `ancient prison obstacle pipe uses the sourced east and west destinations`() {
        assertEquals(Tile(2863, 5219, 0), GodWars.ancientPrisonObstacleDestination(Tile(2862, 5219, 0)))
        assertEquals(Tile(2860, 5219, 0), GodWars.ancientPrisonObstacleDestination(Tile(2863, 5219, 0)))
    }
}
