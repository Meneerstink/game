package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.message.impl.ResumePauseButtonMessage
import gg.rsmod.game.model.MoveGate
import gg.rsmod.game.model.timer.TimerKey

/*
 * Wiring for [DangerWarning]: the two movement gates, the active-playtime counter and interface 382's buttons.
 */

val PLAYTIME_TIMER = TimerKey()
val PLAYTIME_STEP = 100

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
    player.timers[PLAYTIME_TIMER] = PLAYTIME_STEP
}

on_timer(PLAYTIME_TIMER) {
    DangerWarning.countActive(player, PLAYTIME_STEP)
    player.timers[PLAYTIME_TIMER] = PLAYTIME_STEP
}

// The close cross is an ordinary button: it cancels - the waiting warning is ended and nothing moves.
on_button(DangerWarning.INTERFACE_ID, DangerWarning.CLOSE) {
    player.closeInterface(DangerWarning.INTERFACE_ID)
    player.interruptQueues()
}

on_command("warnings") {
    DangerWarning.setDisabled(player, false)
    player.message("Dangerous-area warnings are turned on.")
}

fun sendDontAsk(player: Player) {
    val unlocked = DangerWarning.canDisable(player)
    player.setComponentHidden(DangerWarning.INTERFACE_ID, DangerWarning.DONT_ASK, !unlocked)
    if (unlocked) {
        player.setComponentText(
            DangerWarning.INTERFACE_ID,
            32,
            if (player.attr[DangerWarning.DISABLED] == true) "Warnings off - click to keep them on" else "Don't show interface warnings again",
        )
    }
}

suspend fun showDangerWarning(
    task: QueueTask,
    destination: Tile,
    walk: Boolean,
) {
    val player = task.player
    // The held-back teleport/shortcut may have locked the player (this STRONG task inherits that lock). The move itself never
    // happened, so the lock is released now - otherwise the warning's own buttons could not be clicked.
    player.unlock()
    player.openInterface(DangerWarning.INTERFACE_ID, InterfaceDestination.MAIN_SCREEN)
    player.setComponentText(DangerWarning.INTERFACE_ID, DangerWarning.BODY, DangerWarning.BODY_TEXT)
    sendDontAsk(player)
    task.terminateAction = { player.closeInterface(DangerWarning.INTERFACE_ID) }
    while (true) {
        task.waitReturnValue()
        val msg = task.requestReturnValue as? ResumePauseButtonMessage
        if (msg != null && msg.interfaceId == DangerWarning.INTERFACE_ID && msg.component == DangerWarning.DONT_ASK) {
            DangerWarning.setDisabled(player, player.attr[DangerWarning.DISABLED] != true)
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
