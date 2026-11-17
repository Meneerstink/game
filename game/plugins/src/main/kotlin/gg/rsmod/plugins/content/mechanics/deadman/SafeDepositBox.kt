package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.action.EquipAction
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.SAFE_DEPOSIT_BOX_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.ext.persistNow
import gg.rsmod.plugins.content.mechanics.pvp.PvpSkull
import gg.rsmod.plugins.content.mechanics.removed.RemovedItems

/**
 * Financial Wizard AND Financial Seer use this one account container, registered in core before save loading.
 * OSRS Financial Wizard rev 15364116 / Seer rev 15390113; Transcript:Financial Wizard rev 14868949.
 * DMM2 approval 2026-11-16: ten single tradeable items, no stacks, no effects, no death loss; blocked while skulled.
 *
 * Claude owns the 230/232 UI and NPC bindings. After validating the actual NPC interaction, call beginAccess;
 * buttons must additionally validate the open UI and their server-known slots/ids. Call endAccess on close.
 * Never expose the mutable container to a UI handler. A skull acquired after opening is checked on EVERY operation.
 * All transfers run synchronously on the game thread: inventory and box persist in the same player-save snapshot.
 */
object SafeDepositBox {
    const val SKULL_MESSAGE = "You cannot access your safe deposit box whilst you are skulled!"
    private val ACCESS = AttributeKey<Boolean>(resetOnDeath = true)

    enum class Status { OK, SKULLED, NOT_OPEN, UNAVAILABLE, INVALID_SLOT, INVALID_AMOUNT, ITEM_CHANGED, UNTRADEABLE, BOX_FULL, INVENTORY_FULL }

    data class Transfer(val status: Status, val moved: Int = 0)

    fun beginAccess(player: Player): Status {
        endAccess(player)
        if (PvpSkull.isSkulled(player)) return Status.SKULLED
        if (player.isDead()) return Status.UNAVAILABLE
        player.attr[ACCESS] = true
        return Status.OK
    }

    fun endAccess(player: Player) {
        player.attr.remove(ACCESS)
    }

    private fun accessStatus(player: Player): Status {
        if (PvpSkull.isSkulled(player)) {
            endAccess(player)
            return Status.SKULLED
        }
        if (player.isDead()) {
            endAccess(player)
            return Status.UNAVAILABLE
        }
        return if (player.attr[ACCESS] == true) Status.OK else Status.NOT_OPEN
    }

    private fun container(player: Player): ItemContainer = player.containers.getOrPut(SAFE_DEPOSIT_BOX_KEY) {
        ItemContainer(player.world.definitions, SAFE_DEPOSIT_BOX_KEY)
    }

    /** A deep copy for display; mutating a UI snapshot cannot modify the stored items/charges. */
    fun contents(player: Player): List<Item?>? {
        if (accessStatus(player) != Status.OK) return null
        return container(player).map { it?.let(::Item) }
    }

    /** Amounts refer to physical units, including coins and banknotes: ten coins use all ten slots. */
    fun deposit(player: Player, slot: Int, expectedId: Int, amount: Int = 1): Transfer {
        return depositFrom(player, player.inventory, slot, expectedId, amount)
    }

    /** UI230:11 is Deposit worn items, not a second inventory button (cache script1152). */
    fun depositWorn(player: Player, slot: Int, expectedId: Int, amount: Int): Transfer {
        val result = depositFrom(player, player.equipment, slot, expectedId, amount)
        if (result.moved > 0 && player.equipment[slot] == null) EquipAction.onItemUnequip(player, expectedId, slot)
        return result
    }

    private fun depositFrom(player: Player, inventory: ItemContainer, slot: Int, expectedId: Int, amount: Int): Transfer {
        val access = accessStatus(player)
        if (access != Status.OK) return Transfer(access)
        if (amount <= 0) return Transfer(Status.INVALID_AMOUNT)
        if (slot !in 0 until inventory.capacity) return Transfer(Status.INVALID_SLOT)
        val item = inventory[slot] ?: return Transfer(Status.ITEM_CHANGED)
        if (item.id != expectedId || item.amount <= 0) return Transfer(Status.ITEM_CHANGED)
        val base = item.toUnnoted(player.world.definitions).getDef(player.world.definitions)
        if (!base.tradeable || RemovedItems.isUnobtainable(base)) return Transfer(Status.UNTRADEABLE)
        val box = container(player)
        val count = minOf(amount, item.amount, box.freeSlotCount)
        if (count == 0) return Transfer(Status.BOX_FULL)
        // Prebuild every copy before changing either container. Never use add(Item): it discards attributes.
        val slots = box.rawItems.indices.filter { box[it] == null }.take(count)
        val units = List(count) { Item(item, 1) }
        inventory[slot] = if (count == item.amount) null else Item(item, item.amount - count)
        slots.forEachIndexed { index, destination -> box[destination] = units[index] }
        player.persistNow()
        return Transfer(Status.OK, count)
    }

    /** One safe-box slot is always one item. Never note, uncharge, redirect or merge different item attributes. */
    fun withdraw(player: Player, slot: Int, expectedId: Int): Transfer {
        val access = accessStatus(player)
        if (access != Status.OK) return Transfer(access)
        val box = container(player)
        if (slot !in 0 until box.capacity) return Transfer(Status.INVALID_SLOT)
        val item = box[slot] ?: return Transfer(Status.ITEM_CHANGED)
        if (item.id != expectedId || item.amount != 1) return Transfer(Status.ITEM_CHANGED)
        val inventory = player.inventory
        val stackSlot = if (!item.hasAnyAttr() && item.getDef(player.world.definitions).stackable) {
            inventory.rawItems.indices.firstOrNull {
                val held = inventory[it]
                held != null && held.id == item.id && !held.hasAnyAttr() && held.amount > 0
            }
        } else null
        // A full stack cannot overflow into a second stack of the same item, even with an empty slot.
        if (stackSlot != null && inventory[stackSlot]!!.amount == Int.MAX_VALUE) return Transfer(Status.INVENTORY_FULL)
        val destination = stackSlot ?: inventory.nextFreeSlot
        if (destination < 0) return Transfer(Status.INVENTORY_FULL)
        val received = if (stackSlot != null) Item(item, inventory[destination]!!.amount + 1) else Item(item)
        inventory[destination] = received
        box[slot] = null
        player.persistNow()
        return Transfer(Status.OK, 1)
    }
}
