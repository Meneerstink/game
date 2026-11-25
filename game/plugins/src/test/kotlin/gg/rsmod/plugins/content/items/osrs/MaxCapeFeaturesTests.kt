package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.message.impl.ResumePauseButtonMessage
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.timer.TELEBLOCK_TIMER
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.game.tools.importer.MaxCapeMenuTool
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.items.potion.StaminaPotions
import gg.rsmod.plugins.content.n11.N11Fixture
import gg.rsmod.plugins.content.skills.SkillcapeFeatures
import gg.rsmod.plugins.content.skills.SkillcapePerks
import io.mockk.every
import io.mockk.verify
import kotlin.test.*

/** Exercises the compiled inventory/worn scripts, including the dialog pauses. */
class MaxCapeFeaturesTests {
    private fun load(f: N11Fixture) {
        MaxCapeMenuTool.INVENTORY.forEach { (opcode, option) -> f.def(MaxCapes.MAX_CAPE).inventoryMenu[opcode - 35] = option }
        MaxCapeMenuTool.WORN.forEach { (param, option) -> f.def(MaxCapes.MAX_CAPE).equipmentMenu[param - 528] = option }
        f.load("items.osrs.Max_cape_options_plugin")
    }

    private fun choose(f: N11Fixture, size: Int, choice: Int) {
        f.queues.submitReturnValue(ResumePauseButtonMessage(224 + size * 2, choice + 1, choice + 1))
        f.queues.cycle()
    }

    private fun features(f: N11Fixture, worn: Boolean = false) {
        assertTrue(if (worn) f.repo.executeEquipmentOption(f.player, MaxCapes.MAX_CAPE, 4)
            else f.repo.executeItem(f.player, MaxCapes.MAX_CAPE, 4))
        f.queues.cycle()
    }

    private fun search(f: N11Fixture, choice: Int = 1, worn: Boolean = false) {
        features(f, worn)
        choose(f, 5, 2)
        f.queues.cycle() // Features queues the Search dialog.
        choose(f, 3, choice)
    }

    @Test fun `inventory and worn Search deliver both tools and share the three search limit`() {
        N11Fixture().use { f ->
            load(f)
            f.inventory[0] = Item(MaxCapes.MAX_CAPE)
            search(f)
            f.inventory[0] = null
            f.equipment[EquipmentType.CAPE.id] = Item(MaxCapes.MAX_CAPE)
            repeat(2) { search(f, worn = true) }
            search(f, worn = true)
            assertEquals(3, f.inventory.getItemCount(Items.MITH_GRAPPLE_9419))
            assertEquals(3, f.inventory.getItemCount(Items.BRONZE_CROSSBOW))
            assertEquals(3, SkillcapePerks.usedToday(f.player, SkillcapePerks.DailyUse.FLETCHING_SEARCH))
            search(f, choice = 2, worn = true)
            assertEquals(1, f.inventory.getItemCount(Items.PESTLE_AND_MORTAR), "Herblore is independent of the daily Fletching limit")
        }
    }

    @Test fun `Search with zero or one free slot gives no half pair and spends no daily use`() {
        N11Fixture().use { f ->
            load(f)
            repeat(28) { f.inventory[it] = Item(Items.KNIFE) }
            f.inventory[0] = Item(MaxCapes.MAX_CAPE)
            search(f)
            f.inventory[27] = null
            search(f)
            assertEquals(0, f.inventory.getItemCount(Items.MITH_GRAPPLE_9419))
            assertEquals(0, f.inventory.getItemCount(Items.BRONZE_CROSSBOW))
            assertEquals(0, SkillcapePerks.usedToday(f.player, SkillcapePerks.DailyUse.FLETCHING_SEARCH))
            f.inventory[26] = null
            search(f)
            assertEquals(28, f.inventory.occupiedSlotCount)
            assertEquals(1, SkillcapePerks.usedToday(f.player, SkillcapePerks.DailyUse.FLETCHING_SEARCH))
        }
    }

    @Test fun `dropping the max cape during Search cannot use another carried skillcape instead`() {
        N11Fixture().use { f ->
            load(f)
            f.inventory[0] = Item(MaxCapes.MAX_CAPE)
            f.inventory[1] = Item(Items.FLETCHING_CAPE)
            features(f)
            choose(f, 5, 2)
            f.queues.cycle()
            f.inventory[0] = null
            choose(f, 3, 1)
            assertEquals(0, f.inventory.getItemCount(Items.MITH_GRAPPLE_9419))
            assertEquals(0, SkillcapePerks.usedToday(f.player, SkillcapePerks.DailyUse.FLETCHING_SEARCH))
        }
    }

    @Test fun `max stamina restores energy for one minute without drinking or consuming endurance charges`() {
        for (worn in listOf(false, true)) N11Fixture().use { f ->
            load(f)
            val ring = Item(gg.rsmod.plugins.content.mechanics.deadman.items.DmmItems.RING_OF_ENDURANCE)
            ring.attr[gg.rsmod.game.model.item.ItemAttribute.CHARGES] = 10
            f.equipment[EquipmentType.RING.id] = ring
            if (worn) f.equipment[EquipmentType.CAPE.id] = Item(MaxCapes.MAX_CAPE)
            else f.inventory[0] = Item(MaxCapes.MAX_CAPE)
            features(f, worn)
            choose(f, 5, 1)
            verify(exactly = 1) { f.player.runEnergy = 100.0 }
            assertEquals(SkillcapeFeatures.STAMINA_TICKS, f.timers[StaminaPotions.STAMINA_TIMER])
            assertEquals(10, ring.attr[gg.rsmod.game.model.item.ItemAttribute.CHARGES])
            features(f, worn)
            choose(f, 5, 1)
            assertEquals(1, SkillcapePerks.usedToday(f.player, SkillcapePerks.DailyUse.STAMINA))
            verify(exactly = 1) { f.player.runEnergy = 100.0 }
        }
    }

    @Test fun `Commune is reachable through max Features and changes the existing shared preference`() {
        N11Fixture().use { f ->
            load(f)
            f.inventory[0] = Item(MaxCapes.MAX_CAPE)
            features(f)
            choose(f, 5, 3)
            f.queues.cycle()
            f.queues.submitReturnValue(ResumePauseButtonMessage(211, 3, 3))
            f.queues.cycle()
            choose(f, 2, 1)
            assertEquals(true, f.attrs[AvasAssembler.GATHERING_STOPPED])
        }
    }

    @Test fun `accumulator max cape participates in attraction and gets steel instead of mithril`() {
        N11Fixture().use { f ->
            f.def(Items.STEEL_ARROW).equipSlot = EquipmentType.AMMO.id
            f.def(Items.STEEL_ARROW).stacks = true
            f.load("items.osrs.Avas_assembler_plugin")
            f.equipment[EquipmentType.CAPE.id] = Item(Items.ACCUMULATOR_MAX_CAPE)
            val field = f.repo.javaClass.getDeclaredField("timerPlugins").apply { isAccessible = true }
            val timer = (field.get(f.repo) as Map<*, *>).keys.single() as TimerKey
            assertTrue(f.repo.executeTimer(f.player, timer))
            assertEquals(Items.STEEL_ARROW, f.equipment[EquipmentType.AMMO.id]?.id)
            assertEquals(1, f.equipment[EquipmentType.AMMO.id]?.amount)
            assertEquals(0, f.inventory.getItemCount(Items.MITHRIL_ARROW))
            f.attrs[AvasAssembler.GATHERING_STOPPED] = true
            every { f.player.tile } returns Tile(3204, 3200)
            f.repo.executeTimer(f.player, timer)
            assertEquals(1, f.equipment[EquipmentType.AMMO.id]?.amount)
        }
    }

    @Test fun `accumulator max attraction drops a gathered item when the full backpack cannot accept it`() {
        N11Fixture().use { f ->
            f.load("items.osrs.Avas_assembler_plugin")
            f.equipment[EquipmentType.CAPE.id] = Item(Items.ACCUMULATOR_MAX_CAPE)
            every { f.world.random(any<Int>()) } returns 1999 // steel bar
            repeat(28) { f.inventory[it] = Item(100000 + it) }
            val field = f.repo.javaClass.getDeclaredField("timerPlugins").apply { isAccessible = true }
            val timer = (field.get(f.repo) as Map<*, *>).keys.single() as TimerKey
            f.repo.executeTimer(f.player, timer)
            verify(exactly = 1) { f.world.spawn(match<gg.rsmod.game.model.entity.GroundItem> { it.item == Items.STEEL_BAR }) }
            assertEquals(28, f.inventory.occupiedSlotCount)
        }
    }

    @Test fun `max Home and hunter teleport refuse teleblock and deep wilderness without spending hunter uses`() {
        for (blocked in listOf(true, false)) N11Fixture().use { f ->
            load(f)
            f.inventory[0] = Item(MaxCapes.MAX_CAPE)
            every { f.player.isDead() } returns false
            if (blocked) f.timers[TELEBLOCK_TIMER] = 100
            else every { f.player.tile } returns Tile(3200, 3900)
            assertTrue(f.repo.executeEquipmentOption(f.player, MaxCapes.MAX_CAPE, 1))
            assertTrue(f.repo.executeItem(f.player, MaxCapes.MAX_CAPE, 1))
            f.queues.cycle()
            choose(f, 3, 2) // Skilling Areas
            choose(f, 3, 2) // Feldip Hunter
            assertEquals(0, SkillcapePerks.usedToday(f.player, SkillcapePerks.DailyUse.HUNTER_TELEPORT))
            val refusal = if (blocked) "A magical force has stopped you from teleporting."
                else "You can't use this teleport after level 30 wilderness."
            verify(atLeast = 2) { f.player.write(gg.rsmod.game.message.impl.MessageGameMessage(type = 0, message = refusal, username = null)) }
        }
    }
}
