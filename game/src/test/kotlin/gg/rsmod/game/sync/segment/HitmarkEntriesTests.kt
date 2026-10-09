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

/** OSRS hitsplats per viewer (owner 2026-10-09; RSMod HitBuilder / InternalPlayerHits). */
class OsrsHitsplatResolveTests {
    private val regular = gg.rsmod.game.model.HitmarkGroup.REGULAR_DAMAGE

    @Test
    fun `target and source see lit, others tint`() {
        assertEquals(16, regular.resolve(5, privileged = true, maxThreshold = Int.MAX_VALUE))
        assertEquals(17, regular.resolve(5, privileged = false, maxThreshold = Int.MAX_VALUE))
    }

    @Test
    fun `a regular zero is the block splat`() {
        assertEquals(12, regular.resolve(0, privileged = true, maxThreshold = 0))
        assertEquals(13, regular.resolve(0, privileged = false, maxThreshold = 0))
    }

    @Test
    fun `max splat only for the target and source at the threshold`() {
        assertEquals(43, regular.resolve(30, privileged = true, maxThreshold = 30))
        assertEquals(16, regular.resolve(29, privileged = true, maxThreshold = 30))
        assertEquals(17, regular.resolve(30, privileged = false, maxThreshold = 30))
    }

    @Test
    fun `recoil is untinted and poison has its own tint`() {
        assertEquals(16, gg.rsmod.game.model.HitmarkGroup.UNTINTED_DAMAGE.resolve(3, privileged = false, maxThreshold = 0))
        assertEquals(65, gg.rsmod.game.model.HitmarkGroup.POISON.resolve(6, privileged = true, maxThreshold = 0))
        assertEquals(66, gg.rsmod.game.model.HitmarkGroup.POISON.resolve(6, privileged = false, maxThreshold = 0))
    }

    @Test
    fun `no source means no max splat and the threshold is at least 10`() {
        val hit = Hit.Builder().addHit(damage = 50, type = 0).build()
        assertEquals(Int.MAX_VALUE, hit.maxHitThreshold())
        val entries = HitmarkEntries.of(listOf(hit))
        assertEquals(listOf(16), entries.map { it.type })
    }

    @Test
    fun `pvp shows the uncapped damage`() {
        val hit = Hit.Builder().addHit(damage = 40, type = 0).build()
        hit.hitmarks[0].shownDamage = 40
        hit.hitmarks[0].damage = 12
        assertEquals(listOf(40), HitmarkEntries.of(listOf(hit)).map { it.damage })
    }
}
