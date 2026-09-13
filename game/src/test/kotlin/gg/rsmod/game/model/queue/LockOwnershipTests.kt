package gg.rsmod.game.model.queue

import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.queue.impl.PawnQueueTaskSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * RCV-012 B11 (owner live: "in combat you cannot drop items, and sometimes items cannot be used or dropped at all").
 * Every inventory use/drop, walk and object click is refused while the player's single lock is not NONE, and the
 * refusal happens before any interruption that could clear it. The lock was released only by `fullInterruption`,
 * `interruptQueues`, a completed `lockingQueue` task or a plugin's trailing `unlock()`: a STRONG action replacing a
 * lock-holding task, or a plugin exception between `lock()` and `unlock()`, left it set for good.
 *
 * Roster: every locked [LockState] x every way a task leaves the queue. The holder mirrors `Pawn.lock`'s setter and
 * the player's `releaseLock` without needing a World.
 */
class LockOwnershipTests {
    private val lockedStates = LockState.values().filter { it != LockState.NONE }

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

        fun add(
            priority: TaskPriority = TaskPriority.STANDARD,
            lock: Boolean = false,
            persistent: Boolean = false,
            block: suspend QueueTask.(CoroutineScope) -> Unit,
        ) = set.queue(this, Dispatchers.Unconfined, priority, block, lock = lock, persistent = persistent)

        /** `Pawn.lockingQueue`: the lock is set before the task is queued with lock = true. */
        fun lockingQueue(
            state: LockState,
            priority: TaskPriority = TaskPriority.STANDARD,
            block: suspend QueueTask.(CoroutineScope) -> Unit,
        ) {
            lock = state
            add(priority, lock = true, block = block)
        }

        fun tick(times: Int = 1) = repeat(times) { set.cycle() }
    }

    private fun roster(check: (LockState) -> Unit) = lockedStates.forEach(check)

    @Test
    fun `a lock set inside a task is released when a STRONG action replaces that task`() =
        roster { state ->
            val p = Holder()
            p.add { p.lock = state; wait(5); p.lock = LockState.NONE } // plugin: lock(); wait(n); unlock()
            p.tick()
            assertEquals(state, p.lock)
            p.add(TaskPriority.STRONG) { } // prayer/curse toggle, jewellery or Crown menu
            p.tick()
            assertEquals(LockState.NONE, p.lock, "$state stayed set after its task was replaced by a STRONG action")
        }

    @Test
    fun `a locking queue replaced by a STRONG action releases its lock`() =
        roster { state ->
            val p = Holder()
            p.lockingQueue(state) { wait(5) }
            p.tick()
            p.add(TaskPriority.STRONG) { wait(2) }
            p.tick()
            assertEquals(state, p.lock, "$state must stay held while the replacing STRONG task still runs")
            p.tick(3)
            assertEquals(LockState.NONE, p.lock, "$state stayed set after the replacing STRONG task ended")
        }

    @Test
    fun `a plugin exception between lock and unlock does not leave the lock set`() =
        roster { state ->
            val p = Holder()
            p.add {
                p.lock = state
                wait(1)
                error("plugin failure before unlock()")
            }
            p.tick(3)
            assertEquals(LockState.NONE, p.lock, "$state stayed set after the locking plugin threw")
        }

    @Test
    fun `a locking queue completing normally unlocks as before`() =
        roster { state ->
            val p = Holder()
            p.lockingQueue(state) { wait(2) }
            p.tick()
            assertEquals(state, p.lock)
            p.tick(3)
            assertEquals(LockState.NONE, p.lock, "$state")
        }

    @Test
    fun `a soft interruption that keeps combat releases the lock of the task it ends`() =
        roster { state ->
            val p = Holder()
            var swings = 0
            p.add(persistent = true) { while (true) { swings++; wait(1) } }
            p.tick()
            p.add { p.lock = state; wait(10) } // forced-movement knockback, door, cutscene
            p.tick()
            p.set.terminateTasks(keepPersistent = true)
            assertEquals(LockState.NONE, p.lock, "$state stayed set after a soft interruption")
            val before = swings
            p.tick(2)
            assertTrue(swings > before, "combat must keep running")
        }

    @Test
    fun `a hard interruption releases every owned lock`() =
        roster { state ->
            val p = Holder()
            p.lockingQueue(state) { wait(10) }
            p.tick()
            p.set.terminateTasks()
            assertEquals(LockState.NONE, p.lock, "$state")
        }

    @Test
    fun `a teleport started from a locked dialog keeps its lock until the teleport ends`() =
        roster { state ->
            val p = Holder()
            var arrived = false
            p.add {
                // dialog option -> Pawn.teleport: lock set inside the running dialog task, then a STRONG queue
                p.lock = state
                p.add(TaskPriority.STRONG) { wait(3); arrived = true; p.lock = LockState.NONE }
            }
            p.tick() // the dialog task runs, locks and starts the teleport
            var ticks = 1
            while (!arrived && ticks < 10) {
                assertEquals(state, p.lock, "$state was dropped before the teleport ended (tick $ticks)")
                p.tick()
                ticks++
            }
            assertTrue(arrived)
            assertTrue(ticks >= 3, "the teleport must wait its delay")
            assertEquals(LockState.NONE, p.lock)
        }

    @Test
    fun `the lock stays while another owner still runs`() =
        roster { state ->
            val p = Holder()
            p.lockingQueue(state) { wait(1) }
            p.add { wait(1); p.lock = state; wait(4) }
            p.tick(3)
            assertEquals(state, p.lock, "$state released while a second owner still ran")
            p.tick(6)
            assertEquals(LockState.NONE, p.lock)
        }

    @Test
    fun `a lock set outside the player's tasks keeps its explicit unlock`() =
        roster { state ->
            val p = Holder()
            p.lock = state // trade session (both players), an npc script's pull/stun, a message handler
            p.add { wait(1) }
            p.add(TaskPriority.STRONG) { }
            p.tick(4)
            assertEquals(state, p.lock, "$state set outside any player task must not be released by an unrelated task")
        }
}
