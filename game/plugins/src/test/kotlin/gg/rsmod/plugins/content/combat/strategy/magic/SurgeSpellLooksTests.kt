package gg.rsmod.plugins.content.combat.strategy.magic

import gg.rsmod.plugins.content.areas.poh.PohTeleports
import gg.rsmod.plugins.content.items.osrs.OsrsGfx
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

    /**
     * Owner 2026-09-20: "we need exact OSRS surge animations" - the fire and water surges were still showing 667
     * art. Each surge must draw its cast, travel and impact from the imported OSRS set (OSRS 1455-1466).
     */
    @Test
    fun `every surge draws the OSRS surge graphics for its own element`() {
        val expected =
            mapOf(
                CombatSpell.WIND_SURGE to Triple(OsrsGfx.WIND_SURGE_CASTING, OsrsGfx.WIND_SURGE_TRAVEL, OsrsGfx.WIND_SURGE_IMPACT),
                CombatSpell.WATER_SURGE to Triple(OsrsGfx.WATER_SURGE_CASTING, OsrsGfx.WATER_SURGE_TRAVEL, OsrsGfx.WATER_SURGE_IMPACT),
                CombatSpell.EARTH_SURGE to Triple(OsrsGfx.EARTH_SURGE_CASTING, OsrsGfx.EARTH_SURGE_TRAVEL, OsrsGfx.EARTH_SURGE_IMPACT),
                CombatSpell.FIRE_SURGE to Triple(OsrsGfx.FIRE_SURGE_CASTING, OsrsGfx.FIRE_SURGE_TRAVEL, OsrsGfx.FIRE_SURGE_IMPACT),
            )
        surges.forEach { spell ->
            val (cast, travel, impact) = expected.getValue(spell)
            assertEquals(cast, spell.castGfx?.id, "${spell.name} cast graphic")
            assertEquals(travel, spell.projectile, "${spell.name} projectile")
            assertEquals(impact, spell.impactGfx?.id, "${spell.name} impact graphic")
        }
    }

    /** The specific regression: Wind Surge was firing the *wave* projectile, and no surge may share a wave's look. */
    @Test
    fun `no surge reuses a wave graphic`() {
        val waveGraphics =
            waves.flatMap { listOfNotNull(it.castGfx?.id, it.projectile, it.impactGfx?.id, it.secondProjectile, it.thirdProjectile) }
                .filter { it > 0 }
                .toSet()
        surges.forEach { spell ->
            listOfNotNull(spell.castGfx?.id, spell.projectile, spell.impactGfx?.id).forEach { id ->
                assertTrue(id !in waveGraphics, "${spell.name} reuses wave graphic $id")
            }
        }
    }

    /** The four elements must be visually distinct: no two surges may share a graphic id. */
    @Test
    fun `the four surges do not share graphics with each other`() {
        val all = surges.flatMap { listOfNotNull(it.castGfx?.id, it.projectile, it.impactGfx?.id) }
        assertEquals(all.size, all.toSet().size, "two surges share a graphic: $all")
    }

    /**
     * Every surge sends exactly one projectile, Fire included.
     *
     * The 667 base gave Fire spells a three-projectile volley and Fire Surge inherited it; OSRS fires a single
     * FIRESURGE_TRAVEL (owner 2026-09-20: "its shooting another projectile ... should be the fire surge from
     * osrs"). Fire Blast and Fire Wave keep their volley - only the surges are OSRS-exact here.
     */
    @Test
    fun `no surge fires more than one projectile`() {
        surges.forEach { spell ->
            assertTrue(spell.secondProjectile <= 0, "${spell.name} still has a second projectile: ${spell.secondProjectile}")
            assertTrue(spell.thirdProjectile <= 0, "${spell.name} still has a third projectile: ${spell.thirdProjectile}")
        }
        assertEquals(OsrsGfx.FIRE_SURGE_TRAVEL, CombatSpell.FIRE_SURGE.projectile)
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
