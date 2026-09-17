package gg.rsmod.plugins.content.mechanics.trouver

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.mechanics.death.DeathContainerSource
import gg.rsmod.plugins.content.mechanics.death.DeathContext
import gg.rsmod.plugins.content.mechanics.death.DeathItemRiskResult
import gg.rsmod.plugins.content.mechanics.death.DeathResolutionResult
import gg.rsmod.plugins.content.mechanics.death.DeathSlotItem
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Coverage for [Trouver]: locking/unlocking an eligible item, death protection, and killer
 * compensation. Uses this module's real, cache-backed [DefinitionSet] (see companion object,
 * mirroring `DeathExecutorTests`/`ContainerExtTests`) so `player.inventory.add`/`remove` exercise
 * the real stack-check codepath against this server's actual item defs - including items 22323/
 * 22324, the Trouver-pipeline clones this test's fixtures rely on.
 */
class TrouverTests {
    @After
    fun clearRegistry() {
        TrouverRegistry.clear()
    }

    @Test
    fun `locking a registered item consumes the item, a parchment and the fee, and grants the locked variant`() {
        TrouverRegistry.register(TrouverLockable(Items.FIRE_CAPE, Items.FIRE_CAPE_LOCKED_22324))
        val player = newPlayer()
        player.inventory.add(Items.FIRE_CAPE, 1)
        player.inventory.add(Items.TROUVER_PARCHMENT, 1)
        player.inventory.add(Items.COINS_995, Trouver.LOCK_FEE)

        val result = Trouver.lock(player, Item(Items.FIRE_CAPE, 1))

        assertEquals(Trouver.LockResult.Success, result)
        assertEquals(0, player.inventory.getItemCount(Items.FIRE_CAPE))
        assertEquals(0, player.inventory.getItemCount(Items.TROUVER_PARCHMENT))
        assertEquals(0, player.inventory.getItemCount(Items.COINS_995))
        assertEquals(1, player.inventory.getItemCount(Items.FIRE_CAPE_LOCKED_22324))
    }

    @Test
    fun `locking with the real aliased inventory item (as the item-on-item handler passes it) still works`() {
        // Regression: ItemContainer.remove mutates a matched slot's Item.amount to 0 in place, so a lock()
        // that re-read item.amount after removing used to request 0 of the locked variant and everything below.
        TrouverRegistry.register(TrouverLockable(Items.FIRE_CAPE, Items.FIRE_CAPE_LOCKED_22324))
        val player = newPlayer()
        player.inventory.add(Items.FIRE_CAPE, 1)
        player.inventory.add(Items.TROUVER_PARCHMENT, 1)
        player.inventory.add(Items.COINS_995, Trouver.LOCK_FEE)
        val aliased = player.inventory.items.filterNotNull().first { it.id == Items.FIRE_CAPE }

        assertEquals(Trouver.LockResult.Success, Trouver.lock(player, aliased))

        assertEquals(1, player.inventory.getItemCount(Items.FIRE_CAPE_LOCKED_22324))
    }

    @Test
    fun `locking an unregistered item is rejected without touching the inventory`() {
        val player = newPlayer()
        player.inventory.add(Items.FIRE_CAPE, 1)
        player.inventory.add(Items.TROUVER_PARCHMENT, 1)
        player.inventory.add(Items.COINS_995, Trouver.LOCK_FEE)

        val result = Trouver.lock(player, Item(Items.FIRE_CAPE, 1))

        assertEquals(Trouver.LockResult.NotLockable, result)
        assertEquals(1, player.inventory.getItemCount(Items.FIRE_CAPE))
    }

    @Test
    fun `locking without holding the item is rejected`() {
        TrouverRegistry.register(TrouverLockable(Items.FIRE_CAPE, Items.FIRE_CAPE_LOCKED_22324))
        val player = newPlayer()
        player.inventory.add(Items.TROUVER_PARCHMENT, 1)
        player.inventory.add(Items.COINS_995, Trouver.LOCK_FEE)

        val result = Trouver.lock(player, Item(Items.FIRE_CAPE, 1))

        assertEquals(Trouver.LockResult.ItemNotHeld, result)
    }

    @Test
    fun `locking without a parchment is rejected without consuming coins`() {
        TrouverRegistry.register(TrouverLockable(Items.FIRE_CAPE, Items.FIRE_CAPE_LOCKED_22324))
        val player = newPlayer()
        player.inventory.add(Items.FIRE_CAPE, 1)
        player.inventory.add(Items.COINS_995, Trouver.LOCK_FEE)

        val result = Trouver.lock(player, Item(Items.FIRE_CAPE, 1))

        assertEquals(Trouver.LockResult.MissingParchment, result)
        assertEquals(Trouver.LOCK_FEE, player.inventory.getItemCount(Items.COINS_995))
    }

    @Test
    fun `locking without enough coins is rejected`() {
        TrouverRegistry.register(TrouverLockable(Items.FIRE_CAPE, Items.FIRE_CAPE_LOCKED_22324))
        val player = newPlayer()
        player.inventory.add(Items.FIRE_CAPE, 1)
        player.inventory.add(Items.TROUVER_PARCHMENT, 1)
        player.inventory.add(Items.COINS_995, Trouver.LOCK_FEE - 1)

        val result = Trouver.lock(player, Item(Items.FIRE_CAPE, 1))

        assertEquals(Trouver.LockResult.InsufficientFunds, result)
        assertEquals(1, player.inventory.getItemCount(Items.FIRE_CAPE))
    }

    @Test
    fun `unlocking a locked item refunds a parchment, a partial fee, and the base item`() {
        TrouverRegistry.register(TrouverLockable(Items.FIRE_CAPE, Items.FIRE_CAPE_LOCKED_22324))
        val player = newPlayer()
        player.inventory.add(Items.FIRE_CAPE_LOCKED_22324, 1)

        val result = Trouver.unlock(player, Item(Items.FIRE_CAPE_LOCKED_22324, 1))

        val expectedRefund = Trouver.LOCK_FEE.toLong() * Trouver.UNLOCK_REFUND_PERCENT / 100
        assertEquals(Trouver.UnlockResult.Success, result)
        assertEquals(0, player.inventory.getItemCount(Items.FIRE_CAPE_LOCKED_22324))
        assertEquals(1, player.inventory.getItemCount(Items.FIRE_CAPE))
        assertEquals(1, player.inventory.getItemCount(Items.TROUVER_PARCHMENT))
        assertEquals(expectedRefund.toInt(), player.inventory.getItemCount(Items.COINS_995))
    }

    @Test
    fun `locking and unlocking round-trip an item's attributes, such as a Dizana's quiver's charges`() {
        TrouverRegistry.register(TrouverLockable(Items.DIZANAS_QUIVER, Items.DIZANAS_QUIVER_L))
        val player = newPlayer()
        val quiver = gg.rsmod.plugins.content.items.osrs.DizanasQuiver.charge(Item(Items.DIZANAS_QUIVER), 500).result
        player.inventory.add(quiver)
        player.inventory.add(Items.TROUVER_PARCHMENT, 1)
        player.inventory.add(Items.COINS_995, Trouver.LOCK_FEE)

        assertEquals(Trouver.LockResult.Success, Trouver.lock(player, quiver))
        val locked = player.inventory.items.filterNotNull().first { it.id == Items.DIZANAS_QUIVER_L }
        assertEquals(500, gg.rsmod.plugins.content.items.osrs.DizanasQuiver.charges(locked), "charges survive locking")

        // Unlock with the REAL aliased inventory reference (as `trouverunlock` and the lock item-on-item
        // handler both do) - regression for a real bug: ItemContainer.remove mutates a matched slot's
        // Item.amount to 0 in place, so re-reading amount after removing used to request 0 of everything.
        assertEquals(Trouver.UnlockResult.Success, Trouver.unlock(player, locked))
        val unlocked = player.inventory.items.filterNotNull().first { it.id == Items.DIZANAS_QUIVER }
        assertEquals(500, gg.rsmod.plugins.content.items.osrs.DizanasQuiver.charges(unlocked), "charges survive unlocking")
    }

    @Test
    fun `unlocking an item that isn't a registered locked variant is rejected`() {
        val player = newPlayer()
        player.inventory.add(Items.FIRE_CAPE_LOCKED_22324, 1)

        val result = Trouver.unlock(player, Item(Items.FIRE_CAPE_LOCKED_22324, 1))

        assertEquals(Trouver.UnlockResult.NotLocked, result)
    }

    @Test
    fun `unlocking without holding the locked item is rejected`() {
        TrouverRegistry.register(TrouverLockable(Items.FIRE_CAPE, Items.FIRE_CAPE_LOCKED_22324))
        val player = newPlayer()

        val result = Trouver.unlock(player, Item(Items.FIRE_CAPE_LOCKED_22324, 1))

        assertEquals(Trouver.UnlockResult.ItemNotHeld, result)
    }

    @Test
    fun `unlocking with no free inventory space rolls back and keeps the locked item`() {
        TrouverRegistry.register(TrouverLockable(Items.FIRE_CAPE, Items.FIRE_CAPE_LOCKED_22324))
        val player = newPlayer()
        player.inventory.add(Items.FIRE_CAPE_LOCKED_22324, 1)
        // Fill every remaining slot with distinct non-stackable items so nothing else fits.
        for (slot in 1 until player.inventory.capacity) {
            player.inventory[slot] = Item(FILLER_ITEM_BASE + slot, 1)
        }

        val result = Trouver.unlock(player, Item(Items.FIRE_CAPE_LOCKED_22324, 1))

        assertEquals(Trouver.UnlockResult.InventoryFull, result)
        assertEquals(1, player.inventory.getItemCount(Items.FIRE_CAPE_LOCKED_22324))
        assertEquals(0, player.inventory.getItemCount(Items.TROUVER_PARCHMENT))
        assertEquals(0, player.inventory.getItemCount(Items.COINS_995))
    }

    @Test
    fun `protectedFromDeath is true only for a registered locked id`() {
        TrouverRegistry.register(TrouverLockable(Items.FIRE_CAPE, Items.FIRE_CAPE_LOCKED_22324))

        assertTrue(Trouver.protectedFromDeath(Items.FIRE_CAPE_LOCKED_22324))
        assertFalse(Trouver.protectedFromDeath(Items.FIRE_CAPE))
    }

    @Test
    fun `killer compensation pays out for a protected locked item in a Wilderness death`() {
        TrouverRegistry.register(TrouverLockable(Items.FIRE_CAPE, Items.FIRE_CAPE_LOCKED_22324))
        val killer = newPlayer()
        val result = deathResult(
            context = DeathContext.WILDERNESS_PVP,
            killer = killer,
            protected = listOf(slotItem(Items.FIRE_CAPE_LOCKED_22324, 1)),
        )

        val granted = Trouver.grantKillerCompensation(result) { itemId -> if (itemId == Items.FIRE_CAPE) 1000L else 0L }

        assertEquals(1000L, granted)
        assertEquals(1000, killer.inventory.getItemCount(Items.COINS_995))
    }

    @Test
    fun `killer compensation is a no-op outside a Wilderness death`() {
        TrouverRegistry.register(TrouverLockable(Items.FIRE_CAPE, Items.FIRE_CAPE_LOCKED_22324))
        val killer = newPlayer()
        val result = deathResult(
            context = DeathContext.PVM_SAFE,
            killer = killer,
            protected = listOf(slotItem(Items.FIRE_CAPE_LOCKED_22324, 1)),
        )

        val granted = Trouver.grantKillerCompensation(result) { 1000L }

        assertEquals(0L, granted)
        assertEquals(0, killer.inventory.getItemCount(Items.COINS_995))
    }

    @Test
    fun `killer compensation is a no-op without a killer`() {
        TrouverRegistry.register(TrouverLockable(Items.FIRE_CAPE, Items.FIRE_CAPE_LOCKED_22324))
        val result = deathResult(
            context = DeathContext.WILDERNESS_PVP,
            killer = null,
            protected = listOf(slotItem(Items.FIRE_CAPE_LOCKED_22324, 1)),
        )

        val granted = Trouver.grantKillerCompensation(result) { 1000L }

        assertEquals(0L, granted)
    }

    @Test
    fun `killer compensation ignores protected items that aren't Trouver-locked`() {
        val killer = newPlayer()
        val result = deathResult(
            context = DeathContext.WILDERNESS_PVP,
            killer = killer,
            protected = listOf(slotItem(Items.COINS_995, 1)),
        )

        val granted = Trouver.grantKillerCompensation(result) { 1000L }

        assertEquals(0L, granted)
        assertEquals(0, killer.inventory.getItemCount(Items.COINS_995))
    }

    private fun deathResult(
        context: DeathContext,
        killer: Player?,
        protected: List<DeathSlotItem>,
    ): DeathResolutionResult {
        val victim = newPlayer()
        return DeathResolutionResult(
            context = context,
            victim = victim,
            killer = killer,
            itemRisk = DeathItemRiskResult(protectedItemCount = protected.size, protected = protected, lost = emptyList()),
        )
    }

    private fun slotItem(
        itemId: Int,
        amount: Int,
    ): DeathSlotItem = DeathSlotItem(source = DeathContainerSource.INVENTORY, slot = 0, item = Item(itemId, amount))

    private fun newPlayer(): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        every { player.inventory } returns ItemContainer(DEFINITIONS, INVENTORY_KEY)
        return player
    }

    companion object {
        /** Base id for synthetic filler items used to fill an inventory in the rollback test. */
        private const val FILLER_ITEM_BASE = 1

        private val DEFINITIONS = DefinitionSet()
        private lateinit var store: CacheLibrary

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            store = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            DEFINITIONS.loadAll(store)
            assertNotEquals(0, DEFINITIONS.getCount(ItemDef::class.java))
        }
    }
}
