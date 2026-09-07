package gg.rsmod.plugins.content.skills.summoning

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins [LeftClickAction] to the real revision-667 cache dispatch table.
 *
 * 2026-09-06 correction. The previous version of this test pinned the table one value out at the
 * Follower Details end, because the first case of client script 2671 was read as "value 1" when it
 * is really "value 0". Re-decoded from
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
 *
 * If [LeftClickAction] ever drifts from either table, the orb's left-click configuration silently
 * stops matching what the client's own redraw scripts do.
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

    /** varbit 6455 value -> the 880 row graphic script 2674 highlights for it. */
    private val cs2PreviewRows =
        mapOf(
            0 to 7,
            1 to 9,
            2 to 11,
            3 to 13,
            4 to 15,
            5 to 17,
            6 to 19,
            7 to 25,
        )

    private val expectedLabels =
        mapOf(
            0 to "Follower Details",
            1 to "Special move",
            2 to "Attack",
            3 to "Call follower",
            4 to "Dismiss follower",
            5 to "Take BoB",
            6 to "Renew familiar",
            7 to "Interact",
        )

    @Test
    fun `follower details is varbit value zero, not one`() {
        assertEquals(0, LeftClickAction.FOLLOWER_DETAILS.varbitValue)
    }

    @Test
    fun `special move is varbit value one and has no dispatch case in script 2671`() {
        assertEquals(1, LeftClickAction.SPECIAL_MOVE.varbitValue)
        assertEquals(null, cs2Dispatch[1])
    }

    @Test
    fun `every action script 2671 dispatches resolves to the action that names that component`() {
        cs2Dispatch.forEach { (value, _) ->
            val action = LeftClickAction.byVarbitValue(value)
            assertEquals("no LeftClickAction for varbit value $value", expectedLabels[value], action?.label)
        }
    }

    @Test
    fun `each action selects the 880 row script 2674 highlights for its value`() {
        LeftClickAction.ORDERED.forEach { action ->
            val value = action.varbitValue
            assertEquals("action ${action.label} has no varbit value", true, value != null)
            assertEquals(
                "action ${action.label} points at the wrong 880 row",
                cs2PreviewRows[value],
                action.selectRow.first,
            )
            // Every row is a (graphic, text) pair of adjacent component ids.
            assertEquals(action.selectRow.first + 1, action.selectRow.second)
        }
    }

    @Test
    fun `all eight values are distinct and cover 0 to 7`() {
        assertEquals(8, LeftClickAction.ORDERED.size)
        assertEquals((0..7).toSet(), LeftClickAction.ORDERED.mapNotNull { it.varbitValue }.toSet())
    }
}
