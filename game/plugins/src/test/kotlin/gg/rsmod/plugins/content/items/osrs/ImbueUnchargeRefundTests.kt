package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.mechanics.death.PvpDeathBreakables
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** N94 (owner 2026-10-22): uncharging an imbued ring refunds 100% of its imbue price, like OSRS and like an unprotected PvP death. */
class ImbueUnchargeRefundTests {
    @Test
    fun `every imbued ring with an Uncharge has a refund, and Uncharge pays it`() {
        listOf(Items.RING_OF_SUFFERING_I, Items.RING_OF_THE_GODS_I, Items.TYRANNICAL_RING_I, Items.TREASONOUS_RING_I, Items.BERSERKER_RING_I)
            .forEach { assertNotNull(PvpDeathBreakables.imbueRefund(it), "refund for $it") }
        val s = File("src/main/kotlin/gg/rsmod/plugins/content/items/osrs/osrs_census_options.plugin.kts").readText()
        assertTrue("{ p -> refundImbuePoints(p, imbued) }" in s, "the Uncharge handler pays the refund after the ring is replaced")
        assertTrue("PvpDeathBreakables.imbueRefund(imbued)" in s, "the same price as the PvP-death refund")
    }
}
