package gg.rsmod.plugins.content.mechanics.exchange

import gg.rsmod.game.fs.def.ItemDef
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** WEAR-ZONDER-EQUIPMENT: the house never sells an item whose Wear option does nothing. */
class GeHouseWearTests {
    private fun def(
        menu: String?,
        slot: Int,
    ) = ItemDef(15021).apply {
        inventoryMenu[1] = menu
        equipSlot = slot
    }

    @Test
    fun `a wear option without an equipment slot keeps the item out of the house`() {
        assertTrue(GrandExchangeService.unwearableWithWearOption(def("Wear", -1)))
        assertTrue(GrandExchangeService.unwearableWithWearOption(def("Wield", -1)))
    }

    @Test
    fun `wearable items and items without a wear option stay in the house`() {
        assertFalse(GrandExchangeService.unwearableWithWearOption(def("Wear", 0)))
        assertFalse(GrandExchangeService.unwearableWithWearOption(def("Eat", -1)))
        assertFalse(GrandExchangeService.unwearableWithWearOption(def(null, -1)))
    }

    @Test
    fun `the 26-11 wearable house items have an equipment row`() {
        // Source: Novite data/items/equip.txt (slot) + WornAppearanceRankTool (appearance_id); rings have no worn model.
        val yml = File("../../data/cfg/items.yml").readText().replace("\r\n", "\n")
        val expected = mapOf(15509 to 0, 19287 to 0, 20116 to 4, 22215 to 2, 15398 to 12, 23570 to 12, 20714 to 3)
        for ((id, slot) in expected) {
            val start = yml.indexOf("\n- id: $id\n")
            assertTrue(start >= 0, "$id missing")
            val entry = yml.substring(start + 1, yml.indexOf("\n- id: ", start + 1))
            assertTrue("    equip_slot: $slot\n" in entry, "$id has no equip_slot $slot:\n$entry")
        }
    }
}
