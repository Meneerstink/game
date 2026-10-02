package gg.rsmod.plugins.content.combat

import kotlin.test.Test
import kotlin.test.assertEquals

/** Overnight 2026-10-02 PvP audit: the Elysian and Divine spirit shields had no passive at all (SpiritShields sources). */
class SpiritShieldsTests {
    @Test
    fun `elysian takes 25 percent off on a 70 percent roll and at least 1`() {
        assertEquals(30, SpiritShields.elysian(40, roll = 0))
        assertEquals(30, SpiritShields.elysian(40, roll = 69))
        assertEquals(40, SpiritShields.elysian(40, roll = 70))
        assertEquals(0, SpiritShields.elysian(1, roll = 0))
        assertEquals(2, SpiritShields.elysian(3, roll = 0))
        assertEquals(0, SpiritShields.elysian(0, roll = 0))
        // Over every roll 0..99 exactly 70 fire.
        assertEquals(70, (0..99).count { SpiritShields.elysian(40, it) < 40 })
    }

    @Test
    fun `divine takes 30 percent off for half the negated damage in prayer, rounded up`() {
        assertEquals(28 to 6, SpiritShields.divine(40))
        assertEquals(7 to 2, SpiritShields.divine(10))
        assertEquals(0 to 1, SpiritShields.divine(1))
    }
}
