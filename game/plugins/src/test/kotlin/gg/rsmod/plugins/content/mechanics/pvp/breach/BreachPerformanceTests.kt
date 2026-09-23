package gg.rsmod.plugins.content.mechanics.pvp.breach

import gg.rsmod.game.model.Tile
import gg.rsmod.plugins.content.mechanics.pvp.AreaState
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Owner 2026-09-23 checklist (tick/GC performance, 50/100/200+ players): server-side cost guards for the two paths this batch
 * put on hot routes - the per-monster damage ledger (every landed hit on a breach monster) and the Safe/Dangerous test that
 * [gg.rsmod.game.model.MoveGate] now runs on every walked step and teleport. Budgets are generous (CI noise) but a real
 * regression (a scan of every player, a polygon rebuilt per call) blows through them by orders of magnitude.
 */
class BreachPerformanceTests {
    @Test
    fun `a 250-player fight on one monster records and ranks in milliseconds`() {
        val ledger = BreachContribution()
        val names = (1..250).map { "player$it" }
        val start = System.nanoTime()
        repeat(40) { round -> names.forEach { ledger.add(it, 1 + (round % 7)) } } // 10,000 landed hits
        val loot = ledger.lootEligible()
        val points = ledger.pointEarners()
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue(loot.size == BreachLoot.ELIGIBLE && points.size == BreachPoints.EARNERS)
        assertTrue(ms < 500, "10,000 hits + ranking took $ms ms")
    }

    @Test
    fun `the Safe or Dangerous test is cheap enough for every step of 200 players`() {
        val tiles = listOf(Tile(3165, 3487, 0), Tile(3100, 3530, 0), Tile(3212, 3428, 0), Tile(2944, 3368, 0), Tile(3222, 3218, 0))
        val start = System.nanoTime()
        var dangerous = 0
        // 200 players, 2 steps a tick (running), both ends checked: 800 calls per tick; 1,000 ticks here.
        repeat(800_000) { if (AreaState.isDangerous(tiles[it % tiles.size])) dangerous++ }
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue(dangerous > 0)
        assertTrue(ms < 4_000, "800,000 zone checks (1,000 ticks of 200 running players) took $ms ms")
    }
}
