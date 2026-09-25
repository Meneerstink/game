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
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.game.service.log.LoggerService
import gg.rsmod.plugins.api.ext.refreshBonuses
import gg.rsmod.plugins.content.mechanics.pvp.BEST_KILLSTREAK_ATTR
import gg.rsmod.plugins.content.mechanics.pvp.KillGrace
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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
        // Deadman (owner 2026-09-17): every kill produces a loot key by default; this test covers
        // the ground-loot path a killer gets after asking Skully to switch the keys off.
        killer.attr[gg.rsmod.plugins.content.mechanics.pvp.LootKeys.ENABLED] = false
        val world = mockk<World>(relaxed = true)
        every { world.getMultiCombatChunks() } returns emptySet()
        every { world.getMultiCombatRegions() } returns emptySet()

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
        // Audit D-06: a no-risk kill (this fixture) is not a valid PK, so it earns no kill grace.
        assertFalse(KillGrace.isProtected(killer), "an invalid (low-risk) kill must not start kill grace")
        assertFalse(DeathExecutor.execute(world, result, DeathRecoveryConfig.PLACEHOLDER))
        assertNull(victim.inventory[0], "lost item's slot must be cleared")
        assertNotNull(victim.inventory[1], "kept item must remain")
        assertTrue(victim.deathRecovery.isEmpty, "PvP deaths must not use the recovery container")
        // Audit X-12: the ground loot is queued to appear after the death animation, exactly once.
        verify(exactly = 1) { world.queue(any()) }
        verify(exactly = 0) { world.spawn(any<GroundItem>()) }
    }

    @Test
    fun `multi-combat kill never grants grace while still counting the PvP death`() {
        val victim = newPlayer()
        val killer = newPlayer()
        killer.attr[BEST_KILLSTREAK_ATTR] = 5
        val world = mockk<World>(relaxed = true)
        every { world.getMultiCombatChunks() } returns emptySet()
        every { world.getMultiCombatRegions() } returns setOf(victim.tile.regionId)
        val result = DeathResolutionResult(
            DeathContext.WILDERNESS_PVP,
            victim,
            killer,
            DeathItemRiskResult(protectedItemCount = 0, protected = emptyList(), lost = emptyList()),
        )

        assertTrue(DeathExecutor.execute(world, result, DeathRecoveryConfig.PLACEHOLDER))
        assertFalse(KillGrace.isProtected(killer))
        assertFalse(DeathExecutor.execute(world, result, DeathRecoveryConfig.PLACEHOLDER))
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
        // Audit X-12: ground loot is queued once, to appear after the death animation.
        verify(exactly = 1) { world.queue(any()) }
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
    fun `PvM death that would exceed recovery capacity drops the overflow as the victim's own ground item`() {
        // Audit X-03: a full recovery container no longer lets the overflow stay with the player.
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

        assertTrue(DeathExecutor.execute(world, result, DeathRecoveryConfig.PLACEHOLDER))
        assertNull(victim.inventory[0], "the lost stack leaves the player even when recovery is full")
        assertEquals(0, victim.deathRecovery.getItemCount(LOST_ITEM))
        assertTrue(victim.deathRecovery.isFull, "the pre-existing recovery contents must be untouched")
        assertEquals(1, spawnedItems.count { it.item == LOST_ITEM && it.amount == 5 }, "the overflow is dropped once")
    }

    @Test
    fun `PvM death overflow from equipment is removed and dropped too`() {
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

        // Audit X-03: the equipped item now leaves the player, so the bonuses are refreshed; that needs a live
        // client (varcs, interface text), which this mock player does not have.
        mockkStatic(PLAYER_EXT)
        try {
            every { victim.refreshBonuses() } just Runs
            DeathExecutor.execute(world, result, DeathRecoveryConfig.PLACEHOLDER)
        } finally {
            unmockkStatic(PLAYER_EXT)
        }

        assertNull(victim.equipment[0])
        assertEquals(1, spawnedItems.count { it.item == LOST_ITEM && it.amount == 1 }, "the equipped overflow is dropped once")
    }

    @Test
    fun `repeated execute for an overflowed PvM death does not drop the item twice`() {
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
        assertEquals(0, victim.inventory.getItemCount(LOST_ITEM))
        assertEquals(1, spawnedItems.size, "a second execute() must not drop the item again")
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
        // Audit D-09: the fee follows the value waiting in recovery (Death's Office tiers), never below the config fee.
        val value = gg.rsmod.plugins.content.mechanics.pvp.LootKeys.value(DEFINITIONS, listOf(Item(LOST_ITEM, 5)))
        val fee = maxOf(250, DeathRecoveryConfig.feeFor(value))
        assertEquals(fee, victim.attr[DEATH_RECOVERY_FEE_ATTR])
        verify(exactly = 1) { logger.logDeathRecoveryCreated(victim, 1, expiry, fee) }
    }

    @Test
    fun `a new PvM death purges expired recovery before checking capacity`() {
        val victim = newPlayer()
        val world = mockk<World>(relaxed = true)
        victim.deathRecovery[0] = Item(FILLER_ITEM, 1)
        victim.attr[DEATH_RECOVERY_EXPIRY_ATTR] = System.currentTimeMillis() - 1
        victim.attr[DEATH_RECOVERY_FEE_ATTR] = 250
        victim.inventory[0] = Item(LOST_ITEM, 1)

        val result =
            DeathResolutionResult(
                DeathContext.PVM_SAFE,
                victim,
                null,
                DeathItemRiskResult(
                    protectedItemCount = 0,
                    protected = emptyList(),
                    lost = listOf(DeathSlotItem(DeathContainerSource.INVENTORY, 0, Item(LOST_ITEM, 1))),
                ),
            )

        assertTrue(DeathExecutor.execute(world, result, DeathRecoveryConfig.PLACEHOLDER))
        assertEquals(0, victim.deathRecovery.getItemCount(FILLER_ITEM), "expired recovery must not consume capacity")
        assertEquals(1, victim.deathRecovery.getItemCount(LOST_ITEM), "new loss must enter fresh recovery")
        assertTrue((victim.attr[DEATH_RECOVERY_EXPIRY_ATTR] ?: 0L) > System.currentTimeMillis())
    }

    /** Every ground item spawned through a [newPlayer]'s world in the current test (JUnit: one instance per test). */
    private val spawnedItems = mutableListOf<GroundItem>()

    private fun newPlayer(): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        every { player.timers } returns TimerMap()
        every { player.tile } returns Tile(3200, 3200, 0)
        every { player.inventory } returns ItemContainer(DEFINITIONS, INVENTORY_KEY)
        every { player.equipment } returns ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        every { player.deathRecovery } returns ItemContainer(DEFINITIONS, DEATH_RECOVERY_KEY)
        // Audit D-09: the recovery fee values the lost items through the victim's world definitions. Ground items the
        // victim's world spawns are collected in [spawnedItems]: a mockk verify through victim.world would also count
        // every getWorld() call (spawn + fee lookup), so the drops are counted directly instead.
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns DEFINITIONS
        every { world.spawn(any<GroundItem>()) } answers { spawnedItems.add(firstArg()) }
        every { player.world } returns world
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
        private const val PLAYER_EXT = "gg.rsmod.plugins.api.ext.PlayerExtKt"
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
