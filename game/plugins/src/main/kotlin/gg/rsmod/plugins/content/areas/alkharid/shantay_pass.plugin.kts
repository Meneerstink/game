package gg.rsmod.plugins.content.areas.alkharid

import gg.rsmod.game.model.Tile
import gg.rsmod.plugins.content.inter.bank.openBank
import gg.rsmod.plugins.content.skills.agility.ObstacleApproachFailed
import gg.rsmod.plugins.content.skills.agility.Render
import gg.rsmod.plugins.content.skills.agility.walkOverTile
import gg.rsmod.plugins.content.skills.agility.walkToTile

/*
 * Shantay Pass (667 cache: gate 12774 "Go-through"/"Look-at", Shantay 836 "Buy-pass", Shantay chest 2693 "Open" - none were bound, so
 * the desert gate did nothing). Behaviour and text from the 667 Void donor (content/area/kharidian_desert/shantay_pass): entering the
 * desert (from the north, y >= 3117) costs one Shantay pass, taken by the guard; leaving is free. The pass costs 5 coins.
 */
val PASS_PRICE = 5
val GATE_ROW_NORTH = 3117
val GATE_ROW_SOUTH = 3116

on_obj_option(obj = Objs.SHANTAY_PASS_12774, option = "go-through") {
    val entering = player.tile.z >= GATE_ROW_NORTH
    if (entering && !player.inventory.contains(Items.SHANTAY_PASS)) {
        // The refusal is a dialogue only: never under the crossing lock (conform traversal 2026-10-03: a player without a pass
        // stayed FULL-locked until he clicked the guard's line away).
        player.queue {
            chatNpc(
                "You need a Shantay pass to get through this gate. See Shantay, he will sell you one for a very reasonable price.",
                npc = Npcs.SHANTAY_GUARD,
                facialExpression = FacialExpression.CALM_TALK,
            )
        }
        return@on_obj_option
    }
    player.lockingQueue(lockState = LockState.FULL) {
        if (entering) {
            if (!player.inventory.remove(Items.SHANTAY_PASS).hasSucceeded()) return@lockingQueue
            player.message("The guard takes your Shantay Pass as you go through the gate.")
        }
        // Void ShantayPass: walkToDelay to the gate row on the player's side (arrival), then one step through. The old script
        // threw that walk away and walked collision-free from the click tile (audit 2026-10-03, HIGH: diagonally through the wall).
        val x = if (player.tile.x < 3304) 3303 else 3305
        try {
            walkToTile(player, Tile(x, if (entering) GATE_ROW_NORTH else GATE_ROW_SOUTH, player.tile.height))
            walkOverTile(player, Tile(x, if (entering) GATE_ROW_SOUTH else GATE_ROW_NORTH, player.tile.height), Render.NONE)
        } catch (e: ObstacleApproachFailed) {
            player.stopMovement()
            player.message("I can't reach that!")
        }
    }
}

on_obj_option(obj = Objs.SHANTAY_PASS_12774, option = "look-at") {
    player.queue {
        messageBox("You look at the huge stone gate.<br>Near the gate is a large billboard poster, it reads:")
        messageBox(
            "<col=ff0000>The Desert is a VERY Dangerous place. Do not enter if you are afraid of dying. Beware of high temperatures, " +
                "sand storms, robbers, and slavers. No responsibility is take by Stantay if anything bad should happen to you in any " +
                "circumstances whatsoever.",
        )
        messageBox("Despite this warning lots of people seem to pass through the gate.")
    }
}

on_obj_option(obj = Objs.SHANTAY_CHEST, option = "open") {
    player.openBank()
}

on_npc_option(npc = Npcs.SHANTAY, option = "buy-pass") {
    player.queue {
        if (player.inventory.getItemCount(Items.COINS_995) < PASS_PRICE) {
            chatNpc("Sorry friend, the Shantay Pass is 5 gold coins. You don't seem to have enough money!", facialExpression = FacialExpression.CALM_TALK)
            return@queue
        }
        if (player.inventory.isFull && player.inventory.getItemCount(Items.COINS_995) != PASS_PRICE && !player.inventory.contains(Items.SHANTAY_PASS)) {
            chatNpc("Sorry friend, you'll need more inventory space to buy a pass.", facialExpression = FacialExpression.CALM_TALK)
            return@queue
        }
        if (player.inventory.remove(Items.COINS_995, PASS_PRICE).hasSucceeded()) {
            player.inventory.add(Items.SHANTAY_PASS)
            itemMessageBox("You purchase a Shantay Pass.", item = Items.SHANTAY_PASS)
        }
    }
}
