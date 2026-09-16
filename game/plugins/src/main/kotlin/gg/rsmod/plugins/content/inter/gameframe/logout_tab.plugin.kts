package gg.rsmod.plugins.content.inter.gameframe

import gg.rsmod.game.message.impl.LogoutFullMessage
import gg.rsmod.plugins.content.mechanics.pvp.KillGrace
import gg.rsmod.plugins.content.mechanics.pvp.SevenSecondAction

/**
 * Deadman PvP guards plan (owner-approved 2026-09-16): logging out always opens the shared
 * 7-second countdown, superseding the old flat "blocked for 10 seconds after combat, otherwise
 * instant" rule - the countdown's own attack/movement cancellation now covers the "just got hit"
 * case more precisely than the old flat block did.
 */
fun performLogout(player: gg.rsmod.game.model.entity.Player) {
    // Deadman PvP guards plan (2026-09-16): "logging out ... ends it early" - the kill grace
    // period does not persist meaningfully once logged out, but is cleared here for correctness.
    KillGrace.endEarly(player)
    player.requestLogout()
    player.write(LogoutFullMessage())
    player.channelClose()
}

on_button(interfaceId = 182, component = 13) {
    SevenSecondAction.start(player, SevenSecondAction.Kind.LOGOUT) { performLogout(player) }
}

// TODO: come back to this when/if lobby is enabled
on_button(interfaceId = 182, component = 6) {
    SevenSecondAction.start(player, SevenSecondAction.Kind.LOGOUT) { performLogout(player) }
}
