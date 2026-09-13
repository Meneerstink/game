package gg.rsmod.plugins.api

import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.plugins.api.ext.isProtectedFrom
import gg.rsmod.plugins.api.ext.isProtectedFromSummoning
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whether an overhead prayer/curse icon actually protects, across the **whole** `headicons_prayer`
 * sheet rather than one representative frame.
 *
 * ## The defect this pins
 *
 * Every combat formula used to ask `target.hasPrayerIcon(PrayerIcon.PROTECT_FROM_MELEE)`, which is
 * an exact comparison against a single frame id. Eight frames in this cache block melee. So a
 * player running curse-book Deflect Melee (frame 12), or normal-book Protect from Summoning +
 * Melee (8), or curse-book Deflect Summoning + Melee (16), took full damage from every melee
 * attack in the game while their overhead said otherwise - and the same hole existed for ranged and
 * magic, plus the normal book's own combined Missiles + Magic frame (6), which protected against
 * neither of the two things it draws.
 *
 * These tests are written over the enum itself rather than over three hand-picked examples, so a
 * frame that silently loses (or gains) its protection fails here by name.
 */
class PrayerProtectionIconTests {
    /** Exactly the frames that must block melee, by name, taken from the decoded sprite sheet. */
    private val melee =
        setOf(
            PrayerIcon.PROTECT_FROM_MELEE,
            PrayerIcon.PROTECT_FROM_SUMMONING_AND_MELEE,
            PrayerIcon.DEFLECT_MELEE,
            PrayerIcon.DEFLECT_SUMMONING_AND_MELEE,
        )

    private val ranged =
        setOf(
            PrayerIcon.PROTECT_FROM_MISSILES,
            PrayerIcon.PROTECT_FROM_MISSLES_AND_MAGIC,
            PrayerIcon.PROTECT_FROM_SUMMONING_AND_MISSILES,
            PrayerIcon.DEFLECT_MISSILES,
            PrayerIcon.DEFLECT_SUMMONING_AND_MISSILES,
        )

    private val magic =
        setOf(
            PrayerIcon.PROTECT_FROM_MAGIC,
            PrayerIcon.PROTECT_FROM_MISSLES_AND_MAGIC,
            PrayerIcon.PROTECT_FROM_SUMMONING_AND_MAGIC,
            PrayerIcon.DEFLECT_MAGIC,
            PrayerIcon.DEFLECT_SUMMONING_AND_MAGIC,
        )

    /** Both Summoning protections and every combined frame either one appears in. */
    private val summoning =
        setOf(
            PrayerIcon.PROTECT_FROM_SUMMONING,
            PrayerIcon.PROTECT_FROM_SUMMONING_AND_MELEE,
            PrayerIcon.PROTECT_FROM_SUMMONING_AND_MISSILES,
            PrayerIcon.PROTECT_FROM_SUMMONING_AND_MAGIC,
            PrayerIcon.DEFLECT_SUMMONING,
            PrayerIcon.DEFLECT_SUMMONING_AND_MELEE,
            PrayerIcon.DEFLECT_SUMMONING_AND_MISSILES,
            PrayerIcon.DEFLECT_SUMMONING_AND_MAGIC,
        )

    private fun assertExactly(
        expected: Set<PrayerIcon>,
        style: CombatClass,
    ) {
        val actual = PrayerIcon.values().filter { it.protects.contains(style) }.toSet()
        assertEquals("the set of overheads protecting against $style changed", expected, actual)
        expected.forEach {
            assertTrue(
                "${it.name} (frame ${it.id}) must protect against $style",
                PrayerIcon.protectsAgainst(it.id, style),
            )
        }
    }

    @Test
    fun `exactly the four melee overheads protect against melee`() = assertExactly(melee, CombatClass.MELEE)

    @Test
    fun `exactly the five missile overheads protect against ranged`() = assertExactly(ranged, CombatClass.RANGED)

    @Test
    fun `exactly the five magic overheads protect against magic`() = assertExactly(magic, CombatClass.MAGIC)

    @Test
    fun `exactly the eight summoning overheads protect against familiars`() {
        val actual = PrayerIcon.values().filter { it.protectsSummoning }.toSet()
        assertEquals(summoning, actual)
        summoning.forEach { assertTrue(it.name, PrayerIcon.protectsAgainstSummoning(it.id)) }
    }

    /**
     * The three regressions that motivated the fix, stated as the failure the player actually saw:
     * a curse-book Deflect and both combined-frame families used to protect against nothing.
     */
    @Test
    fun `the deflect and combined frames that used to lose all protection now keep it`() {
        listOf(
            Triple(PrayerIcon.DEFLECT_MELEE, CombatClass.MELEE, "curse-book Deflect Melee"),
            Triple(PrayerIcon.DEFLECT_MISSILES, CombatClass.RANGED, "curse-book Deflect Missiles"),
            Triple(PrayerIcon.DEFLECT_MAGIC, CombatClass.MAGIC, "curse-book Deflect Magic"),
            Triple(PrayerIcon.PROTECT_FROM_SUMMONING_AND_MELEE, CombatClass.MELEE, "Protect Summoning + Melee"),
            Triple(PrayerIcon.PROTECT_FROM_SUMMONING_AND_MISSILES, CombatClass.RANGED, "Protect Summoning + Missiles"),
            Triple(PrayerIcon.PROTECT_FROM_SUMMONING_AND_MAGIC, CombatClass.MAGIC, "Protect Summoning + Magic"),
            Triple(PrayerIcon.DEFLECT_SUMMONING_AND_MELEE, CombatClass.MELEE, "Deflect Summoning + Melee"),
            Triple(PrayerIcon.DEFLECT_SUMMONING_AND_MISSILES, CombatClass.RANGED, "Deflect Summoning + Missiles"),
            Triple(PrayerIcon.DEFLECT_SUMMONING_AND_MAGIC, CombatClass.MAGIC, "Deflect Summoning + Magic"),
            Triple(PrayerIcon.PROTECT_FROM_MISSLES_AND_MAGIC, CombatClass.RANGED, "Protect Missiles + Magic (ranged half)"),
            Triple(PrayerIcon.PROTECT_FROM_MISSLES_AND_MAGIC, CombatClass.MAGIC, "Protect Missiles + Magic (magic half)"),
        ).forEach { (icon, style, label) ->
            assertTrue("$label must block $style", PrayerIcon.protectsAgainst(icon.id, style))
        }
    }

    /** Overheads that are not protections must never be mistaken for one. */
    @Test
    fun `non-protection overheads protect against nothing`() {
        listOf(PrayerIcon.NONE, PrayerIcon.RETRIBUTION, PrayerIcon.SMITE, PrayerIcon.REDEMPTION, PrayerIcon.WRATH, PrayerIcon.SOUL_SPLIT)
            .forEach { icon ->
                CombatClass.values.forEach { style ->
                    assertFalse("${icon.name} must not block $style", PrayerIcon.protectsAgainst(icon.id, style))
                }
                assertFalse(icon.name, PrayerIcon.protectsAgainstSummoning(icon.id))
            }
    }

    /** An unmapped frame (11 is blank, 21..26 are skulls/stars) is not a protection either. */
    @Test
    fun `unmapped frames are not protections`() {
        listOf(11, 21, 22, 23, 24, 25, 26, 99).forEach { id ->
            CombatClass.values.forEach { style -> assertFalse("frame $id", PrayerIcon.protectsAgainst(id, style)) }
            assertFalse("frame $id", PrayerIcon.protectsAgainstSummoning(id))
        }
    }

    /** The `Pawn` extensions the combat formulas actually call read the pawn's live overhead. */
    @Test
    fun `the pawn extensions read the live overhead`() {
        val pawn = mockk<Pawn>(relaxed = true)
        every { pawn.prayerIcon } returns PrayerIcon.DEFLECT_SUMMONING_AND_MELEE.id
        assertTrue(pawn.isProtectedFrom(CombatClass.MELEE))
        assertFalse(pawn.isProtectedFrom(CombatClass.MAGIC))
        assertTrue(pawn.isProtectedFromSummoning())

        every { pawn.prayerIcon } returns PrayerIcon.NONE.id
        assertFalse(pawn.isProtectedFrom(CombatClass.MELEE))
        assertFalse(pawn.isProtectedFromSummoning())
    }
}
