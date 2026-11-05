package gg.rsmod.plugins.content.skills.slayer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** N522 nachtrun deel 3: OSRS Wiki "Larran's key" / "Larran's small chest" / "Larran's big chest", Module:Slayer chest fish chart. */
class LarransChestsTests {
    @Test fun `key rate follows Template WildernessSlayerDropTable`() {
        assertEquals(1972, LarransChests.keyDenominator(1, false)) // 100 + floor(0.3 x 79^2)
        assertEquals(100, LarransChests.keyDenominator(80, false))
        assertEquals(99, LarransChests.keyDenominator(81, false)) // floor(99 - 0)
        assertEquals(58, LarransChests.keyDenominator(305, false)) // Chaos Elemental: floor(99 - 224 x 49 / 269) = floor(58.2)
        assertEquals(50, LarransChests.keyDenominator(350, false))
        assertEquals(50, LarransChests.keyDenominator(1000, false))
        assertEquals(83, LarransChests.keyDenominator(80, true)) // floor(100 / 1.2)
        assertEquals(1.0 / 58, LarransChests.keyChance(305, false), 1e-12)
    }

    @Test fun `both main tables are out of 60 with the fish table at 3 of 60`() {
        assertEquals(60, LarransChests.SMALL_TABLE.sumOf { it.weight })
        assertEquals(60, LarransChests.BIG_TABLE.sumOf { it.weight })
        assertEquals(3, LarransChests.SMALL_TABLE.single { it.item == LarransChests.FISH }.weight)
        assertEquals(3, LarransChests.BIG_TABLE.single { it.item == LarransChests.FISH }.weight)
    }

    @Test fun `interp is the Skilling success chart formula`() {
        assertEquals(21.0 / 256, LarransChests.interp(-10, 20, 99), 1e-12)
        assertEquals(0.0, LarransChests.interp(-10, 20, 1), 1e-12) // floor(-10 + 0.5) + 1 = -9 -> clamped 0
        assertEquals(161.0 / 256, LarransChests.interp(160, 0, 1), 1e-12)
    }

    @Test fun `fish rolls sequentially from manta ray down and ends on lobster`() {
        // Everything fails at level 1 except what the chart gives; force every roll to fail -> lobster.
        assertEquals(LarransChests.Fish.LOBSTER, LarransChests.fish(1, { 0.999 }, { 0 }))
        // A succeeding manta roll at 99 with sub-table 0..1 is the manta ray itself, 2..4 a raw shark, 5..7 a shark lure (not here).
        assertEquals(LarransChests.Fish.MANTA, LarransChests.fish(99, { 0.0 }, { 1 }))
        assertEquals(LarransChests.Fish.SHARK, LarransChests.fish(99, { 0.0 }, { 4 }))
        assertNull(LarransChests.fish(99, { 0.0 }, { 7 }))
    }
}
