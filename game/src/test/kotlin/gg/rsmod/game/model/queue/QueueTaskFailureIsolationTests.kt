package gg.rsmod.game.model.queue

import gg.rsmod.game.model.queue.impl.PawnQueueTaskSet
import gg.rsmod.game.model.queue.impl.WorldQueueTaskSet
import kotlinx.coroutines.Dispatchers
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QueueTaskFailureIsolationTests {
    @Test
    fun `a failing pawn condition is removed and later persistent work continues`() {
        val set = PawnQueueTaskSet()
        var released = 0
        var followUpRuns = 0
        set.releaseLock = { released++ }

        set.queue(Any(), Dispatchers.Unconfined, TaskPriority.STANDARD, block = {
            followUpRuns++
            wait(5)
        }, persistent = true)
        set.queue(Any(), Dispatchers.Unconfined, TaskPriority.STANDARD, block = {
            wait { error("stale pawn condition") }
        }, lock = true)

        set.cycle()
        set.cycle()

        assertEquals(1, set.size)
        assertEquals(1, released)
        assertEquals(1, followUpRuns)
    }

    @Test
    fun `a failing world condition is removed and later world work continues`() {
        val set = WorldQueueTaskSet()
        var followUpRuns = 0

        set.queue(Any(), Dispatchers.Unconfined, TaskPriority.STANDARD, block = {
            followUpRuns++
            wait(5)
        })
        set.queue(Any(), Dispatchers.Unconfined, TaskPriority.STANDARD, block = {
            wait { error("stale world condition") }
        })

        set.cycle()
        set.cycle()

        assertEquals(1, set.size)
        assertEquals(1, followUpRuns)
    }

    @Test
    fun `a world task that queues another world task keeps running and the new task starts next cycle`() {
        // 2026-09-19 live log: a Deadman breach (a world task) queued a projectile task every spawn; the queue's live
        // iterator threw ConcurrentModificationException and the breach task itself was failed and removed.
        val set = WorldQueueTaskSet()
        var parentTicks = 0
        var childRuns = 0
        set.queue(Any(), Dispatchers.Unconfined, TaskPriority.STANDARD, block = {
            repeat(3) {
                parentTicks++
                set.queue(Any(), Dispatchers.Unconfined, TaskPriority.STANDARD, block = { childRuns++ })
                wait(1)
            }
        })

        repeat(5) { set.cycle() }

        assertEquals(3, parentTicks, "the parent task ran all three iterations")
        assertEquals(3, childRuns, "every queued child ran")
        assertEquals(0, set.size)
    }

    @Test
    fun `a condition throwing an Error fails only that task`() {
        // Audit T-01: a StackOverflowError or TODO() in a condition used to escape the Exception-only net.
        val set = PawnQueueTaskSet()
        var survivorRuns = 0
        set.queue(Any(), Dispatchers.Unconfined, TaskPriority.STANDARD, block = {
            while (true) {
                survivorRuns++
                wait(1)
            }
        }, persistent = true)
        set.queue(Any(), Dispatchers.Unconfined, TaskPriority.STANDARD, block = {
            wait { throw StackOverflowError("runaway recursion in a condition") }
        })
        set.queue(Any(), Dispatchers.Unconfined, TaskPriority.STANDARD, block = {
            wait { throw NotImplementedError("TODO() in a condition") }
        })

        set.cycle()
        set.cycle()

        assertEquals(1, set.size)
        assertTrue(survivorRuns >= 2, "the surviving task keeps running")
    }

    @Test
    fun `waitTile reads the pawn's current tile instead of a captured Tile object`() {
        // Audit T-09: movement replaces Pawn.tile with a new object, so a condition holding the old
        // object never became true and the task never resumed (monkey bars shortcut).
        val source = File("src/main/kotlin/gg/rsmod/game/model/queue/QueueTask.kt").readText()
        val waitTile = source.substringAfter("suspend fun waitTile(tile: Tile)").substringBefore("\n        }")
        assertTrue("PredicateCondition { pawn.tile.sameAs(tile) }" in waitTile, waitTile)
        assertFalse("TileCondition(" in waitTile)
    }
}
