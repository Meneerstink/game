package gg.rsmod.plugins.content.combat

import gg.rsmod.game.model.Tile
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * RCV-005 npc leash model (Void `CombatMovement.withinAggro` + `npc_ranges`), driven by the real
 * generated table `data/cfg/npcs/npc-ranges.json`.
 */
class NpcLeashTests {
    companion object {
        @BeforeClass
        @JvmStatic
        fun load() {
            val rows = NpcLeash.load(Paths.get("..", "..", "data", "cfg", "npcs", "npc-ranges.json").toFile())
            assertTrue(rows > 0, "the generated leash table must not be empty")
        }
    }

    @Test
    fun `boss max ranges come from the Void npc_ranges table`() {
        // Void wander_ranges.tables.toml rows, matched to rev-667 ids by tools/npc-ranges/generate.js.
        val expected =
            mapOf(
                50 to 50, // king_black_dragon
                6260 to 20, // general_graardor
                6222 to 20, // kree_arra
                6247 to 15, // commander_zilyana
                6203 to 16, // kril_tsutsaroth
                1158 to 40, // kalphite_queen
                1160 to 40, // kalphite_queen_airborne
                2883 to 63, // dagannoth_rex
                8133 to 25, // corporeal_beast
            )
        val wrong = expected.filter { (id, range) -> NpcLeash.maxRange(id) != range }
        assertTrue(wrong.isEmpty(), "leash ranges differ from Void for npc ids: ${wrong.keys}")
    }

    @Test
    fun `an npc without a row uses the Void default max range of 7`() {
        assertEquals(NpcLeash.DEFAULT_MAX_RANGE, NpcLeash.maxRange(3264)) // Goblin: no npc_ranges row
        assertEquals(7, NpcLeash.DEFAULT_MAX_RANGE)
    }

    @Test
    fun `a running player is followed across the whole leash, not dropped after 6 tiles`() {
        val spawn = Tile(3200, 3200)
        // Default melee npc: max range 7 + attack range 1 = 8 tiles from spawn on each axis.
        assertTrue(NpcLeash.withinAggro(spawn, Tile(3207, 3200), 7, 1))
        assertTrue(NpcLeash.withinAggro(spawn, Tile(3208, 3203), 7, 1))
        assertFalse(NpcLeash.withinAggro(spawn, Tile(3209, 3200), 7, 1), "one tile past the leash ends the fight")
    }

    @Test
    fun `the melee diagonal corner is outside the leash exactly as in Void`() {
        val spawn = Tile(3200, 3200)
        assertFalse(NpcLeash.withinAggro(spawn, Tile(3208, 3208), 7, 1))
        assertTrue(NpcLeash.withinAggro(spawn, Tile(3214, 3214), 7, 7), "the corner rule applies to melee only")
    }
}
