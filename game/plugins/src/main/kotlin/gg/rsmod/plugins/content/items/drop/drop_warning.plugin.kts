package gg.rsmod.plugins.content.items.drop

import gg.rsmod.game.message.handler.OpHeld5Handler

/** N215: the drop warning ([DropWarning]) in front of every plain floor drop. */
OpHeld5Handler.confirm = { player, item, slot ->
    if (!DropWarning.needsWarning(player.world, item)) {
        false
    } else {
        val name = item.getName(player.world.definitions)
        player.queue {
            itemMessageBox(DropWarning.question(name), item = item.id)
            if (options("Yes, drop it.", "No, keep it.", title = "Drop $name?") == FIRST_OPTION) {
                OpHeld5Handler.drop(player, player.world, item, slot)
            }
        }
        true
    }
}
