package gg.rsmod.plugins.content.n11

import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.items.FerociousRing
import gg.rsmod.plugins.content.skills.farming.FarmingAmulet
import gg.rsmod.plugins.content.skills.farming.data.Patch
import gg.rsmod.plugins.content.skills.farming.logic.PatchState
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlin.test.*

class N11RubTests {
    @Test fun `compiled ring teleport refuses Tele Block without a charge and consumes exactly one on success`() {
        N11Fixture().use { f ->
            f.load("items.Teleport_items_plugin")
            f.inventory[0] = Item(Items.FEROCIOUS_RING_5)
            val rub = f.def(Items.FEROCIOUS_RING_5).inventoryMenu.indexOf("Rub") + 1
            f.timers[gg.rsmod.game.model.timer.TELEBLOCK_TIMER] = 10
            assertTrue(f.repo.executeItem(f.player, Items.FEROCIOUS_RING_5, rub))
            assertEquals(Items.FEROCIOUS_RING_5, f.inventory[0]?.id)
            f.timers.remove(gg.rsmod.game.model.timer.TELEBLOCK_TIMER)
            f.repo.executeItem(f.player, Items.FEROCIOUS_RING_5, rub)
            assertEquals(Items.FEROCIOUS_RING_4, f.inventory[0]?.id)
            f.repo.executeItem(f.player, Items.FEROCIOUS_RING_5, rub)
            assertEquals(Items.FEROCIOUS_RING_4, f.inventory[0]?.id, "stale repeat cannot spend another charge")
            val last = Items.FEROCIOUS_RING_1
            val ringSlot = gg.rsmod.plugins.api.EquipmentType.RING.id
            f.equipment[ringSlot] = Item(last)
            val wornOption = f.def(last).equipmentMenu.indexOf("Kuradal") + 1
            assertTrue(f.repo.executeEquipmentOption(f.player, last, wornOption))
            assertNull(f.equipment[ringSlot], "last worn charge disappears")
        }
    }

    @Test fun `compiled Rub reaches the same farming monitor from inventory and equipment`() {
        N11Fixture().use { f ->
            FarmingAmulet.ids.forEach { id ->
                f.menu(id, "Rub")
                f.def(id).equipmentMenu[0] = "Rub"
            }
            f.load("skills.farming.Farming_amulet_plugin")
            f.inventory[0] = Item(Items.AMULET_OF_FARMING_8)
            f.equipment[gg.rsmod.plugins.api.EquipmentType.AMULET.id] = Item(Items.AMULET_OF_FARMING_8)
            assertTrue(f.repo.executeItem(f.player, Items.AMULET_OF_FARMING_8, 1))
            assertTrue(f.repo.executeEquipmentOption(f.player, Items.AMULET_OF_FARMING_8, 1))
            assertEquals(Items.AMULET_OF_FARMING_8, f.inventory[0]?.id, "unbound Rub costs no charge")
            FarmingAmulet.bind(f.player, Patch.CatherbyFlower)
            f.repo.executeEquipmentOption(f.player, Items.AMULET_OF_FARMING_8, 1)
            f.queues.cycle()
            f.queues.terminateTasks() // closing the first conversation must not cost a charge
            assertEquals(Items.AMULET_OF_FARMING_8, f.equipment[gg.rsmod.plugins.api.EquipmentType.AMULET.id]?.id)
            val manager = mockk<gg.rsmod.plugins.content.skills.farming.logic.FarmingManager>()
            val patchManager = mockk<gg.rsmod.plugins.content.skills.farming.logic.PatchManager>()
            val state = mockk<PatchState>(relaxed = true)
            every { state.isDiseased } returns true
            every { patchManager.state } returns state
            every { manager.getPatchManager(Patch.CatherbyFlower) } returns patchManager
            f.attrs[gg.rsmod.plugins.content.skills.farming.constants.Constants.farmingManagerAttr] = manager
            f.repo.executeEquipmentOption(f.player, Items.AMULET_OF_FARMING_8, 1)
            f.queues.cycle()
            f.queues.submitReturnValue(gg.rsmod.game.message.impl.ResumePauseButtonMessage(interfaceId = 228, component = 2, button = 2))
            f.queues.cycle()
            assertEquals(Items.AMULET_OF_FARMING_7, f.equipment[gg.rsmod.plugins.api.EquipmentType.AMULET.id]?.id)
            io.mockk.verify(exactly = 1) { manager.getPatchManager(Patch.CatherbyFlower) }
        }
    }

    @Test fun `farming charges decrement once in inventory and worn slot and disappear after final report`() {
        N11Fixture().use { f ->
            for (container in listOf(f.inventory, f.equipment)) {
                for (id in FarmingAmulet.ids) {
                    val held = Item(id)
                    container[2] = held
                    assertTrue(FarmingAmulet.useCharge(container, 2, held))
                    assertFalse(FarmingAmulet.useCharge(container, 2, held), "stale double click")
                    assertEquals(if (id == 12608) null else id - 2, container[2]?.id)
                }
                container[2] = Item(12623)
                assertFalse(FarmingAmulet.useCharge(container, 2, container[2]!!), "noted")
            }
        }
    }

    @Test fun `farming amulet binds one shared allotment or flower and never a herb patch`() {
        N11Fixture().use { f ->
            FarmingAmulet.bind(f.player, Patch.FaladorAllotmentNorth)
            assertEquals(Patch.FaladorAllotmentNorth, FarmingAmulet.bound(f.player))
            FarmingAmulet.bind(f.player, Patch.CatherbyFlower)
            assertEquals(Patch.CatherbyFlower, FarmingAmulet.bound(f.player))
            FarmingAmulet.bind(f.player, Patch.CatherbyHerb)
            assertEquals(Patch.CatherbyFlower, FarmingAmulet.bound(f.player))
        }
        val state = mockk<PatchState>(relaxed = true)
        every { state.isDiseased } returns true
        assertEquals("Your patch is diseased.", FarmingAmulet.status(state))
        every { state.isDead } returns true
        assertEquals("Your patch has died.", FarmingAmulet.status(state))
    }

    @Test fun `ferocious chain spends one charge and binds through shared restricted teleport route`() {
        assertEquals(listOf(15399, 15400, 15401, 15402, -1), FerociousRing.ids.map { FerociousRing.next(it) })
        assertNull(FerociousRing.next(15403))
        assertEquals(1, FerociousRing.destination.height)
        val script = File("src/main/kotlin/gg/rsmod/plugins/content/items/teleport_items.plugin.kts").readText()
        assertTrue(script.contains("bindTeleport(id, \"Rub\", null, next, destination, TeleportType.FEROCIOUS_RING)"))
        assertTrue(script.contains("bindTeleport(id, \"Kuradal\", EquipmentType.RING, next, destination, TeleportType.FEROCIOUS_RING)"))
        assertEquals(20, gg.rsmod.plugins.content.magic.TeleportType.FEROCIOUS_RING.wildLvlRestriction)
        assertEquals(gg.rsmod.plugins.content.magic.TeleportType.JEWELRY.startSound,
            gg.rsmod.plugins.content.magic.TeleportType.FEROCIOUS_RING.startSound)
        assertTrue(script.contains("canTeleport(type)"))
        val farming = File("src/main/kotlin/gg/rsmod/plugins/content/skills/farming/farming_amulet.plugin.kts").readText()
        assertFalse(farming.contains("teleport", ignoreCase = true))
    }
}
