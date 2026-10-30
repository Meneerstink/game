package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.ContainerKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.message

/**
 * N424 (owner 2026-10-29: "mensen hebben anders lootkeys, looting bag en ook nog BoB-storage, dat is teveel"): the looting bag is
 * out of the game - no drop, no shop, no use. Both item ids are unobtainable ([gg.rsmod.plugins.content.mechanics.removed.RemovedItems]),
 * so the Grand Exchange refuses them and the login purge takes any copy a player still has.
 *
 * What was stored in a bag is the player's own: at login, before that purge, it goes to the bank, and what the bank cannot take
 * to Death's Office, fee-free. Whatever fits neither stays in the old container (still saved) and is tried again at the next
 * login - nothing is ever thrown away. The container key stays registered only so old saves keep loading it.
 */
object LootingBag {
    val KEY = ContainerKey("looting_bag", capacity = 28, stackType = ContainerStackType.NORMAL)

    val IDS: Set<Int> = setOf(Items.LOOTING_BAG, Items.LOOTING_BAG_OPEN)

    fun isBag(itemId: Int): Boolean = itemId in IDS

    /** Empties an old bag's contents into the bank / Death's Office; returns how many stacks moved. */
    fun retire(player: Player): Int {
        val bag: ItemContainer = player.containers[KEY] ?: return 0
        if (bag.occupiedSlotCount == 0) return 0
        var banked = 0
        var office = 0
        for (slot in 0 until bag.capacity) {
            val item = bag[slot] ?: continue
            if (gg.rsmod.plugins.content.inter.bank.Bank.deposit(player, bag, slot, item.amount) && bag[slot] == null) {
                banked++
                continue
            }
            val left = bag[slot] ?: continue
            val stored = gg.rsmod.plugins.content.mechanics.death.DeathsOffice.store(player, left, free = true)
            if (stored <= 0) continue
            bag[slot] = if (stored >= left.amount) null else Item(left.id, left.amount - stored).copyAttr(left)
            if (bag[slot] == null) office++
        }
        bag.dirty = true
        player.inventory.dirty = true
        if (banked + office > 0) {
            player.message("The looting bag is gone from the game. Everything it held has been moved to your bank" + (if (office > 0) " and Death's Office." else "."))
        }
        if (bag.occupiedSlotCount > 0) {
            player.message("Your bank and Death's Office are full - the rest of your old looting bag's items are kept for your next login.")
        }
        return banked + office
    }
}
