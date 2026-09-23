package gg.rsmod.plugins.content.items.potion

import kotlin.test.Test
import kotlin.test.assertEquals

/** Shared percentage-formula regression coverage for the complete combat/potion path. */
class PotionFormulaAuditTests {
    @Test
    fun `percentage boosts retain fractions before flooring at every level`() {
        for (level in 1..99) {
            assertEquals(level * 15 / 100 + 5, PotionType.SUPER_STRENGTH.boostQuantity(level.toDouble(), "s"), "super combat at $level")
            assertEquals(level * 35 / 100 + 7, PotionType.SUPER_PRAYER.boostQuantity(level.toDouble(), "s_prayer"), "super prayer at $level")
            assertEquals(level * 15 / 100 + 2, PotionType.SARADOMIN_BREW.boostQuantity(level.toDouble(), "brewHealth"), "Saradomin brew at $level")
        }
    }

    @Test
    fun `level 99 reference values match OSRS potion behaviour`() {
        assertEquals(19, PotionType.SUPER_STRENGTH.boostQuantity(99.0, "s"))
        assertEquals(41, PotionType.SUPER_PRAYER.boostQuantity(99.0, "s_prayer"))
        assertEquals(16, PotionType.SARADOMIN_BREW.boostQuantity(99.0, "brewHealth"))
    }
}
