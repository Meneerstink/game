package gg.rsmod.plugins.content.skills

import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Npcs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** K03 N410 part 1: every OSRS Cape of Accomplishment seller sells its cape, and the cape follows the 667 skill ids. */
class SkillcapeSellerTests {
    @Test
    fun `the sellers that had no cape dialogue sell their skill's cape`() {
        val sold = SkillcapeSeller.SELLERS.values.toSet()
        listOf(Skillcapes.RANGED, Skillcapes.AGILITY, Skillcapes.THIEVING, Skillcapes.SLAYER, Skillcapes.RUNECRAFTING, Skillcapes.HUNTER, Skillcapes.SUMMONING)
            .forEach { assertTrue(it in sold, "$it has no seller") }
        assertEquals(Skillcapes.RUNECRAFTING, SkillcapeSeller.SELLERS[Npcs.AUBURY])
        assertEquals(Skillcapes.SLAYER, SkillcapeSeller.SELLERS[Npcs.DURADEL_8466])
        assertEquals(99_000, SkillcapeSeller.PRICE)
    }

    @Test
    fun `the cape enum follows the skill ids`() {
        assertEquals(Skills.ATTACK, SkillcapeSeller.skill(Skillcapes.ATTACK))
        assertEquals(Skills.RANGED, SkillcapeSeller.skill(Skillcapes.RANGED))
        assertEquals(Skills.AGILITY, SkillcapeSeller.skill(Skillcapes.AGILITY))
        assertEquals(Skills.RUNECRAFTING, SkillcapeSeller.skill(Skillcapes.RUNECRAFTING))
        assertEquals(Skills.SUMMONING, SkillcapeSeller.skill(Skillcapes.SUMMONING))
    }

    @Test
    fun `N410 - every one of the 24 skillcapes can be bought from a master, never only from Max`() {
        val sources =
            java.io.File("src/main/kotlin/gg/rsmod/plugins/content").walkTopDown()
                .filter { it.isFile && (it.name.endsWith(".kts") || it.name.endsWith(".kt")) }
                .map { it.readText() }
                .filter { "purchaseSkillcape(" in it || "SkillcapeSeller.sell(" in it }
                .toList()
        val missing =
            Skillcapes.values().filter { cape ->
                cape !in SkillcapeSeller.SELLERS.values && sources.none { "Skillcapes.${cape.name}" in it }
            }
        assertTrue(missing.isEmpty(), "no master sells: $missing")
        assertEquals(Skillcapes.CONSTRUCTION, SkillcapeSeller.SELLERS[Npcs.ESTATE_AGENT])
    }
}
