package gg.rsmod.plugins.api.ext

import gg.rsmod.game.message.Message
import gg.rsmod.game.message.impl.IfOpenSubMessage
import gg.rsmod.game.message.impl.ResumePauseButtonMessage
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.TaskPriority
import gg.rsmod.game.model.queue.impl.PawnQueueTaskSet
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * N605: the 6..8-option chatboxes (236/238/240) draw only their first line in this client, so a menu with more than five entries is
 * paged OSRS-style: four entries + "More..." per page (interface 234 for a full page), the last page's "More..." returns to the first,
 * and the returned index is the entry's position in the whole list.
 */
class OptionsPagingTests {
    private class Fixture {
        val player = mockk<Player>(relaxed = true)
        // N108Ui.cache (title fonts) needs a real attribute map; a relaxed mock cannot be cast to its HashMap.
        init { io.mockk.every { player.attr } returns gg.rsmod.game.model.attr.AttributeMap() }
        val queues = PawnQueueTaskSet()
        val sent = mutableListOf<Message>()

        init {
            every { player.write(*anyVararg<Message>()) } answers { sent += firstArg<Array<Message>>().toList() }
        }

        fun opened(): List<Int> = sent.filterIsInstance<IfOpenSubMessage>().map { it.component }

        fun queue(block: suspend gg.rsmod.game.model.queue.QueueTask.() -> Unit) {
            queues.queue(player, Dispatchers.Unconfined, TaskPriority.STANDARD, { block() })
        }

        fun click(button: Int) {
            queues.submitReturnValue(ResumePauseButtonMessage(interfaceId = 0, component = 0, button = button + 1))
            queues.cycle()
        }
    }

    private val seven = arrayOf("A", "B", "C", "D", "E", "F", "G")

    @Test
    fun `seven options page as four plus More and return the entry's position in the full list`() {
        val f = Fixture()
        var result: Int? = null
        f.queue { result = options(*seven, title = "Pick") }
        f.queues.cycle()
        assertEquals("first page = 4 entries + More... = interface 234", listOf(234), f.opened())
        f.click(5) // More...
        assertNull(result)
        assertEquals("second page = E, F, G + More... = interface 232", listOf(234, 232), f.opened())
        f.click(2) // F
        assertEquals(6, result)
    }

    @Test
    fun `an entry on the first page returns directly`() {
        val f = Fixture()
        var result: Int? = null
        f.queue { result = options(*seven) }
        f.queues.cycle()
        f.click(3)
        assertEquals(3, result)
    }

    @Test
    fun `More on the last page returns to the first page`() {
        val f = Fixture()
        var result: Int? = null
        f.queue { result = options(*seven) }
        f.queues.cycle()
        f.click(5) // -> page 2
        f.click(4) // More... on page 2 -> page 1
        assertEquals(listOf(234, 232, 234), f.opened())
        f.click(1)
        assertEquals(1, result)
    }

    @Test
    fun `five options stay one page without More`() {
        val f = Fixture()
        var result: Int? = null
        f.queue { result = options("A", "B", "C", "D", "E") }
        f.queues.cycle()
        assertEquals(listOf(234), f.opened())
        f.click(5)
        assertEquals(5, result)
    }
}
