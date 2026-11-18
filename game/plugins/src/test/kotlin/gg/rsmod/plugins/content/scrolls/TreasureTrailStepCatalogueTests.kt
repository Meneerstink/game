package gg.rsmod.plugins.content.scrolls

import com.google.gson.JsonParser
import gg.rsmod.game.RspsPaths
import gg.rsmod.plugins.api.EquipmentType
import java.io.File
import kotlin.test.*

class TreasureTrailStepCatalogueTests {
    private val raw get() = File(RspsPaths.data("cfg/clue/steps.json")).readText()
    private val rows get() = TreasureTrailStepCatalogue.parse(raw).rows

    @Test fun `all emote scrolls show the source instruction without the Wiki editorial prefix`() {
        assertEquals(23,rows.count { it.action==TreasureTrailStepCatalogue.Action.EMOTE })
        assertTrue(rows.none { "The emote reveals" in it.hint })
        assertTrue(rows.filter { it.action==TreasureTrailStepCatalogue.Action.EMOTE }.all { it.hint==it.hint.trim() })
    }

    /**
     * Claude client round 3 (17-Nov): 'The cryptic reveals where to dig: ...' reached the player. In-game text per RuneLite's clue
     * plugin (matches the widget text exactly): anagram 'This anagram reveals who to speak to next: ', cipher 'The cipher reveals who
     * to speak to next: ', coordinates 'DD degrees MM minutes north|south<br>DD degrees MM minutes east|west', everything else bare.
     */
    @Test fun `no scroll shows a Wiki editorial label and lead-ins match the game`() {
        val text = rows.filter { it.kind != "MAP" }
        assertTrue(text.none { Regex("^The (clue|cryptic|emote|map|coordinate|anagram) reveals", RegexOption.IGNORE_CASE).containsMatchIn(it.hint) },
            text.filter { Regex("^The (clue|cryptic|emote|map|coordinate|anagram) reveals").containsMatchIn(it.hint) }.joinToString { it.key })
        assertTrue(text.filter { it.kind == "ANAGRAM" }.all { it.hint.startsWith("This anagram reveals who to speak to next: ") &&
            it.hint.removePrefix("This anagram reveals who to speak to next: ").let { letters -> letters == letters.uppercase() } })
        assertTrue(text.filter { it.kind == "CIPHER" }.all { it.hint.startsWith("The cipher reveals who to speak to next: ") })
        val coordinate = Regex("""\d+ degrees \d+ minutes (north|south)<br>\d+ degrees \d+ minutes (east|west)""")
        assertTrue(text.filter { it.kind == "COORDINATE" }.all { coordinate.matches(it.hint) }, text.filter { it.kind == "COORDINATE" }.joinToString { it.hint })
    }

    @Test fun `historical Jarvis event answer is not an unconditional ordinary challenge answer`() {
        val step = rows.single { it.key.endsWith("AREA CHEF TREK") }
        assertEquals(TreasureTrailStepCatalogue.Challenge.NUMBER, step.challenge)
        assertEquals(setOf(19), step.answers)
        assertFalse(20 in step.answers)
        val record = JsonParser().parse(raw).asJsonArray.single { it.asJsonObject["key"].asString == step.key }.asJsonObject
        assertEquals("19 or 20", record["challenge"].asJsonObject["sourceAnswer"].asString)
        assertEquals(20, record["challenge"].asJsonObject["unavailableEventAnswer"].asJsonObject["answer"].asInt)
    }

    @Test fun `selected steps keep their tiers exceptional fights and master puzzle gates`() {
        val selection = rows
        assertEquals(mapOf(ClueScrollTier.EASY to 31, ClueScrollTier.MEDIUM to 56, ClueScrollTier.HARD to 75,
            ClueScrollTier.ELITE to 17, ClueScrollTier.MASTER to 12), selection.groupingBy { it.tier }.eachCount())
        assertEquals(191, selection.map { it.id }.toSet().size)
        val arena = selection.single { it.key.endsWith("02.39N 29.58E") }
        assertEquals(TreasureTrailStepCatalogue.Challenge.NONE, arena.challenge)
        assertTrue(arena.guards.isEmpty())
        selection.filter { it.tier == ClueScrollTier.MASTER && it.kind == "COORDINATE" }.forEach {
            assertEquals(listOf(7310), it.guards)
            assertFalse(it.chooseGuard)
        }
        selection.filter { it.tier == ClueScrollTier.MASTER && it.kind == "ANAGRAM" }.also { assertEquals(2, it.size) }.forEach {
            assertEquals(TreasureTrailStepCatalogue.Challenge.PUZZLE_CHOICE, it.challenge)
            assertEquals(setOf(TreasureTrailStepCatalogue.Challenge.SLIDING_PUZZLE, TreasureTrailStepCatalogue.Challenge.LIGHT_BOX), it.puzzles)
        }
        assertEquals(3, selection.count { it.tornPart })
        assertTrue(selection.filter { it.action == TreasureTrailStepCatalogue.Action.EMOTE && it.tier == ClueScrollTier.MEDIUM }.all { it.secondEmote != null })
    }

    @Test fun `wrong gear optional missing imports empty slots and only-listed equipment cannot bypass a step`() {
        val step = rows.single { it.key.endsWith("Salute in the banana plantation") }
        val slots = MutableList<Int?>(14) { null }
        val resolve = { item: TreasureTrailStepCatalogue.Equipment -> item.existingLocal }
        assertFalse(step.acceptsEquipment(slots, resolve))
        slots[EquipmentType.RING.id] = 1643
        slots[EquipmentType.AMULET.id] = 1731
        assertTrue(step.acceptsEquipment(slots, resolve))
        slots[EquipmentType.CHEST.id] = 1127
        assertFalse(step.acceptsEquipment(slots, resolve))
        slots[EquipmentType.CHEST.id] = null
        slots[EquipmentType.LEGS.id] = 1079
        assertFalse(step.acceptsEquipment(slots, resolve))

        val only = rows.single { it.key.endsWith("Salute in the Warriors' guild bank") }
        slots.fill(null)
        slots[EquipmentType.WEAPON.id] = only.equipment.single().single().existingLocal
        assertTrue(only.acceptsEquipment(slots, resolve))
        slots[EquipmentType.HEAD.id] = 1163
        assertFalse(only.acceptsEquipment(slots, resolve))
        assertFalse(only.acceptsEquipment(slots) { null })
    }

    @Test fun `corrupt source data fails closed instead of producing a free talk or naked emote`() {
        val array = JsonParser().parse(raw).asJsonArray
        val master = array.first { it.asJsonObject["key"].asString == "master:mandrith" }.asJsonObject
        master["challenge"].asJsonObject.remove("alternatives")
        assertFailsWith<IllegalArgumentException> { TreasureTrailStepCatalogue.parse(array.toString()) }
        val emptyHint = JsonParser().parse(raw).asJsonArray
        emptyHint[0].asJsonObject.addProperty("hint", "")
        assertFailsWith<IllegalArgumentException> { TreasureTrailStepCatalogue.parse(emptyHint.toString()) }
    }
}
