package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.content.mechanics.deadman.items.BookOfTheDead
import kotlin.test.*

class BookOfTheDeadTests {
    @Test fun `an empty book is zero and destroy retains account charges`() {
        val f = DeadmanPlayerFixture()
        assertEquals(0, BookOfTheDead.charges(f.player))
        f.attr[BookOfTheDead.CHARGES] = 250
        val book = Item(BookOfTheDead.ITEM)
        f.inventory[0] = book
        assertTrue(BookOfTheDead.destroy(f.player, 0, book))
        assertNull(f.inventory[0])
        assertEquals(250, BookOfTheDead.charges(f.player))
        f.inventory[0] = Item(BookOfTheDead.ITEM)
        assertEquals(250, BookOfTheDead.charges(f.player))
        assertFalse(BookOfTheDead.destroy(f.player, 0, book))
        assertNotNull(f.inventory[0])
    }

    @Test fun `destroy cannot remove a moved book or another item from an old dialog`() {
        val f = DeadmanPlayerFixture()
        val book = Item(BookOfTheDead.ITEM)
        f.inventory[1] = book
        assertFalse(BookOfTheDead.destroy(f.player, 0, book))
        f.inventory[0] = Item(9000)
        assertFalse(BookOfTheDead.destroy(f.player, 0, book))
        assertEquals(9000, f.inventory[0]!!.id)
        assertSame(book, f.inventory[1])
    }
}
