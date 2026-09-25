package gg.rsmod.plugins.content.areas.poh

/*
 * Settings tab "Open House Options" (261:8) and the House Options panel 398 (owner 2026-09-24: "Zorg al onze tandwiel instellingen in
 * game correct werken"). The click was logged as unhandled (logs/unhandled-object-actions.tsv: button 8, interface 261).
 * Cache facts (InterfaceHookProbeTool 398): building mode is drawn from varbit 2176 (CS2 2680), the arrival choice from varbit 6450
 * (CS2 2683 "arrive at portal" = 1, CS2 2684 "arrive in house" = 0, both through CS2 2685), the room count from varc 944 (CS2 2681).
 * The house here is a finished, furnished private house (PlayerHouse) with no Construction building, so building mode stays off and
 * says why; there are never guests in it.
 */

on_button(interfaceId = 261, component = 8) {
    player.setVarbit(2176, 0)
    if (player.attr[PlayerHouse.ARRIVAL_CHOSEN_ATTR] != true) player.setVarbit(PlayerHouse.ARRIVAL_VARBIT, 1)
    player.setVarc(944, PlayerHouse.ROOM_COUNT)
    player.openInterface(398, InterfaceDestination.SETTINGS_TAB)
}

on_button(interfaceId = 398, component = 19) {
    player.openInterface(261, InterfaceDestination.SETTINGS_TAB)
}

on_button(interfaceId = 398, component = 15) {
    player.setVarbit(2176, 0)
    player.message("Your house is furnished by the estate agent; there is no building mode on this server.")
}

on_button(interfaceId = 398, component = 1) {
    player.setVarbit(2176, 0)
}

on_button(interfaceId = 398, component = 25) {
    player.attr[PlayerHouse.ARRIVAL_CHOSEN_ATTR] = true
    player.setVarbit(PlayerHouse.ARRIVAL_VARBIT, 1)
}

on_button(interfaceId = 398, component = 26) {
    player.attr[PlayerHouse.ARRIVAL_CHOSEN_ATTR] = true
    player.setVarbit(PlayerHouse.ARRIVAL_VARBIT, 0)
}

on_button(interfaceId = 398, component = 27) {
    player.message("There are no guests in your house.")
}

on_button(interfaceId = 398, component = 29) {
    if (!PlayerHouse.isSafeTile(player.tile)) {
        player.message("You are not in a house.")
        return@on_button
    }
    gg.rsmod.plugins.content.mechanics.pvp.DeadmanTimerGate.requestRoute(player, gg.rsmod.plugins.content.mechanics.pvp.SevenSecondAction.Kind.PORTAL) {
        player.moveTo(PlayerHouse.EXIT_TILE)
    }
}
