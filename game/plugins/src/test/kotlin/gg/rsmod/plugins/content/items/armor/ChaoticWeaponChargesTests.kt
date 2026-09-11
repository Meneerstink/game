package gg.rsmod.plugins.content.items.armor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Whole-set checks over the chaotic weapon/shield roster: distinct real ids, sourced max charge. */
class ChaoticWeaponChargesTests {
    @Test
    fun everyChaoticItemHasDistinctChargedAndBrokenIds() {
        assertEquals(6, ChaoticWeapon.values().size)
        val seen = mutableSetOf<Int>()
        for (weapon in ChaoticWeapon.values()) {
            assertNotEquals("${weapon.name} charged/broken id must differ", weapon.chargedId, weapon.brokenId)
            assertTrue("${weapon.name} charged id ${weapon.chargedId} reused", seen.add(weapon.chargedId))
            assertTrue("${weapon.name} broken id ${weapon.brokenId} reused", seen.add(weapon.brokenId))
        }
    }

    @Test
    fun maxChargesMatchesVoidsProductionTomlValue() {
        // Sourced from Void's own data/skill/dungeoneering/dungeoneering.items.toml: every
        // chaotic item declares `charges = 30000` - not invented.
        assertEquals(30_000, ChaoticWeaponCharges.MAX_CHARGES)
    }
}
