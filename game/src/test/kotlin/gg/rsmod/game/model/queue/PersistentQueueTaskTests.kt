package gg.rsmod.game.model.queue

import gg.rsmod.game.model.queue.impl.PawnQueueTaskSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * RC-1 (HANDOFF_CURRENT.md, RCV-005 step 1): owner live report "combat stops on any other action".
 * The player combat loop was an ordinary queue task, so every STRONG queue (prayer/curse grid,
 * jewellery/Crown menus, alchemy, ...) and every soft handler interruption (eat, drop, equip,
 * familiar attack command, spell targets) terminated it, and any task holding the head of the
 * queue (prayer lock wait, Soul Split follow-up) froze it. These tests drive the real
 * [PawnQueueTaskSet] with a simulated combat loop and compare against an undisturbed reference.
 */
class PersistentQueueTaskTests {
    private val ctx = Any()

    private fun QueueTaskSet.add(
        priority: TaskPriority = TaskPriority.STANDARD,
        persistent: Boolean = false,
        block: suspend QueueTask.(CoroutineScope) -> Unit,
    ) = queue(ctx, Dispatchers.Unconfined, priority, block, persistent = persistent)

    private class Loop {
        var swings = 0
        var engaged = true
        var task: QueueTask? = null
    }

    private fun QueueTaskSet.startCombat(persistent: Boolean = true): Loop {
        val loop = Loop()
        add(persistent = persistent) {
            loop.task = this
            while (loop.engaged) {
                loop.swings++
                wait(1)
            }
        }
        return loop
    }

    private fun QueueTaskSet.tick(times: Int) = repeat(times) { cycle() }

    /** Swings an undisturbed loop makes over [ticks] after its first cycle. */
    private fun referenceSwings(ticks: Int): Int {
        val set = PawnQueueTaskSet()
        val loop = set.startCombat()
        set.tick(1)
        val before = loop.swings
        set.tick(ticks)
        return loop.swings - before
    }

    @Test
    fun `a STRONG action keeps the persistent combat loop and replaces other actions`() {
        val set = PawnQueueTaskSet()
        val combat = set.startCombat()
        set.tick(1)
        var skillingTask: QueueTask? = null
        set.add { skillingTask = this; wait(10) }
        set.tick(1)

        // prayer / curse grid, jewellery and Crown menus, alchemy: all TaskPriority.STRONG
        var prayerToggled = false
        set.add(TaskPriority.STRONG) { prayerToggled = true }
        val before = combat.swings
        set.tick(4)

        assertTrue(prayerToggled)
        assertTrue(skillingTask!!.terminated, "a STRONG action still replaces a non-persistent action")
        assertFalse(combat.task!!.terminated, "the combat loop must survive a STRONG action")
        assertEquals(referenceSwings(4), combat.swings - before, "combat must keep its full attack cadence")
    }

    @Test
    fun `a soft interruption keeps combat and ends other actions`() {
        val set = PawnQueueTaskSet()
        val combat = set.startCombat()
        set.tick(1)
        var dialog: QueueTask? = null
        set.add { dialog = this; wait(10) }
        set.tick(1)

        // eat / drop / equip / familiar attack command / familiar special / Lunar spell target
        set.terminateTasks(keepPersistent = true)
        val before = combat.swings
        set.tick(3)

        assertTrue(dialog!!.terminated)
        assertEquals(1, set.size)
        assertEquals(referenceSwings(3), combat.swings - before)
    }

    @Test
    fun `a hard interruption still ends combat`() {
        val set = PawnQueueTaskSet()
        val combat = set.startCombat()
        set.tick(2)

        // walking, a new entity interaction, a new attack, stun, death, logout
        set.terminateTasks()
        val before = combat.swings
        set.tick(3)

        assertTrue(combat.task!!.terminated)
        assertEquals(0, set.size)
        assertEquals(before, combat.swings, "no swing after walking away")
    }

    @Test
    fun `combat keeps attacking while a waiting task holds the head of the queue`() {
        val set = PawnQueueTaskSet()
        val combat = set.startCombat()
        set.tick(1)
        val parked = set.startCombat(persistent = false) // ordinary task queued behind the head
        var headDone = false
        set.add { wait(6); headDone = true } // prayer lock wait / Soul Split follow-up
        val before = combat.swings
        set.tick(4)

        assertFalse(headDone)
        assertEquals(referenceSwings(4), combat.swings - before, "combat must not freeze behind the head task")
        assertEquals(0, parked.swings, "only persistent tasks run behind the head")
    }

    @Test
    fun `a combat loop that ends on its own condition is removed`() {
        val set = PawnQueueTaskSet()
        val combat = set.startCombat()
        set.add { wait(10) }
        set.tick(2)

        combat.engaged = false // teleport lock or lost target: cycle() returns false
        set.tick(3)

        assertEquals(1, set.size, "only the unrelated waiting task remains")
        assertFalse(combat.task!!.terminated)
    }
}
