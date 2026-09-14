package gg.rsmod.plugins.content.items.potion

import gg.rsmod.plugins.api.Skills
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Adjacent gap "667 Ranging / Magic potion boosts vs OSRS" (OSRS Wiki raw wikitext 2026-09-14), every level 1-99:
 * - Ranging potion: floor(Ranged level x 1/10) + 4; Magic potion: Magic level + 4.
 * - Attack / Strength / Defence potion: floor(level x 1/10) + 3 (already equal to OSRS, unchanged).
 */
class OsrsCombatPotionBoostTests {
    @Test
    fun `ranging and magic potions boost like OSRS and the melee potions stay unchanged`() {
        assertEquals(listOf("ranging_potion"), PotionType.RANGING.alterStrategy.toList())
        assertEquals(listOf(Skills.RANGED), PotionType.RANGING.alteredSkills.toList())
        assertEquals(listOf("magic_potion"), PotionType.MAGIC.alterStrategy.toList())
        listOf(PotionType.ATTACK, PotionType.STRENGTH, PotionType.DEFENCE).forEach { assertEquals(listOf("r"), it.alterStrategy.toList(), "$it") }
        for (level in 1..99) {
            assertEquals(level / 10 + 4, PotionType.RANGING.boostQuantity(level.toDouble(), "ranging_potion"), "ranging at $level")
            assertEquals(4, PotionType.MAGIC.boostQuantity(level.toDouble(), "magic_potion"), "magic at $level")
            assertEquals(level / 10 + 3, PotionType.ATTACK.boostQuantity(level.toDouble(), "r"), "attack at $level")
        }
    }
}
