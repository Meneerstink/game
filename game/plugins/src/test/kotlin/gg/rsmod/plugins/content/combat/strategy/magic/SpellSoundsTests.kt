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
            CombatSpell.WEAK_FIRE_BLAST, CombatSpell.WARLOCK_SKELETON_EARTH_STRIKE,
        )

    @Test
    fun `every sourced combat spell has an impact sound`() {
        val missing = CombatSpell.values.filter { SpellSounds.of(it)?.impact == null }.toSet() - unsourced
        assertEquals(emptySet(), missing)
        assertEquals(unsourced, CombatSpell.values.filter { SpellSounds.of(it) == null }.toSet(), "the exception list is exact")
    }

    @Test
    fun `N66 - the Miasmic spells cast and land with their own sounds`() {
        assertEquals(SpellSounds.Sounds(5368, 5365), SpellSounds.of(CombatSpell.MIASMIC_RUSH))
        assertEquals(SpellSounds.Sounds(5366, 5372), SpellSounds.of(CombatSpell.MIASMIC_BURST))
        assertEquals(SpellSounds.Sounds(5370, 5367), SpellSounds.of(CombatSpell.MIASMIC_BLITZ))
        assertEquals(SpellSounds.Sounds(5371, 5369), SpellSounds.of(CombatSpell.MIASMIC_BARRAGE))
    }

    @Test
    fun `a missed spell plays the splash sound`() {
        assertEquals(227, SpellSounds.SPLASH)
    }
}