package gg.rsmod.plugins.content.mechanics.trouver

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.filterableMessage

/**
 * Generic Trouver-parchment item-locking engine (`RSPS_DECISIONS.md` 2026-09-02 "STANDING OWNER
 * AUTHORIZATION" - Trouver parchment explicitly approved, built generically). Real OSRS mechanic
 * (added 2019, item id 24187 - see `RSPS_IMPORT_MANIFEST.yml`): pay 500,000 coins plus a Trouver
 * parchment to the NPC Perdu to lock one untradeable item at a time; a locked ("(l)") item
 * survives destruction it would otherwise suffer on an unprotected Wilderness death.
 *
 * Deliberately data-driven via [TrouverRegistry] rather than hard-coding any specific item, so a
 * future lockable item (once its real ids exist in this cache) is added with one registry entry
 * and no changes here.
 *
 * Death (owner 2026-09-26, OSRS Wiki "Trouver parchment"): a locked item is no longer kept whole. It takes part in the
 * normal keep-1 ranking; when it is lost on a PvP death it becomes broken (level 20 and below) or mangled (above 20), stays
 * with the victim and keeps its lock, and the killer receives the repair fee - see
 * [gg.rsmod.plugins.content.mechanics.death.UntradeableDeathProtection]. The old server-original compensation (100 % of the
 * item's value to the killer) is gone.
 *
 * Correction 2026-09-16: an earlier version of this doc claimed Perdu was absent from this cache
 * and fronted both lock/unlock through a Trouver parchment used directly on the item plus a
 * `::trouverunlock` stopgap command. That was wrong - Perdu (upstream OSRS npc 7456) was already a
 * real import (RCV-012 "ferox" batch, local npc id 14394) and now hosts both interactions properly
 * at the Grand Exchange (`grand_exchange_hub.plugin.kts`): locking via `Trouver parchment` used on
 * the eligible item still triggers [lock] (`trouver.plugin.kts`), and unlocking runs by using the
 * locked item on Perdu directly ([unlock]); `::trouverunlock` remains only as a backup path.
 */
object Trouver {
    const val LOCK_FEE = 500_000
    const val UNLOCK_REFUND_PERCENT = 95

    sealed class LockResult {
        object Success : LockResult()
        object NotLockable : LockResult()
        object ItemNotHeld : LockResult()
        object MissingParchment : LockResult()
        object InsufficientFunds : LockResult()
    }

    sealed class UnlockResult {
        object Success : UnlockResult()
        object NotLocked : UnlockResult()
        object ItemNotHeld : UnlockResult()
        object InventoryFull : UnlockResult()
    }

    /**
     * Locks [item] for [player]: consumes 1 Trouver parchment plus [LOCK_FEE] coins and swaps the
     * base item for its registered locked variant. Every precondition is checked before anything
     * is mutated, so a failure is always a clean no-op.
     */
    fun lock(
        player: Player,
        item: Item,
    ): LockResult {
        // Captured before any removal: [item] is commonly an alias of the actual slot in
        // [player]'s inventory (the real callers read it straight off `player.inventory.items`),
        // and `ItemContainer.remove` mutates a matched slot's `Item.amount` in place down to 0 -
        // re-reading `item.amount` after removing would then request 0 of everything below.
        val amount = item.amount
        val lockable = TrouverRegistry.lockableFor(item.id) ?: return LockResult.NotLockable
        if (player.inventory.getItemCount(item.id) < amount) return LockResult.ItemNotHeld
        if (player.inventory.getItemCount(Items.TROUVER_PARCHMENT) < 1) return LockResult.MissingParchment
        if (player.inventory.getItemCount(Items.COINS_995) < LOCK_FEE) return LockResult.InsufficientFunds

        player.inventory.remove(item.id, amount, assureFullRemoval = true)
        player.inventory.remove(Items.TROUVER_PARCHMENT, 1, assureFullRemoval = true)
        player.inventory.remove(Items.COINS_995, LOCK_FEE, assureFullRemoval = true)
        player.inventory.add(lockable.lockedItemId, amount, assureFullInsertion = true)
        // Locking is a state change on the same item (e.g. a Dizana's quiver's charges and stored
        // ammo), not a fresh one - carry [item]'s attributes onto whichever slot the fresh add landed in.
        if (item.hasAnyAttr()) {
            val slot = player.inventory.items.indexOfFirst { it?.id == lockable.lockedItemId }
            if (slot != -1) player.inventory[slot] = Item(lockable.lockedItemId, amount).copyAttr(item)
        }
        player.filterableMessage(
            "You lock your item with a Trouver parchment. It can no longer be destroyed on death.",
        )
        return LockResult.Success
    }

    /**
     * Unlocks [lockedItem] for [player]: removes the locked variant, refunds a fresh Trouver
     * parchment plus [UNLOCK_REFUND_PERCENT]% of [LOCK_FEE], and restores the base item. Rolls
     * back everything it already granted if the base item ultimately doesn't fit, so a failed
     * unlock never leaves the player with a duplicated or missing item.
     */
    fun unlock(
        player: Player,
        lockedItem: Item,
    ): UnlockResult {
        // See lock()'s doc: captured before any removal, since [lockedItem] is commonly the real
        // aliased inventory slot and `remove` mutates a matched slot's `Item.amount` to 0 in place.
        val amount = lockedItem.amount
        val lockable = TrouverRegistry.entryForLocked(lockedItem.id) ?: return UnlockResult.NotLocked
        if (player.inventory.getItemCount(lockedItem.id) < amount) return UnlockResult.ItemNotHeld

        val refund = (LOCK_FEE.toLong() * UNLOCK_REFUND_PERCENT / 100).toInt()

        player.inventory.remove(lockedItem.id, amount, assureFullRemoval = true)

        val parchmentGrant = player.inventory.add(Items.TROUVER_PARCHMENT, 1, assureFullInsertion = true)
        val coinGrant =
            if (parchmentGrant.hasSucceeded()) {
                player.inventory.add(Items.COINS_995, refund, assureFullInsertion = true)
            } else {
                null
            }
        val baseGrant =
            if (parchmentGrant.hasSucceeded() && coinGrant?.hasSucceeded() == true) {
                player.inventory.add(lockable.baseItemId, amount, assureFullInsertion = true)
            } else {
                null
            }

        if (baseGrant?.hasSucceeded() == true) {
            // Symmetric to lock(): the unlocked item keeps whatever state (charges, stored ammo) the
            // locked one carried.
            if (lockedItem.hasAnyAttr()) {
                val slot = player.inventory.items.indexOfFirst { it?.id == lockable.baseItemId }
                if (slot != -1) player.inventory[slot] = Item(lockable.baseItemId, amount).copyAttr(lockedItem)
            }
            player.filterableMessage("You unlock your item. Your Trouver parchment and $refund coins are returned.")
            return UnlockResult.Success
        }

        // Roll back: undo whatever partially succeeded and restore the locked item exactly as it was.
        if (coinGrant?.hasSucceeded() == true) player.inventory.remove(Items.COINS_995, refund, assureFullRemoval = true)
        if (parchmentGrant.hasSucceeded()) player.inventory.remove(Items.TROUVER_PARCHMENT, 1, assureFullRemoval = true)
        player.inventory.add(lockedItem.id, amount, assureFullInsertion = true)
        player.filterableMessage("You don't have enough inventory space to unlock that item.")
        return UnlockResult.InventoryFull
    }
}
