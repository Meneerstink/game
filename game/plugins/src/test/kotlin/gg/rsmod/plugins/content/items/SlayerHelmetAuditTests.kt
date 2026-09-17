package gg.rsmod.plugins.content.items

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.rsmod.plugins.api.cfg.Items
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * OSRS-IMPORT audit round 2026-09-17b, native Slayer helmet family (13263/14636/14637/15492/15496/15497,
 * all pre-existing 667 cache ids). `Items.SLAYER_HELMET_E`/`SLAYER_HELMET_CHARGED` (14636/14637) and every
 * `FULL_SLAYER_HELMET*` variant are the imbued lineage, not a separate/unused pair - grep-confirmed they
 * were already listed alongside the plain helmet in `TargetModifiers.SLAYER_HELMETS` for the melee 7/6
 * boost, but every combat formula's own comment incorrectly claimed "no imbued helm exists in this cache"
 * and both 14636/14637 had their magic/ranged bonuses zeroed out and no `skill_reqs` at all (15496/15497
 * were missing `skill_reqs` too, despite already carrying the correct imbued stat block).
 *
 * SOURCE: OSRS Wiki raw wikitext "Slayer helmet", "Slayer helmet (i)", "Full slayer helmet" (2026-09-17):
 * plain helmet astab/aslash/acrush 0/0/0, amagic -6, arange -2, dstab/dslash/dcrush/drange 30/32/27/30,
 * dmagic -1, str 0, Defence 10 to wear; imbuing adds amagic +3 (net delta +9), arange +3 (delta +5),
 * dmagic +10 (delta +11), defence stab/slash/crush/ranged UNCHANGED from the plain helmet. "Full slayer
 * helmet ... is created by using a slayer helmet (i)" - it is always this same imbued tier, there is no
 * unimbued Full helmet. `ItemSkillRequirementsCacheAuditTests` re-run green after adding the `skill_reqs`
 * to all 4 previously-missing ids, confirming no conflict with the real client cache wield-requirement
 * params for these ids.
 */
class SlayerHelmetAuditTests {
    private val yml by lazy { ObjectMapper(YAMLFactory()).readTree(Paths.get("..", "..", "data", "cfg", "items.yml").toFile()).toList() }

    private fun equipment(id: Int) = yml.first { it.path("id").asInt() == id }.path("equipment")

    private fun bonuses(id: Int): List<Int> {
        val eq = equipment(id)
        return listOf(
            eq.path("attack_magic").asInt(), eq.path("attack_ranged").asInt(),
            eq.path("defence_stab").asInt(), eq.path("defence_slash").asInt(), eq.path("defence_crush").asInt(),
            eq.path("defence_magic").asInt(), eq.path("defence_ranged").asInt(), eq.path("melee_strength").asInt(),
        )
    }

    private fun reqs(id: Int) = equipment(id).path("skill_reqs").associate { it.path("skill").asInt() to it.path("level").asInt() }

    @Test
    fun `the plain unimbued Slayer helmet keeps its melee-only negative magic and ranged penalty`() {
        assertEquals(listOf(-6, -2, 30, 32, 27, -1, 30, 0), bonuses(Items.SLAYER_HELMET), "Slayer helmet")
        assertEquals(mapOf(1 to 10), reqs(Items.SLAYER_HELMET), "Slayer helmet requirement")
    }

    @Test
    fun `every imbued Slayer helmet variant carries the sourced imbued bonuses and wear requirement`() {
        val imbuedBonuses = listOf(3, 3, 30, 32, 27, 10, 30, 0)
        val expectedReqs = mapOf(1 to 10)
        listOf(
            Items.SLAYER_HELMET_E,
            Items.SLAYER_HELMET_CHARGED,
            Items.FULL_SLAYER_HELMET,
            Items.FULL_SLAYER_HELMET_E,
            Items.FULL_SLAYER_HELMET_CHARGED,
        ).forEach { id ->
            assertEquals(imbuedBonuses, bonuses(id), "item $id")
            assertEquals(expectedReqs, reqs(id), "item $id requirement")
        }
    }
}
