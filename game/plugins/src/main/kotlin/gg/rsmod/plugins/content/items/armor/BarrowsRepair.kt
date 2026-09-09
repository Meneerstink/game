package gg.rsmod.plugins.content.items.armor

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.chatNpc
import gg.rsmod.plugins.api.ext.chatPlayer
import gg.rsmod.plugins.api.ext.format
import gg.rsmod.plugins.api.ext.options
import gg.rsmod.plugins.api.ext.player

/**
 * Barrows repair dialogue shared by Bob (Lumbridge) and any other repair NPC.
 *
 * 2011 wiki prices for a fully degraded piece: helm 60,000, body 90,000, legs 80,000, weapon
 * 100,000 coins; partly degraded pieces cost proportionally less. Repairs every degraded piece
 * carried or worn in one go.
 */
object BarrowsRepair {
    suspend fun repair(task: QueueTask) {
        val player = task.player
        task.chatPlayer("Can you repair my items for me?")
        val damaged = mutableListOf<Pair<Item, Int>>()
        (player.inventory.rawItems.filterNotNull() + player.equipment.rawItems.filterNotNull()).forEach { item ->
            val piece = BarrowsPiece.forId(item.id) ?: return@forEach
            val cost = piece.repairCost(item.id)
            if (cost > 0) damaged.add(item to cost)
        }
        if (damaged.isEmpty()) {
            task.chatNpc("You don't have anything that needs repairing!")
            return
        }
        val total = damaged.sumOf { it.second }
        task.chatNpc("I can repair your Barrows equipment for a total of ${total.format()} coins. Shall I go ahead?")
        if (task.options("Yes, please.", "No, thanks.") != 1) {
            return
        }
        if (player.inventory.getItemCount(Items.COINS_995) < total) {
            task.chatNpc("You don't have enough coins for that.")
            return
        }
        player.inventory.remove(Items.COINS_995, total)
        damaged.forEach { (item, _) ->
            val piece = BarrowsPiece.forId(item.id)!!
            val fixed = Item(piece.baseId, item.amount)
            if (player.inventory.remove(item).hasSucceeded()) {
                player.inventory.add(fixed)
            } else if (player.equipment.remove(item).hasSucceeded()) {
                val def = player.world.definitions.get(ItemDef::class.java, fixed.id)
                player.equipment.add(fixed, beginSlot = def.equipSlot)
            }
        }
        task.chatNpc("There you go - good as new!")
    }
}
