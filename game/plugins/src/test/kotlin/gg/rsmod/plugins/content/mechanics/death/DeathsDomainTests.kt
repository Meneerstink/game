package gg.rsmod.plugins.content.mechanics.death

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.DEATH_COFFER_ATTR
import gg.rsmod.game.model.attr.DEATH_RECOVERY_EXPIRY_ATTR
import gg.rsmod.game.model.attr.DEATH_RECOVERY_FEE_ATTR
import gg.rsmod.game.model.attr.GRAVESTONE_TICKS_ATTR
import gg.rsmod.game.model.attr.GRAVESTONE_TILE_ATTR
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.BANK_KEY
import gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.container.key.GRAVESTONE_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.game.model.timer.TimerMap
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The OSRS death system (gravestone, Death's Office, Death's Coffer) and the owner's Deadman decisions of 2026-09-26:
 * nothing is ever deleted, food and potions stay in
 * the gravestone. Death rework (owner 2026-09-26): the gravestone is free, can be blessed/repaired, and Death's Office is unlimited.
 */
class DeathsDomainTests {
    // ---- fees (owner 2026-09-26: the gravestone has none, Death's Office keeps 5 %) ----

    @Test
    fun `death's office charges 5 percent per unit worth 100k or more`() {
        val c = DeathsDomainConfig.OSRS
        assertEquals(0, DeathFees.officeUnitFee(Item(WHIP, 1), values(WHIP to 99_999L), c))
        assertEquals(5_000, DeathFees.officeUnitFee(Item(WHIP, 1), values(WHIP to 100_000L), c))
        assertEquals(50_000L * 4, DeathFees.officeFee(Item(WHIP, 4), 4, values(WHIP to 1_000_000L), c))
        assertEquals(0, DeathFees.officeUnitFee(Item(WHIP, 1).putAttr(ItemAttribute.DEATH_FEE_FREE, 1), values(WHIP to 1_000_000L), c))
    }

    @Test
    fun `the config file holds exactly the OSRS values`() {
        val file = Paths.get("..", "..", "data", "cfg", "deaths_domain.yml").toFile()
        assertEquals(DeathsDomainConfig.OSRS, DeathsDomainConfig.load(file))
    }

    // ---- storage ----

    @Test
    fun `a fee-free stack never merges with a chargeable stack of the same item`() {
        val office = ItemContainer(DEFINITIONS, DEATH_RECOVERY_KEY)
        DeathStorage.insert(DEFINITIONS, office, Item(COINS, 10))
        DeathStorage.insert(DEFINITIONS, office, Item(COINS, 5).putAttr(ItemAttribute.DEATH_FEE_FREE, 1))
        DeathStorage.insert(DEFINITIONS, office, Item(COINS, 1))
        assertEquals(11, office[0]!!.amount)
        assertEquals(5, office[1]!!.amount)
        assertEquals(1, office[1]!!.getAttr(ItemAttribute.DEATH_FEE_FREE))
    }

    // ---- gravestone ----

    @Test
    fun `a repeat death sends the old gravestone's resources and surplus unstackables to Death but keeps food`() {
        val player = newPlayer()
        val grave = player.gravestone
        player.attr[GRAVESTONE_TILE_ATTR] = Tile(3000, 3000, 0).as30BitInteger
        grave[0] = Item(BONES, 1)
        grave[1] = Item(LOBSTER, 1)
        for (slot in 2 until 32) grave[slot] = Item(BRONZE_SWORD, 1) // 30 unstackable swords: 2 over the 28 limit

        val deposit = Gravestone.deposit(player, listOf(Item(WHIP, 1)), Tile(3100, 3100, 0), moveExisting = false, config = DeathsDomainConfig.OSRS)

        assertTrue(deposit.movedFromOldGrave)
        assertTrue(deposit.addedToPrevious)
        assertEquals(1, player.deathRecovery.getItemCount(BONES), "bones are a resource: OSRS sends them to Death's Office")
        assertEquals(2, player.deathRecovery.getItemCount(BRONZE_SWORD), "unstackables beyond 28 go to Death's Office")
        assertEquals(28, grave.getItemCount(BRONZE_SWORD))
        assertEquals(1, grave.getItemCount(LOBSTER), "owner 2026-09-26: food and potions stay in the gravestone")
        assertEquals(1, grave.getItemCount(WHIP))
        assertEquals(Tile(3000, 3000, 0), Gravestone.tile(player), "the gravestone keeps its original place")
    }

    @Test
    fun `a PvM death in 20+ Wilderness moves the existing gravestone there`() {
        val player = newPlayer()
        player.gravestone[0] = Item(BRONZE_SWORD, 1)
        player.attr[GRAVESTONE_TILE_ATTR] = Tile(3000, 3000, 0).as30BitInteger
        Gravestone.deposit(player, listOf(Item(WHIP, 1)), Tile(3100, 3800, 0), moveExisting = true, config = DeathsDomainConfig.OSRS)
        assertEquals(Tile(3100, 3800, 0), Gravestone.tile(player))
    }

    @Test
    fun `the gravestone timer only runs while the player is active`() {
        val player = newPlayer()
        Gravestone.deposit(player, listOf(Item(WHIP, 1)), Tile(3100, 3100, 0), moveExisting = false, config = DeathsDomainConfig.OSRS)
        val before = Gravestone.ticksLeft(player)
        assertFalse(Gravestone.tick(player, active = false))
        assertEquals(before, Gravestone.ticksLeft(player), "idle / window open / Death's Office: the timer stands still")
        assertFalse(Gravestone.tick(player, active = true))
        assertEquals(before - 1, Gravestone.ticksLeft(player))
    }

    @Test
    fun `a collapsing gravestone sends everything to Death's Office, items saved as paid stay free`() {
        val player = newPlayer()
        player.gravestone[0] = Item(WHIP, 1).putAttr(ItemAttribute.DEATH_FEE_FREE, 1)
        player.gravestone[1] = Item(COINS, 50)
        player.attr[GRAVESTONE_TILE_ATTR] = Tile(3000, 3000, 0).as30BitInteger
        player.attr[GRAVESTONE_TICKS_ATTR] = 1

        assertTrue(Gravestone.tick(player, active = true))

        assertFalse(Gravestone.exists(player))
        assertTrue(player.gravestone.isEmpty)
        assertEquals(1, player.deathRecovery.getItemCount(WHIP))
        assertEquals(50, player.deathRecovery.getItemCount(COINS))
        assertTrue(DeathFees.isFeeFree(player.deathRecovery.rawItems.filterNotNull().first { it.id == WHIP }))
    }

    @Test
    fun `owner rule - a full Death's Office keeps the gravestone standing instead of deleting anything`() {
        val player = newPlayer()
        for (slot in 0 until player.deathRecovery.capacity) player.deathRecovery[slot] = Item(BRONZE_SWORD, 1)
        player.gravestone[0] = Item(WHIP, 1)
        player.attr[GRAVESTONE_TILE_ATTR] = Tile(3000, 3000, 0).as30BitInteger
        player.attr[GRAVESTONE_TICKS_ATTR] = 1

        assertFalse(Gravestone.tick(player, active = true))
        assertTrue(Gravestone.exists(player))
        assertEquals(1, player.gravestone.getItemCount(WHIP))

        player.deathRecovery[0] = null
        assertTrue(Gravestone.tick(player, active = true), "once there is room, it collapses into the office")
        assertEquals(1, player.deathRecovery.getItemCount(WHIP))
    }

    @Test
    fun `owner 2026-09-26 - taking items from the gravestone is free`() {
        val player = newPlayer()
        player.gravestone[0] = Item(WHIP, 1)
        player.gravestone[1] = Item(COINS, 500)
        player.attr[GRAVESTONE_TILE_ATTR] = Tile(3000, 3000, 0).as30BitInteger
        player.attr[DEATH_COFFER_ATTR] = 4_000
        player.bank[0] = Item(COINS, 10_000)

        val taken = Gravestone.takeAll(player)

        assertTrue(taken is GraveTakeOutcome.Taken)
        assertEquals(1, player.inventory.getItemCount(WHIP))
        assertEquals(500, player.inventory.getItemCount(COINS), "nothing is charged")
        assertEquals(4_000, player.attr[DEATH_COFFER_ATTR])
        assertEquals(10_000, player.bank.getItemCount(COINS))
        assertFalse(Gravestone.exists(player), "an emptied gravestone disappears")
    }

    @Test
    fun `bless - another player with 70 Prayer, once, adds min(60, points - 10) minutes`() {
        val owner = newPlayer()
        val blesser = newPlayer()
        Gravestone.deposit(owner, listOf(Item(WHIP, 1)), Tile(3100, 3100, 0), moveExisting = false, config = DeathsDomainConfig.OSRS)
        val before = Gravestone.ticksLeft(owner)
        every { blesser.skills.getMaxLevel(5) } returns 69
        assertEquals(Gravestone.PrayOutcome.LevelTooLow(70), Gravestone.bless(blesser, owner))
        every { blesser.skills.getMaxLevel(5) } returns 70
        every { blesser.getCurrentPrayerPoints() } returns 45
        assertEquals(Gravestone.PrayOutcome.OwnGrave, Gravestone.bless(owner, owner))

        assertEquals(Gravestone.PrayOutcome.Done(35), Gravestone.bless(blesser, owner))
        verify { blesser.alterPrayerPoints(-35) }
        assertEquals(before + 35 * 100, Gravestone.ticksLeft(owner), "the extra time lives in the persisted grave timer")
        assertEquals(Gravestone.PrayOutcome.AlreadyDone, Gravestone.bless(blesser, owner))
    }

    @Test
    fun `repair - Prayer 2, once, adds min(5, points) minutes, and a new gravestone can be blessed and repaired again`() {
        val owner = newPlayer()
        Gravestone.deposit(owner, listOf(Item(WHIP, 1)), Tile(3100, 3100, 0), moveExisting = false, config = DeathsDomainConfig.OSRS)
        val before = Gravestone.ticksLeft(owner)
        every { owner.skills.getMaxLevel(5) } returns 2
        every { owner.getCurrentPrayerPoints() } returns 3
        assertEquals(Gravestone.PrayOutcome.Done(3), Gravestone.repair(owner, owner))
        assertEquals(before + 300, Gravestone.ticksLeft(owner))
        assertEquals(Gravestone.PrayOutcome.AlreadyDone, Gravestone.repair(owner, owner))

        Gravestone.clear(owner)
        owner.gravestone[0] = null
        Gravestone.deposit(owner, listOf(Item(WHIP, 1)), Tile(3100, 3100, 0), moveExisting = false, config = DeathsDomainConfig.OSRS)
        assertEquals(Gravestone.PrayOutcome.Done(3), Gravestone.repair(owner, owner), "flags are per gravestone")
    }

    @Test
    fun `owner 2026-09-26 - Death's Office is unlimited`() {
        val player = newPlayer()
        for (i in 0 until 1_000) assertEquals(1, DeathsOffice.store(player, Item(BRONZE_SWORD, 1)))
        assertEquals(1_000, player.deathRecovery.getItemCount(BRONZE_SWORD))
        assertTrue(player.deathRecovery.capacity >= 4_000)
    }

    @Test
    fun `a full inventory leaves the item in the gravestone`() {
        val player = newPlayer()
        for (slot in 0 until 28) player.inventory[slot] = Item(BRONZE_SWORD, 1)
        player.gravestone[0] = Item(WHIP, 1)
        player.attr[GRAVESTONE_TILE_ATTR] = Tile(3000, 3000, 0).as30BitInteger
        assertEquals(GraveTakeOutcome.NoInventorySpace, Gravestone.take(player, 0))
        assertEquals(1, player.gravestone.getItemCount(WHIP))
    }

    // ---- Death's Office ----

    @Test
    fun `death's office charges its fee and hands back unmarked items`() {
        val player = newPlayer()
        val value = values(WHIP to 1_000_000L)
        DeathsOffice.store(player, Item(WHIP, 1))
        player.inventory[0] = Item(COINS, 60_000)

        val outcome = DeathsOffice.retrieve(player, 0, 1, value)

        assertTrue(outcome is OfficeRetrieveOutcome.Retrieved)
        assertEquals(50_000L, (outcome as OfficeRetrieveOutcome.Retrieved).fee)
        assertEquals(10_000, player.inventory.getItemCount(COINS))
        assertEquals(1, player.inventory.getItemCount(WHIP))
        assertTrue(DeathsOffice.isEmpty(player))
    }

    @Test
    fun `an unaffordable office fee takes nothing`() {
        val player = newPlayer()
        DeathsOffice.store(player, Item(WHIP, 1))
        val outcome = DeathsOffice.retrieve(player, 0, 1, values(WHIP to 1_000_000L))
        assertEquals(OfficeRetrieveOutcome.CannotAfford(50_000L), outcome)
        assertEquals(1, player.deathRecovery.getItemCount(WHIP))
        assertEquals(0, player.inventory.getItemCount(WHIP))
    }

    @Test
    fun `items saved before the rework as free stay free to take back`() {
        val player = newPlayer()
        DeathsOffice.store(player, Item(WHIP, 1), free = true)
        val outcome = DeathsOffice.retrieve(player, 0, 1, values(WHIP to 50_000_000L))
        assertEquals(0L, (outcome as OfficeRetrieveOutcome.Retrieved).fee)
    }

    @Test
    fun `legacy saves lose the deadline and a free batch stays free`() {
        val player = newPlayer()
        player.deathRecovery[0] = Item(WHIP, 1)
        player.attr[DEATH_RECOVERY_EXPIRY_ATTR] = 1L
        player.attr[DEATH_RECOVERY_FEE_ATTR] = 0

        DeathsOffice.migrateLegacy(player)

        assertEquals(1, player.deathRecovery.getItemCount(WHIP), "nothing is forfeited any more")
        assertNull(player.attr[DEATH_RECOVERY_EXPIRY_ATTR])
        assertNull(player.attr[DEATH_RECOVERY_FEE_ATTR])
        assertTrue(DeathFees.isFeeFree(player.deathRecovery[0]!!))
    }

    // ---- Death's Coffer ----

    @Test
    fun `the coffer takes items worth 10k or more at 105 percent`() {
        val player = newPlayer()
        // Tradeability comes from items.yml in the server (ItemMetadataService); unit tests only load the cache.
        DEFINITIONS.get(ItemDef::class.java, WHIP).tradeable = true
        player.inventory[0] = Item(WHIP, 1)
        val outcome = DeathCoffer.sacrifice(player, 0, 1, values(WHIP to 23_200L), DeathsDomainConfig.OSRS)
        // The OSRS news-post screenshot: a rune full helm worth 23,200 shows "24,360 coins".
        assertTrue(outcome is CofferOutcome.Sacrificed)
        assertEquals(24_360L, (outcome as CofferOutcome.Sacrificed).coins)
        assertEquals(WHIP, outcome.item.id)
        assertEquals(24_360, player.attr[DEATH_COFFER_ATTR])
        assertEquals(0, player.inventory.getItemCount(WHIP))
    }

    @Test
    fun `the coffer refuses cheap and untradeable items and never overflows`() {
        val player = newPlayer()
        DEFINITIONS.get(ItemDef::class.java, WHIP).tradeable = true
        DEFINITIONS.get(ItemDef::class.java, BRONZE_SWORD).tradeable = false
        player.inventory[0] = Item(WHIP, 1)
        assertEquals(CofferOutcome.Ineligible, DeathCoffer.sacrifice(player, 0, 1, values(WHIP to 9_999L), DeathsDomainConfig.OSRS))
        assertEquals(1, player.inventory.getItemCount(WHIP))
        player.inventory[1] = Item(BRONZE_SWORD, 1)
        assertEquals(CofferOutcome.Ineligible, DeathCoffer.sacrifice(player, 1, 1, values(BRONZE_SWORD to 1_000_000L), DeathsDomainConfig.OSRS), "untradeable")
        player.attr[DEATH_COFFER_ATTR] = Int.MAX_VALUE - 100
        assertEquals(CofferOutcome.Full, DeathCoffer.sacrifice(player, 0, 1, values(WHIP to 1_000_000L), DeathsDomainConfig.OSRS))
        assertEquals(1, player.inventory.getItemCount(WHIP))
    }

    @Test
    fun `payment never takes anything when the three sources together fall short`() {
        val player = newPlayer()
        player.attr[DEATH_COFFER_ATTR] = 10
        player.inventory[0] = Item(COINS, 10)
        player.bank[0] = Item(COINS, 10)
        assertFalse(DeathPayment.pay(player, 31))
        assertEquals(10, player.attr[DEATH_COFFER_ATTR])
        assertEquals(10, player.inventory.getItemCount(COINS))
        assertEquals(10, player.bank.getItemCount(COINS))
        assertTrue(DeathPayment.pay(player, 30))
        assertEquals(0L, DeathPayment.available(player))
    }

    private fun values(vararg prices: Pair<Int, Long>): ItemRiskValueProvider {
        val map = prices.toMap()
        return ItemRiskValueProvider { map[it] ?: 1L }
    }

    private fun newPlayer(): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        every { player.timers } returns TimerMap()
        every { player.tile } returns Tile(3200, 3200, 0)
        every { player.inventory } returns ItemContainer(DEFINITIONS, INVENTORY_KEY)
        every { player.equipment } returns ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        every { player.bank } returns ItemContainer(DEFINITIONS, BANK_KEY)
        every { player.deathRecovery } returns ItemContainer(DEFINITIONS, DEATH_RECOVERY_KEY)
        every { player.gravestone } returns ItemContainer(DEFINITIONS, GRAVESTONE_KEY)
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns DEFINITIONS
        every { player.world } returns world
        return player
    }

    companion object {
        private const val WHIP = 4151
        private const val COINS = 995
        private const val BRONZE_SWORD = 1277
        private const val BONES = 526
        private const val LOBSTER = 379

        private val DEFINITIONS = DefinitionSet()
        private lateinit var store: CacheLibrary

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            store = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            DEFINITIONS.loadAll(store)
            assertNotEquals(DEFINITIONS.getCount(ItemDef::class.java), 0)
            assertTrue(File(Paths.get("..", "..", "data", "cfg").toFile(), "deaths_domain.yml").isFile)
        }
    }
}
