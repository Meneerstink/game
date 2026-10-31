package gg.rsmod.plugins.content.n11

import gg.rsmod.game.message.Message
import gg.rsmod.game.message.impl.IfSetTextMessage
import gg.rsmod.game.model.interf.InterfaceSet
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.timer.ACTIVE_COMBAT_TIMER
import gg.rsmod.plugins.content.items.books.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import kotlin.test.*

class N11ReadTests {
    @Test fun `all documents have complete attributed text and real Read bindings`() {
        N11Fixture().use { f ->
            f.load("items.books.Documents_plugin")
            assertEquals(19, ReadableDocuments.entries.size)
            val source = File("src/main/kotlin/gg/rsmod/plugins/content/items/books/ReadableDocuments.kt").readText()
            ReadableDocuments.entries.forEach { (id, document) ->
                assertTrue(document.text.isNotBlank())
                assertTrue(source.contains("// Source: ${document.sourceUrl}"), document.title)
                assertFalse(document.text.contains("transcluded"), document.title)
                assertFalse(document.text.contains("##"), document.title)
                val option = f.def(id).inventoryMenu.indexOfFirst { it.equals("Read", true) } + 1
                assertTrue(option > 0, "census offers Read for $id")
                assertTrue(f.repo.hasItemOption(id, option), "Read bound for $id")
                val recovered = document.book.pages.flatMap { it.leftLines + it.rightLines }.joinToString("")
                assertEquals(document.text.filterNot(Char::isWhitespace), recovered.filterNot(Char::isWhitespace), "all letters preserved for $id")
            }
        }
    }

    @Test fun `compiled Read opens book and button dispatch reaches every page then clears session`() {
        N11Fixture().use { f ->
            val interfaces = InterfaceSet(mockk(relaxed = true))
            every { f.player.interfaces } returns interfaces
            f.load("items.books.Documents_plugin")
            val document = ReadableDocuments.entries.getValue(4707)
            f.inventory[0] = Item(document.item)
            assertTrue(f.repo.executeItem(f.player, document.item, 1))
            val session = assertNotNull(f.attrs[CURR_BOOK])
            assertEquals(Book.INTERFACE, interfaces.currentModal)
            assertTrue(document.book.pages.size > 5, "long book exercises pagination")
            repeat(document.book.pages.size + 2) { assertTrue(f.repo.executeButton(f.player, 960, 73)) }
            assertEquals(document.book.pages.lastIndex, session.page)
            repeat(document.book.pages.size + 2) { f.repo.executeButton(f.player, 960, 72) }
            assertEquals(0, session.page)
            verify { f.player.write(*varargAny<Message> { it is IfSetTextMessage }) }
            // Stale buttons after another modal cannot mutate the old book.
            interfaces.currentModal = 12
            f.repo.executeButton(f.player, 960, 73)
            assertEquals(0, session.page)
            f.repo.executeInterfaceClose(f.player, 960)
            assertNull(f.attrs[CURR_BOOK])
        }
    }

    @Test fun `combat another modal noted item and stale slot cannot open a document`() {
        N11Fixture().use { f ->
            val interfaces = InterfaceSet(mockk(relaxed = true))
            every { f.player.interfaces } returns interfaces
            f.inventory[0] = Item(600)
            f.timers[ACTIVE_COMBAT_TIMER] = 5
            assertFalse(ReadableDocuments.read(f.player, 0, 600))
            f.timers.remove(ACTIVE_COMBAT_TIMER)
            interfaces.currentModal = 12
            assertFalse(ReadableDocuments.read(f.player, 0, 600))
            interfaces.currentModal = -1
            f.def(600).noteTemplateId = 799
            assertFalse(ReadableDocuments.read(f.player, 0, 600))
            f.inventory[0] = Item(601)
            f.inventory[1] = Item(600)
            assertFalse(ReadableDocuments.read(f.player, 0, 600))
            assertNull(f.attrs[CURR_BOOK])
            verify(exactly = 0) { f.player.write(*varargAny<Message> { it is gg.rsmod.game.message.impl.IfOpenSubMessage }) }
        }
    }

    @Test fun `shared definitions keep each reader's page independent and preserve long words`() {
        N11Fixture().use { first -> N11Fixture().use { second ->
            val book = Book.fromText("long", "x".repeat(1700) + " FINAL-WORD")
            val a = BookSession(book)
            val b = BookSession(book)
            every { first.player.interfaces.currentModal } returns 960
            every { second.player.interfaces.currentModal } returns 960
            a.turn(first.player, 1)
            assertEquals(1, a.page)
            assertEquals(0, b.page)
            assertTrue(book.pages.last().let { it.leftLines + it.rightLines }.joinToString("").endsWith("FINAL-WORD"))
            assertEquals("x".repeat(1700) + "FINAL-WORD", book.pages.flatMap { it.leftLines + it.rightLines }.joinToString("").replace(" ", ""))
        } }
    }

    @Test fun `existing wilderness and barbarian books still open through the shared interface`() {
        N11Fixture().use { f ->
            val interfaces = InterfaceSet(mockk(relaxed = true))
            every { f.player.interfaces } returns interfaces
            val ids = listOf(gg.rsmod.plugins.api.cfg.Items.WILDERNESS_MANUAL, gg.rsmod.plugins.api.cfg.Items.BARBARIAN_SKILLS)
            ids.forEach { f.menu(it, "Read") }
            f.load("items.books.Documents_plugin")
            f.load("items.books.Book_wilderness_manual_plugin")
            f.load("items.books.Book_barbarian_skills_plugin")
            ids.forEach { id ->
                interfaces.currentModal = -1
                f.inventory[0] = Item(id)
                assertTrue(f.repo.executeItem(f.player, id, 1))
                f.queues.cycle()
                assertNotNull(f.attrs[CURR_BOOK])
                assertEquals(960, interfaces.currentModal)
                f.repo.executeInterfaceClose(f.player, 960)
            }
        }
    }
}
