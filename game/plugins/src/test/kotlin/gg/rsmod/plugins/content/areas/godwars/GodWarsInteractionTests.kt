package gg.rsmod.plugins.content.areas.godwars

import gg.rsmod.game.model.Tile
import gg.rsmod.plugins.api.cfg.Npcs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GodWarsInteractionTests {
    @Test
    fun `unused altars allow first recharge without integer overflow`() {
        // RCV-011 Q-043-b: the world-cycle attribute became the persisted countdown ALTAR_RECHARGE_TIMER (no cycle
        // arithmetic left to overflow). A never-used altar has no running clock, so the first recharge is allowed;
        // a running clock refuses; the window stays 1000 ticks.
        assertNull(GodWars.altarRefusal(prayerFull = false, recharging = false, underAttack = false))
        assertEquals(GodWars.MSG_ALTAR_WAIT, GodWars.altarRefusal(prayerFull = false, recharging = true, underAttack = false))
        assertEquals(1000, GodWars.ALTAR_RECHARGE_TICKS)
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
    fun `followers drop their god's frozen key piece like Darkan sendGodwarsKill`() {
        assertEquals(gg.rsmod.plugins.api.cfg.Items.FROZEN_KEY_PIECE_SARADOMIN, GodWars.frozenKeyPiece(6247))
        assertEquals(gg.rsmod.plugins.api.cfg.Items.FROZEN_KEY_PIECE_BANDOS, GodWars.frozenKeyPiece(6283))
        assertEquals(gg.rsmod.plugins.api.cfg.Items.FROZEN_KEY_PIECE_ARMADYL, GodWars.frozenKeyPiece(6222))
        assertEquals(gg.rsmod.plugins.api.cfg.Items.FROZEN_KEY_PIECE_ZAMORAK, GodWars.frozenKeyPiece(6221))
        assertEquals(null, GodWars.frozenKeyPiece(13447)) // Zaros followers drop none
        assertEquals(null, GodWars.frozenKeyPiece(6202))
    }

    @Test
    fun `ancient prison remains inside the God Wars lifecycle`() {
        assertTrue(GodWars.inDungeon(Tile(2910, 5203, 0)))
        assertTrue(GodWars.inDungeon(Tile(2899, 5203, 0)))
        assertTrue(GodWars.inDungeon(Tile(2864, 5354, 2)))
        // Darkan isAtGodwars regions: the Zaros approach (11345: followers at x 2860..2894, hole 57254 lands at 2855,5221).
        assertTrue(GodWars.inDungeon(Tile(2855, 5221, 0)))
        assertTrue(GodWars.inDungeon(Tile(2870, 5210, 0)))
        assertTrue(GodWars.inDungeon(Tile(2900, 5189, 0)))
        assertFalse(GodWars.inDungeon(Tile(2815, 5203, 0)))
        assertFalse(GodWars.inDungeon(Tile(2900, 5183, 0)))
        assertFalse(GodWars.inDungeon(Tile(3000, 5203, 0)))
    }

    @Test
    fun `ancient prison obstacle pipe uses the sourced east and west destinations`() {
        assertEquals(Tile(2863, 5219, 0), GodWars.ancientPrisonObstacleDestination(Tile(2862, 5219, 0)))
        assertEquals(Tile(2860, 5219, 0), GodWars.ancientPrisonObstacleDestination(Tile(2863, 5219, 0)))
    }

    @Test
    fun `zamorak bridge uses the sourced preparation side and lands on the far bank of this map`() {
        assertTrue(GodWars.inZamorakPrepare(Tile(2887, 5348, 0)))
        assertFalse(GodWars.inZamorakPrepare(Tile(2887, 5333, 3)))
        // Void ZamorakBridge on this map's two ice bridges (2885,5333,2 south end, 2885,5344,2 north end; probe 2026-10-04): the
        // swim tile is river, the landing is the open bank beside the other bridge end, on plane 2 (Novite's plane-0 tiles were not).
        val south = Tile(2885, 5333, 2)
        val north = Tile(2885, 5344, 2)
        val world = gg.rsmod.plugins.testutil.RealMapWorld.world
        val landing = gg.rsmod.game.model.collision.Landing
        assertEquals(Tile(2885, 5345, 2), GodWars.zamorakBridgeLanding(south, north = true))
        assertEquals(Tile(2885, 5332, 2), GodWars.zamorakBridgeLanding(north, north = false))
        listOf(GodWars.zamorakBridgeLanding(south, true), GodWars.zamorakBridgeLanding(north, false)).forEach {
            assertTrue(landing.pocket(world.collision, it).size >= landing.OPEN_GROUND)
        }
    }
}
