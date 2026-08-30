package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.plugins.content.areas.home.BountyHunterHome

val wildernessCheckTimer = TimerKey()

val INTERFACE_ID = 381

/**
 * The authoritative "is this player currently in dangerous Wilderness" flag, shared by this
 * UI check, combat ([BountyHunterHome.canPlayersFight]), and anything else that needs to know
 * (deaths, hotspots, Breaches all call [BountyHunterHome.isDangerousWilderness] directly, which
 * this attribute simply caches per-player to avoid re-sending the same client state every tick).
 * Missing on login/reconnect defaults to "not dangerous", so the first tick after login always
 * applies the real state instead of skipping it as a no-op.
 */
val IN_DANGEROUS_WILDERNESS = AttributeKey<Boolean>()

on_login {
    player.timers[wildernessCheckTimer] = 1
}

on_timer(wildernessCheckTimer) {
    checkWildernessLevel(player)
    player.timers[wildernessCheckTimer] = 1
}

fun checkWildernessLevel(player: Player) {
    val dangerous = BountyHunterHome.isDangerousWilderness(player)
    val wasDangerous = player.attr[IN_DANGEROUS_WILDERNESS] ?: false
    // Only push interface/option state on an actual transition, not every tick.
    if (dangerous == wasDangerous) {
        return
    }
    player.attr[IN_DANGEROUS_WILDERNESS] = dangerous
    if (dangerous) {
        player.openInterface(dest = InterfaceDestination.PVP_OVERLAY, interfaceId = INTERFACE_ID)
        player.sendOption("Attack", 2)
    } else {
        player.closeInterface(dest = InterfaceDestination.PVP_OVERLAY)
        player.removeOption(2)
    }
}
