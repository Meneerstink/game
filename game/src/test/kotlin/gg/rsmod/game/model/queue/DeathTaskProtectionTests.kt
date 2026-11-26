package gg.rsmod.game.model.queue

import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.queue.impl.PawnQueueTaskSet
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-09 CALLISTO-ROAR-ONSTERFELIJK (nachtrun 25-11, 1 op 17 doden): Callisto's roar queued a STRONG locking knockback in the cycle
 * the player died; the STRONG queue terminated the death task, so "Oh dear, you are dead!" and the respawn never came and the
 * player kept every item. The death task now survives every other queue and termination and keeps its lock until it ends itself.
 */
class DeathTaskProtectionTests {
    private class Holder {
        val set = PawnQueueTaskSet()
        var lock = LockState.NONE
            set(value) {
                field = value
                set.onLockChanged(value != LockState.NONE)
            }

        init {
            set.releaseLock = { lock = LockState.NONE }
        }

        fun tick(times: Int = 1) = repeat(times) { set.cycle() }
    }

    @Test
    fun `a STRONG knockback queued in the death cycle does not end the death task or its lock`() {
        val p = Holder()
        var respawned = false
        p.lock = LockState.FULL
        p.set.queue(p, Dispatchers.Unconfined, TaskPriority.STRONG, { wait(6); respawned = true; p.lock = LockState.NONE }, lock = true, death = true)
        // Callisto's roar, same cycle: lockingQueue(STRONG, FULL) that returns at once (player is dead).
        p.lock = LockState.FULL
        p.set.queue(p, Dispatchers.Unconfined, TaskPriority.STRONG, { }, lock = true)
        p.tick()
        assertTrue(p.set.hasDeathTask, "the STRONG knockback terminated the death task")
        assertEquals(LockState.FULL, p.lock, "the knockback task released the dying player's lock")
        p.tick(8)
        assertTrue(respawned, "the death never finished")
        assertEquals(false, p.set.hasDeathTask)
    }

    @Test
    fun `terminateTasks (stun, walking, teleport interrupts) keeps the death task`() {
        val p = Holder()
        var respawned = false
        p.set.queue(p, Dispatchers.Unconfined, TaskPriority.STRONG, { wait(3); respawned = true }, lock = true, death = true)
        p.tick()
        p.set.terminateTasks()
        p.set.terminateTasks(keepPersistent = true)
        assertTrue(p.set.hasDeathTask)
        p.tick(5)
        assertTrue(respawned)
    }
}
