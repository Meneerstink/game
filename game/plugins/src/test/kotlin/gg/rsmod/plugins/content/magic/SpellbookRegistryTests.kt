package gg.rsmod.plugins.content.magic

import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell
import gg.rsmod.plugins.content.combat.strategy.magic.SpellEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whole-set consistency checks for the three spellbooks (standard 192, Ancient Magicks 193,
 * Lunar 430): every combat spell must be backed by cache-decoded spell data on the same
 * interface/component, ids must be unique per book, and every Ancient spell must carry its
 * secondary effect. The failing entry is named so a broken import can't hide behind an exemplar.
 */
class SpellbookRegistryTests {
    private val books = setOf(192, 193, 430)

    @Test
    fun everySpellDataEntryLivesInAKnownBookWithUniqueComponent() {
        val seen = mutableMapOf<Pair<Int, Int>, SpellbookData>()
        for (spell in SpellbookData.values()) {
            assertTrue("${spell.name} is on unknown interface ${spell.interfaceId}", spell.interfaceId in books)
            val key = spell.interfaceId to spell.component
            val clash = seen.put(key, spell)
            assertTrue("${spell.name} shares ${spell.interfaceId}:${spell.component} with ${clash?.name}", clash == null)
        }
    }

    @Test
    fun uniqueIdsDoNotCollideAcrossBooks() {
        val seen = mutableMapOf<Int, SpellbookData>()
        for (spell in SpellbookData.values()) {
            val clash = seen.put(spell.uniqueId, spell)
            assertTrue("${spell.name} shares uniqueId ${spell.uniqueId} with ${clash?.name}", clash == null)
        }
    }

    @Test
    fun everyCombatSpellIsBackedBySpellDataOnTheSameComponent() {
        MagicSpells.loadSpellRequirements()
        for (spell in CombatSpell.values) {
            if (spell.componentId == -1) continue // npc-only spells
            val data = MagicSpells.getMetadata(spell.uniqueId)
            assertNotNull("${spell.name} (uniqueId ${spell.uniqueId}) has no SpellbookData entry", data)
            assertEquals("${spell.name} interface", spell.interfaceId, data!!.interfaceId)
            assertEquals("${spell.name} component", spell.componentId, data.component)
            assertEquals("${spell.name} must be a combat spell so it binds to spell-on-npc/player", SpellType.COMBAT_SPELL_TYPE, data.spellType)
        }
    }

    @Test
    fun autocastIdsAreUniqueAndEffectOnlySpellsNeverAutocast() {
        val seen = mutableMapOf<Int, CombatSpell>()
        for (spell in CombatSpell.values) {
            if (spell.autoCastId == -1) {
                assertTrue("${spell.name} has no autocast id but deals damage", !spell.damaging || spell.componentId == -1)
                continue
            }
            if (spell.componentId == -1) continue
            val clash = seen.put(spell.autoCastId, spell)
            assertTrue("${spell.name} shares autocast id ${spell.autoCastId} with ${clash?.name}", clash == null)
        }
    }

    @Test
    fun everyAncientSpellHasItsFamilyEffectAndTier() {
        val ancients = CombatSpell.values.filter { it.interfaceId == 193 }
        assertEquals("20 Ancient Magicks combat spells expected", 20, ancients.size)
        for (spell in ancients) {
            val effect = spell.effect
            assertNotNull("${spell.name} has no secondary effect", effect)
            val expected =
                when {
                    spell.name.startsWith("ICE") -> SpellEffect.Freeze::class
                    spell.name.startsWith("BLOOD") -> SpellEffect.BloodHeal::class
                    spell.name.startsWith("SMOKE") -> SpellEffect.Poison::class
                    spell.name.startsWith("SHADOW") -> SpellEffect.ShadowDrain::class
                    else -> SpellEffect.Miasmic::class
                }
            assertTrue("${spell.name} effect ${effect!!::class.simpleName} should be ${expected.simpleName}", expected.isInstance(effect))
            val multi = spell.name.endsWith("BURST") || spell.name.endsWith("BARRAGE")
            assertEquals("${spell.name} multi-target flag", multi, spell.multiTarget)
            assertTrue("${spell.name} needs either a projectile or a fixed hit delay", spell.projectile > -1 || spell.fixedHitDelay > -1)
        }
        // Freeze durations: rush 5s, burst 10s, blitz 15s, barrage 20s (8/16/24/32 ticks).
        assertEquals(8, (CombatSpell.ICE_RUSH.effect as SpellEffect.Freeze).ticks)
        assertEquals(16, (CombatSpell.ICE_BURST.effect as SpellEffect.Freeze).ticks)
        assertEquals(24, (CombatSpell.ICE_BLITZ.effect as SpellEffect.Freeze).ticks)
        assertEquals(32, (CombatSpell.ICE_BARRAGE.effect as SpellEffect.Freeze).ticks)
        // Max hits climb monotonically inside each family.
        for (family in listOf("SMOKE", "SHADOW", "BLOOD", "ICE", "MIASMIC")) {
            val tiers = listOf("RUSH", "BURST", "BLITZ", "BARRAGE").map { CombatSpell.valueOf("${family}_$it").maxHit }
            assertEquals("$family max hits must increase rush<burst<blitz<barrage", tiers.sorted(), tiers)
        }
    }

    @Test
    fun cacheDecodedLevelsMatchTheWikiForKeySpells() {
        MagicSpells.loadSpellRequirements()
        val expected =
            mapOf(
                CombatSpell.ICE_BARRAGE to 94,
                CombatSpell.BLOOD_BARRAGE to 92,
                CombatSpell.SMOKE_RUSH to 50,
                CombatSpell.MIASMIC_BARRAGE to 97,
                CombatSpell.BIND to 20,
                CombatSpell.ENTANGLE to 79,
                CombatSpell.TELEPORT_BLOCK to 85,
            )
        expected.forEach { (spell, level) ->
            assertEquals("${spell.name} level", level, MagicSpells.getMetadata(spell.uniqueId)!!.lvl)
        }
        assertEquals(94, SpellbookData.VENGEANCE.level)
        assertEquals(68, SpellbookData.CURE_OTHER.level)
    }
}
