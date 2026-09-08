package gg.rsmod.plugins.content.mechanics.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the quick-prayer data model to the real revision-667 cache.
 *
 * Quick-prayer *selection* is not a bitmask the server invents: the client reads it back out of one
 * varbit per prayer. Client script 2297 - the predicate the "Select"/"Deselect" grid builder (1388)
 * calls for every slot - maps the prayer's struct id through a switch straight onto a varbit, and
 * 1388 registers its redraw hook on varps 1397 (normal book) and 1587 (curses), which are the varps
 * those varbits live in.
 *
 * The 30 normal-book varbits below were decoded from the production cache with
 * `./gradlew :game:runInterfaceHookProbeTool --args="<cache> script 2297"` - they are the values of
 * that script's second switch table, in ascending struct-id order. If [Prayer.qpVarbit] ever drifts
 * from this set, the checkbox grid silently stops reflecting the server's state, which is exactly
 * the failure mode that made quick-prayer selection unusable before.
 */
class QuickPrayerVarbitTests {
    /** Client script 2297, switch table for `varbit 6840 != 1` (the 30-entry normal prayer book). */
    private val cs2QuickPrayerVarbits =
        setOf(
            5971, 5972, 5973, 5974, 5975, 5976, 5977, 5978, 5979, 5980,
            5981, 5982, 5983, 5984, 5985, 5986, 5987, 5988, 5989, 5990,
            5991, 5992, 5993, 5994, 5995, 5996, 5997, 7382, 7770, 7771,
        )

    @Test
    fun `every prayer maps to a distinct quick-prayer varbit from the client's own switch table`() {
        val mapped = Prayer.values.map { it.qpVarbit }
        assertEquals("duplicate qpVarbit", mapped.size, mapped.toSet().size)
        assertEquals(cs2QuickPrayerVarbits, mapped.toSet())
    }

    @Test
    fun `the prayer book has exactly the thirty slots the client builds`() {
        // Client script 1388 loops slot 0 until 30 when varbit 6840 != 1 (normal book) and 0 until
        // 20 when it is 1 (curses); Prayers.unlockPrayerBookButtons derives its event range from
        // this same count, so an extra or missing prayer would leave a slot permanently unclickable.
        assertEquals(30, Prayer.values.size)
        assertEquals((0 until 30).toSet(), Prayer.values.map { it.slot }.toSet())
    }

    @Test
    fun `selection varbits are never the activation varbits`() {
        // varp 1395 holds "this prayer is on right now"; varp 1397 holds "this prayer is part of my
        // quick set". Collapsing the two - which the old varc-181 bitmask effectively did - makes
        // toggling quick prayers rewrite the selection and vice versa.
        val active = Prayer.values.map { it.varbit }.toSet()
        Prayer.values.forEach { prayer ->
            assertNotEquals(prayer.named, prayer.varbit, prayer.qpVarbit)
        }
        assertTrue(active.intersect(cs2QuickPrayerVarbits).isEmpty())
    }
}
