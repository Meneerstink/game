package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.DEATH_LOOT_RESOLVED_ATTR
import gg.rsmod.game.model.attr.DEATH_RECOVERY_EXPIRY_ATTR
import gg.rsmod.game.model.attr.DEATH_RECOVERY_FEE_ATTR
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

        var removedEquipment = false
        for (slotItem in result.itemRisk.lost) {
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
            lostItemCount = result.itemRisk.lost.size,
        )

        if (result.itemRisk.lost.isEmpty()) {
            return true
        }

        when (result.context) {
            DeathContext.WILDERNESS_PVP -> spawnPvpLoot(world, result, logger)
            DeathContext.PVM_SAFE -> createDeathRecovery(victim, result, recoveryConfig, logger)
        }
        return true
    }

    private fun spawnPvpLoot(
        world: World,
        result: DeathResolutionResult,
        logger: LoggerService?,
    ) {
        val victim = result.victim
        val lostItems = result.itemRisk.lost.map { it.item }
        // No gravestone and no GP printing for Wilderness/PvP deaths - the
        // ground loot itself is the entire PK reward.
        lostItems.forEach { item ->
            world.spawn(GroundItem(Item(item), victim.tile, result.killer))
        }
        logger?.logDeathLootTransfer(victim, result.killer, lostItems)
    }

    private fun createDeathRecovery(
        victim: Player,
        result: DeathResolutionResult,
        recoveryConfig: DeathRecoveryConfig,
        logger: LoggerService?,
    ) {
        result.itemRisk.lost.forEach { slotItem ->
            victim.deathRecovery.add(slotItem.item.id, slotItem.item.amount, assureFullInsertion = false)
        }
        // A player who dies again before reclaiming a prior batch has their
        // new losses merged additively into the same shared deathRecovery
        // container, and the expiry/fee below is overwritten for the whole
        // batch. This avoids silently losing the earlier batch's items, but
        // is a simplification versus a fully faithful multi-gravestone
        // system - see the milestone report's known limitations.
        val expiresAt = System.currentTimeMillis() + recoveryConfig.recoveryDurationMs
        victim.attr[DEATH_RECOVERY_EXPIRY_ATTR] = expiresAt
        victim.attr[DEATH_RECOVERY_FEE_ATTR] = recoveryConfig.reclaimFee
        logger?.logDeathRecoveryCreated(
            player = victim,
            itemCount = result.itemRisk.lost.size,
            expiresAtMs = expiresAt,
            reclaimFee = recoveryConfig.reclaimFee,
        )
    }
}
