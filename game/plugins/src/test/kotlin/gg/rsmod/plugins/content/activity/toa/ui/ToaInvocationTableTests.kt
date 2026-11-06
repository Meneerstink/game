package gg.rsmod.plugins.content.activity.toa.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** N462: ToA invocation selection rules (OSRS cache 2686 enum 4664; groups/requirements from 10-invocations.txt). */
class ToaInvocationTableTests {
    private val t = ToaInvocationTable

    @Test
    fun `44 standard invocations in enum order, no Leagues entries`() {
        assertEquals(44, t.ENTRIES.size)
        assertEquals((0 until 44).toList(), t.ENTRIES.map { it.order })
        assertFalse(t.ENTRIES.any { it.name.startsWith("Blazing Tombs") })
    }

    @Test
    fun `exclusive groups keep one choice`() {
        var s = t.toggle(emptySet(), "Try Again")
        s = t.toggle(s, "Hardcore Run")
        assertEquals(setOf("Hardcore Run"), s)
        s = t.toggle(s, "Pathseeker")
        s = t.toggle(s, "Pathmaster")
        assertEquals(setOf("Hardcore Run", "Pathmaster"), s)
        s = t.toggle(s, "Walk the Path") // category 9, not exclusive with Path Level
        assertTrue("Walk the Path" in s && "Pathmaster" in s)
    }

    @Test
    fun `requirements need their parent and fall with it`() {
        assertEquals(emptySet(), t.toggle(emptySet(), "Insanity"))
        var s = t.toggle(emptySet(), "Overclocked")
        s = t.toggle(s, "Overclocked 2")
        s = t.toggle(s, "Insanity")
        assertEquals(setOf("Overclocked", "Overclocked 2", "Insanity"), s)
        s = t.toggle(s, "Overclocked") // dropping the root drops the chain
        assertEquals(emptySet(), s)
        s = t.toggle(t.toggle(t.toggle(emptySet(), "Not Just a Head"), "Arterial Spray"), "Blood Thinners")
        assertEquals(setOf("Not Just a Head", "Arterial Spray", "Blood Thinners"), s)
        assertEquals(emptySet(), t.toggle(s, "Not Just a Head"))
    }

    @Test
    fun `raid level and mode borders`() {
        assertEquals(0, t.raidLevel(emptySet()))
        assertEquals(25 + 25 + 40 + 50 + 50, t.raidLevel(setOf("Hardcore Run", "Sprint for It", "No Help Needed", "Walk the Path", "Pathmaster")))
        assertEquals("Entry", t.mode(149)); assertEquals("Normal", t.mode(150)); assertEquals("Normal", t.mode(299)); assertEquals("Expert", t.mode(300))
        assertEquals(600, t.ENTRIES.groupBy { it.category }.entries.sumOf { (cat, list) ->
            if (cat in setOf(3, 4, 5, 6)) list.maxOf { it.raidLevel } else list.sumOf { it.raidLevel }
        })
    }
}
