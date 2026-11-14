package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.COMPLETED_MAGE_ARENA
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.BANK_KEY
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.game.model.varp.VarpSet
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.setVarp
import gg.rsmod.plugins.content.quests.QuestStatus
import gg.rsmod.plugins.content.quests.tab.QuestTabDetails
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlin.test.*

class MageArenaIITests {
    // Synthetic test-only allocations. They never enter a target cache or production mapping.
    private val assets = MageArenaII.Assets { 50_000 + it }
    private class Account {
        val player = mockk<Player>(relaxed = true)
        val attrs = AttributeMap()
        val skills = SkillSet(25)
        val defs = mockk<DefinitionSet>()
        val inventory = ItemContainer(defs, INVENTORY_KEY)
        val bank = ItemContainer(defs, BANK_KEY)
        val equipment = ItemContainer(defs, EQUIPMENT_KEY)
        init {
            every { defs.get(ItemDef::class.java, any()) } answers { ItemDef(secondArg()) }
            every { player.attr } returns attrs
            every { player.skills } returns skills
            every { player.inventory } returns inventory
            every { player.bank } returns bank
            every { player.equipment } returns equipment
            every { player.containers } returns hashMapOf(INVENTORY_KEY to inventory, BANK_KEY to bank, EQUIPMENT_KEY to equipment)
            every { player.varps } returns VarpSet((0..2000).toSet())
        }
        fun requirements() {
            attrs[COMPLETED_MAGE_ARENA] = true
            skills.setBaseLevel(Skills.MAGIC, 75)
            GodSpellTraining.SPELLS.forEach { attrs[it.key] = 100 }
        }
    }

    private fun start(a: Account) {
        a.requirements()
        assertEquals(MageArenaII.SymbolResult.GRANTED, MageArenaII.symbol(a.player, assets))
    }
    private fun deliver(a: Account, god: MageArenaII.God, count: Int = 1) {
        a.inventory.add(assets.remains(god)!!, count)
        assertEquals(count, MageArenaII.handIn(a.player, god, assets))
    }

    @Test fun `all three quest remains refuse actual bank deposits without removing items or changing progress`() {
        val a = Account(); start(a)
        mockkObject(MageArenaAssets)
        try {
            every { MageArenaAssets.item(any()) } answers { assets.item(firstArg()) }
            MageArenaII.God.values().forEach { god ->
                val item = assets.remains(god)!!
                a.inventory.add(item)
                val slot = a.inventory.getItemIndex(item, skipAttrItems = false)
                assertFalse(gg.rsmod.plugins.content.inter.bank.Bank.deposit(a.player, a.inventory, slot, 1))
                assertTrue(a.inventory.contains(item))
                assertFalse(a.bank.contains(item))
                assertFalse(MageArenaII.delivered(a.player, god))
            }
        } finally { unmockkObject(MageArenaAssets) }
    }

    @Test fun `destroy rechecks the original slot leaves handins intact and permits a lost symbol replacement`() {
        val a = Account(); start(a)
        val first = MageArenaII.God.SARADOMIN
        deliver(a, first)
        val symbol = assets.symbol!!
        val slot = a.inventory.getItemIndex(symbol, skipAttrItems = false)
        a.inventory[slot] = Item(Items.SHARK)
        assertFalse(MageArenaII.destroy(a.player, symbol, slot, assets))
        assertEquals(Items.SHARK, a.inventory[slot]?.id)
        assertFalse(MageArenaII.destroy(a.player, Items.SHARK, slot, assets))
        a.inventory[slot] = Item(symbol)
        assertTrue(MageArenaII.destroy(a.player, symbol, slot, assets))
        assertFalse(MageArenaII.destroy(a.player, symbol, slot, assets))
        assertTrue(MageArenaII.delivered(a.player, first))
        assertEquals(MageArenaII.SymbolResult.GRANTED, MageArenaII.symbol(a.player, assets))
        val remains = assets.remains(MageArenaII.God.GUTHIX)!!
        a.inventory.add(remains)
        assertTrue(MageArenaII.destroy(a.player, remains, a.inventory.getItemIndex(remains, false), assets))
        assertFalse(MageArenaII.delivered(a.player, MageArenaII.God.GUTHIX))
        assertEquals(4, MageArenaII.destroyDetails(assets).size)
    }

    @Test fun `start requires actual MA1 base75 and all three independently trained spells`() {
        val a = Account()
        a.requirements(); a.attrs[COMPLETED_MAGE_ARENA] = false
        assertFalse(MageArenaII.canStart(a.player))
        a.attrs[COMPLETED_MAGE_ARENA] = true
        a.skills.setBaseLevel(Skills.MAGIC, 74); a.skills.setCurrentLevel(Skills.MAGIC, 99)
        assertFalse(MageArenaII.canStart(a.player))
        a.skills.setBaseLevel(Skills.MAGIC, 75)
        GodSpellTraining.SPELLS.forEach { training ->
            a.attrs[training.key] = 99
            assertFalse(MageArenaII.canStart(a.player))
            a.attrs[training.key] = 100
        }
        assertTrue(MageArenaII.canStart(a.player))
    }

    @Test fun `old completed varp and existing capes do not fabricate completion or consume old items`() {
        val a = Account()
        a.player.setVarp(MageArenaII.VARP, 1)
        a.inventory.add(Items.IMBUED_GUTHIX_CAPE)
        assertFalse(MageArenaII.started(a.player))
        assertFalse(MageArenaII.completed(a.player))
        assertEquals(QuestStatus.State.NOT_STARTED, QuestStatus.state(a.player, MageArenaII.SLOT))
        assertEquals(1, a.inventory.getItemCount(Items.IMBUED_GUTHIX_CAPE))
        assertSame(MageArenaIIQuest, QuestTabDetails.quest("Mage Arena II"))
    }

    @Test fun `full start and missing import cannot advance and a banked symbol prevents duplicates`() {
        val a = Account(); a.requirements()
        assertEquals(MageArenaII.SymbolResult.ASSETS_MISSING, MageArenaII.symbol(a.player, MageArenaII.Assets { null }))
        a.inventory.add(Items.SHARK, 28)
        assertEquals(MageArenaII.SymbolResult.INVENTORY_FULL, MageArenaII.symbol(a.player, assets))
        assertFalse(MageArenaII.started(a.player))
        a.inventory.removeAll()
        assertEquals(MageArenaII.SymbolResult.GRANTED, MageArenaII.symbol(a.player, assets))
        a.inventory.remove(assets.symbol!!, 1); a.bank.add(assets.symbol!!)
        assertEquals(MageArenaII.SymbolResult.OWNED, MageArenaII.symbol(a.player, assets))
        assertFalse(a.inventory.contains(assets.symbol!!))
        a.bank.remove(assets.symbol!!, 1)
        assertEquals(MageArenaII.SymbolResult.GRANTED, MageArenaII.symbol(a.player, assets))
    }

    @Test fun `separate handins persist per god duplicates buy only one first cape and stale dialogue cannot duplicate`() {
        val a = Account(); start(a)
        val gods = MageArenaII.God.values()
        deliver(a, gods[0], 3)
        assertEquals(1, MageArenaII.displayStage(a.player))
        assertEquals(listOf(gods[1], gods[2]), MageArenaII.trackable(a.player, assets))
        assertEquals(0, MageArenaII.handIn(a.player, gods[0], assets))
        deliver(a, gods[2]); deliver(a, gods[1])
        assertTrue(MageArenaII.completed(a.player))
        assertEquals(QuestStatus.State.COMPLETED, QuestStatus.state(a.player, MageArenaII.SLOT))
        a.inventory.removeAll(); a.inventory.add(gods[1].cape); a.inventory.add(Items.SHARK, 27)
        val slot = a.inventory.getItemIndex(gods[1].cape, skipAttrItems = false)
        assertEquals(MageArenaII.ImbueResult.IMBUED, MageArenaII.imbue(a.player, gods[1], slot))
        assertEquals(0, a.inventory.freeSlotCount)
        assertEquals(MageArenaII.ImbueResult.NO_CAPE, MageArenaII.imbue(a.player, gods[1], slot))
        assertEquals(1, a.inventory.getItemCount(gods[1].imbued))
        a.inventory[slot] = Item(gods[0].cape)
        assertEquals(MageArenaII.ImbueResult.NO_CREDIT, MageArenaII.imbue(a.player, gods[0], slot))
    }

    @Test fun `repeat handin grants a single matching credit and is rechecked after confirmation`() {
        val a = Account(); start(a)
        val gods = MageArenaII.God.values()
        gods.forEach { deliver(a, it) }
        a.inventory.removeAll(); a.inventory.add(gods[0].cape)
        assertEquals(MageArenaII.ImbueResult.IMBUED, MageArenaII.imbue(a.player, gods[0], 0))
        deliver(a, gods[1], 2)
        a.inventory[0] = Item(gods[2].cape)
        assertEquals(MageArenaII.ImbueResult.NO_CREDIT, MageArenaII.imbue(a.player, gods[2], 0))
        assertEquals(MageArenaII.ImbueResult.NO_CAPE, MageArenaII.imbue(a.player, gods[1], 0))
        a.inventory[0] = Item(gods[1].cape)
        assertEquals(MageArenaII.ImbueResult.IMBUED, MageArenaII.imbue(a.player, gods[1], 0))
        a.inventory[0] = Item(gods[1].cape)
        assertEquals(MageArenaII.ImbueResult.NO_CREDIT, MageArenaII.imbue(a.player, gods[1], 0))
    }

    @Test fun `owned remains in familiar storage suppress repeat tracking but cannot be handed in remotely`() {
        val a = Account(); start(a)
        val gods = MageArenaII.God.values()
        gods.forEach { deliver(a, it) }
        val key = gg.rsmod.game.model.container.key.ContainerKey("test_familiar", 10,
            gg.rsmod.game.model.container.ContainerStackType.NORMAL)
        val cargo = ItemContainer(a.defs, key)
        a.player.containers[key] = cargo
        cargo.add(assets.remains(gods[0])!!)
        assertTrue(MageArenaII.holdsRemains(a.player, gods[0], assets))
        assertEquals(listOf(gods[1], gods[2]), MageArenaII.trackable(a.player, assets))
        assertEquals(0, MageArenaII.handIn(a.player, gods[0], assets))
        assertTrue(cargo.contains(assets.remains(gods[0])!!))
        cargo.removeAll()
        assertEquals(3, MageArenaII.trackable(a.player, assets).size)
    }

    @Test fun `symbol filters delivered remains before completion and carried remains afterwards`() {
        val a = Account(); start(a)
        val gods = MageArenaII.God.values()
        a.inventory.add(assets.remains(gods[0])!!)
        assertEquals(3, MageArenaII.trackable(a.player, assets).size)
        assertEquals(1, MageArenaII.handIn(a.player, gods[0], assets))
        assertEquals(2, MageArenaII.trackable(a.player, assets).size)
        deliver(a, gods[1]); deliver(a, gods[2])
        assertEquals(3, MageArenaII.trackable(a.player, assets).size)
        a.inventory.add(assets.remains(gods[0])!!); a.inventory.add(assets.remains(gods[2])!!)
        assertEquals(listOf(gods[1]), MageArenaII.trackable(a.player, assets))
        assertTrue(MageArenaII.journal(a.player, assets).last().contains("one god cape"))
    }
}
