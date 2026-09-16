package gg.rsmod.plugins.content.inter.gameframe

import gg.rsmod.game.message.impl.LogoutFullMessage
import gg.rsmod.plugins.content.mechanics.pvp.DeadmanTimerGate
import gg.rsmod.plugins.content.mechanics.pvp.KillGrace
import gg.rsmod.plugins.content.mechanics.pvp.SevenSecondAction

/**
 * Deadman logout (owner 2026-09-17, superseding the 2026-09-16 "always a countdown" rule): the shared
 * 7-second countdown opens only when the player is PK-skulled or in combat (a boss fight never
 * counts - [DeadmanTimerGate]); otherwise the logout happens at once.
 */
fun performLogout(player: gg.rsmod.game.model.entity.Player) {
    // Deadman PvP guards plan (2026-09-16): "logging out ... ends it early" - the kill grace
    // period does not persist meaningfully once logged out, but is cleared here for correctness.
    KillGrace.endEarly(player)
    player.requestLogout()
    player.write(LogoutFullMessage())
    player.channelClose()
}

fun requestLogout(player: gg.rsmod.game.model.entity.Player) {
    if (DeadmanTimerGate.needsCountdown(player)) {
        SevenSecondAction.start(player, SevenSecondAction.Kind.LOGOUT) { performLogout(player) }
    } else {
        performLogout(player)
    }
}

on_button(interfaceId = 182, component = 13) {
    requestLogout(player)
}

// TODO: come back to this when/if lobby is enabled
on_button(interfaceId = 182, component = 6) {
    requestLogout(player)
}
