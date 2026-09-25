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
import gg.rsmod.plugins.api.ext.isMulti
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
        extraPvpLoot: () -> List<Item> = { emptyList() },
    ): Boolean {
        val victim = result.victim
        if (victim.attr[DEATH_LOOT_RESOLVED_ATTR] == true) {
            return false
        }
        // Recovery is lazy-cleaned because players can be offline when its deadline passes.
        // Purge an old batch before simulating capacity, otherwise expired items could be
        // silently renewed by the next PvM death.
        DeathRecoveryService.expireIfNeeded(victim)
        victim.attr[DEATH_LOOT_RESOLVED_ATTR] = true

        // A looting bag is never protected. Detach its contents before the inventory mutation so
        // the bag item and its stored stacks follow one atomic death outcome.
        val bagContents =
            if (victim.inventory.contains(gg.rsmod.plugins.api.cfg.Items.LOOTING_BAG) ||
                victim.inventory.contains(gg.rsmod.plugins.api.cfg.Items.LOOTING_BAG_OPEN)
            ) {
                if (result.context == DeathContext.WILDERNESS_PVP) {
                    gg.rsmod.plugins.content.mechanics.pvp.LootingBag.pvpDeathContents(victim)
                } else {
                    gg.rsmod.plugins.content.mechanics.pvp.LootingBag.takeContents(victim)
                }
            } else {
                emptyList()
            }

        // For a PvM/safe death, capacity viability must be known *before* any
        // inventory/equipment slot is cleared - a lost stack that can't fit
        // into deathRecovery (e.g. a prior death's recovery batch is still
        // unreclaimed and near-full) must simply stay with the player rather
        // than being destroyed. Wilderness/PvP loot has no such constraint;
        // ground loot is uncapped, so every lost item is always removed.
        // RCV-012 decision 3b: loot keys never go to death recovery - "If a player dies a PvM death with loot keys in the inventory,
        // they are removed instead" - and on a PvP death LootKeys decides where they go.
        val lostKeys = result.itemRisk.lost.filter { gg.rsmod.plugins.content.mechanics.pvp.LootKeys.isKey(it.item.id) }
        val toRemove =
            when (result.context) {
                DeathContext.WILDERNESS_PVP -> result.itemRisk.lost
                DeathContext.PVM_SAFE -> partitionRecoverable(victim, result.itemRisk.lost - lostKeys.toSet()).fitsInRecovery + lostKeys
            }

        var removedEquipment = false
        // Deadman emblems actually taken from the victim by this death (only these may reach the killer - no dupes).
        val removedEmblemTiers = mutableListOf<Int>()
        for (slotItem in toRemove) {
            val container =
                when (slotItem.source) {
                    DeathContainerSource.INVENTORY -> victim.inventory
                    DeathContainerSource.EQUIPMENT -> victim.equipment
                }
            // Defends against acting on a stale slot if something else
            // mutated the container between resolve() and execute() - only
            // remove from the slot if it still holds the exact stack we resolved.
            // Owner 2026-09-18: a stack can be partially kept (3 of 1,000 coins), so remove the
            // lost AMOUNT and leave the kept remainder in place rather than clearing the slot.
            val current = container[slotItem.slot]
            if (current?.id == slotItem.item.id) {
                container[slotItem.slot] =
                    if (current.amount > slotItem.item.amount) Item(current, current.amount - slotItem.item.amount) else null
                if (slotItem.source == DeathContainerSource.EQUIPMENT) {
                    removedEquipment = true
                }
                val emblemTier = gg.rsmod.plugins.content.mechanics.pvp.emblem.DeadmanEmblem.tierOf(slotItem.item.id)
                if (emblemTier > 0) removedEmblemTiers += emblemTier
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

        if (result.context == DeathContext.WILDERNESS_PVP && result.killer != null) {
            gg.rsmod.plugins.content.mechanics.pvp.Killstreaks.onWildernessKill(result.killer, victim)
            // Grant once, after a resolved PvP death, not on a speculative/lethal combat hit.
            // The timer's HUD and attack gate already consume KillGrace; previously nothing
            // in production ever started it. Multi-combat kills do not earn protection.
            if (result.killer !== victim && !victim.tile.isMulti(world)) {
                gg.rsmod.plugins.content.mechanics.pvp.KillGrace.grant(result.killer, victim)
            }
        }

        when (result.context) {
            // Owner 2026-09-18 (#6): the lost stacks AND every converted killer-bound item (broken-item
            // repair coins, uncharged staves, quiver ammo, ornament kits ...) are handed to the loot-key
            // plan in ONE call, so a kill produces a loot key or ground loot - never both, unless the
            // killer already holds the maximum number of keys.
            DeathContext.WILDERNESS_PVP -> {
                val converted = extraPvpLoot() + bagContents
                // Deadman emblems never become loot-key or ground loot: DeadmanEmblem hands them to the killer.
                val loot = toRemove.filterNot { gg.rsmod.plugins.content.mechanics.pvp.emblem.DeadmanEmblem.isEmblem(it.item.id) }
                val keys = gg.rsmod.plugins.content.mechanics.pvp.LootKeys
                // The real value the killer takes (emblem excluded): lost stacks, converted loot, the looting bag's
                // contents and the loot stored behind the victim's lost keys. Read before the key plan clears them.
                val keyLoot = loot.filter { keys.isKey(it.item.id) }.flatMap { keys.slotItems(victim, keys.keyIndex(it.item.id)) }
                val stacks =
                    loot.filterNot { keys.isKey(it.item.id) || gg.rsmod.plugins.content.mechanics.pvp.LootingBag.isBag(it.item.id) }
                        .map { it.item }
                val risk = gg.rsmod.plugins.content.mechanics.pvp.emblem.DeadmanEmblem.riskValue(world, stacks + converted + keyLoot)
                gg.rsmod.plugins.content.mechanics.pvp.emblem.DeadmanEmblem.onPvpDeath(world, victim, result.killer, removedEmblemTiers, risk)
                spawnPvpLoot(world, result, loot, converted, logger)
            }
            DeathContext.PVM_SAFE -> {
                gg.rsmod.plugins.content.mechanics.pvp.LootKeys.removeKeys(victim, lostKeys.map { it.item.id })
                // The bag itself disappears completely; only its detached contents enter the
                // unprotected death outcome. It must never be recoverable as an item.
                val lostBags = toRemove.filter { gg.rsmod.plugins.content.mechanics.pvp.LootingBag.isBag(it.item.id) }.toSet()
                val recoverable = toRemove - lostKeys.toSet() - lostBags
                if (recoverable.isNotEmpty() || bagContents.isNotEmpty()) {
                    createDeathRecovery(victim, recoverable, recoveryConfig, logger, bagContents)
                }
            }
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
        converted: List<Item>,
        logger: LoggerService?,
    ) {
        val victim = result.victim
        // OSRS destroys the bag item itself; only its contents (already supplied through
        // [converted]) can become killer loot.
        val lostItems = lost.filterNot { gg.rsmod.plugins.content.mechanics.pvp.LootingBag.isBag(it.item.id) }.map { it.item } + converted
        if (lostItems.isEmpty()) return
        // No gravestone and no GP printing for Wilderness/PvP deaths - the
        // ground loot itself is the entire PK reward, unless loot keys take it (RCV-012 decision 3b).
        val groundItems = gg.rsmod.plugins.content.mechanics.pvp.LootKeys.onWildernessPvpDeath(world, victim, result.killer, lostItems)
        groundItems.forEach { item ->
            world.spawn(GroundItem(Item(item), victim.tile, result.killer))
        }
        logger?.logDeathLootTransfer(victim, result.killer, lostItems)
    }

    private fun createDeathRecovery(
        victim: Player,
        lost: List<DeathSlotItem>,
        recoveryConfig: DeathRecoveryConfig,
        logger: LoggerService?,
        extra: List<Item> = emptyList(),
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
        extra.forEach { item ->
            victim.deathRecovery.add(item.id, item.amount, assureFullInsertion = false)
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
