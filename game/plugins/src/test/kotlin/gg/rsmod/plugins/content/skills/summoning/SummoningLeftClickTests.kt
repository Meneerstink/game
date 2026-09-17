package gg.rsmod.plugins.content.skills.summoning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [FamiliarAction]'s left-click half to the real revision-667 cache dispatch table.
 *
 * The cache tables below are **unchanged** by the 2026-09-07 owner requirements and are still the
 * authority for what each varbit value means. What changed is only which of those values the orb
 * *offers*: requirement H6 reduces the orb to six actions, dropping value 0 ("Follower Details",
 * now a sidebar tab - see [FollowerDetailsTab]) and value 7 ("Interact", a duplicate of the
 * familiar's own npc option). So this test still pins all eight cache cases, and additionally
 * pins that exactly the right two are the ones no longer offered.
 *
 * 2026-09-06 correction, still valid. The values were once read one out at the Follower Details
 * end, because the first case of client script 2671 was read as "value 1" when it is really
 * "value 0". Re-decoded from
 * `./gradlew :game:runInterfaceHookProbeTool --args="<cache> disasm 2671"`:
 *
 * ```
 * [0] POP_VARBIT(6454)          ; push varbit 6454
 * [1] BRANCH_IF_FALSE(1)        ; ==0 -> jump to [3]
 * [2] BRANCH(7)                 ; else -> jump to [10]
 * [3] PUSH 0 / PUSH 48955410 / OP_2003    ; IF_SETHIDE(false, 747:18 = op1 'Follower Details')
 * ```
 *
 * i.e. the *zero* case is Follower Details. Every later case is an explicit
 * `PUSH_CONSTANT_INT(n) / BRANCH_EQUALS`, and there is no case for 1 at all - that is Special
 * move, which has no direct component on 747 because the orb's "Spell, Cast" button (747:24/25)
 * is permanently separate.
 *
 * The 880 row -> value mapping is pinned from a second, independent script: `disasm 2674`
 * (`GOSUB`'d by 2672, itself 880:3's `onVarTransmit` off varp 1494) paints the selected sprite on
 * one row per argument value, giving 0 -> 880:7, 1 -> 880:9 ... 7 -> 880:25.
 */
class SummoningLeftClickTests {
    /** varbit 6454 value -> the 747 direct component script 2671's matching case reveals. */
    private val cs2Dispatch =
        mapOf(
            0 to 18,
            2 to 23,
            3 to 19,
            4 to 20,
            5 to 21,
            6 to 22,
            7 to 26,
        )

    /** The cache's own eight labels, in varbit-value order. */
    private val cacheLabels =
        mapOf(
            0 to "Follower Details",
            1 to "Special move",
            2 to "Attack",
            3 to "Call Follower",
            4 to "Dismiss",
            5 to "Take BoB",
            6 to "Renew Familiar",
            7 to "Interact",
        )

    /** Requirement H6: these two cache values are deliberately not orb actions any more. */
    private val removedValues = setOf(2, 7)

    @Test
    fun `the orb offers exactly the six allowed actions`() {
        assertEquals(6, FamiliarAction.ORDERED.size)
        assertEquals(
            setOf(
                FamiliarAction.SPECIAL_MOVE,
                FamiliarAction.FOLLOWER_DETAILS,
                FamiliarAction.CALL,
                FamiliarAction.DISMISS,
                FamiliarAction.TAKE_BOB,
                FamiliarAction.RENEW,
            ),
            FamiliarAction.ORDERED.toSet(),
        )
    }

    @Test
    fun `attack and interact are the two values no longer offered`() {
        removedValues.forEach { value ->
            assertNull(
                "varbit value $value (${cacheLabels[value]}) must not resolve to an orb action",
                FamiliarAction.byLeftClickValue(value),
            )
        }
        assertEquals((0..7).toSet() - removedValues, FamiliarAction.ORDERED.map { it.leftClickValue }.toSet())
    }

    @Test
    fun `special move is varbit value one and has no dispatch case in script 2671`() {
        assertEquals(1, FamiliarAction.SPECIAL_MOVE.leftClickValue)
        assertNull(cs2Dispatch[1])
    }

    @Test
    fun `every offered action keeps the cache's own label for its value`() {
        FamiliarAction.ORDERED.forEach { action ->
            assertEquals(
                "action ${action.name} does not carry the cache label for value ${action.leftClickValue}",
                cacheLabels[action.leftClickValue],
                action.label,
            )
        }
    }

    @Test
    fun `every offered action owns the 747 component script 2671 dispatches to for its value`() {
        FamiliarAction.ORDERED.forEach { action ->
            val dispatched = cs2Dispatch[action.leftClickValue] ?: return@forEach
            assertTrue(
                "action ${action.name} does not own 747:$dispatched, the component script 2671 " +
                    "reveals for varbit value ${action.leftClickValue}",
                dispatched in action.orbComponents,
            )
        }
    }

    @Test
    fun `no Summoning source opens the rejected selector interface any more`() {
        // G4: interface 880 is real cache content but the owner rejected it outright, twice.
        // Its eight rows are baked, two of them ("Follower details", "Interact") are forbidden
        // outright and the rest ignore the current familiar's capabilities, so a server can
        // refuse a click but can never stop it advertising options that do not exist. This
        // fails if any Summoning source reaches for it again.
        val sources = summoningSources()
        assertTrue("no Summoning sources were found to scan", sources.isNotEmpty())
        sources.forEach { file ->
            val offending =
                file.readLines().withIndex().filter { (_, line) ->
                    SELECTOR_INTERFACE_USE.containsMatchIn(line)
                }
            assertTrue(
                "${file.name} still opens or binds interface 880: " +
                    offending.joinToString { "line ${it.index + 1}: ${it.value.trim()}" },
                offending.isEmpty(),
            )
        }
    }

    @Test
    fun `the removed actions' components are still hidden by the orb refresh`() {
        // The components themselves must remain known, because 2671 keeps re-showing them.
        removedValues.forEach { value ->
            val component = cs2Dispatch.getValue(value)
            assertTrue(
                "747:$component (${cacheLabels[value]}) must be in the removed set so the orb hides it",
                component in FamiliarAction.REMOVED_ORB_COMPONENTS,
            )
        }
        // Follower Details op6 twin 747:9 and Interact op6 twin 747:15, alongside their op1 twins.
        assertEquals(setOf(14, 23, 15, 26), FamiliarAction.REMOVED_ORB_COMPONENTS.toSet())
    }

    @Test
    fun `no component is claimed by two actions, and none is both offered and removed`() {
        val offered = FamiliarAction.ORDERED.flatMap { it.orbComponents.toList() }
        assertEquals("two actions claim the same 747 component", offered.size, offered.distinct().size)
        assertTrue(
            "a component is both offered and removed",
            offered.intersect(FamiliarAction.REMOVED_ORB_COMPONENTS.toSet()).isEmpty(),
        )
    }

    /**
     * Every Kotlin source in the Summoning package, for the source-level guards above.
     */
    private fun summoningSources(): List<java.io.File> =
        java.nio.file.Paths
            .get("src", "main", "kotlin", "gg", "rsmod", "plugins", "content", "skills", "summoning")
            .toFile()
            .walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "kts") }
            .toList()

    companion object {
        /**
         * Opening or binding interface 880. Deliberately narrow: the number 880 also appears in
         * prose (the doc comments that record *why* it is not used are worth keeping), so only a
         * real call site matches.
         */
        private val SELECTOR_INTERFACE_USE =
            Regex("""(openInterface|on_button|on_interface_close|setComponent\w+)\s*\(\s*880\b""")
    }
}
