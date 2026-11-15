package gg.rsmod.plugins.content.scrolls

import com.google.gson.JsonParser
import gg.rsmod.game.RspsPaths
import gg.rsmod.plugins.api.EquipmentType
import java.io.File
import kotlin.test.*

class TreasureTrailStepCatalogueTests {
    private val raw get() = File(RspsPaths.data("cfg/clue/steps.json")).readText()
    private val rows get() = TreasureTrailStepCatalogue.parse(raw).rows

    @Test fun `selected steps keep their tiers exceptional fights and master puzzle gates`() {
        val selection = rows
        assertEquals(mapOf(ClueScrollTier.EASY to 15, ClueScrollTier.MEDIUM to 15, ClueScrollTier.HARD to 15,
            ClueScrollTier.ELITE to 12, ClueScrollTier.MASTER to 12), selection.groupingBy { it.tier }.eachCount())
        assertEquals(69, selection.map { it.id }.toSet().size)
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
