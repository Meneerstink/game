package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.DEATH_LOOT_RESOLVED_ATTR
import gg.rsmod.game.model.attr.DEATH_RECOVERY_EXPIRY_ATTR
import gg.rsmod.game.model.attr.DEATH_RECOVERY_FEE_ATTR
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.service.log.LoggerService
import gg.rsmod.plugins.api.ext.refreshBonuses

/**
 * Mutates player/world state to carry out an already-computed
 * [DeathResolutionResult]: removes lost item stacks from inventory/equipment
 * exactly once, then either spawns them as killer-owned ground loot
 * ([DeathContext.WILDERNESS_PVP]) or moves them into the victim's
 * [Player.deathRecovery] container pending reclaim ([DeathContext.PVM_SAFE]).
 *
 * Performs no ranking/selection logic of its own - `DeathResolver` computes
 * the full outcome first, and this object only carries out the removal and
 * transfer that outcome implies. This split, plus the guard below, means the
 * inventory is never partially mutated before the full result is known.
 */
object DeathExecutor {
    /**
     * @return
     * `true` if this call actually performed the loot/recovery transfer,
     * `false` if it was a no-op because [result]'s victim already had loot
     * resolved for this death (see [gg.rsmod.game.model.attr.DEATH_LOOT_RESOLVED_ATTR]).
     * This guard exists in addition to the death-flow's own re-entrancy
     * guard ([gg.rsmod.game.model.attr.DEATH_FLAG] in
     * [gg.rsmod.game.action.PlayerDeathAction]) so that repeated calls to
     * this function - for whatever reason - can never generate ground loot
     * or recovery state twice for the same death.
     */
    fun execute(
        world: World,
        result: DeathResolutionResult,
        recoveryConfig: DeathRecoveryConfig,
        logger: LoggerService? = null,
    ): Boolean {
        val victim = result.victim
        if (victim.attr[DEATH_LOOT_RESOLVED_ATTR] == true) {
            return false
        }
        victim.attr[DEATH_LOOT_RESOLVED_ATTR] = true

        // For a PvM/safe death, capacity viability must be known *before* any
        // inventory/equipment slot is cleared - a lost stack that can't fit
        // into deathRecovery (e.g. a prior death's recovery batch is still
        // unreclaimed and near-full) must simply stay with the player rather
        // than being destroyed. Wilderness/PvP loot has no such constraint;
        // ground loot is uncapped, so every lost item is always removed.
        val toRemove =
            when (result.context) {
                DeathContext.WILDERNESS_PVP -> result.itemRisk.lost
                DeathContext.PVM_SAFE -> partitionRecoverable(victim, result.itemRisk.lost).fitsInRecovery
            }

        var removedEquipment = false
        for (slotItem in toRemove) {
            val container =
                when (slotItem.source) {
                    DeathContainerSource.INVENTORY -> victim.inventory
                    DeathContainerSource.EQUIPMENT -> victim.equipment
                }
            // Defends against acting on a stale slot if something else
            // mutated the container between resolve() and execute() - only
            // remove the slot if it still holds the exact stack we resolved.
            if (container[slotItem.slot]?.id == slotItem.item.id) {
                container[slotItem.slot] = null
                if (slotItem.source == DeathContainerSource.EQUIPMENT) {
                    removedEquipment = true
                }
            }
        }
        if (removedEquipment) {
            victim.refreshBonuses()
        }

        logger?.logPlayerDeath(
            player = victim,
            killer = result.killer,
            context = result.context.name,
            protectedItemCount = result.itemRisk.protectedItemCount,
            lostItemCount = toRemove.size,
        )

        if (toRemove.isEmpty()) {
            return true
        }

        when (result.context) {
            DeathContext.WILDERNESS_PVP -> spawnPvpLoot(world, result, toRemove, logger)
            DeathContext.PVM_SAFE -> createDeathRecovery(victim, toRemove, recoveryConfig, logger)
        }
        return true
    }

    /**
     * Splits [lost] into the stacks that [victim]'s current
     * [Player.deathRecovery] contents actually have room for versus those
     * that don't, without mutating [victim]'s real container. Simulated
     * against a defensive copy (via [ItemContainer]'s copy constructor) using
     * the container's own `add(assureFullInsertion = true)` semantics, so
     * stacking/slot behavior exactly matches what the real transfer will do
     * and no stack is ever partially split between the two lists.
     *
     * Items are tried in [lost]'s order and the simulation carries forward
     * between items, so a stackable item merging into an earlier lost stack
     * of the same id (or an existing recovery stack) frees no extra slot,
     * while one oversized/non-stacking stack that doesn't fit does not block
     * later, smaller stacks from still being recovered.
     */
    private fun partitionRecoverable(
        victim: Player,
        lost: List<DeathSlotItem>,
    ): RecoveryFitResult {
        val simulated = ItemContainer(victim.deathRecovery)
        val fits = mutableListOf<DeathSlotItem>()
        val overflow = mutableListOf<DeathSlotItem>()
        for (slotItem in lost) {
            val transaction = simulated.add(slotItem.item.id, slotItem.item.amount, assureFullInsertion = true)
            if (transaction.hasSucceeded()) {
                fits.add(slotItem)
            } else {
                overflow.add(slotItem)
            }
        }
        return RecoveryFitResult(fits, overflow)
    }

    private data class RecoveryFitResult(
        val fitsInRecovery: List<DeathSlotItem>,
        val overflow: List<DeathSlotItem>,
    )

    private fun spawnPvpLoot(
        world: World,
        result: DeathResolutionResult,
        lost: List<DeathSlotItem>,
        logger: LoggerService?,
    ) {
        val victim = result.victim
        val lostItems = lost.map { it.item }
        // No gravestone and no GP printing for Wilderness/PvP deaths - the
        // ground loot itself is the entire PK reward.
        lostItems.forEach { item ->
            world.spawn(GroundItem(Item(item), victim.tile, result.killer))
        }
        logger?.logDeathLootTransfer(victim, result.killer, lostItems)
    }

    private fun createDeathRecovery(
        victim: Player,
        lost: List<DeathSlotItem>,
        recoveryConfig: DeathRecoveryConfig,
        logger: LoggerService?,
    ) {
        lost.forEach { slotItem ->
            // assureFullInsertion = true: partitionRecoverable() already
            // proved this exact stack fits against the real container's
            // current (pre-mutation) contents, and no other code path mutates
            // deathRecovery between that check and this call, so this can
            // never fail - but requiring full insertion here (rather than
            // best-effort) means a stack is never silently split if that
            // invariant is ever violated by a future change.
            victim.deathRecovery.add(slotItem.item.id, slotItem.item.amount, assureFullInsertion = true)
        }
        // A player who dies again before reclaiming a prior batch has their
        // new losses merged additively into the same shared deathRecovery
        // container, and the expiry/fee below is overwritten for the whole
        // batch (now correctly extending/refreshing the whole batch's
        // deadline rather than risking an already-shorter expiry). Any lost
        // stack that doesn't fit is left with the player instead of being
        // destroyed - see partitionRecoverable(). This is a simplification
        // versus a fully faithful multi-gravestone system - see the
        // milestone report's known limitations.
        val expiresAt = System.currentTimeMillis() + recoveryConfig.recoveryDurationMs
        victim.attr[DEATH_RECOVERY_EXPIRY_ATTR] = expiresAt
        victim.attr[DEATH_RECOVERY_FEE_ATTR] = recoveryConfig.reclaimFee
        logger?.logDeathRecoveryCreated(
            player = victim,
            itemCount = lost.size,
            expiresAtMs = expiresAt,
            reclaimFee = recoveryConfig.reclaimFee,
        )
    }
}
