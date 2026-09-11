package gg.rsmod.plugins.content.objs.prayeraltar

import gg.rsmod.plugins.content.skills.prayer.burying.BoneData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Enumerates every bone [BoneData] defines (the same set the pre-existing bone-burying feature
 * uses) rather than asserting on a single exemplar bone, per this project's subsystem-wide
 * evidence rule.
 */
class GildedAltarOfferingTests {
    @Test
    fun `every bone yields three times its bury XP`() {
        BoneData.values.forEach { data ->
            val xp = GildedAltarOffering.xpFor(data.bone)
            assertEquals(
                "${data.name} (item ${data.bone}) should yield 3x its bury XP",
                data.experience * 3.0,
                xp!!,
                0.0001,
            )
        }
    }

    @Test
    fun `a non-bone item is rejected`() {
        assertNull(GildedAltarOffering.xpFor(bone = -1))
    }
}
