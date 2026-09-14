package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** OSRS-IMPORT Ring of suffering recoil against the OSRS Wiki page and owner decision (h). */
class RingOfSufferingTests {
    @Test
    fun `40 charges per ring of recoil up to 100000, (r) and (ri) revert at 0`() {
        val plain = Item(Items.RING_OF_SUFFERING)
        assertEquals(2_500, RingOfSuffering.recoilsToAdd(plain, 10_000))
        val charged = RingOfSuffering.withCharges(plain, 40)
        assertEquals(Items.RING_OF_SUFFERING_R, charged.id)
        assertEquals(Items.RING_OF_SUFFERING_RI, RingOfSuffering.withCharges(Item(Items.RING_OF_SUFFERING_I), 40).id)
        assertEquals(Items.RING_OF_SUFFERING_I, RingOfSuffering.withCharges(Item(Items.RING_OF_SUFFERING_RI), 0).id)
        assertEquals(100_000, RingOfSuffering.charges(RingOfSuffering.withCharges(plain, 250_000)))
        assertEquals(Items.RING_OF_SUFFERING, RingOfSuffering.afterRecoil(charged, 40)!!.id)
    }

    @Test
    fun `a switched-off or uncharged ring uses no charges, the toggle stays on the ring`() {
        val charged = RingOfSuffering.withCharges(Item(Items.RING_OF_SUFFERING), 400)
        assertEquals(394, RingOfSuffering.charges(RingOfSuffering.afterRecoil(charged, 6)!!))
        val off = RingOfSuffering.toggled(charged)
        assertNull(RingOfSuffering.afterRecoil(off, 6))
        assertEquals(false, RingOfSuffering.recoilEnabled(RingOfSuffering.withCharges(off, 800)), "toggle survives recharging")
        assertNull(RingOfSuffering.afterRecoil(Item(Items.RING_OF_SUFFERING), 6))
        assertTrue("RingOfSuffering.afterRecoil(ring, reflect)" in File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/combatresponse/RingOfRecoil.kt").readText())
    }
}
