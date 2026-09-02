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
import gg.rsmod.plugins.content.mechanics.pvp.BEST_KILLSTREAK_ATTR
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
        // The killer needs a real AttributeMap like the victim: a Wilderness PvP death runs
        // through Killstreaks.onWildernessKill, which reads the killer's killstreak and PK-point
        // attributes as Ints, and a relaxed mock hands back a plain Object there.
        val killer = newPlayer()
        // Seeded above the streak this kill produces so the leaderboard's best-streak branch,
        // which writes data/killstreak_leaderboard.txt, stays out of a unit test.
        killer.attr[BEST_KILLSTREAK_ATTR] = 5
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
    fun `PvM death recovers an item that exactly fills the last free recovery slot`() {
        val victim = newPlayer()
        val world = mockk<World>(relaxed = true)
        fillDeathRecovery(victim, slots = 0 until 41, itemId = FILLER_ITEM)
        victim.inventory[0] = Item(LOST_ITEM, 1)

        val itemRisk =
            DeathItemRiskResult(
                protectedItemCount = 0,
                protected = emptyList(),
                lost = listOf(DeathSlotItem(DeathContainerSource.INVENTORY, 0, Item(LOST_ITEM, 1))),
            )
        val result = DeathResolutionResult(DeathContext.PVM_SAFE, victim, null, itemRisk)

        val executed = DeathExecutor.execute(world, result, DeathRecoveryConfig.PLACEHOLDER)

        assertTrue(executed)
        assertNull(victim.inventory[0], "item must be removed once it fits in the last free slot")
        assertEquals(1, victim.deathRecovery.getItemCount(LOST_ITEM))
        assertTrue(victim.deathRecovery.isFull, "recovery must now be exactly full, not overflowed")
    }

    @Test
    fun `PvM death that would exceed recovery capacity keeps the overflow item on the player instead of losing it`() {
        val victim = newPlayer()
        val world = mockk<World>(relaxed = true)
        fillDeathRecovery(victim, slots = 0 until 42, itemId = FILLER_ITEM)
        victim.inventory[0] = Item(LOST_ITEM, 5)

        val itemRisk =
            DeathItemRiskResult(
                protectedItemCount = 0,
                protected = emptyList(),
                lost = listOf(DeathSlotItem(DeathContainerSource.INVENTORY, 0, Item(LOST_ITEM, 5))),
            )
        val result = DeathResolutionResult(DeathContext.PVM_SAFE, victim, null, itemRisk)

        val executed = DeathExecutor.execute(world, result, DeathRecoveryConfig.PLACEHOLDER)

        assertTrue(executed, "the death itself still resolves even though this one stack couldn't be recovered")
        val keptItem = victim.inventory[0]
        assertNotNull(keptItem, "overflowing stack must never be destroyed")
        assertEquals(LOST_ITEM, keptItem.id)
        assertEquals(5, keptItem.amount, "overflowing stack must stay exactly as it was")
        assertEquals(0, victim.deathRecovery.getItemCount(LOST_ITEM), "overflow item must never enter recovery")
        assertTrue(victim.deathRecovery.isFull, "the pre-existing recovery contents must be untouched")
        verify(exactly = 0) { world.spawn(any<GroundItem>()) }
    }

    @Test
    fun `PvM death overflow does not partially mutate the inventory slot it couldn't recover`() {
        val victim = newPlayer()
        val world = mockk<World>(relaxed = true)
        fillDeathRecovery(victim, slots = 0 until 42, itemId = FILLER_ITEM)
        val original = Item(LOST_ITEM, 5)
        victim.inventory[0] = original

        val itemRisk =
            DeathItemRiskResult(
                protectedItemCount = 0,
                protected = emptyList(),
                lost = listOf(DeathSlotItem(DeathContainerSource.INVENTORY, 0, original)),
            )
        val result = DeathResolutionResult(DeathContext.PVM_SAFE, victim, null, itemRisk)

        DeathExecutor.execute(world, result, DeathRecoveryConfig.PLACEHOLDER)

        val afterSlot = victim.inventory[0]
        assertNotNull(afterSlot, "the slot must not be left cleared with the item lost nowhere")
        assertEquals(LOST_ITEM, afterSlot.id)
        assertEquals(5, afterSlot.amount, "amount must not be partially reduced")
    }

    @Test
    fun `PvM death overflow does not partially mutate the equipment slot it couldn't recover`() {
        val victim = newPlayer()
        val world = mockk<World>(relaxed = true)
        fillDeathRecovery(victim, slots = 0 until 42, itemId = FILLER_ITEM)
        val original = Item(LOST_ITEM, 1)
        victim.equipment[0] = original

        val itemRisk =
            DeathItemRiskResult(
                protectedItemCount = 0,
                protected = emptyList(),
                lost = listOf(DeathSlotItem(DeathContainerSource.EQUIPMENT, 0, original)),
            )
        val result = DeathResolutionResult(DeathContext.PVM_SAFE, victim, null, itemRisk)

        DeathExecutor.execute(world, result, DeathRecoveryConfig.PLACEHOLDER)

        val afterSlot = victim.equipment[0]
        assertNotNull(afterSlot, "the equipped item must not be removed if it can't be recovered")
        assertEquals(LOST_ITEM, afterSlot.id)
        assertEquals(1, afterSlot.amount)
    }

    @Test
    fun `repeated execute for an overflowed PvM death does not duplicate the kept-back item`() {
        val victim = newPlayer()
        val world = mockk<World>(relaxed = true)
        fillDeathRecovery(victim, slots = 0 until 42, itemId = FILLER_ITEM)
        victim.inventory[0] = Item(LOST_ITEM, 1)

        val itemRisk =
            DeathItemRiskResult(
                protectedItemCount = 0,
                protected = emptyList(),
                lost = listOf(DeathSlotItem(DeathContainerSource.INVENTORY, 0, Item(LOST_ITEM, 1))),
            )
        val result = DeathResolutionResult(DeathContext.PVM_SAFE, victim, null, itemRisk)

        val first = DeathExecutor.execute(world, result, DeathRecoveryConfig.PLACEHOLDER)
        val second = DeathExecutor.execute(world, result, DeathRecoveryConfig.PLACEHOLDER)

        assertTrue(first)
        assertFalse(second, "a second execute() for the same death must be a no-op")
        assertEquals(1, victim.inventory.getItemCount(LOST_ITEM), "kept-back item must not be duplicated")
        assertEquals(0, victim.deathRecovery.getItemCount(LOST_ITEM))
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

    /**
     * Pre-fills [player]'s death-recovery container directly by slot (not via
     * `add`), simulating an unreclaimed prior death's recovery batch, so a
     * new death's capacity-viability check has to contend with a
     * near-full/full container. [itemId] is non-stackable, so each slot holds
     * its own independent stack regardless of how many slots are filled.
     */
    private fun fillDeathRecovery(
        player: Player,
        slots: IntRange,
        itemId: Int,
    ) {
        for (slot in slots) {
            player.deathRecovery[slot] = Item(itemId, 1)
        }
    }

    companion object {
        private const val LOST_ITEM = 4151
        private const val KEPT_ITEM = 995

        /** A distinct non-stackable item used only to occupy recovery slots as filler. */
        private const val FILLER_ITEM = 1277

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
