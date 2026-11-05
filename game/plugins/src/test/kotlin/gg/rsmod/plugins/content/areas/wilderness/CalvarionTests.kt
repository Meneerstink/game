package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.model.Tile
import gg.rsmod.plugins.content.areas.wilderness.bosses.CalvarionCombat
import gg.rsmod.plugins.content.areas.wilderness.bosses.ScorpiaCombat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** N522 nachtrun deel 2: Calvar'ion - OSRS Wiki infobox / drop table / Strategies, Near Reality Vetion.java for the tick details. */
class CalvarionTests : WildernessBossContractTests(CalvarionCombat.NORMAL, 150, listOf(250, 250, 225, 178, 1), 6, Int.MAX_VALUE, "Calvar'ion", 2800) {
    @Test fun `lightning hurts up to 20 on its tile, up to 10 next to it, nothing further`() {
        assertEquals(20, CalvarionCombat.lightningMax(0))
        assertEquals(10, CalvarionCombat.lightningMax(1))
        assertEquals(0, CalvarionCombat.lightningMax(2))
    }

    @Test fun `every other player in the tomb gets a bolt on or next to him, seven players at most`() {
        val others = (1..9).map { Tile(1880 + it, 11545, 1) }
        val bolts = CalvarionCombat.playerBolts(others, { true }) { 1 }
        assertEquals(6, bolts.size, "the target's own bolt + 6 = the Wiki cap of 7")
        bolts.forEachIndexed { i, b -> assertTrue(b.sameAs(others[i].transform(1, 1))) }
        val fallback = CalvarionCombat.playerBolts(others.take(1), { false }) { 1 }
        assertTrue(fallback.single().sameAs(others[0]), "a blocked neighbour falls back to the player's own tile")
    }

    @Test fun `five bolts - one next to the target, four more within 5`() {
        val t = Tile(1887, 11540, 1)
        val tiles = CalvarionCombat.lightningTiles(t, { true }, { it }, t.transform(1, 0))
        assertEquals(5, tiles.size)
        assertTrue(tiles.first().sameAs(t.transform(1, 0)))
        tiles.forEach { assertTrue(Math.abs(it.x - t.x) <= 5 && Math.abs(it.z - t.z) <= 5) }
        val blocked = CalvarionCombat.lightningTiles(t, { !it.sameAs(t.transform(1, 0)) }, { it }, t.transform(1, 0))
        assertTrue(blocked.first().sameAs(t), "a blocked neighbour falls back to the target's own tile")
    }

    @Test fun `the shield ring is his footprint plus one tile all round, the side bash two tiles deep on the target's side`() {
        val f = WildernessBossTestSupport.Fixture()
        val npc = f.npc(CalvarionCombat.NORMAL, CalvarionCombat.SPAWN)
        val ring = CalvarionCombat.shieldTiles(npc, CalvarionCombat.SPAWN.transform(1, -1), ring = true)
        assertEquals(9 + 16, ring.size)
        val south = CalvarionCombat.shieldTiles(npc, CalvarionCombat.SPAWN.transform(1, -1), ring = false)
        val outside = south.filter { ScorpiaCombat.distanceToArea(it, npc.tile, 3) > 0 }
        assertEquals(6, outside.size, "3 wide x 2 deep south of him")
        outside.forEach { assertTrue(it.z < CalvarionCombat.SPAWN.z) }
    }
}
