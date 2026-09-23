package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.message.impl.ResumePauseButtonMessage
import gg.rsmod.game.model.MoveGate

/*
 * Wiring for [DangerWarning]: the two movement gates and interface 382's buttons.
 */

on_world_init {
    MoveGate.step = { player, from, to ->
        DangerWarning.intercept(player, from, to, destination = player.movementQueue.peekLast() ?: to, walk = true)
    }
    MoveGate.teleport = { player, to -> DangerWarning.intercept(player, player.tile, to, destination = to, walk = false) }
    DangerWarning.open = { player, destination, walk ->
        player.queue(TaskPriority.STRONG) { showDangerWarning(this, destination, walk) }
    }
}

on_login {
    DangerWarning.syncVarp(player)
}

// The 667 warning-settings screen (Doomsayer "Toggle-warnings", interface 583): its Wilderness tile switches the
// Dangerous-area warning.
on_button(DangerWarning.SETTINGS_INTERFACE, DangerWarning.SETTINGS_TOGGLE) {
    val active = !DangerWarning.isActive(player)
    DangerWarning.setActive(player, active)
    player.message(if (active) "Dangerous-area warnings are turned on." else "Dangerous-area warnings are turned off.")
}

// The close cross (382:14, IF_BUTTON1 'Close' in the cache) cancels: the waiting warning ends and nothing moves.
on_button(DangerWarning.INTERFACE_ID, DangerWarning.CLOSE) {
    player.closeInterface(DangerWarning.INTERFACE_ID)
    player.interruptQueues()
}

on_command("warnings") {
    DangerWarning.setActive(player, true)
    player.message("Dangerous-area warnings are turned on.")
}

fun sendDontAsk(player: Player) {
    player.setComponentHidden(DangerWarning.INTERFACE_ID, DangerWarning.DONT_ASK, false)
    player.setComponentText(
        DangerWarning.INTERFACE_ID,
        DangerWarning.DONT_ASK_TEXT,
        if (DangerWarning.isActive(player)) "Don't show interface warnings again" else "Warnings off - click to keep them on",
    )
}

suspend fun showDangerWarning(
    task: QueueTask,
    destination: Tile,
    walk: Boolean,
) {
    val player = task.player
    // The held-back teleport/shortcut may have locked the player (this STRONG task inherits that lock) and started its
    // animation (a tunnel's crawl-in pose). The move itself never happened, so both are undone now - otherwise the player
    // hangs in that pose and the warning's own buttons cannot be clicked.
    player.unlock()
    player.animate(-1)
    player.openInterface(DangerWarning.INTERFACE_ID, InterfaceDestination.MAIN_SCREEN)
    player.setComponentText(DangerWarning.INTERFACE_ID, DangerWarning.BODY, DangerWarning.BODY_TEXT)
    sendDontAsk(player)
    task.terminateAction = { player.closeInterface(DangerWarning.INTERFACE_ID) }
    while (true) {
        task.waitReturnValue()
        val msg = task.requestReturnValue as? ResumePauseButtonMessage
        if (msg != null && msg.interfaceId == DangerWarning.INTERFACE_ID && msg.component == DangerWarning.DONT_ASK) {
            DangerWarning.setActive(player, !DangerWarning.isActive(player))
            sendDontAsk(player)
            continue
        }
        player.closeInterface(DangerWarning.INTERFACE_ID)
        task.terminateAction = null
        val confirmed =
            msg != null && msg.interfaceId == DangerWarning.INTERFACE_ID &&
                (msg.component == DangerWarning.ENTER || msg.component == DangerWarning.ENTER_HOVER)
        if (!confirmed) return
        DangerWarning.allowNextCrossing(player)
        if (walk) player.walkTo(destination) else player.moveTo(destination)
        return
    }
}
