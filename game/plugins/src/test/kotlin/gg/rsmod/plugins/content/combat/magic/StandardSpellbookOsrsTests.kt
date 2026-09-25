package gg.rsmod.plugins.content.combat.magic

import gg.rsmod.plugins.content.combat.formula.ElementalWeakness
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell
import gg.rsmod.plugins.content.combat.strategy.magic.SpellEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Paths

/**
 * The standard spellbook's combat spells against the OSRS Wiki "Standard spellbook" table (read 2026-09-24): base experience,
 * max hit (with the 29 May 2024 tier scaling and Magic Dart's level formula), freeze and drain effects, elemental weakness.
 */
class StandardSpellbookOsrsTests {
    private val xp =
        mapOf(
            CombatSpell.WIND_STRIKE to 5.5, CombatSpell.WATER_STRIKE to 7.5, CombatSpell.EARTH_STRIKE to 9.5, CombatSpell.FIRE_STRIKE to 11.5,
            CombatSpell.WIND_BOLT to 13.5, CombatSpell.WATER_BOLT to 16.5, CombatSpell.EARTH_BOLT to 19.5, CombatSpell.FIRE_BOLT to 22.5,
            CombatSpell.CRUMBLE_UNDEAD to 24.5,
            CombatSpell.WIND_BLAST to 25.5, CombatSpell.WATER_BLAST to 28.5, CombatSpell.EARTH_BLAST to 31.5, CombatSpell.FIRE_BLAST to 34.5,
            CombatSpell.IBAN_BLAST to 30.0, CombatSpell.MAGIC_DART to 30.0,
            CombatSpell.SARADOMIN_STRIKE to 35.0, CombatSpell.CLAWS_OF_GUTHIX to 35.0, CombatSpell.FLAMES_OF_ZAMORAK to 35.0,
            CombatSpell.WIND_WAVE to 36.0, CombatSpell.WATER_WAVE to 37.5, CombatSpell.EARTH_WAVE to 40.0, CombatSpell.FIRE_WAVE to 42.5,
            CombatSpell.WIND_SURGE to 44.5, CombatSpell.WATER_SURGE to 46.5, CombatSpell.EARTH_SURGE to 48.2, CombatSpell.FIRE_SURGE to 50.5,
            CombatSpell.CONFUSE to 13.0, CombatSpell.WEAKEN to 21.0, CombatSpell.CURSE to 29.0, CombatSpell.BIND to 30.0,
            CombatSpell.SNARE to 60.0, CombatSpell.VULNERABILITY to 76.0, CombatSpell.ENFEEBLE to 83.0, CombatSpell.ENTANGLE to 89.0,
            CombatSpell.STUN to 90.0, CombatSpell.TELEPORT_BLOCK to 80.0,
        )

    @Test
    fun `every standard combat spell gives the OSRS base experience`() {
        xp.forEach { (spell, expected) -> assertEquals(spell.name, expected, spell.experience, 0.0001) }
    }

    @Test
    fun `fixed max hits match OSRS`() {
        mapOf(
            CombatSpell.CRUMBLE_UNDEAD to 15, CombatSpell.IBAN_BLAST to 25, CombatSpell.SARADOMIN_STRIKE to 20,
            CombatSpell.CLAWS_OF_GUTHIX to 20, CombatSpell.FLAMES_OF_ZAMORAK to 20,
        ).forEach { (spell, max) -> assertEquals(spell.name, max, CombatSpell.baseMaxHit(spell, 99)) }
    }

    @Test
    fun `elemental spells hit as hard as the strongest unlocked spell of their tier`() {
        assertEquals(2, CombatSpell.baseMaxHit(CombatSpell.WIND_STRIKE, 1))
        assertEquals(4, CombatSpell.baseMaxHit(CombatSpell.WIND_STRIKE, 5))
        assertEquals(6, CombatSpell.baseMaxHit(CombatSpell.WIND_STRIKE, 12))
        assertEquals(8, CombatSpell.baseMaxHit(CombatSpell.WIND_STRIKE, 13))
        // OSRS Wiki "Water Bolt": 23-28 -> 10, 29-34 -> 11, 35+ -> 12.
        assertEquals(10, CombatSpell.baseMaxHit(CombatSpell.WATER_BOLT, 28))
        assertEquals(11, CombatSpell.baseMaxHit(CombatSpell.WATER_BOLT, 29))
        assertEquals(12, CombatSpell.baseMaxHit(CombatSpell.WATER_BOLT, 35))
        // OSRS Wiki "Earth Surge": 90-94 -> 23, 95+ -> 24.
        assertEquals(23, CombatSpell.baseMaxHit(CombatSpell.EARTH_SURGE, 94))
        assertEquals(24, CombatSpell.baseMaxHit(CombatSpell.EARTH_SURGE, 95))
        CombatSpell.ELEMENTAL_TIERS.forEach { tier ->
            val top = tier.last().first.maxHit
            tier.forEach { (spell, _) -> assertEquals(spell.name, top, CombatSpell.baseMaxHit(spell, 99)) }
        }
    }

    @Test
    fun `magic dart hits 10 plus a tenth of the visible magic level`() {
        assertEquals(15, CombatSpell.baseMaxHit(CombatSpell.MAGIC_DART, 50))
        assertEquals(17, CombatSpell.baseMaxHit(CombatSpell.MAGIC_DART, 75))
        assertEquals(19, CombatSpell.baseMaxHit(CombatSpell.MAGIC_DART, 99))
        assertEquals(20, CombatSpell.baseMaxHit(CombatSpell.MAGIC_DART, 105))
    }

    @Test
    fun `binds freeze for the OSRS tick counts and curses drain the OSRS percentages`() {
        assertEquals(8, (CombatSpell.BIND.effect as SpellEffect.Freeze).ticks)
        assertEquals(16, (CombatSpell.SNARE.effect as SpellEffect.Freeze).ticks)
        assertEquals(24, (CombatSpell.ENTANGLE.effect as SpellEffect.Freeze).ticks)
        mapOf(CombatSpell.CONFUSE to 5, CombatSpell.WEAKEN to 5, CombatSpell.CURSE to 5, CombatSpell.VULNERABILITY to 10, CombatSpell.ENFEEBLE to 10, CombatSpell.STUN to 10)
            .forEach { (spell, pct) -> assertEquals(spell.name, pct, (spell.effect as SpellEffect.StatDrain).percent) }
    }

    /**
     * Owner 2026-09-24: every normal-spellbook combat spell looks like OSRS (batches "standardspells" tx-20260924-173440 and
     * "standardspellseq" tx-20260924-173716; surges and Tele Block were imported earlier). Imported spotanims start at 3066.
     */
    @Test
    fun `every standard combat spell draws imported OSRS graphics`() {
        val imported = 3066
        val wrong =
            xp.keys.filter { spell ->
                val impact = spell.impactGfx?.id ?: -1
                val projectileOk = spell.projectile < 0 || spell.projectile >= imported || spell.projectile == 0
                val castOk = spell.castGfx == null || spell.castGfx!!.id == 0 || spell.castGfx!!.id >= imported
                impact < imported || !projectileOk || !castOk || spell.secondProjectile >= 0
            }
        assertEquals(emptyList<CombatSpell>(), wrong)
        // HUMAN_CASTSTRIKE (15754) / _STAFF (15774) for strike, bolt and blast; HUMAN_CASTWAVE (15755) / _STAFF (15756) for waves.
        CombatSpell.ELEMENTAL_TIERS.dropLast(2).flatten().forEach { (spell, _) -> assertEquals(spell.name, listOf(15754, 15774), spell.castAnimation.take(2)) }
        CombatSpell.ELEMENTAL_TIERS[3].forEach { (spell, _) -> assertEquals(spell.name, listOf(15755, 15756), spell.castAnimation.take(2)) }
        // Every other spell casts an imported OSRS player sequence too (standardspellseq 15754+, surge 15524, Tele Block 15525/15526).
        val osrsCast = xp.keys.filter { spell -> spell.castAnimation.take(2).any { it !in 15524..15526 && it !in 15754..15774 } }
        assertEquals(emptyList<CombatSpell>(), osrsCast)
    }

    @Test
    fun `elemental weakness counts only standard strike to surge spells`() {
        ElementalWeakness.load(Paths.get("../../data/cfg/elemental_weakness.tsv"))
        assertTrue("weakness table loaded", ElementalWeakness.size > 500)
        assertEquals(ElementalWeakness.Weakness(ElementalWeakness.Element.WATER, 40), ElementalWeakness.of("Tz-Kih"))
        assertEquals(ElementalWeakness.Element.AIR, ElementalWeakness.elementOf(CombatSpell.WIND_SURGE))
        assertEquals(ElementalWeakness.Element.FIRE, ElementalWeakness.elementOf(CombatSpell.FIRE_STRIKE))
        assertNull(ElementalWeakness.elementOf(CombatSpell.FLAMES_OF_ZAMORAK))
        assertNull(ElementalWeakness.elementOf(CombatSpell.ICE_BARRAGE))
    }
}
