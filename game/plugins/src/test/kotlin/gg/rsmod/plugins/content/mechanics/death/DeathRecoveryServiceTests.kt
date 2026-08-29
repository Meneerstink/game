package gg.rsmod.plugins.content.mechanics.death

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.DEATH_RECOVERY_EXPIRY_ATTR
import gg.rsmod.game.model.attr.DEATH_RECOVERY_FEE_ATTR
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Regression tests for [DeathRecoveryService.reclaim]: exactly-once
 * successful reclaim, no partial mutation when the fee can't be afforded, and
 * non-duplicating behavior when called again after a successful reclaim.
 *
 * Uses a real cache-backed [DefinitionSet] because [DeathRecoveryService]
 * itself calls `ItemContainer.add`/`remove`, which require a loaded [ItemDef]
 * to decide whether an item stacks.
 */
class DeathRecoveryServiceTests {
    @Test
    fun `reclaim moves items back and charges the fee exactly once`() {
        val player = newPlayer()
        player.deathRecovery[0] = Item(RECOVERED_ITEM, 3)
        player.inventory[0] = Item(Items.COINS_995, 100)
        player.attr[DEATH_RECOVERY_FEE_ATTR] = 40
        player.attr[DEATH_RECOVERY_EXPIRY_ATTR] = 123L

        val outcome = DeathRecoveryService.reclaim(player)

        check(outcome is DeathReclaimOutcome.Reclaimed)
        assertEquals(1, outcome.itemCount)
        assertEquals(40, outcome.feePaid)
        assertEquals(60, player.inventory.getItemCount(Items.COINS_995), "fee must be deducted exactly once")
        assertEquals(3, player.inventory.getItemCount(RECOVERED_ITEM), "recovered stack must land in inventory")
        assertTrue(player.deathRecovery.isEmpty)
        assertFalse(player.attr.has(DEATH_RECOVERY_EXPIRY_ATTR), "expiry must be cleared once fully reclaimed")
        assertFalse(player.attr.has(DEATH_RECOVERY_FEE_ATTR), "fee marker must be cleared once fully reclaimed")
    }

    @Test
    fun `insufficient funds leaves death-recovery and inventory untouched`() {
        val player = newPlayer()
        player.deathRecovery[0] = Item(RECOVERED_ITEM, 3)
        player.inventory[0] = Item(Items.COINS_995, 10)
        player.attr[DEATH_RECOVERY_FEE_ATTR] = 40

        val outcome = DeathRecoveryService.reclaim(player)

        assertEquals(DeathReclaimOutcome.InsufficientFunds, outcome)
        assertEquals(10, player.inventory.getItemCount(Items.COINS_995), "no coins may be deducted")
        assertEquals(3, player.deathRecovery.getItemCount(RECOVERED_ITEM), "recovered items must stay put")
        assertEquals(0, player.inventory.getItemCount(RECOVERED_ITEM))
    }

    @Test
    fun `reclaiming again after a full reclaim is a no-op`() {
        val player = newPlayer()
        player.deathRecovery[0] = Item(RECOVERED_ITEM, 1)
        player.inventory[0] = Item(Items.COINS_995, 100)
        player.attr[DEATH_RECOVERY_FEE_ATTR] = 40

        val first = DeathRecoveryService.reclaim(player)
        check(first is DeathReclaimOutcome.Reclaimed)

        val second = DeathRecoveryService.reclaim(player)

        assertEquals(DeathReclaimOutcome.NothingToReclaim, second)
        assertEquals(60, player.inventory.getItemCount(Items.COINS_995), "second call must not charge another fee")
        assertEquals(1, player.inventory.getItemCount(RECOVERED_ITEM), "item must not be duplicated")
    }

    private fun newPlayer(): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        every { player.inventory } returns ItemContainer(DEFINITIONS, INVENTORY_KEY)
        every { player.equipment } returns ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        every { player.deathRecovery } returns ItemContainer(DEFINITIONS, DEATH_RECOVERY_KEY)
        return player
    }

    companion object {
        private const val RECOVERED_ITEM = 1277

        private val DEFINITIONS = DefinitionSet()
        private lateinit var store: CacheLibrary

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            store = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            DEFINITIONS.loadAll(store)
            assertNotEquals(DEFINITIONS.getCount(ItemDef::class.java), 0)
        }
    }
}
