package gg.rsmod.plugins.content.skills.summoning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 78/78 audit of [FamiliarCapabilityTable].
 *
 * The owner's 2026-09-07 brief is explicit that a fix proved on one exemplar familiar is not a
 * fix: "Do NOT implement a Unicorn-only, Pack-yak-only, Steel-titan-only or other exemplar-only
 * fix and call the subsystem fixed." So every assertion in this class enumerates the whole roster
 * rather than picking a representative, and a failure names the familiar that broke it.
 *
 * These tests check the **capability model**, which is what every Summoning surface now reads.
 * They deliberately do not check that a menu entry appears or that a handler exists - the brief
 * rules both out as evidence. What they check is that the derived answer for each familiar agrees
 * with the sourced table it came from, and that the invariants the surfaces rely on hold for all
 * 78 at once.
 */
class FamiliarCapabilityTests {
    private val roster = SummoningPouchData.values().toList()

    @Test
    fun `the canonical roster is exactly 78 familiars with distinct npc ids`() {
        assertEquals("the canonical 2011 Summoning roster is 78 familiars", 78, roster.size)
        val npcIds = roster.map { it.npc }
        assertEquals("two pouches share one familiar npc id", npcIds.size, npcIds.distinct().size)
        val pouchIds = roster.map { it.pouch }
        assertEquals("two familiars share one pouch item id", pouchIds.size, pouchIds.distinct().size)
    }

    @Test
    fun `every familiar has exactly one capability record`() {
        assertEquals(78, FamiliarCapabilityTable.all.size)
        roster.forEach { pouch ->
            assertNotNull("${pouch.name} has no capability record", FamiliarCapabilityTable.forNpc(pouch.npc))
        }
    }

    @Test
    fun `carrying agrees with the beast-of-burden ledger for all 78`() {
        roster.forEach { pouch ->
            val capabilities = FamiliarCapabilityTable.forNpc(pouch.npc)!!
            val storage = BeastOfBurden.storageFor(pouch)
            assertEquals(
                "${pouch.name}: capability record disagrees with BeastOfBurden about carrying",
                storage != null,
                capabilities.carries,
            )
            if (storage == null) {
                assertFalse("${pouch.name} is not a carrier but claims to be a BoB", capabilities.isBeastOfBurden)
                assertFalse("${pouch.name} is not a carrier but claims to be a forager", capabilities.isForager)
            } else {
                assertEquals(
                    "${pouch.name}: beast of burden and forager are the two carrying contracts and " +
                        "a familiar is exactly one of them",
                    1,
                    listOf(capabilities.isBeastOfBurden, capabilities.isForager).count { it },
                )
                assertEquals(
                    "${pouch.name}: forager means withdraw-only",
                    storage.withdrawOnly,
                    capabilities.isForager,
                )
            }
        }
    }

    @Test
    fun `no non-carrier is ever offered Take BoB`() {
        val offenders =
            FamiliarCapabilityTable.all
                .filter { !it.carries && it.supports(FamiliarAction.TAKE_BOB) }
                .map { it.pouch.name }
        assertEquals("familiars that cannot carry but are offered Take BoB: $offenders", emptyList<String>(), offenders)
    }

    /**
     * Requirement H8, called out by name because the owner reported it specifically. It is a
     * consequence of the rule above rather than a special case, and is asserted separately so a
     * regression names the familiar the owner will look for first.
     */
    @Test
    fun `steel titan never exposes Take BoB`() {
        val steelTitan = FamiliarCapabilityTable.forNpc(SummoningPouchData.STEEL_TITAN.npc)!!
        assertFalse("Steel titan is a combat familiar, not a carrier", steelTitan.carries)
        assertFalse("Steel titan must never expose Take BoB", steelTitan.supports(FamiliarAction.TAKE_BOB))
    }

    @Test
    fun `attacking agrees with the sourced combat definitions for all 78`() {
        roster.forEach { pouch ->
            val capabilities = FamiliarCapabilityTable.forNpc(pouch.npc)!!
            val executable = SummoningCombatDefinitions.getByNpc(pouch.npc)?.isExecutable == true
            assertEquals(
                "${pouch.name}: capability record disagrees with SummoningCombatDefinitions",
                executable,
                capabilities.canFight,
            )
            assertEquals(
                "${pouch.name}: Attack is offered only when the familiar is commandable",
                capabilities.canReceiveAttackCommand,
                capabilities.supports(FamiliarAction.ATTACK),
            )
        }
    }

    @Test
    fun `every familiar can be called, dismissed and renewed`() {
        FamiliarCapabilityTable.all.forEach { capabilities ->
            listOf(FamiliarAction.CALL, FamiliarAction.DISMISS, FamiliarAction.RENEW).forEach { action ->
                assertTrue(
                    "${capabilities.pouch.name} must support $action - every familiar has a lifetime, " +
                        "a recall and a dismissal",
                    capabilities.supports(action),
                )
            }
        }
    }

    @Test
    fun `a special move is offered exactly when one is bound, and always has a target mode`() {
        roster.forEach { pouch ->
            val capabilities = FamiliarCapabilityTable.forNpc(pouch.npc)!!
            val bound =
                SummoningSpecialMoves.bindings.firstOrNull { binding ->
                    binding.scrolls.any { pouch.npc in it.familiars }
                }
            assertEquals(
                "${pouch.name}: Special Move is offered exactly when SummoningSpecialMoves binds one",
                bound != null,
                capabilities.supports(FamiliarAction.SPECIAL_MOVE),
            )
            if (bound != null) {
                assertNotNull(
                    "${pouch.name}: a bound special move must declare how it is targeted",
                    capabilities.specialTarget,
                )
                assertTrue(
                    "${pouch.name}: a bound special move must name the scroll that powers it",
                    bound.scroll.scroll > 0,
                )
            }
        }
    }

    /**
     * Every familiar must end up with a usable orb. With the six-action set, the smallest possible
     * offering is Call/Dismiss/Renew, so an empty or under-filled action set means the derivation
     * broke rather than that a familiar is genuinely featureless.
     */
    @Test
    fun `every familiar is offered at least the three unconditional actions`() {
        FamiliarCapabilityTable.all.forEach { capabilities ->
            assertTrue(
                "${capabilities.pouch.name} was derived with only ${capabilities.actions}",
                capabilities.actions.size >= 3,
            )
            assertTrue(
                "${capabilities.pouch.name} offers an action outside the six the orb allows",
                FamiliarAction.ORDERED.containsAll(capabilities.actions),
            )
        }
    }

    @Test
    fun `the capability model is the only source of truth the surfaces read`() {
        // SummoningUi's two public predicates must agree with the table for every familiar, so a
        // surface cannot answer the question differently by calling the older helper.
        roster.forEach { pouch ->
            val capabilities = FamiliarCapabilityTable.forNpc(pouch.npc)!!
            assertEquals("${pouch.name}: SummoningUi.carries disagrees", capabilities.carries, SummoningUi.carries(pouch.npc))
            assertEquals(
                "${pouch.name}: SummoningUi.canFight disagrees",
                capabilities.canFight,
                SummoningUi.canFight(pouch.npc),
            )
        }
    }
}
