package gg.rsmod.plugins.api.ext

import com.displee.cache.CacheLibrary
import gg.rsmod.game.model.World
import gg.rsmod.plugins.content.quests.tab.N108Ui
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * N499 (owner 2026-10-31): no chatbox options title runs over its swords. Measured with the production cache's q8_full glyph widths
 * (the title font of interfaces 228-238): the pet warning takes the wide sword pair, short titles keep the narrow one, and every
 * literal title in the plugins is listed with the pair it gets - none is wider than the 480 px title line itself.
 */
class OptionTitleSwordsTests {
    companion object {
        private val world: World by lazy {
            val w = mockk<World>(relaxed = true)
            every { w.filestore } returns CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            w
        }

        private fun width(text: String) = N108Ui.textWidth(world, OptionTitleSwords.FONT, text)

        private val TITLE = Regex("""options\((?:[^()]|\([^()]*\))*?title\s*=\s*"((?:[^"\\$]|\\.)*)"""", RegexOption.DOT_MATCHES_ALL)
    }

    @Test
    fun `the cache font measures the owner's pet title as in his screenshot`() {
        // bug-20261031-203321: "Your pet is carrying items. Pick it up?" spans 263 game px (515 screen px at 1.958) - q8_full says 265.
        assertEquals(265, width("Your pet is carrying items. Pick it up?"))
    }

    @Test
    fun `short titles keep the narrow swords, the pet warning takes the wide ones, longer ones none`() {
        assertEquals(OptionTitleSwords.Swords.NARROW, OptionTitleSwords.choose(228, width("Select an Option")))
        assertEquals(OptionTitleSwords.Swords.WIDE, OptionTitleSwords.choose(228, width("Warning: items stored in your pet!")))
        assertEquals(OptionTitleSwords.Swords.WIDE, OptionTitleSwords.choose(228, width("What would you like to talk about?")))
        assertEquals(OptionTitleSwords.Swords.NONE, OptionTitleSwords.choose(228, width("Revert the maul? The ornate handle will be destroyed.")))
        assertEquals(215, OptionTitleSwords.room(337))
        assertEquals(354, OptionTitleSwords.room(476))
        for (id in listOf(228, 230, 232, 234, 236, 238)) {
            assertEquals(OptionTitleSwords.Swords.NARROW, OptionTitleSwords.choose(id, 0), "interface $id")
            assertEquals(OptionTitleSwords.Swords.NONE, OptionTitleSwords.choose(id, 400), "interface $id")
        }
    }

    @Test
    fun `class - every literal options title fits its title line and gets a sword pair it does not touch`() {
        val root = File("src/main/kotlin")
        val rows = mutableListOf<String>()
        val tooWide = mutableListOf<String>()
        root.walkTopDown().filter { it.isFile && (it.name.endsWith(".kt") || it.name.endsWith(".kts")) }.forEach { file ->
            val text = file.readText()
            TITLE.findAll(text).forEach { m ->
                val title = m.groupValues[1].replace("\\\"", "\"")
                if (title.contains("<br>")) return@forEach
                val w = width(title)
                val swords = OptionTitleSwords.choose(228, w)
                if (swords != OptionTitleSwords.Swords.NARROW) rows += "$w px $swords  \"$title\"  (${file.name})"
                if (w > 480) tooWide += "$w px \"$title\" (${file.name})"
            }
        }
        println("[N499] titles that leave the narrow swords:\n" + rows.sorted().joinToString("\n"))
        assertTrue(rows.any { "items stored in your pet" in it }, "the pet warning is one of them")
        assertTrue(tooWide.isEmpty(), "wider than the 480 px title line:\n" + tooWide.joinToString("\n"))
    }
}
