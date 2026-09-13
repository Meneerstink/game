package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.PrayerIcon
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * Deflect Summoning and Protect from Summoning finally doing something.
 *
 * Both prayers exist for exactly one purpose - familiar damage - and until 2026-09-12 neither had
 * any effect at all in this project: the overhead rendered and every familiar hit landed in full.
 * Deflect Summoning was the curse the owner named as unfinished, and this is the behaviour half of
 * finishing it. The visual half (which sprite frame the overhead draws, including the six combined
 * frames) is pinned separately by `PrayerProtectionIconTests`.
 *
 * The rule is stated over [PrayerIcon] rather than over a chosen example: **every** overhead that
 * carries the Summoning motif blocks familiar damage, and no other overhead does - so a new
 * combined frame, or a Deflect losing its Summoning half, fails here by name instead of drifting.
 */
class FamiliarSummoningProtectionTests {
    private fun playerWithOverhead(icon: PrayerIcon): Player =
        mockk<Player>(relaxed = true).also { every { it.prayerIcon } returns icon.id }

    @Test
    fun `every summoning overhead stops familiar damage and no other overhead does`() {
        PrayerIcon.values().forEach { icon ->
            val blocked = FamiliarCombat.blockedBySummoningProtection(playerWithOverhead(icon))
            if (icon.protectsSummoning) {
                assertTrue("${icon.name} (frame ${icon.id}) must stop familiar damage", blocked)
            } else {
                assertFalse("${icon.name} (frame ${icon.id}) must not stop familiar damage", blocked)
            }
        }
    }

    /**
     * The four overheads the owner's report is actually about, named individually so a failure
     * message says which prayer stopped working rather than only that a set changed.
     */
    @Test
    fun `both books' summoning protections block, alone and combined with a combat overhead`() {
        listOf(
            PrayerIcon.DEFLECT_SUMMONING,
            PrayerIcon.DEFLECT_SUMMONING_AND_MELEE,
            PrayerIcon.DEFLECT_SUMMONING_AND_MISSILES,
            PrayerIcon.DEFLECT_SUMMONING_AND_MAGIC,
            PrayerIcon.PROTECT_FROM_SUMMONING,
            PrayerIcon.PROTECT_FROM_SUMMONING_AND_MELEE,
            PrayerIcon.PROTECT_FROM_SUMMONING_AND_MISSILES,
            PrayerIcon.PROTECT_FROM_SUMMONING_AND_MAGIC,
        ).forEach {
            assertTrue(it.name, FamiliarCombat.blockedBySummoningProtection(playerWithOverhead(it)))
        }
    }

    /**
     * A curse-book Deflect *Melee* is not a Summoning protection. Without this, a player running
     * any Deflect at all would be immune to every familiar in the game.
     */
    @Test
    fun `a combat-only deflect does not block familiar damage`() {
        listOf(PrayerIcon.DEFLECT_MELEE, PrayerIcon.DEFLECT_MISSILES, PrayerIcon.DEFLECT_MAGIC, PrayerIcon.WRATH, PrayerIcon.SOUL_SPLIT)
            .forEach { assertFalse(it.name, FamiliarCombat.blockedBySummoningProtection(playerWithOverhead(it))) }
    }

    /** Npcs have no prayer book, so a familiar attacking an npc is never blocked by this rule. */
    @Test
    fun `an npc target is never protected by a summoning prayer`() {
        val npc = mockk<Npc>(relaxed = true)
        every { npc.prayerIcon } returns PrayerIcon.DEFLECT_SUMMONING.id
        assertFalse(FamiliarCombat.blockedBySummoningProtection(npc))
    }
}
