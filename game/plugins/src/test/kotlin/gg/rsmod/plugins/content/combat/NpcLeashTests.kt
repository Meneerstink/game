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
    fun `a breach monster fighting a familiar keeps its leash and never follows it into a safe zone`() {
        // Deadman audit 2026-09-28: the familiar exception used to let a player drag a breach boss into a safe zone behind it.
        val breachKey =
            gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach::class.java.getDeclaredField("BREACH_NPC_ATTR")
                .apply { isAccessible = true }.get(null) as gg.rsmod.game.model.attr.AttributeKey<Boolean>
        val spawn = Tile(3164, 3530, 0) // just north of the GE, within the chase range of the GE tile below
        fun pawnAt(tile: Tile, owner: gg.rsmod.game.model.entity.Player?, breach: Boolean) =
            io.mockk.mockk<gg.rsmod.game.model.entity.Npc>(relaxed = true).also { n ->
                val attrs = gg.rsmod.game.model.attr.AttributeMap()
                if (breach) attrs[breachKey] = true
                io.mockk.every { n.attr } returns attrs
                io.mockk.every { n.tile } returns tile
                io.mockk.every { n.spawnTile } returns spawn
                io.mockk.every { n.owner } returns owner
                io.mockk.every { n.id } returns if (breach) 14438 else 7331
            }
        val owner = io.mockk.mockk<gg.rsmod.game.model.entity.Player>(relaxed = true)
        val boss = pawnAt(spawn, null, breach = true)
        val safeTile = Tile(3164, 3516, 0) // Grand Exchange, a guarded zone (GuardedZonesTests)
        assertTrue(gg.rsmod.plugins.content.mechanics.pvp.GuardedZones.contains(safeTile))
        assertFalse(gg.rsmod.plugins.content.mechanics.pvp.GuardedZones.contains(Tile(3164, 3535, 0)))
        assertFalse(NpcLeash.mayPursue(boss, pawnAt(safeTile, owner, breach = false), 1), "into the GE behind a familiar")
        assertFalse(NpcLeash.mayPursue(boss, pawnAt(Tile(3164, 3530 + 30, 0), owner, breach = false), 1), "30 tiles past its landing")
        assertTrue(NpcLeash.mayPursue(boss, pawnAt(Tile(3164, 3530 + 5, 0), owner, breach = false), 1), "a familiar next to its landing")
        // an ordinary npc still always defends itself against a familiar (Void Combat.retaliate)
        assertTrue(NpcLeash.mayPursue(pawnAt(spawn, null, breach = false), pawnAt(safeTile, owner, breach = false), 1))
    }

    @Test
    fun `the melee diagonal corner is outside the leash exactly as in Void`() {
        val spawn = Tile(3200, 3200)
        assertFalse(NpcLeash.withinAggro(spawn, Tile(3208, 3208), 7, 1))
        assertTrue(NpcLeash.withinAggro(spawn, Tile(3214, 3214), 7, 7), "the corner rule applies to melee only")
    }
}
