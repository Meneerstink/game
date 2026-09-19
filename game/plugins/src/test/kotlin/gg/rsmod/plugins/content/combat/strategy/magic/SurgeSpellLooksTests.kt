package gg.rsmod.plugins.content.combat.strategy.magic

import gg.rsmod.plugins.content.areas.poh.PohTeleports
import gg.rsmod.plugins.content.items.osrs.OsrsSeq
import gg.rsmod.plugins.content.magic.teleports.TeleportSpell
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner 2026-09-19: "all the surge spells in normal spellbook have the same animation as osrs and sound". Every Surge casts
 * OSRS 7855 HUMAN_CAST_SURGE (with or without staff) and plays its own imported OSRS surge synths (OSRS 4025-4032,
 * tx-20260919-205526); no Wave spell carries the Surge animation (the 2026-09-19 night run had put it on the Waves).
 */
class SurgeSpellLooksTests {
    private val surges = listOf(CombatSpell.WIND_SURGE, CombatSpell.WATER_SURGE, CombatSpell.EARTH_SURGE, CombatSpell.FIRE_SURGE)
    private val waves = listOf(CombatSpell.WIND_WAVE, CombatSpell.WATER_WAVE, CombatSpell.EARTH_WAVE, CombatSpell.FIRE_WAVE)

    @Test
    fun `every surge casts the OSRS surge animation`() {
        val wrong = surges.filter { s -> s.castAnimation.take(2).any { it != OsrsSeq.HUMAN_CAST_SURGE } }
        assertEquals(emptyList(), wrong)
    }

    @Test
    fun `no wave casts the surge animation`() {
        val wrong = waves.filter { w -> w.castAnimation.any { it == OsrsSeq.HUMAN_CAST_SURGE } }
        assertEquals(emptyList(), wrong)
    }

    @Test
    fun `every surge plays its own imported OSRS surge sounds`() {
        val expected =
            mapOf(
                CombatSpell.EARTH_SURGE to SpellSounds.Sounds(10331, 10332),
                CombatSpell.WIND_SURGE to SpellSounds.Sounds(10334, 10333),
                CombatSpell.WATER_SURGE to SpellSounds.Sounds(10336, 10335),
                CombatSpell.FIRE_SURGE to SpellSounds.Sounds(10338, 10337),
            )
        surges.forEach { assertEquals(expected[it], SpellSounds.of(it), it.name) }
    }

    @Test
    fun `the house teleport directory holds every teleport spell`() {
        val names = PohTeleports.leaves().map { it.name }.toSet()
        val missing =
            TeleportSpell.values().map { it.spellName.removeSuffix(" Teleport").removePrefix("Teleport to ") }.filter { it !in names }
        assertEquals(emptyList(), missing)
        assertTrue(PohTeleports.leaves().size >= 60, "directory has ${PohTeleports.leaves().size} destinations")
    }
}
