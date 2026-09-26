package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.ContainerKey
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.getWildernessLevel
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.areas.home.BountyHunterHome
import gg.rsmod.plugins.content.inter.bank.Bank
import gg.rsmod.plugins.content.items.food.Food
import gg.rsmod.plugins.content.items.potion.Potion

/**
 * OSRS looting-bag storage. The cache item is imported separately; this object owns the
 * persistent 28-slot server container and all transfer rules. The 667 client has no verified
 * looting-bag interface in this checkout, so the plugin exposes the cache's item options through
 * chat messages while keeping the item lifecycle and storage rules server-side.
 */
object LootingBag {
    val KEY = ContainerKey("looting_bag", capacity = 28, stackType = ContainerStackType.NORMAL)
    val STORE_MODE = AttributeKey<Int>(persistenceKey = "looting_bag_store_mode")

    private const val STORE_ONE = 1
    private const val STORE_FIVE = 5
    private const val STORE_TEN = 10
    private const val STORE_ALL = 0

    fun isBag(itemId: Int): Boolean = itemId == Items.LOOTING_BAG || itemId == Items.LOOTING_BAG_OPEN

    fun container(player: Player): ItemContainer =
        player.containers.getOrPut(KEY) { ItemContainer(player.world.definitions, KEY) }

    /** OSRS: the bag is usable in the Wilderness and in Ferox Enclave. */
    fun usableHere(player: Player): Boolean =
        player.tile.getWildernessLevel() > 0 || BountyHunterHome.isSafe(player)

    fun mode(player: Player): Int = player.attr.getOrDefault(STORE_MODE, STORE_ALL)

    fun setMode(player: Player, amount: Int) {
        player.attr[STORE_MODE] = amount
        player.message("Looting bag storage amount set to ${if (amount == STORE_ALL) "all" else amount}.")
    }

    fun canStore(player: Player, itemId: Int): Boolean {
        if (isBag(itemId)) return false
        val def = player.world.definitions.get(ItemDef::class.java, itemId)
        return def.tradeable && !def.isPlaceholder
    }

    fun store(player: Player, itemId: Int, requested: Int): Int {
        if (!usableHere(player)) {
            player.message("You can only use a looting bag in the Wilderness or Ferox Enclave.")
            return 0
        }
        if (!canStore(player, itemId)) {
            player.message("You can only store tradeable items in a looting bag.")
            return 0
        }
        val available = player.inventory.getItemCount(itemId)
        val amount = requested.coerceAtMost(available)
        if (amount <= 0) return 0

        val bag = container(player)
        val added = bag.add(itemId, amount, assureFullInsertion = false).completed
        if (added <= 0) {
            player.message("Your looting bag is full.")
            return 0
        }
        val removed = player.inventory.remove(itemId, added, assureFullRemoval = true).completed
        if (removed < added) {
            bag.remove(itemId, added - removed, assureFullRemoval = true)
        }
        refresh(player)
        if (removed > 0) player.message("You store $removed item${if (removed == 1) "" else "s"} in your looting bag.")
        return removed
    }

    fun storeSelected(player: Player, itemId: Int) {
        val available = player.inventory.getItemCount(itemId)
        store(player, itemId, if (mode(player) == STORE_ALL) available else mode(player))
    }

    fun storeInventory(player: Player): Int {
        if (!usableHere(player)) {
            player.message("You can only use a looting bag in the Wilderness or Ferox Enclave.")
            return 0
        }
        val snapshot = player.inventory.sequence().map { it.id to it.amount }.toList()
        var moved = 0
        snapshot.forEach { (id, amount) ->
            if (!isBag(id) && canStore(player, id)) moved += store(player, id, amount)
        }
        if (moved == 0) player.message("There are no tradeable items in your inventory to store.")
        return moved
    }

    fun check(player: Player) {
        val bag = container(player)
        val contents = bag.sequence().joinToString(", ") { item ->
            val name = player.world.definitions.get(ItemDef::class.java, item.id).name
            "$name x${item.amount}"
        }
        player.message(if (contents.isEmpty()) "Your looting bag is empty." else "Your looting bag contains: $contents")
    }

    /** Contents can only be removed at a bank, matching OSRS's bank-only empty action. */
    fun depositAtBank(player: Player): Int {
        if (!player.interfaces.isVisible(Bank.BANK_INTERFACE_ID)) {
            player.message("You need to be at a bank to empty your looting bag.")
            return 0
        }
        val bag = container(player)
        var moved = 0
        for (slot in bag.capacity - 1 downTo 0) {
            val item = bag[slot] ?: continue
            val before = item.amount
            if (Bank.deposit(player, bag, slot, before)) {
                moved += before - (bag[slot]?.amount ?: 0)
            }
        }
        refresh(player)
        player.message(if (moved == 0) "Your looting bag is empty." else "You empty your looting bag into the bank.")
        return moved
    }

    fun withdrawAtBank(player: Player): Int {
        if (!player.interfaces.isVisible(Bank.BANK_INTERFACE_ID)) {
            player.message("You need to be at a bank to withdraw from your looting bag.")
            return 0
        }
        val bag = container(player)
        var moved = 0
        for (slot in 0 until bag.capacity) {
            val item = bag[slot] ?: continue
            val added = player.inventory.add(item.id, item.amount, assureFullInsertion = false).completed
            if (added <= 0) continue
            val removed = bag.remove(item.id, added, assureFullRemoval = true).completed
            if (removed < added) player.inventory.remove(item.id, added - removed, assureFullRemoval = true)
            moved += removed
        }
        refresh(player)
        if (moved == 0) player.message("You don't have enough inventory space.")
        return moved
    }

    fun destroy(player: Player) {
        val bag = container(player)
        if (player.tile.getWildernessLevel() > 0) {
            for (item in bag.sequence().toList()) {
                if (!destroyOnWilderness(item, player)) {
                    player.world.spawn(GroundItem(item.id, item.amount, player.tile, player))
                }
            }
        }
        bag.removeAll()
        player.inventory.remove(Items.LOOTING_BAG, 1, assureFullRemoval = false)
        player.inventory.remove(Items.LOOTING_BAG_OPEN, 1, assureFullRemoval = false)
        refresh(player)
        player.message("You destroy the looting bag.")
    }

    fun setOpen(player: Player, open: Boolean) {
        val slot = player.attr[gg.rsmod.game.model.attr.INTERACTING_ITEM_SLOT] ?: return
        val current = player.inventory[slot] ?: return
        val wanted = if (open) Items.LOOTING_BAG_OPEN else Items.LOOTING_BAG
        if (!isBag(current.id) || current.id == wanted) return
        player.inventory[slot] = Item(wanted, current.amount)
        refresh(player)
    }

    /** The bag's stored stacks, untouched (the Items Kept on Death preview). */
    fun peekContents(player: Player): List<Item> = container(player).sequence().map { Item(it) }.toList()

    /** Whether a PvP death destroys [item] from the bag instead of handing it to the killer (see [pvpDeathContents]). */
    fun destroyedOnPvpDeath(
        player: Player,
        item: Item,
    ): Boolean {
        val def = player.world.definitions.get(ItemDef::class.java, item.id)
        return !def.noted && (item.id == Items.VIAL || Food.values().any { it.item == item.id } || Potion.values().any { it.item == item.id })
    }

    /** Removes and returns the bag's stored stacks when the bag itself is lost on death. */
    fun takeContents(player: Player): List<Item> {
        val bag = container(player)
        val contents = bag.sequence().map { Item(it) }.toList()
        bag.removeAll()
        refresh(player)
        return contents
    }

    /** Food and potions in a bag are destroyed on a PvP death; all other stacks reach the killer. */
    fun pvpDeathContents(player: Player): List<Item> =
        takeContents(player).filterNot { item ->
            val def = player.world.definitions.get(ItemDef::class.java, item.id)
            !def.noted && (item.id == Items.VIAL || Food.values().any { it.item == item.id } || Potion.values().any { it.item == item.id })
        }

    /** Food, potions and vials are destroyed on a Wilderness bag-destroy; other contents drop. */
    private fun destroyOnWilderness(item: Item, player: Player): Boolean {
        val def = player.world.definitions.get(ItemDef::class.java, item.id)
        if (def.noted) return false
        return item.id == Items.VIAL || Food.values().any { it.item == item.id } || Potion.values().any { it.item == item.id }
    }

    fun refresh(player: Player) {
        player.inventory.dirty = true
        container(player).dirty = true
    }
}
