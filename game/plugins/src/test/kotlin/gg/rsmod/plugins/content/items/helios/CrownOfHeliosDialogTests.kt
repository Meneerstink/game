package gg.rsmod.plugins.content.items.helios

import gg.rsmod.game.message.Message
import gg.rsmod.game.message.impl.IfOpenSubMessage
import gg.rsmod.game.message.impl.KeyTypedMessage
import gg.rsmod.game.message.impl.ResumePauseButtonMessage
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.TaskPriority
import gg.rsmod.game.model.queue.impl.PawnQueueTaskSet
import gg.rsmod.plugins.api.ext.inputInt
import gg.rsmod.plugins.api.ext.options
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Q-033-a regression: the Crown of Helios chatbox menus are plain `options()` dialogs driven by
 * the player's queue. These tests pin the engine behaviour the owner-reported "stuck in the
 * chatbox" / "Back stays on Please wait" hang depended on: a dialog task that has been terminated
 * (which is exactly what `Pawn.teleport`'s STRONG queue does to the menu that requested it) must
 * never open a new prompt, because nothing can ever resume it.
 */
class CrownOfHeliosDialogTests {
    private class Fixture {
        val player = mockk<Player>(relaxed = true)
        val queues = PawnQueueTaskSet()

        /** Every packet the dialog helpers wrote to the (mock) client, in order. */
        val sent = mutableListOf<Message>()

        init {
            every { player.write(*anyVararg<Message>()) } answers { sent += firstArg<Array<Message>>().toList() }
        }

        fun openedInterfaces(): List<IfOpenSubMessage> = sent.filterIsInstance<IfOpenSubMessage>()

        fun queue(priority: TaskPriority = TaskPriority.STANDARD, block: suspend gg.rsmod.game.model.queue.QueueTask.() -> Unit) {
            queues.queue(player, Dispatchers.Unconfined, priority, { block() })
        }
    }

    @Test
    fun `an options dialog opens interface 224 plus two per option and returns the clicked option`() {
        val f = Fixture()
        var result: Int? = null
        f.queue { result = options("Home", "Boss locations", "Recent / Favorites", "Back", title = "Teleport") }
        f.queues.cycle()
        val opened = f.openedInterfaces()
        assertEquals(1, opened.size)
        assertEquals(listOf(752, 13, 232), listOf(opened[0].parent, opened[0].child, opened[0].component))
        assertNull("dialog must still be waiting for the player", result)

        f.queues.submitReturnValue(ResumePauseButtonMessage(interfaceId = 232, component = 5, button = 5))
        f.queues.cycle()
        assertEquals("Back is the 4th entry (component 5)", 4, result)
        assertEquals(0, f.queues.size)
    }

    @Test
    fun `a terminated dialog task refuses to open a new prompt`() {
        val f = Fixture()
        var afterTeleport: Int? = null
        var afterInput: Int? = null
        f.queue {
            // Simulates Crown "Teleport > Home": Pawn.teleport starts a STRONG queue, which
            // terminates every queued task including this one, then the old flow re-opened a menu.
            f.queues.queue(f.player, Dispatchers.Unconfined, TaskPriority.STRONG, {})
            afterTeleport = options("Home", "Back", title = "Teleport")
            afterInput = inputInt("Enter x")
        }
        f.queues.cycle()
        assertEquals("terminated task gets the interrupted sentinel", -1, afterTeleport)
        assertEquals(-1, afterInput)
        assertEquals("no prompt may be opened on a dead task", emptyList<IfOpenSubMessage>(), f.openedInterfaces())
    }

    @Test
    fun `a number key beyond the shown options is treated as interrupted, not as an option`() {
        val f = Fixture()
        var result: Int? = null
        f.queue { result = options("Recent", "Favorites", "Back") }
        f.queues.cycle()
        f.queues.submitReturnValue(KeyTypedMessage(keycode = 20)) // key "5" on a 3-entry menu
        f.queues.cycle()
        assertEquals(-1, result)

        var second: Int? = null
        f.queue { second = options("Recent", "Favorites", "Back") }
        f.queues.cycle()
        f.queues.submitReturnValue(KeyTypedMessage(keycode = 17)) // key "2"
        f.queues.cycle()
        assertEquals(2, second)
    }

    @Test
    fun `a STRONG re-open replaces a waiting Crown menu instead of stacking a dead task in front of it`() {
        val f = Fixture()
        var first: Int? = null
        var second: Int? = null
        f.queue { first = options("A", "B") }
        f.queues.cycle()
        f.queue(TaskPriority.STRONG) { second = options("C", "D") }
        f.queues.cycle()
        assertNull("terminated waiting flow is dropped and never resumes", first)
        assertEquals(1, f.queues.size)
        f.queues.submitReturnValue(ResumePauseButtonMessage(interfaceId = 228, component = 3, button = 3))
        f.queues.cycle()
        assertEquals(2, second)
        assertEquals(0, f.queues.size)
    }
}
