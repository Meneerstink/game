package gg.rsmod.game.model.queue

import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.impl.PawnQueueTaskSet
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * N165 (team 2026-10-21): Bury with the skill guide open froze the player until logout. `lockingQueue` set the lock at once,
 * the STANDARD task then waited for the modal to close (PawnQueueTaskSet.isPaused), and the locked player could not close it.
 * A locking action now closes the open modal first, as a STRONG one always did.
 */
class LockingQueueModalTests {
    @Test
    fun `a locking action closes the open modal it would otherwise wait on for good`() {
        assertTrue(Pawn.closesModalOnLock(TaskPriority.STANDARD, LockState.DELAY_ACTIONS) { true })
        assertTrue(Pawn.closesModalOnLock(TaskPriority.STANDARD, LockState.FULL) { true })
        assertTrue(Pawn.closesModalOnLock(TaskPriority.STRONG, LockState.NONE) { false })
    }

    @Test
    fun `without a modal, or without a lock, nothing is closed`() {
        assertFalse(Pawn.closesModalOnLock(TaskPriority.STANDARD, LockState.DELAY_ACTIONS) { false })
        assertFalse(Pawn.closesModalOnLock(TaskPriority.STANDARD, LockState.NONE) { true })
    }

    @Test
    fun `the queued bury task only runs once the modal is gone`() {
        var modal = true
        val world = mockk<World>(relaxed = true)
        every { world.plugins.isMenuOpened(any()) } answers { modal }
        val p = mockk<Player>(relaxed = true)
        every { p.world } returns world
        val set = PawnQueueTaskSet()
        var ran = false
        set.queue(p, Dispatchers.Unconfined, TaskPriority.STANDARD, { ran = true })
        repeat(3) { set.cycle() }
        assertFalse(ran, "the guide open: the task waits - so the locking action must close it")
        if (Pawn.closesModalOnLock(TaskPriority.STANDARD, LockState.DELAY_ACTIONS) { modal }) modal = false
        set.cycle()
        assertTrue(ran, "the guide closed by the action: the bone is buried")
    }
}
