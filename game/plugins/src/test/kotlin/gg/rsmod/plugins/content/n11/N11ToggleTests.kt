package gg.rsmod.plugins.content.n11

import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.items.osrs.AvasAssembler
import gg.rsmod.plugins.content.combat.strategy.ranged.AvasDevices
import io.mockk.every
import io.mockk.verify
import kotlin.test.*

class N11ToggleTests {
    private fun prepare(f: N11Fixture) {
        AvasAssembler.LEGACY_DEVICES.forEach {
            f.menu(it, "", "Wear", "", "Toggle")
            f.def(it).equipmentMenu[0] = "Toggle"
        }
        listOf(Items.IRON_ARROW, Items.STEEL_ARROW).forEach {
            f.def(it).equipSlot = EquipmentType.AMMO.id
            f.def(it).stacks = true
        }
        f.load("items.osrs.Avas_assembler_plugin")
    }

    private fun tick(f: N11Fixture) {
        val field = f.repo.javaClass.getDeclaredField("timerPlugins").apply { isAccessible = true }
        val key = (field.get(f.repo) as Map<*, *>).keys.single() as TimerKey
        assertTrue(f.repo.executeTimer(f.player, key))
    }

    @Test fun `inventory and worn Toggle change one persisted preference twice immediately`() {
        N11Fixture().use { f ->
            prepare(f)
            assertEquals("avas_assembler_gathering_stopped", AvasAssembler.GATHERING_STOPPED.persistenceKey)
            for (id in AvasAssembler.LEGACY_DEVICES) {
                f.inventory[0] = Item(id)
                f.equipment[EquipmentType.CAPE.id] = Item(id)
                assertTrue(f.repo.executeItem(f.player, id, 4))
                assertEquals(true, f.attrs[AvasAssembler.GATHERING_STOPPED])
                assertTrue(f.repo.executeEquipmentOption(f.player, id, 1))
                assertEquals(false, f.attrs[AvasAssembler.GATHERING_STOPPED])
                f.repo.executeItem(f.player, id, 4)
                f.repo.executeItem(f.player, id, 4)
                assertEquals(false, f.attrs[AvasAssembler.GATHERING_STOPPED])
            }
            f.inventory[0] = Item(Items.BLACK_WARLOCK_NOTED)
            f.equipment[EquipmentType.CAPE.id] = null
            f.repo.executeItem(f.player, Items.AVAS_ACCUMULATOR, 4)
            f.repo.executeEquipmentOption(f.player, Items.AVAS_ATTRACTOR, 1)
            assertEquals(false, f.attrs[AvasAssembler.GATHERING_STOPPED], "stale or noted cannot toggle")
            assertFalse(f.repo.hasItemOption(Items.BLACK_WARLOCK_NOTED, 4))
        }
    }

    @Test fun `real gathering timer reads Toggle and uses iron steel and existing mithril table`() {
        for ((id, arrow) in listOf(Items.AVAS_ATTRACTOR to Items.IRON_ARROW, Items.AVAS_ACCUMULATOR to Items.STEEL_ARROW, Items.AVAS_ASSEMBLER to Items.MITHRIL_ARROW)) {
            N11Fixture().use { f ->
                prepare(f)
                every { f.world.random(any<Int>()) } returns 0
                f.equipment[EquipmentType.CAPE.id] = Item(id)
                f.attrs[AvasAssembler.GATHERING_STOPPED] = true
                tick(f)
                assertNull(f.equipment[EquipmentType.AMMO.id])
                f.attrs[AvasAssembler.GATHERING_STOPPED] = false
                tick(f)
                assertEquals(arrow, f.equipment[EquipmentType.AMMO.id]?.id)
                tick(f)
                assertEquals(1, f.equipment[EquipmentType.AMMO.id]?.amount, "movement gate")
                assertEquals(AvasDevices.AmmoOutcome.RECOVERED, AvasDevices.outcome(f.player, 99))
                f.attrs[AvasAssembler.GATHERING_STOPPED] = true
                assertEquals(AvasDevices.AmmoOutcome.RECOVERED, AvasDevices.outcome(f.player, 99), "Toggle does not disable ammunition recovery")
            }
        }
    }

    @Test fun `legacy gathering overflows full inventory onto ground and respects metal torso`() {
        N11Fixture().use { f ->
            prepare(f)
            f.equipment[EquipmentType.CAPE.id] = Item(Items.AVAS_ACCUMULATOR)
            every { f.world.random(any<Int>()) } returns 1999 // steel bar
            (0 until 28).forEach { f.inventory[it] = Item(100000 + it) }
            tick(f)
            verify(exactly = 1) { f.world.spawn(any<gg.rsmod.game.model.entity.GroundItem>()) }
        }
        N11Fixture().use { f ->
            prepare(f)
            f.equipment[EquipmentType.CAPE.id] = Item(Items.AVAS_ATTRACTOR)
            f.def(Items.IRON_PLATEBODY).name = "Iron platebody"
            f.equipment[EquipmentType.CHEST.id] = Item(Items.IRON_PLATEBODY)
            tick(f)
            assertNull(f.equipment[EquipmentType.AMMO.id])
        }
    }

    @Test fun `rare attracted helmet equips through shared requirements or falls back to inventory`() {
        for (baseDefence in listOf(1, 99)) N11Fixture().use { f ->
            prepare(f)
            f.equipment[EquipmentType.CAPE.id] = Item(Items.AVAS_ACCUMULATOR)
            f.def(Items.STEEL_MED_HELM).equipSlot = EquipmentType.HEAD.id
            f.def(Items.STEEL_MED_HELM).skillReqs = it.unimi.dsi.fastutil.bytes.Byte2ByteOpenHashMap().apply { put(1.toByte(), 5.toByte()) }
            every { f.skills.getMaxLevel(1) } returns baseDefence
            every { f.world.random(any<Int>()) } returns 1998
            tick(f)
            if (baseDefence < 5) {
                assertNull(f.equipment[EquipmentType.HEAD.id])
                assertEquals(1, f.inventory.getItemCount(Items.STEEL_MED_HELM))
            } else {
                assertEquals(Items.STEEL_MED_HELM, f.equipment[EquipmentType.HEAD.id]?.id)
                assertEquals(0, f.inventory.getItemCount(Items.STEEL_MED_HELM))
            }
        }
    }

    @Test fun `already bound inventory and worn options are preserved without duplicate registration`() {
        N11Fixture().use { f ->
            var invCalls = 0
            var wornCalls = 0
            f.repo.bindItem(Items.AVAS_ATTRACTOR, 4) { invCalls++ }
            f.repo.bindEquipmentOption(Items.AVAS_ACCUMULATOR, 1) { wornCalls++ }
            prepare(f)
            f.repo.executeItem(f.player, Items.AVAS_ATTRACTOR, 4)
            f.repo.executeEquipmentOption(f.player, Items.AVAS_ACCUMULATOR, 1)
            assertEquals(1, invCalls)
            assertEquals(1, wornCalls)
            assertEquals(null, f.attrs[AvasAssembler.GATHERING_STOPPED])
        }
    }

    @Test fun `each legacy weighted table contains exactly two thousand outcomes`() {
        for ((id, table) in listOf(Items.AVAS_ATTRACTOR to AvasAssembler.ATTRACTOR_TABLE, Items.AVAS_ACCUMULATOR to AvasAssembler.ACCUMULATOR_TABLE)) {
            assertEquals(2000, table.sumOf { it.second })
            assertEquals(table.toMap(), (0 until 2000).map { AvasAssembler.attracted(it, id) }.groupingBy { it }.eachCount())
        }
    }
}
