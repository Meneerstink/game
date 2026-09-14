package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.plugins.api.cfg.Items
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** OSRS-IMPORT Snapshot (Magic shortbow and Magic shortbow (i)) against the OSRS Wiki. */
class MagicShortbowSnapshotTests {
    @Test
    fun `energy, accuracy and the Snapshot max hit follow the wiki`() {
        assertEquals(55, MagicShortbowSnapshot.energy(Items.MAGIC_SHORTBOW))
        assertEquals(50, MagicShortbowSnapshot.energy(Items.MAGIC_SHORTBOW_I))
        assertNull(MagicShortbowSnapshot.energy(Items.MAGIC_LONGBOW))
        assertEquals(10.0 / 7.0, MagicShortbowSnapshot.ACCURACY)
        // ⌊0.5 + (99 + 10) × (49 + 64) / 640⌋ = ⌊19.745⌋ = 19 with rune arrows (+49).
        assertEquals(19.0, MagicShortbowSnapshot.maxHit(99, 49))
        // ⌊0.5 + (1 + 10) × (7 + 64) / 640⌋ = ⌊1.72⌋ = 1 with bronze arrows (+7).
        assertEquals(1.0, MagicShortbowSnapshot.maxHit(1, 7))
    }

    @Test
    fun `both bows use the shared Snapshot with the custom max hit and the imbued bow fires the same arrows`() {
        val specials = File("src/main/kotlin/gg/rsmod/plugins/content/combat/specialattack/weapons/ranged_specials.plugin.kts").readText()
        assertTrue("listOf(Items.MAGIC_SHORTBOW, Items.MAGIC_SHORTBOW_I).forEach" in specials)
        assertTrue("maxHitOverride = snapshotMax" in specials)
        assertFalse("accuracy = 0.9" in specials, "the old 667 ×0.9 Snapshot is gone")
        val bows = gg.rsmod.plugins.content.combat.strategy.ranged.weapon.BowType.values
        val msb = bows.first { it.item == Items.MAGIC_SHORTBOW }.ammo.toSet()
        assertEquals(msb, bows.first { it.item == Items.MAGIC_SHORTBOW_I }.ammo.toSet())
    }
}
