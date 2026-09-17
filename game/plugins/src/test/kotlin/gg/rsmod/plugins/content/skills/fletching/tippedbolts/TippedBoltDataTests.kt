package gg.rsmod.plugins.content.skills.fletching.tippedbolts

import gg.rsmod.plugins.api.cfg.Items
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * OSRS-IMPORT dragon bolt gem-tipping (OSRS Wiki "<gem> dragon bolts" item pages, fetched 2026-09-16, one page
 * per tier, independently sourced XP - not assumed from the lower runite-shaft tier).
 */
class TippedBoltDataTests {
    @Test
    fun `every dragon bolt gem tier uses Dragon bolts at level 84 with its independently sourced experience`() {
        val expected =
            mapOf(
                TippedBoltData.OPAL_DRAGON to (Items.OPAL_BOLT_TIPS to (Items.OPAL_DRAGON_BOLTS to 1.6)),
                TippedBoltData.JADE_DRAGON to (Items.JADE_BOLT_TIPS to (Items.JADE_DRAGON_BOLTS to 2.4)),
                TippedBoltData.PEARL_DRAGON to (Items.PEARL_BOLT_TIPS to (Items.PEARL_DRAGON_BOLTS to 3.2)),
                TippedBoltData.TOPAZ_DRAGON to (Items.TOPAZ_BOLT_TIPS to (Items.TOPAZ_DRAGON_BOLTS to 3.9)),
                TippedBoltData.SAPPHIRE_DRAGON to (Items.SAPPHIRE_BOLT_TIPS to (Items.SAPPHIRE_DRAGON_BOLTS to 4.7)),
                TippedBoltData.EMERALD_DRAGON to (Items.EMERALD_BOLT_TIPS to (Items.EMERALD_DRAGON_BOLTS to 5.5)),
                TippedBoltData.RUBY_DRAGON to (Items.RUBY_BOLT_TIPS to (Items.RUBY_DRAGON_BOLTS to 6.3)),
                TippedBoltData.DIAMOND_DRAGON to (Items.DIAMOND_BOLT_TIPS to (Items.DIAMOND_DRAGON_BOLTS to 7.0)),
                TippedBoltData.ONYX_DRAGON to (Items.ONYX_BOLT_TIPS to (Items.ONYX_DRAGON_BOLTS to 9.4)),
            )
        expected.forEach { (tier, values) ->
            val (tip, rest) = values
            val (product, xp) = rest
            assertEquals(Items.OSRS_DRAGON_BOLTS, tier.plainBolt, "${tier.name} plainBolt")
            assertEquals(tip, tier.tip, "${tier.name} tip")
            assertEquals(product, tier.product, "${tier.name} product")
            assertEquals(84, tier.levelRequirement, "${tier.name} level")
            assertEquals(xp, tier.experience, "${tier.name} experience")
        }
    }

    @Test
    fun `no dragon bolt tier collides with an existing runite-shaft tier's item-on-item pair, and byProduct has no dupes`() {
        assertEquals(TippedBoltData.values.size, TippedBoltData.byProduct.size, "two tiers share a product id")
        val pairs = TippedBoltData.values.map { it.tip to it.plainBolt }
        assertEquals(pairs.size, pairs.toSet().size, "two tiers share the same (tip, plainBolt) item-on-item binding")
        // Dragonstone dragon bolts: no dedicated "Dragonstone bolt tips" item in this cache - correctly not built.
        assertFalse(TippedBoltData.values.any { it.product == Items.DRAGONSTONE_DRAGON_BOLTS })
    }
}
