package gg.rsmod.plugins.content.items.books

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.plugin.KotlinPlugin
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.*

val CURR_BOOK = AttributeKey<BookSession>("currBook")

/** Immutable content; page position and player references must never live in a shared book definition. */
data class Book(val title: String, val pages: List<BookPage>) {
    init {
        require(pages.isNotEmpty())
        require(pages.all { it.leftLines.size <= 15 && it.rightLines.size <= 15 })
    }

    fun open(player: Player) {
        // Mount first: replacing an interface fires its close hook and clears the old session.
        player.openInterface(INTERFACE, dest = InterfaceDestination.MAIN_SCREEN)
        val session = BookSession(this)
        player.attr[CURR_BOOK] = session
        session.update(player)
    }

    companion object {
        const val INTERFACE = 960
        internal val LEFT_COMPONENTS = intArrayOf(49, 56, 61, 62, 54, 63, 55, 51, 60, 58, 53, 50, 57, 59, 52)
        internal val RIGHT_COMPONENTS = intArrayOf(33, 39, 36, 44, 37, 46, 40, 42, 34, 35, 38, 43, 47, 45, 41)

        /** All text is retained, including the final partial page; 15 lines on each leaf of interface 960. */
        fun fromText(title: String, text: String): Book {
            require(text.isNotBlank())
            val lines = text.trim().lines().flatMap { paragraph ->
                if (paragraph.isBlank()) listOf("") else wrap(paragraph.trim())
            }
            return Book(title, lines.chunked(30).map { BookPage(it.take(15), it.drop(15)) })
        }

        private fun wrap(text: String): List<String> {
            val lines = mutableListOf<String>()
            var rest = text
            // Conservative width for the existing book leaves; split overlong words without losing letters.
            while (rest.length > 28) {
                val split = rest.lastIndexOf(' ', 28).takeIf { it > 0 } ?: 28
                lines += rest.take(split)
                rest = rest.drop(split).trimStart()
            }
            if (rest.isNotEmpty()) lines += rest
            return lines
        }
    }
}

class BookSession(val book: Book) {
    var page = 0
        private set

    fun turn(player: Player, delta: Int) {
        if (player.interfaces.currentModal != Book.INTERFACE) return
        page = (page + delta).coerceIn(0, book.pages.lastIndex)
        update(player)
    }

    fun update(player: Player) {
        player.setComponentText(Book.INTERFACE, 69, book.title)
        player.setComponentHidden(Book.INTERFACE, 72, page == 0)
        player.setComponentText(Book.INTERFACE, 70, if (page == 0) "" else "Prev")
        player.setComponentHidden(Book.INTERFACE, 73, page == book.pages.lastIndex)
        player.setComponentText(Book.INTERFACE, 71, if (page == book.pages.lastIndex) "" else "Next")
        Book.LEFT_COMPONENTS.indices.forEach { line ->
            player.setComponentText(Book.INTERFACE, Book.LEFT_COMPONENTS[line], book.pages[page].getLeftLine(line))
            player.setComponentText(Book.INTERFACE, Book.RIGHT_COMPONENTS[line], book.pages[page].getRightLine(line))
        }
    }
}

data class BookPage(val leftLines: List<String>, val rightLines: List<String>) {
    fun getLeftLine(line: Int): String = leftLines.getOrElse(line) { "" }
    fun getRightLine(line: Int): String = rightLines.getOrElse(line) { "" }
}

fun Player.openBook(book: Book) = book.open(this)

class BookInterface(val plugin: KotlinPlugin) {
    fun registerBookInterface() {
        plugin.on_interface_close(Book.INTERFACE) { player.attr.remove(CURR_BOOK) }
        plugin.on_button(Book.INTERFACE, 72) { player.attr[CURR_BOOK]?.turn(player, -1) }
        plugin.on_button(Book.INTERFACE, 73) { player.attr[CURR_BOOK]?.turn(player, 1) }
    }
}
