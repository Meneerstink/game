package gg.rsmod.plugins.content.mechanics.death

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.DEATH_RECOVERY_EXPIRY_ATTR
import gg.rsmod.game.model.attr.DEATH_RECOVERY_FEE_ATTR
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.service.log.LoggerService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regression tests for [DeathExecutor]: exactly-once ground-loot generation
 * for Wilderness/PvP deaths, exactly-once (non-duplicating) execution when
 * called repeatedly for the same death, and correct PvM death-recovery state
 * creation.
 *
 * Item slots are set directly (`container[slot] = item`) rather than through
 * `ItemContainer.add`, so fixture setup is independent of any particular
 * item's stackability. The real cache-backed [DefinitionSet] is only needed
 * because [DeathExecutor]'s PvM path itself calls `ItemContainer.add`, which
 * requires a loaded [ItemDef] to decide whether an item stacks.
 */
class DeathExecutorTests {
    @Test
    fun `PvP death spawns lost items as killer-owned ground loot exactly once`() {
        val victim = newPlayer()
        val killer = mockk<Player>(relaxed = true)
        val world = mockk<World>(relaxed = true)

        victim.inventory[0] = Item(LOST_ITEM, 3)
        victim.inventory[1] = Item(KEPT_ITEM, 1)

        val itemRisk =
            DeathItemRiskResult(
                protectedItemCount = 1,
                protected = listOf(DeathSlotItem(DeathContainerSource.INVENTORY, 1, Item(KEPT_ITEM, 1))),
                lost = listOf(DeathSlotItem(DeathContainerSource.INVENTORY, 0, Item(LOST_ITEM, 3))),
            )
        val result = DeathResolutionResult(DeathContext.WILDERNESS_PVP, victim, killer, itemRisk)

        val executed = DeathExecutor.execute(world, result, DeathRecoveryConfig.PLACEHOLDER)

        assertTrue(executed)
        assertNull(victim.inventory[0], "lost item's slot must be cleared")
        assertNotNull(victim.inventory[1], "kept item must remain")
        assertTrue(victim.deathRecovery.isEmpty, "PvP deaths must not use the recovery container")
        verify(exactly = 1) {
            world.spawn(match<GroundItem> { it.item == LOST_ITEM && it.amount == 3 })
        }
    }

    @Test
    fun `repeated execute for the same death does not spawn loot twice`() {
        val victim = newPlayer()
        val world = mockk<World>(relaxed = true)
        victim.inventory[0] = Item(LOST_ITEM, 1)

        val itemRisk =
            DeathItemRiskResult(
                protectedItemCount = 0,
                protected = emptyList(),
                lost = listOf(DeathSlotItem(DeathContainerSource.INVENTORY, 0, Item(LOST_ITEM, 1))),
            )
        val result = DeathResolutionResult(DeathContext.WILDERNESS_PVP, victim, null, itemRisk)

        val first = DeathExecutor.execute(world, result, DeathRecoveryConfig.PLACEHOLDER)
        val second = DeathExecutor.execute(world, result, DeathRecoveryConfig.PLACEHOLDER)

        assertTrue(first)
        assertFalse(second, "a second execute() for the same death must be a no-op")
        verify(exactly = 1) { world.spawn(any<GroundItem>()) }
    }

    @Test
    fun `PvM death moves lost items into death-recovery and sets expiry and fee`() {
        val victim = newPlayer()
        val world = mockk<World>(relaxed = true)
        val logger = mockk<LoggerService>(relaxed = true)
        victim.inventory[0] = Item(LOST_ITEM, 5)

        val itemRisk =
            DeathItemRiskResult(
                protectedItemCount = 0,
                protected = emptyList(),
                lost = listOf(DeathSlotItem(DeathContainerSource.INVENTORY, 0, Item(LOST_ITEM, 5))),
            )
        val result = DeathResolutionResult(DeathContext.PVM_SAFE, victim, null, itemRisk)
        val config = DeathRecoveryConfig(recoveryDurationMs = 60_000L, reclaimFee = 250)

        val before = System.currentTimeMillis()
        val executed = DeathExecutor.execute(world, result, config, logger)
        val after = System.currentTimeMillis()

        assertTrue(executed)
        assertNull(victim.inventory[0], "lost item must be removed from inventory")
        assertEquals(5, victim.deathRecovery.getItemCount(LOST_ITEM), "full stack must be moved into recovery")
        verify(exactly = 0) { world.spawn(any<GroundItem>()) }

        val expiry = victim.attr[DEATH_RECOVERY_EXPIRY_ATTR]
        assertNotNull(expiry)
        assertTrue(expiry in (before + config.recoveryDurationMs)..(after + config.recoveryDurationMs))
        assertEquals(250, victim.attr[DEATH_RECOVERY_FEE_ATTR])
        verify(exactly = 1) { logger.logDeathRecoveryCreated(victim, 1, expiry, 250) }
    }

    private fun newPlayer(): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        every { player.tile } returns Tile(3200, 3200, 0)
        every { player.inventory } returns ItemContainer(DEFINITIONS, INVENTORY_KEY)
        every { player.equipment } returns ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        every { player.deathRecovery } returns ItemContainer(DEFINITIONS, DEATH_RECOVERY_KEY)
        return player
    }

    companion object {
        private const val LOST_ITEM = 4151
        private const val KEPT_ITEM = 995

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
