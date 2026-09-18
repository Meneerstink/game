package gg.rsmod.game.sync.segment

import gg.rsmod.game.model.Hit
import kotlin.test.Test
import kotlin.test.assertEquals

class HitmarkEntriesTests {
    @Test
    fun `every hitmark becomes its own client entry with its hit's delay`() {
        val double = Hit.Builder().addHit(damage = 12, type = 1).addHit(damage = 7, type = 1).setClientDelay(2).build()
        val single = Hit.Builder().addHit(damage = 30, type = 1).build()
        val entries = HitmarkEntries.of(listOf(double, single))
        assertEquals(3, entries.size, "a two-hitmark hit must not be packed into one client entry")
        assertEquals(listOf(12, 7, 30), entries.map { it.damage })
        assertEquals(listOf(2, 2, single.clientDelay), entries.map { it.delay })
    }

    @Test
    fun `a hit without hitmarks sends the no-hitmark marker`() {
        val barOnly = Hit.Builder().onlyShowHitbar().build()
        val entries = HitmarkEntries.of(listOf(barOnly))
        assertEquals(listOf(HitmarkEntries.NO_HITMARK), entries.map { it.type })
    }
}
