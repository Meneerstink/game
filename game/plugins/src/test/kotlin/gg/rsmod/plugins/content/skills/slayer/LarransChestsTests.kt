package gg.rsmod.plugins.content.skills.slayer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** N522 nachtrun deel 3: OSRS Wiki "Larran's key" / "Larran's small chest" / "Larran's big chest", Module:Slayer chest fish chart. */
class LarransChestsTests {
    @Test fun `key chance follows the three Wiki combat-level bands and Slayer monsters get 20 percent more`() {
        assertEquals(1.0 / 1972, LarransChests.keyChance(1, false), 1e-12) // floor(0.3 x 79^2) + 100
        assertEquals(1.0 / 100, LarransChests.keyChance(80, false), 1e-12)
        assertEquals(1.0 / 100, LarransChests.keyChance(81, false), 1e-12) // floor(-15.0) + 115
        assertEquals(1.0 / 50, LarransChests.keyChance(350, false), 1e-12) // floor(-64.8) + 115
        assertEquals(1.0 / 58, LarransChests.keyChance(305, false), 1e-12) // Chaos Elemental: floor(-56.5) + 115
        assertEquals(1.0 / 50, LarransChests.keyChance(1000, false), 1e-12)
        assertEquals(1.2 / 100, LarransChests.keyChance(80, true), 1e-12)
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
