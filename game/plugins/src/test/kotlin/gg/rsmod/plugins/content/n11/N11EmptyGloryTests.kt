package gg.rsmod.plugins.content.n11

import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import kotlin.test.*

class N11EmptyGloryTests {
    private fun load(f: N11Fixture) {
        listOf(Items.AMULET_OF_GLORY_6, Items.AMULET_OF_GLORY_5, Items.AMULET_OF_GLORY_4,
            Items.AMULET_OF_GLORY_3, Items.AMULET_OF_GLORY_2, Items.AMULET_OF_GLORY_1,
            Items.AMULET_OF_GLORY_T4, Items.AMULET_OF_GLORY_T3, Items.AMULET_OF_GLORY_T2, Items.AMULET_OF_GLORY_T1,
        ).forEach { id ->
            f.menu(id, "", "Wear", "", "Rub")
            listOf("Edgeville", "Karamja", "Draynor Village", "Al Kharid").forEachIndexed { i, option -> f.def(id).equipmentMenu[i] = option }
        }
        f.menu(10719, "", "Wear", "", "Rub")
        f.load("items.jewellery.Amulet_of_glory_plugin")
    }

    @Test fun `zero-charge trimmed legacy amulet uses refusal only and cannot teleport or change items`() {
        N11Fixture().use { f ->
            load(f)
            val amulet = Item(10719)
            f.inventory[0] = amulet
            assertTrue(f.repo.executeItem(f.player, 10719, 4))
            assertSame(amulet, f.inventory[0])
            assertFalse(f.repo.hasEquipmentOption(10719, 4))
            assertFalse(f.repo.hasItemOption(10718, 4))
            f.inventory[0] = Item(995)
            f.repo.executeItem(f.player, 10719, 4)
            assertEquals(995, f.inventory[0]?.id)
        }
    }

    @Test fun `zero-charge glory does not duplicate an existing Rub`() {
        N11Fixture().use { f ->
            f.repo.bindItem(10719, 4) {}
            load(f)
        }
    }
}
