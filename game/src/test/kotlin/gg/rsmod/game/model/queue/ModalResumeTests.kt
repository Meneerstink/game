package gg.rsmod.game.model.queue

import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.impl.PawnQueueTaskSet
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner 2026-10-07 (foto 164030): a quest dialogue (a standard task) opened the Dangerous-area warning (main-screen modal
 * 382) and waited for "Enter"; standard tasks are paused while a modal is open, so the click was only read after the
 * player closed the window and the teleport started late. The RS engine resumes the suspended script on its
 * resume-button packet, so a standard task runs once its own answer arrived - and still waits without one.
 */
class ModalResumeTests {
    private fun player(modalOpen: () -> Boolean): Player {
        val world = mockk<World>(relaxed = true)
        every { world.plugins.isMenuOpened(any()) } answers { modalOpen() }
        val player = mockk<Player>(relaxed = true)
        every { player.world } returns world
        return player
    }

    @Test
    fun `a standard task waiting on its modal resumes on the click, not on the close`() {
        var modal = false
        val p = player { modal }
        val set = PawnQueueTaskSet()
        var answer: Any? = null
        var waiting: QueueTask? = null
        set.queue(p, Dispatchers.Unconfined, TaskPriority.STANDARD, {
            waiting = this
            modal = true // the task opens the warning
            waitReturnValue()
            answer = requestReturnValue
        })
        set.cycle()
        assertTrue(waiting!!.suspended())
        repeat(5) { set.cycle() }
        assertEquals(null, answer, "no click yet: the task keeps waiting")
        set.submitReturnValue("enter")
        set.cycle()
        assertEquals("enter", answer, "the click is read while the modal is still open")
    }

    @Test
    fun `a standard task without an answer still waits while a modal is open`() {
        val p = player { true }
        val set = PawnQueueTaskSet()
        var ran = false
        set.queue(p, Dispatchers.Unconfined, TaskPriority.STANDARD, { ran = true })
        repeat(3) { set.cycle() }
        assertFalse(ran, "a standard task never starts under an open modal")
    }
}
