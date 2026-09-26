package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.model.attr.DEATH_RECOVERY_EXPIRY_ATTR
import gg.rsmod.game.model.attr.DEATH_RECOVERY_FEE_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.ext.addPreservingAttr

/** The outcome of taking items back from Death. */
sealed class OfficeRetrieveOutcome {
    object NothingThere : OfficeRetrieveOutcome()

    object NoInventorySpace : OfficeRetrieveOutcome()

    data class CannotAfford(val fee: Long) : OfficeRetrieveOutcome()

    data class Retrieved(val item: Item, val fee: Long) : OfficeRetrieveOutcome()
}

/**
 * Death's Office storage ([Player.deathRecovery], OSRS "Death's Office Item Retrieval", 120 slots). Death keeps items here
 * without a time limit (OSRS Wiki "Death's Office"); taking them back costs the office fee ([DeathFees.officeUnitFee]),
 * paid through [DeathPayment]. Owner decision 2026-09-26: the office never deletes anything - when it is full, whatever
 * cannot come in simply stays where it is (a collapsing gravestone keeps standing).
 */
object DeathsOffice {
    fun isEmpty(player: Player): Boolean = player.deathRecovery.isEmpty

    fun usedSlots(player: Player): Int = player.deathRecovery.capacity - player.deathRecovery.freeSlotCount

    /**
     * Puts [item] in Death's Office; [free] marks it fee-free (familiar cargo, or gravestone items whose fee was paid).
     * @return the amount that went in; the caller keeps the rest.
     */
    fun store(
        player: Player,
        item: Item,
        free: Boolean = false,
    ): Int {
        val stored = Item(item.id, item.amount).copyAttr(item)
        if (free) stored.putAttr(ItemAttribute.DEATH_FEE_FREE, 1)
        return DeathStorage.insert(player.world.definitions, player.deathRecovery, stored)
    }

    /** How many units of [item] the office can take now. */
    fun room(
        player: Player,
        item: Item,
    ): Int = DeathStorage.room(player.world.definitions, player.deathRecovery, item)

    /**
     * Takes [amount] of the stack in [slot] back into the inventory, charging the office fee first. Only as much as fits in
     * the inventory is taken and charged for.
     */
    fun retrieve(
        player: Player,
        slot: Int,
        amount: Int,
        value: ItemRiskValueProvider,
    ): OfficeRetrieveOutcome {
        if (slot !in 0 until player.deathRecovery.capacity) return OfficeRetrieveOutcome.NothingThere
        val stored = player.deathRecovery[slot] ?: return OfficeRetrieveOutcome.NothingThere
        val handed = handedBack(stored)
        val fits = inventoryRoom(player, handed, minOf(amount, stored.amount))
        if (fits <= 0) return OfficeRetrieveOutcome.NoInventorySpace
        val fee = DeathFees.officeFee(stored, fits, value)
        if (!DeathPayment.pay(player, fee)) return OfficeRetrieveOutcome.CannotAfford(fee)
        val taken = DeathStorage.takeFromSlot(player.deathRecovery, slot, fits) ?: return OfficeRetrieveOutcome.NothingThere
        val back = handedBack(taken)
        val added = player.inventory.addPreservingAttr(back, assureFullInsertion = false).completed
        if (added < back.amount) {
            // The inventory changed between the room check and the insert: the remainder goes straight back to Death.
            store(player, Item(taken, back.amount - added).copyAttr(taken))
        }
        return OfficeRetrieveOutcome.Retrieved(Item(back.id, added), fee)
    }

    /** The item as it leaves Death: the office-only fee-free mark is removed so it stacks and trades normally again. */
    fun handedBack(item: Item): Item = Item(item.id, item.amount).copyAttr(item).removeAttr(ItemAttribute.DEATH_FEE_FREE)

    /** Units of [item] (already stripped of office marks) the inventory can take, up to [wanted]. */
    fun inventoryRoom(
        player: Player,
        item: Item,
        wanted: Int,
    ): Int {
        val definitions = player.world.definitions
        return if (DeathStorage.stackable(definitions, item.id) && !item.hasAnyAttr()) {
            val existing = player.inventory.getItemCount(item.id)
            when {
                existing > 0 -> minOf(wanted.toLong(), Int.MAX_VALUE.toLong() - existing).toInt()
                player.inventory.freeSlotCount > 0 -> wanted
                else -> 0
            }
        } else if (DeathStorage.stackable(definitions, item.id)) {
            if (player.inventory.freeSlotCount > 0) wanted else 0
        } else {
            minOf(wanted, player.inventory.freeSlotCount)
        }
    }

    /**
     * Saves from before 2026-09-26 kept a 15-minute deadline and one flat fee for the whole batch. Death's Office has no
     * deadline, and a batch that was free (familiar cargo, or a death whose flat fee was 0) stays free.
     */
    fun migrateLegacy(player: Player) {
        val legacyFee = player.attr[DEATH_RECOVERY_FEE_ATTR]
        if (legacyFee != null && legacyFee <= 0) {
            for (slot in 0 until player.deathRecovery.capacity) {
                player.deathRecovery[slot]?.putAttr(ItemAttribute.DEATH_FEE_FREE, 1)
            }
        }
        player.attr.remove(DEATH_RECOVERY_FEE_ATTR)
        player.attr.remove(DEATH_RECOVERY_EXPIRY_ATTR)
    }
}
