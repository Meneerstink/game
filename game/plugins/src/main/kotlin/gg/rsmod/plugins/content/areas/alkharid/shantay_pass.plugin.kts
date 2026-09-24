package gg.rsmod.plugins.content.areas.alkharid

import gg.rsmod.game.model.Tile
import gg.rsmod.plugins.content.inter.bank.openBank

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
    player.queue {
        if (entering) {
            if (!player.inventory.contains(Items.SHANTAY_PASS)) {
                chatNpc(
                    "You need a Shantay pass to get through this gate. See Shantay, he will sell you one for a very reasonable price.",
                    npc = Npcs.SHANTAY_GUARD,
                    facialExpression = FacialExpression.CALM_TALK,
                )
                return@queue
            }
            if (!player.inventory.remove(Items.SHANTAY_PASS).hasSucceeded()) return@queue
            player.message("The guard takes your Shantay Pass as you go through the gate.")
        }
        val x = if (player.tile.x < 3304) 3303 else 3305
        player.walkTo(this, x, if (entering) GATE_ROW_NORTH else GATE_ROW_SOUTH)
        player.walkTo(Tile(x, if (entering) GATE_ROW_SOUTH else GATE_ROW_NORTH, player.tile.height), detectCollision = false)
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
