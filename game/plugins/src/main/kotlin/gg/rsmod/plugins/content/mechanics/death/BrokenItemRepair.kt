package gg.rsmod.plugins.content.mechanics.death

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
 * OSRS repairs every [PvpDeathBreakables] broken item "at Perdu" for its sourced coin cost. Perdu
 * (upstream OSRS npc 7456) is a real import (RCV-012 "ferox" batch, local npc id 14394) and is
 * wired into her own "talk-to" option at the Grand Exchange (`grand_exchange_hub.plugin.kts`).
 * Correction 2026-09-16: an earlier note here claimed Perdu was absent and fronted this service
 * through Bob instead - that was wrong (the ferox batch already imported her); repair now runs on
 * Perdu directly and Bob no longer calls this.
 */
object BrokenItemRepair {
    suspend fun repair(task: QueueTask) {
        val player = task.player
        val damaged = mutableListOf<Pair<Item, PvpDeathBreakables.Breakable>>()
        (player.inventory.rawItems.filterNotNull() + player.equipment.rawItems.filterNotNull()).forEach { item ->
            val breakable = PvpDeathBreakables.forBroken(item.id) ?: return@forEach
            damaged.add(item to breakable)
        }
        if (damaged.isEmpty()) {
            return
        }
        task.chatPlayer("Can you repair this for me?")
        val total = damaged.sumOf { (item, breakable) -> breakable.repairCost.toLong() * item.amount }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        task.chatNpc("I can repair that for a total of ${total.format()} coins. Shall I go ahead?")
        if (task.options("Yes, please.", "No, thanks.") != 1) {
            return
        }
        if (player.inventory.getItemCount(Items.COINS_995) < total) {
            task.chatNpc("You don't have enough coins for that.")
            return
        }
        player.inventory.remove(Items.COINS_995, total)
        damaged.forEach { (item, breakable) ->
            val fixed = Item(breakable.itemId, item.amount)
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
