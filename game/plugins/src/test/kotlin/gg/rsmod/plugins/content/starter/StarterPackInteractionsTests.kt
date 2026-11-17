package gg.rsmod.plugins.content.starter

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.hasEntranaRestrictedEquipment
import gg.rsmod.plugins.content.items.food.Food
import gg.rsmod.plugins.content.items.food.Foods
import gg.rsmod.plugins.content.mechanics.deadman.DeadmanPlayerFixture
import gg.rsmod.plugins.content.mechanics.deadman.Dmm2Ids
import gg.rsmod.plugins.content.newplayer.NewPlayerConfig
import io.mockk.*
import kotlin.test.*

class StarterPackInteractionsTests {
    private val f = DeadmanPlayerFixture()
    private val ids = DeadmanStarterPack.Ids(700, 701, 702, 703, 704, 705, 706, 707)
    init {
        DeadmanStarterPack.install(ids)
        (700..707).forEach { f.define(it) }
        Dmm2Ids.install(listOf("item\t13658\t708")); f.define(708, true)
        every { f.player.tile } returns Tile(3200, 3200)
    }

    @Test fun `Chronicle card charging caps at one thousand and preserves remaining cards and the book`() {
        f.inventory[0] = Item(ids.chronicle); f.inventory[1] = Item(708, 25)
        f.attr[DeadmanStarterPack.CHRONICLE_CHARGES] = 990
        assertEquals(StarterPackInteractions.Result.OK, StarterPackInteractions.charge(f.player, 0, ids.chronicle))
        assertEquals(1000, StarterPackInteractions.charges(f.player)); assertEquals(15L, f.count(708))
        assertEquals(StarterPackInteractions.Result.FULL, StarterPackInteractions.charge(f.player, 0, ids.chronicle))
        assertEquals(15L, f.count(708)); assertEquals(1L, f.count(ids.chronicle))
        assertFalse(f.inventory[0]!!.hasAnyAttr())
    }

    @Test fun `Chronicle teleport rechecks book identity wilderness and account charges after the countdown`() {
        val book = Item(ids.chronicle)
        f.inventory[0] = book
        f.attr[DeadmanStarterPack.CHRONICLE_CHARGES] = 2
        f.inventory[0] = Item(ids.chronicle)
        assertEquals(StarterPackInteractions.Result.ITEM_CHANGED, StarterPackInteractions.spend(f.player, book, 0, false))
        assertEquals(2, StarterPackInteractions.charges(f.player))
        f.inventory[0] = book
        every { f.player.tile } returns Tile(3200, 3600)
        assertEquals(StarterPackInteractions.Result.WILDERNESS, StarterPackInteractions.spend(f.player, book, 0, false))
        assertEquals(2, StarterPackInteractions.charges(f.player))
        every { f.player.tile } returns Tile(3200, 3200)
        assertEquals(StarterPackInteractions.Result.OK, StarterPackInteractions.spend(f.player, book, 0, false))
        f.inventory[0] = null; f.equipment[5] = book
        assertEquals(StarterPackInteractions.Result.OK, StarterPackInteractions.spend(f.player, book, 5, true))
        assertEquals(StarterPackInteractions.Result.NO_CHARGES, StarterPackInteractions.spend(f.player, book, 5, true))
        assertEquals(1L, f.count(ids.chronicle))
        assertEquals("Your book has run out of charges.", StarterPackInteractions.checkMessage(f.player))
    }

    @Test fun `starter tuna observes the existing food cooldown and consumes only the selected special fish`() {
        mockkObject(Foods)
        try {
            every { Foods.canEat(f.player, Food.TUNA) } returns false
            every { Foods.eat(f.player, Food.TUNA) } just Runs
            f.define(Items.TUNA)
            f.inventory[0] = Item(ids.tuna); f.inventory[1] = Item(Items.TUNA)
            assertEquals(StarterPackInteractions.Result.ITEM_CHANGED, StarterPackInteractions.eatTuna(f.player, 1, ids.tuna))
            assertEquals(StarterPackInteractions.Result.BLOCKED, StarterPackInteractions.eatTuna(f.player, 0, ids.tuna))
            assertEquals(1L, f.count(ids.tuna))
            every { Foods.canEat(f.player, Food.TUNA) } returns true
            assertEquals(StarterPackInteractions.Result.OK, StarterPackInteractions.eatTuna(f.player, 0, ids.tuna))
            assertEquals(0L, f.count(ids.tuna)); assertEquals(1L, f.count(Items.TUNA))
            assertEquals(StarterPackInteractions.Result.ITEM_CHANGED, StarterPackInteractions.eatTuna(f.player, 0, ids.tuna))
            verify(exactly = 1) { Foods.eat(f.player, Food.TUNA) }
        } finally { unmockkObject(Foods) }
    }

    @Test fun `Entrana rejects a closed starter pack even though it is not equipment`() {
        val pack = NewPlayerConfig.current.starterChestItem
        f.define(pack).equipSlot = -1
        f.inventory[0] = Item(pack)
        assertTrue(f.player.hasEntranaRestrictedEquipment())
        f.inventory[0] = null
        assertFalse(f.player.hasEntranaRestrictedEquipment())
    }
}
