package gg.rsmod.plugins.content.combat.strategy.magic

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every player combat spell has its cast and impact sound (owner 2026-09-18: "alle mage spells hebben geen sounds"),
 * except the ones no source names. The exception list is exact so a spell silently losing its sounds fails here.
 */
class SpellSoundsTests {
    private val unsourced =
        setOf(
            CombatSpell.MIASMIC_RUSH, CombatSpell.MIASMIC_BURST, CombatSpell.MIASMIC_BLITZ, CombatSpell.MIASMIC_BARRAGE,
            CombatSpell.WEAK_FIRE_BLAST, CombatSpell.WARLOCK_SKELETON_EARTH_STRIKE,
        )

    @Test
    fun `every sourced combat spell has an impact sound`() {
        val missing = CombatSpell.values.filter { SpellSounds.of(it)?.impact == null }.toSet() - unsourced
        assertEquals(emptySet(), missing)
    }

    @Test
    fun `a missed spell plays the splash sound`() {
        assertEquals(227, SpellSounds.SPLASH)
    }
}