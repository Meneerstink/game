package gg.rsmod.plugins.content.mechanics.deadman

import com.google.gson.Gson
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.*
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.game.plugin.PluginRepository
import gg.rsmod.game.service.serializer.json.JsonPlayerSerializer.PersistentContainer
import gg.rsmod.plugins.api.ext.hasItem
import gg.rsmod.plugins.content.mechanics.death.DeathContext
import gg.rsmod.plugins.content.mechanics.death.DeathResolver
import gg.rsmod.plugins.content.mechanics.death.ItemRiskValueProvider
import gg.rsmod.plugins.content.mechanics.ownership.Possessions
import gg.rsmod.plugins.content.mechanics.pvp.LootKeys
import gg.rsmod.plugins.content.mechanics.removed.RemovedItems
import io.mockk.every
import io.mockk.mockk
import kotlin.test.*

class SafeDepositBoxTests {
    private val definitions = mockk<DefinitionSet>()
    private val defs = mutableMapOf<Int, ItemDef>()
    private val world = mockk<World>()
    private val inventory = ItemContainer(definitions, INVENTORY_KEY)
    private val containers = hashMapOf(INVENTORY_KEY to inventory)
    private val attr = AttributeMap()
    private val timers = TimerMap()
    private val player = mockk<Player>().also { p ->
        every { p.world } returns world
        every { p.inventory } returns inventory
        every { p.containers } returns containers
        every { p.attr } returns attr
        every { p.timers } returns timers
        every { p.isDead() } returns false
        every { world.definitions } returns definitions
        every { definitions.get(ItemDef::class.java, any()) } answers { defs.getValue(secondArg()) }
    }

    init {
        define(995, stackable = true)
        define(4151)
        define(4152, stackable = true).also { it.noteTemplateId = 799; it.noteLinkId = 4151 }
        define(772, tradeable = false)
    }

    private fun define(id: Int, stackable: Boolean = false, tradeable: Boolean = true): ItemDef =
        ItemDef(id).also { it.stacks = stackable; it.tradeable = tradeable; defs[id] = it }

    private fun open() = assertEquals(SafeDepositBox.Status.OK, SafeDepositBox.beginAccess(player))

    private fun stored(): List<Item> = containers[SAFE_DEPOSIT_BOX_KEY]?.filterNotNull() ?: emptyList()

    private fun total(id: Int): Long = containers.values.sumOf { c -> c.filterNotNull().filter { it.id == id }.sumOf { it.amount.toLong() } }

    @Test
    fun `ten coins occupy ten slots and a full box never consumes another unit`() {
        inventory[0] = Item(995, 100)
        open()
        assertEquals(SafeDepositBox.Transfer(SafeDepositBox.Status.OK, 10), SafeDepositBox.deposit(player, 0, 995, Int.MAX_VALUE))
        assertEquals(10, stored().size)
        assertTrue(stored().all { it.amount == 1 })
        assertEquals(90, inventory[0]!!.amount)
        assertEquals(SafeDepositBox.Status.BOX_FULL, SafeDepositBox.deposit(player, 0, 995).status)
        assertEquals(100L, total(995))
        assertEquals(SafeDepositBox.Status.OK, SafeDepositBox.withdraw(player, 4, 995).status)
        assertEquals(91, inventory[0]!!.amount)
        assertEquals(100L, total(995))
    }

    @Test
    fun `untradeables and notes of untradeables cannot be stored`() {
        inventory[0] = Item(772)
        define(900).also { it.noteTemplateId = 799; it.noteLinkId = 772 }
        inventory[1] = Item(900, 100)
        open()
        assertEquals(SafeDepositBox.Status.UNTRADEABLE, SafeDepositBox.deposit(player, 0, 772).status)
        assertEquals(SafeDepositBox.Status.UNTRADEABLE, SafeDepositBox.deposit(player, 1, 900).status)
        assertTrue(stored().isEmpty())
        assertEquals(100, inventory[1]!!.amount)
    }

    @Test
    fun `notes count per unit and keep their form on withdrawal`() {
        inventory[0] = Item(4152, 20)
        open()
        assertEquals(10, SafeDepositBox.deposit(player, 0, 4152, 20).moved)
        assertTrue(stored().all { it.id == 4152 && it.amount == 1 })
        SafeDepositBox.withdraw(player, 0, 4152)
        assertEquals(11, inventory[0]!!.amount)
        assertEquals(20L, total(4152))
    }

    @Test
    fun `GE exclusion alone does not prevent safekeeping but fully retired items are refused`() {
        val retained = RemovedItems.NOT_EXCHANGEABLE_IDS.first { !RemovedItems.isUnobtainable(ItemDef(it)) }
        val removed = RemovedItems.IDS.first()
        define(retained)
        define(removed)
        inventory[0] = Item(retained)
        inventory[1] = Item(removed)
        open()
        assertEquals(1, SafeDepositBox.deposit(player, 0, retained).moved)
        assertEquals(SafeDepositBox.Status.UNTRADEABLE, SafeDepositBox.deposit(player, 1, removed).status)
        assertEquals(retained, stored().single().id)
        assertEquals(removed, inventory[1]!!.id)
    }

    @Test
    fun `invalid packets and stale ids are noops`() {
        inventory[0] = Item(995, 50)
        assertEquals(SafeDepositBox.Status.NOT_OPEN, SafeDepositBox.deposit(player, 0, 995).status)
        open()
        listOf(-1, 28, Int.MAX_VALUE).forEach {
            assertEquals(SafeDepositBox.Status.INVALID_SLOT, SafeDepositBox.deposit(player, it, 995).status)
            assertEquals(SafeDepositBox.Status.INVALID_SLOT, SafeDepositBox.withdraw(player, it, 995).status)
        }
        listOf(0, -1, Int.MIN_VALUE).forEach {
            assertEquals(SafeDepositBox.Status.INVALID_AMOUNT, SafeDepositBox.deposit(player, 0, 995, it).status)
        }
        assertEquals(SafeDepositBox.Status.ITEM_CHANGED, SafeDepositBox.deposit(player, 0, 4151).status)
        assertEquals(SafeDepositBox.Status.ITEM_CHANGED, SafeDepositBox.deposit(player, 1, 995).status)
        assertEquals(50L, total(995))
        assertTrue(stored().isEmpty())
    }

    @Test
    fun `new skull invalidates an open session for deposits withdrawals and reads`() {
        inventory[0] = Item(995, 20)
        open()
        SafeDepositBox.deposit(player, 0, 995)
        timers[SKULL_ICON_DURATION_TIMER] = 500
        assertEquals(SafeDepositBox.Status.SKULLED, SafeDepositBox.deposit(player, 0, 995).status)
        assertEquals(SafeDepositBox.Status.SKULLED, SafeDepositBox.withdraw(player, 0, 995).status)
        assertNull(SafeDepositBox.contents(player))
        assertEquals(SafeDepositBox.Status.SKULLED, SafeDepositBox.beginAccess(player))
        timers.remove(SKULL_ICON_DURATION_TIMER)
        assertEquals(SafeDepositBox.Status.NOT_OPEN, SafeDepositBox.withdraw(player, 0, 995).status)
        open()
        assertEquals(1, SafeDepositBox.withdraw(player, 0, 995).moved)
        assertEquals(20L, total(995))
    }

    @Test
    fun `loot key skull blocks access even without a skull timer`() {
        inventory[0] = Item(LootKeys.KEY_IDS[0])
        assertEquals(SafeDepositBox.Status.SKULLED, SafeDepositBox.beginAccess(player))
    }

    @Test
    fun `full inventory and max stack leave box untouched then allow exact stack merge`() {
        inventory[0] = Item(995, 20)
        open()
        SafeDepositBox.deposit(player, 0, 995)
        inventory[0] = Item(995, Int.MAX_VALUE)
        assertEquals(SafeDepositBox.Status.INVENTORY_FULL, SafeDepositBox.withdraw(player, 0, 995).status)
        assertEquals(1, inventory.occupiedSlotCount)
        assertEquals(1, stored().size)
        (1 until 28).forEach { inventory[it] = Item(4151) }
        assertEquals(SafeDepositBox.Status.INVENTORY_FULL, SafeDepositBox.withdraw(player, 0, 995).status)
        assertEquals(1, stored().size)
        inventory[0]!!.amount--
        assertEquals(1, SafeDepositBox.withdraw(player, 0, 995).moved)
        assertEquals(Int.MAX_VALUE, inventory[0]!!.amount)
        assertTrue(stored().isEmpty())
    }

    @Test
    fun `charges survive deposit and withdrawal without aliasing or creation redirects`() {
        val original = Item(4151).putAttr(ItemAttribute.CHARGES, 123)
        inventory[0] = original
        open()
        SafeDepositBox.deposit(player, 0, 4151)
        original.putAttr(ItemAttribute.CHARGES, 0)
        val snapshot = SafeDepositBox.contents(player)!![0]!!
        snapshot.putAttr(ItemAttribute.CHARGES, 999)
        assertEquals(123, stored().single().getAttr(ItemAttribute.CHARGES))
        val oldRedirect = ItemContainer.creationRedirect
        try {
            ItemContainer.creationRedirect = { if (it == 4151) 772 else it }
            SafeDepositBox.withdraw(player, 0, 4151)
        } finally {
            ItemContainer.creationRedirect = oldRedirect
        }
        assertEquals(4151, inventory[0]!!.id)
        assertEquals(123, inventory[0]!!.getAttr(ItemAttribute.CHARGES))
        assertTrue(stored().isEmpty())
        assertEquals(SafeDepositBox.Status.ITEM_CHANGED, SafeDepositBox.withdraw(player, 0, 4151).status)
    }

    @Test
    fun `Wizard close then Seer reopen shares one box while accounts stay separate`() {
        inventory[0] = Item(4151)
        open()
        SafeDepositBox.deposit(player, 0, 4151)
        SafeDepositBox.endAccess(player)
        assertNull(SafeDepositBox.contents(player))
        open()
        assertEquals(4151, SafeDepositBox.contents(player)!![0]!!.id)
        val other = mockk<Player>()
        every { other.attr } returns AttributeMap()
        every { other.inventory } returns ItemContainer(definitions, INVENTORY_KEY)
        every { other.timers } returns TimerMap()
        every { other.isDead() } returns false
        every { other.containers } returns hashMapOf()
        every { other.world } returns world
        assertEquals(SafeDepositBox.Status.OK, SafeDepositBox.beginAccess(other))
        assertTrue(SafeDepositBox.contents(other)!!.all { it == null })
        assertEquals(1, stored().size)
    }

    @Test
    fun `registered container roundtrips in player JSON with attributes and stays outside usable items`() {
        inventory[0] = Item(4151).putAttr(ItemAttribute.CHARGES, 321)
        open()
        SafeDepositBox.deposit(player, 0, 4151)
        assertEquals("your safe deposit box", Possessions.whereOwned(player, setOf(4151)))
        assertEquals(1, Possessions.count(player, 4151))
        assertFalse(player.hasItem(4151))
        val registered = PluginRepository(world).containerKeys.single { it.name == "safe_deposit_box" }
        assertEquals(SAFE_DEPOSIT_BOX_KEY, registered)
        assertEquals(10, registered.capacity)
        val gson = Gson()
        val saved = PersistentContainer(registered.name, containers[registered]!!.toMap())
        val restored = gson.fromJson(gson.toJson(saved), PersistentContainer::class.java)
        val loaded = ItemContainer(definitions, registered)
        restored.items.forEach { (slot, item) -> loaded[slot] = item }
        containers[registered] = loaded
        SafeDepositBox.endAccess(player)
        attr.removeIf { it.resetOnDeath }
        open()
        assertEquals(1, SafeDepositBox.withdraw(player, 0, 4151).moved)
        assertEquals(321, inventory[0]!!.getAttr(ItemAttribute.CHARGES))
        assertTrue(player.hasItem(4151))
    }

    @Test
    fun `repeated roundtrips conserve quantity and never duplicate a stored item`() {
        inventory[0] = Item(995, 12345)
        open()
        repeat(100) {
            SafeDepositBox.deposit(player, 0, 995, 10)
            (0 until 10).forEach { slot -> SafeDepositBox.withdraw(player, slot, 995) }
            assertEquals(12345L, total(995))
            assertTrue(stored().isEmpty())
        }
    }

    @Test
    fun `PvM and PvP death resolution never includes stored items or makes them carried keeps`() {
        inventory[0] = Item(4151)
        every { player.equipment } returns ItemContainer(definitions, EQUIPMENT_KEY)
        open()
        SafeDepositBox.deposit(player, 0, 4151)
        for (context in DeathContext.values()) {
            for (skulled in listOf(false, true)) {
                val result = DeathResolver.resolve(
                    victim = player, killer = null, skulled = skulled,
                    valueProvider = ItemRiskValueProvider { 1_000_000L }, contextOverride = context,
                )
                assertTrue(result.itemRisk.protected.isEmpty())
                assertTrue(result.itemRisk.lost.isEmpty())
                assertEquals(4151, stored().single().id)
            }
        }
        every { player.isDead() } returns true
        assertEquals(SafeDepositBox.Status.UNAVAILABLE, SafeDepositBox.withdraw(player, 0, 4151).status)
        every { player.isDead() } returns false
        assertEquals(SafeDepositBox.Status.NOT_OPEN, SafeDepositBox.withdraw(player, 0, 4151).status)
        open()
        assertEquals(1, SafeDepositBox.withdraw(player, 0, 4151).moved)
    }
}
