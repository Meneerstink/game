package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.model.Tile
import gg.rsmod.plugins.content.areas.wilderness.bosses.ArtioCombat
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** N522 nachtrun deel 2: Artio - OSRS Wiki infobox / drop table / Strategies, Near Reality Callisto.java for the tick details. */
class ArtioTests : WildernessBossContractTests(ArtioCombat.NPC, 450, listOf(250, 270, 150, 90, 120), 5, 11, "Artio", 2800) {
    @Test fun `she roars once below 66 percent and once below 33 percent, never twice`() {
        assertEquals(1, ArtioCombat.phaseFor(1, 450, 450))
        assertEquals(1, ArtioCombat.phaseFor(1, 300, 450), "66.7 % exactly is not below 66 %")
        assertEquals(2, ArtioCombat.phaseFor(1, 299, 450))
        assertEquals(2, ArtioCombat.phaseFor(2, 200, 450))
        assertEquals(3, ArtioCombat.phaseFor(2, 149, 450))
        assertEquals(3, ArtioCombat.phaseFor(1, 100, 450), "one big hit past both thresholds: the second roar")
        assertEquals(3, ArtioCombat.phaseFor(3, 10, 450))
    }

    @Test fun `knockback hurts 5 for the full push and 15 more per blocked tile`() {
        assertEquals(5, ArtioCombat.knockbackDamage(3))
        assertEquals(20, ArtioCombat.knockbackDamage(2))
        assertEquals(35, ArtioCombat.knockbackDamage(1))
        assertEquals(50, ArtioCombat.knockbackDamage(0))
    }

    @Test fun `protect from melee halves the 15 to 35 swipe`() {
        assertEquals(35, ArtioCombat.meleeDamage(35, protected = false))
        assertEquals(17, ArtioCombat.meleeDamage(35, protected = true))
        assertEquals(7, ArtioCombat.meleeDamage(15, protected = true))
    }

    @Test fun `melee reaches every tile next to the 3x3 including the diagonals, nothing further`() {
        val f = WildernessBossTestSupport.Fixture()
        val npc = f.npc(ArtioCombat.NPC, ArtioCombat.SPAWN)
        val s = ArtioCombat.SPAWN
        assertTrue(ArtioCombat.inMeleeReach(npc, s.transform(-1, -1)), "diagonal corner")
        assertTrue(ArtioCombat.inMeleeReach(npc, s.transform(3, 1)))
        assertFalse(ArtioCombat.inMeleeReach(npc, s.transform(4, 1)))
        assertFalse(ArtioCombat.inMeleeReach(npc, s.transform(1, 1)), "under her")
    }

    @Test fun `traps land on four free tiles of the trap square, never under her or on a trap`() {
        val f = WildernessBossTestSupport.Fixture()
        val npc = f.npc(ArtioCombat.NPC, ArtioCombat.SPAWN)
        val collision = mockk<gg.rsmod.game.model.collision.CollisionManager>(relaxed = true)
        every { f.world.collision } returns collision
        every { collision.isClipped(any()) } returns false
        val taken = setOf(Tile(1751, 11533, 0))
        val tiles = ArtioCombat.trapTiles(npc, taken) { it }
        assertEquals(4, tiles.size)
        tiles.forEach { t ->
            assertTrue(t.x in ArtioCombat.TRAP_X && t.z in ArtioCombat.TRAP_Z)
            assertFalse(t.sameAs(Tile(1751, 11533, 0)))
            assertTrue(ScorpiaCombatDistance.outside(npc.tile, t))
        }
    }
}

private object ScorpiaCombatDistance {
    fun outside(origin: Tile, t: Tile) = gg.rsmod.plugins.content.areas.wilderness.bosses.ScorpiaCombat.distanceToArea(t, origin, 3) > 0
}
