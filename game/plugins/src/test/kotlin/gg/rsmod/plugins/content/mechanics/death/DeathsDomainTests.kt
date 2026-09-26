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
 * nothing is ever deleted (a full office keeps the gravestone standing), familiar cargo is free, food and potions stay in
 * the gravestone. Every expected number is the OSRS Wiki / OSRS clientscript one (see [DeathFees]).
 */
class DeathsDomainTests {
    // ---- fees ----

    @Test
    fun `gravestone fee per unit follows the OSRS tiers`() {
        val c = DeathsDomainConfig.OSRS
        assertEquals(0, DeathFees.graveUnitFee(99_999L, c))
        assertEquals(1_000, DeathFees.graveUnitFee(100_000L, c))
        assertEquals(1_000, DeathFees.graveUnitFee(999_999L, c))
        assertEquals(10_000, DeathFees.graveUnitFee(1_000_000L, c))
        assertEquals(10_000, DeathFees.graveUnitFee(9_999_999L, c))
        assertEquals(100_000, DeathFees.graveUnitFee(10_000_000L, c))
        assertEquals(100_000, DeathFees.graveUnitFee(2_000_000_000L, c))
    }

    @Test
    fun `gravestone fee multiplies by the stack and is capped at 500k`() {
        val c = DeathsDomainConfig.OSRS
        assertEquals(3_000L, DeathFees.graveStackFee(Item(WHIP, 3), values(WHIP to 200_000L), c))
        assertEquals(500_000, DeathFees.graveFee(List(6) { Item(WHIP, 1) }, values(WHIP to 50_000_000L), c))
        assertEquals(0L, DeathFees.graveStackFee(Item(WHIP, 1).putAttr(ItemAttribute.DEATH_FEE_FREE, 1), values(WHIP to 50_000_000L), c))
    }

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
    fun `a collapsing gravestone sends everything to Death's Office, paid items stay free`() {
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
    fun `locked items need the fee, unlocking pays coffer first then coins then bank`() {
        val player = newPlayer()
        val value = values(WHIP to 2_000_000L)
        player.gravestone[0] = Item(WHIP, 1)
        player.attr[GRAVESTONE_TILE_ATTR] = Tile(3000, 3000, 0).as30BitInteger
        player.attr[DEATH_COFFER_ATTR] = 4_000
        player.inventory[0] = Item(COINS, 3_000)
        player.bank[0] = Item(COINS, 10_000)

        assertEquals(GraveTakeOutcome.Locked, Gravestone.take(player, 0, value))
        assertEquals(10_000, Gravestone.fee(player, value), "1m-10m: 10,000 coins")
        assertTrue(Gravestone.unlock(player, value))
        assertEquals(0, player.attr[DEATH_COFFER_ATTR])
        assertEquals(0, player.inventory.getItemCount(COINS))
        assertEquals(7_000, player.bank.getItemCount(COINS))
        assertEquals(0, Gravestone.fee(player, value), "Fee: Paid")

        val taken = Gravestone.take(player, 0, value)
        assertTrue(taken is GraveTakeOutcome.Taken)
        assertEquals(1, player.inventory.getItemCount(WHIP))
        assertNull(player.inventory.rawItems.filterNotNull().first { it.id == WHIP }.getAttr(ItemAttribute.DEATH_FEE_FREE), "the office mark never leaves")
        assertFalse(Gravestone.exists(player), "an emptied gravestone disappears")
    }

    @Test
    fun `an unaffordable unlock changes nothing`() {
        val player = newPlayer()
        val value = values(WHIP to 20_000_000L)
        player.gravestone[0] = Item(WHIP, 1)
        player.attr[GRAVESTONE_TILE_ATTR] = Tile(3000, 3000, 0).as30BitInteger
        player.inventory[0] = Item(COINS, 99_999)

        assertFalse(Gravestone.unlock(player, value))
        assertEquals(99_999, player.inventory.getItemCount(COINS))
        assertEquals(1, player.gravestone.getItemCount(WHIP))
    }

    @Test
    fun `a full inventory leaves the item in the gravestone`() {
        val player = newPlayer()
        for (slot in 0 until 28) player.inventory[slot] = Item(BRONZE_SWORD, 1)
        player.gravestone[0] = Item(WHIP, 1)
        player.attr[GRAVESTONE_TILE_ATTR] = Tile(3000, 3000, 0).as30BitInteger
        assertEquals(GraveTakeOutcome.NoInventorySpace, Gravestone.take(player, 0, values()))
        assertEquals(1, player.gravestone.getItemCount(WHIP))
    }

    @Test
    fun `only items behind the fee can be discarded`() {
        val player = newPlayer()
        val value = values(WHIP to 2_000_000L)
        player.gravestone[0] = Item(WHIP, 1)
        player.gravestone[1] = Item(BRONZE_SWORD, 1)
        player.attr[GRAVESTONE_TILE_ATTR] = Tile(3000, 3000, 0).as30BitInteger
        assertNull(Gravestone.discard(player, 1, value), "a free item is never destroyed")
        assertEquals(WHIP, Gravestone.discard(player, 0, value)?.id)
        assertEquals(0, Gravestone.fee(player, value))
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
    fun `familiar cargo is free to take back`() {
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
