package gg.rsmod.plugins.content.skills.fletching.javelins

import gg.rsmod.plugins.api.cfg.Items
import kotlin.test.Test
import kotlin.test.assertEquals

/** OSRS-IMPORT javelins (OSRS Wiki "<metal> javelin" item pages, fetched 2026-09-16, one page per tier). */
class JavelinDataTests {
    @Test
    fun `every tier matches its independently sourced level and experience`() {
        val expected =
            mapOf(
                JavelinData.BRONZE to (3 to 1.0),
                JavelinData.IRON to (17 to 2.0),
                JavelinData.STEEL to (32 to 5.0),
                JavelinData.MITHRIL to (47 to 8.0),
                JavelinData.ADAMANT to (62 to 10.0),
                JavelinData.RUNE to (77 to 12.5),
                JavelinData.AMETHYST to (84 to 13.5),
                JavelinData.DRAGON to (92 to 15.0),
            )
        expected.forEach { (tier, values) ->
            val (level, xp) = values
            assertEquals(Items.JAVELIN_SHAFT, tier.shaft, "${tier.name} shaft")
            assertEquals(level, tier.levelRequirement, "${tier.name} level")
            assertEquals(xp, tier.experience, "${tier.name} experience")
        }
    }

    @Test
    fun `every tier has a distinct tip and product, and byProduct has no dupes`() {
        assertEquals(JavelinData.values.size, JavelinData.byProduct.size, "two tiers share a product id")
        val tips = JavelinData.values.map { it.tip }
        assertEquals(tips.size, tips.toSet().size, "two tiers share the same tip item")
    }
}
