package gg.rsmod.plugins.content.areas.godwars

import gg.rsmod.game.model.Tile
import gg.rsmod.plugins.api.cfg.Npcs
import kotlin.test.Test
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
    fun `wind chill follows the sourced polygon boundaries`() {
        assertTrue(GodWars.inGodWarsChillArea(Tile(2943, 3712, 0)))
        assertTrue(GodWars.inGodWarsChillArea(Tile(2839, 3744, 0)))
        assertTrue(GodWars.inGodWarsChillArea(Tile(2879, 3839, 0)))
        assertFalse(GodWars.inGodWarsChillArea(Tile(2838, 3712, 0)))
        assertFalse(GodWars.inGodWarsChillArea(Tile(2880, 3839, 0)))
        assertFalse(GodWars.inGodWarsChillArea(Tile(2900, 3800, 0)))
    }
}
