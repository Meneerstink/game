package gg.rsmod.plugins.content.skills.construction.mahogany

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items

/**
 * CONSTRUCTION-99 nachtrun 27-11 (C7): Phials (OSRS Wiki "Phials", OSRS npc 1614), next to the Rimmington general store by the house
 * portal: un-notes items for 5 coins each - the usual way to bring planks (and bones for the gilded altar) into the house.
 * Use a banknote on him: Exchange 1 / 5 / All / X, limited by free slots and coins. Talk-to explains it.
 */
val PHIALS = 15368
val FEE = 5

spawn_npc(npc = PHIALS, x = 2951, z = 3216, height = 0, walkRadius = 2)

on_npc_option(npc = PHIALS, option = "talk-to") {
    player.queue {
        chatNpc("Hello there! If you have any banknotes, use them on me and I'll exchange them for the real thing - $FEE coins an item.")
    }
}

on_any_item_on_npc(PHIALS) {
    val used = player.getInteractingItem()
    val defs = world.definitions
    val def = defs.get(ItemDef::class.java, used.id)
    if (!def.noted) {
        player.queue { chatNpc("Sorry, I only exchange banknotes.") }
        return@on_any_item_on_npc
    }
    val real = Item(used.id).toUnnoted(defs).id
    val name = defs.get(ItemDef::class.java, real).name
    player.queue {
        val have = player.inventory.getItemCount(used.id)
        val choice =
            options(
                "Exchange '$name': $FEE coins",
                "Exchange 5: ${5 * FEE} coins",
                "Exchange All: ${have * FEE} coins",
                "Exchange X",
                title = "Select an option",
            )
        val wanted =
            when (choice) {
                1 -> 1
                2 -> 5
                3 -> have
                4 -> inputInt("How many would you like to exchange?")
                else -> 0
            }
        if (wanted <= 0) return@queue
        val inv = player.inventory
        val notes = inv.getItemCount(used.id)
        val coins = inv.getItemCount(Items.COINS_995)
        var n = minOf(wanted, notes, coins / FEE)
        if (n == 0) {
            chatNpc("You don't have enough coins - it's $FEE coins an item.")
            return@queue
        }
        // Room: the free slots, plus the slot of the notes / the coins when that whole stack goes.
        fun room(k: Int) = inv.freeSlotCount + (if (k >= notes) 1 else 0) + (if (k * FEE >= coins) 1 else 0)
        while (n > 0 && n > room(n)) n--
        if (n == 0) {
            chatNpc("You don't have any free space in your inventory.")
            return@queue
        }
        if (!inv.remove(used.id, n, assureFullRemoval = true).hasSucceeded()) return@queue
        if (!inv.remove(Items.COINS_995, n * FEE, assureFullRemoval = true).hasSucceeded()) {
            inv.add(used.id, n)
            return@queue
        }
        val added = inv.add(real, n).completed
        if (added < n) {
            // Safety net (never expected after the room check): give back what was not exchanged.
            inv.add(used.id, n - added)
            inv.add(Items.COINS_995, (n - added) * FEE)
        }
    }
}
