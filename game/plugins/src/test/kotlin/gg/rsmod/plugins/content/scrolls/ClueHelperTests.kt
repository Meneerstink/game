package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.RspsPaths
import java.io.File
import kotlin.test.*

/** CLUE-QH 18-11: the Quest Helper's clue texts = RuneLite's Clue Scroll overlay data + our steps.json. */
class ClueHelperTests {
    private val steps by lazy { TreasureTrailStepCatalogue.parse(File(RspsPaths.data("cfg/clue/steps.json")).readText()).rows }
    private val rows by lazy { ClueHelper.parse(File(RspsPaths.data("cfg/clue/helper.json")).readText()) }

    private fun step(key: String) = steps.single { it.key == key }

    @Test fun `every helper row names an existing step and nearly every step has RuneLite help`() {
        assertTrue(rows.keys.all { k -> steps.any { it.key == k } })
        val missing = steps.map { it.key }.filter { it !in rows }
        // Only these have no RuneLite entry (generator output 18-11 nachtrun): they show our own hint.
        assertEquals(setOf("Clue scroll (medium) - To the east of the Ranging guild", "master:jorral",
            "Clue scroll (hard) - AIQ0440", "Clue scroll (hard) - ALP1140", "Clue scroll (hard) - BIP7013",
            "Clue scroll (hard) - BJR1123", "Clue scroll (hard) - BLP6200"), missing.toSet())
    }

    @Test fun `anagram names the npc, the area and the challenge answer`() {
        val s = step("Clue scroll (medium) - A BAKER")
        val h = ClueHelper.help(s, rows[s.key])
        assertEquals(ClueHelper.Help("Anagram Clue", "Speak to Baraek.", "Varrock square", "Answer: 5"), h)
    }

    @Test fun `cipher falls back to our npc name when RuneLite only has an npc id`() {
        val s = step("Clue scroll (medium) - GUHCHO")
        val h = ClueHelper.help(s, rows[s.key])
        assertEquals("Speak to Drezel.", h.instruction)
        assertEquals("Answer: 7", h.tip)
    }

    @Test fun `emote lists the emote and every item to equip`() {
        val s = step("Clue scroll (easy) - Dance at the crossroads north of Draynor")
        val h = ClueHelper.help(s, rows[s.key])
        assertEquals("Emote: Dance", h.instruction)
        assertEquals("Draynor Village", h.where)
        assertEquals("Equip: Iron chainbody, Sapphire ring, Longbow", h.tip)
    }

    @Test fun `cryptic, coordinate and map steps show RuneLite's solution text`() {
        val c = step("Clue scroll (easy) - Dig between some ominous stones in Falador")
        assertEquals("Three standing stones inside a walled area. East of the northern Falador gate.", ClueHelper.help(c, rows[c.key]).instruction)
        val k = step("Clue scroll (medium) - 00.13S 13.58E")
        assertEquals("West of Banana plantation on Karamja.", ClueHelper.help(k, rows[k.key]).instruction)
        assertEquals("Coordinate Clue", ClueHelper.help(k, rows[k.key]).title)
    }

    @Test fun `a step without a row shows our hint without markup, a talk step its npc`() {
        val j = step("master:jorral")
        assertEquals("Speak to Jorral.", ClueHelper.help(j, null).instruction)
        val r = step("Clue scroll (medium) - To the east of the Ranging guild")
        val h = ClueHelper.help(r, null)
        assertFalse(h.instruction.contains("<"))
        assertEquals("Clue Scroll", h.title)
        assertNull(h.tip)
    }

    @Test fun `every step yields a non-empty instruction`() {
        steps.forEach { s -> assertTrue(ClueHelper.help(s, rows[s.key]).instruction.isNotBlank(), s.key) }
    }
}
