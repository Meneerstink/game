package gg.rsmod.plugins.content.items

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.rsmod.plugins.api.cfg.Items
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * OSRS-IMPORT audit round 2026-09-17b, native "Temple Knight" armour lineage: Initiate (sallet/hauberk/cuisse,
 * Recruitment Drive reward, ids 5574-5576) and Proselyte (sallet/hauberk/cuisse/tasset, The Slug Menace reward,
 * ids 9672/9674/9676/9678).
 *
 * SOURCE: OSRS Wiki raw wikitext for each of the 7 pieces (fetched 2026-09-17b). Every combat bonus already
 * matched `items.yml` exactly for both sets. FOUND AND FIXED: Initiate's wear requirement was missing the wiki's
 * "10 Prayer" (only "20 Defence" was present) on all 3 pieces, while its sibling Proselyte set already correctly
 * carries BOTH skills for all 4 of its pieces in this same cache - strong evidence the Initiate omission is a
 * genuine gap, not a deliberate 667 difference. Added as a sourced `ItemSkillRequirementsCacheAuditTests`
 * override (the 2011 cache param only encodes Defence for these 3 ids) rather than guessed. Proselyte needed no
 * change - already correct.
 */
class InitiateProselyteAuditTests {
    private val yml by lazy { ObjectMapper(YAMLFactory()).readTree(Paths.get("..", "..", "data", "cfg", "items.yml").toFile()).toList() }

    private fun equipment(id: Int) = yml.first { it.path("id").asInt() == id }.path("equipment")

    private fun bonuses(id: Int): List<Int> {
        val eq = equipment(id)
        return listOf(
            eq.path("attack_magic").asInt(), eq.path("attack_ranged").asInt(),
            eq.path("defence_stab").asInt(), eq.path("defence_slash").asInt(), eq.path("defence_crush").asInt(),
            eq.path("defence_magic").asInt(), eq.path("defence_ranged").asInt(), eq.path("prayer").asInt(),
        )
    }

    private fun reqs(id: Int) = equipment(id).path("skill_reqs").associate { it.path("skill").asInt() to it.path("level").asInt() }

    @Test
    fun `Initiate armour matches the wiki bonuses and now requires both 20 Defence and 10 Prayer`() {
        // (amagic, arange, dstab, dslash, dcrush, dmagic, drange, prayer)
        assertEquals(listOf(-6, -3, 13, 14, 11, -1, 13, 3), bonuses(Items.INITIATE_SALLET), "Initiate sallet")
        assertEquals(listOf(-30, -15, 46, 44, 38, -6, 44, 6), bonuses(Items.INITIATE_HAUBERK), "Initiate hauberk")
        assertEquals(listOf(-21, -11, 24, 22, 20, -4, 22, 5), bonuses(Items.INITIATE_CUISSE), "Initiate cuisse")
        val expectedReqs = mapOf(1 to 20, 5 to 10)
        assertEquals(expectedReqs, reqs(Items.INITIATE_SALLET), "Initiate sallet requirement")
        assertEquals(expectedReqs, reqs(Items.INITIATE_HAUBERK), "Initiate hauberk requirement")
        assertEquals(expectedReqs, reqs(Items.INITIATE_CUISSE), "Initiate cuisse requirement")
    }

    @Test
    fun `Proselyte armour matches the wiki bonuses and its 30 Defence 20 Prayer requirement, unchanged`() {
        // (amagic, arange, dstab, dslash, dcrush, dmagic, drange, prayer)
        assertEquals(listOf(-6, -3, 19, 21, 16, -1, 19, 4), bonuses(Items.PROSELYTE_SALLET), "Proselyte sallet")
        assertEquals(listOf(-30, -15, 65, 63, 55, -6, 63, 8), bonuses(Items.PROSELYTE_HAUBERK), "Proselyte hauberk")
        assertEquals(listOf(-21, -11, 33, 31, 29, -4, 31, 6), bonuses(Items.PROSELYTE_CUISSE), "Proselyte cuisse")
        assertEquals(listOf(-21, -11, 33, 31, 29, -4, 31, 6), bonuses(Items.PROSELYTE_TASSET), "Proselyte tasset")
        val expectedReqs = mapOf(1 to 30, 5 to 20)
        assertEquals(expectedReqs, reqs(Items.PROSELYTE_SALLET), "Proselyte sallet requirement")
        assertEquals(expectedReqs, reqs(Items.PROSELYTE_HAUBERK), "Proselyte hauberk requirement")
        assertEquals(expectedReqs, reqs(Items.PROSELYTE_CUISSE), "Proselyte cuisse requirement")
        assertEquals(expectedReqs, reqs(Items.PROSELYTE_TASSET), "Proselyte tasset requirement")
    }
}
