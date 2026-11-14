package gg.rsmod.plugins.content.items

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Regression for the removed elite tree: missing source outcomes must no longer roll nothing. */
class OsrsEliteCasketTests {
    @Test
    fun `current elite rewards contain every formerly blocked slot and the opening route uses them`() {
        val catalogue = gg.rsmod.plugins.content.scrolls.ClueRewardCatalogue.load(File("../../data/cfg/clue/rewards.json"))
        val tier = gg.rsmod.plugins.content.scrolls.ClueScrollTier.ELITE
        val outcomes = catalogue.outcomes.getValue(tier)
        assertTrue(outcomes.all { it.rows.none { row -> row.shopOnly } })
        assertEquals(1.0, outcomes.sumOf { it.probability }, 1e-12)
        assertTrue(outcomes.flatMap { it.rows }.any { it.name == "Charge dragonstone jewellery scroll" })
        assertTrue(outcomes.flatMap { it.rows }.any { it.name == "Master scroll book (empty)" })
        assertEquals(4..6, tier.rewardRolls)
        assertTrue(gg.rsmod.plugins.content.scrolls.ClueItems.local(20543) == 19039)
        val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/scrolls/clue_caskets.plugin.kts").readText()
        assertTrue("ClueCasketRewards.planForPlayer" in plugin && "ClueCasketRewards.award" in plugin)
        assertTrue("EliteCasketTable" !in File("src/main/kotlin/gg/rsmod/plugins/content/items/casket.plugin.kts").readText())
    }
}
