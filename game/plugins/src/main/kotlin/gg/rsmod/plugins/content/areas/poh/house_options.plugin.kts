package gg.rsmod.plugins.content.areas.poh

/*
 * Settings tab "Open House Options" (261:8) and the House Options panel 398 (owner 2026-09-24: "Zorg al onze tandwiel instellingen in
 * game correct werken"). The click was logged as unhandled (logs/unhandled-object-actions.tsv: button 8, interface 261).
 * Cache facts (InterfaceHookProbeTool 398): building mode is drawn from varbit 2176 (CS2 2680), the arrival choice from varbit 6450
 * (CS2 2683 "arrive at portal" = 1, CS2 2684 "arrive in house" = 0, both through CS2 2685), the room count from varc 944 (CS2 2681).
 * Building mode (owner 2026-10-10 night run): on/off like OSRS - saved, shown by varbit 2176, and the house reloads around the
 * player when they are in it (PohBuild.setBuildMode). There are never guests in a house here (only its owner may enter).
 * The rev-667 panel has no "Call Servant" or "Render doors" button (InterfaceHookProbeTool layout 398: building mode on/off, the
 * two arrival choices, Expel guests, Leave house and the room count text); the servant is called with the dining room's bell-pull.
 */

on_button(interfaceId = 261, component = 8) {
    player.setVarbit(PlayerHouse.BUILD_MODE_VARBIT, if (PlayerHouse.isBuildMode(player)) 1 else 0)
    if (player.attr[PlayerHouse.ARRIVAL_CHOSEN_ATTR] != true) player.setVarbit(PlayerHouse.ARRIVAL_VARBIT, 1)
    player.setVarc(944, PlayerHouse.ROOM_COUNT)
    player.openInterface(398, InterfaceDestination.SETTINGS_TAB)
}

on_button(interfaceId = 398, component = 19) {
    player.openInterface(261, InterfaceDestination.SETTINGS_TAB)
}

on_button(interfaceId = 398, component = 15) {
    PohBuild.setBuildMode(player, true)
}

on_button(interfaceId = 398, component = 1) {
    PohBuild.setBuildMode(player, false)
}

on_button(interfaceId = 398, component = 25) {
    player.attr[PlayerHouse.ARRIVAL_CHOSEN_ATTR] = true
    player.setVarbit(PlayerHouse.ARRIVAL_VARBIT, 1)
}

on_button(interfaceId = 398, component = 26) {
    player.attr[PlayerHouse.ARRIVAL_CHOSEN_ATTR] = true
    player.setVarbit(PlayerHouse.ARRIVAL_VARBIT, 0)
}

// A40 (owner 10-11, "house options alleen in het eigen house"): the buttons that act on the house itself answer outside it like
// Darkan HousePlayerManager.expelGuests / House.leaveHouse; building mode and the arrival choice stay settable anywhere (OSRS Wiki
// Player-owned house: the choice applies at the next Teleport to House).
on_button(interfaceId = 398, component = 27) {
    if (!PlayerHouse.isSafeTile(player.tile)) {
        player.message(HouseOptions.EXPEL_OUTSIDE)
        return@on_button
    }
    player.message("There are no guests in your house.")
}

// A29 (owner 10-10): Leave house is instant - no Deadman countdown (Darkan House.leaveHouse: straight out, KICKED). The house is a
// safe zone, so the 7-second gate has nothing to guard here.
on_button(interfaceId = 398, component = 29) {
    if (!PlayerHouse.isSafeTile(player.tile)) {
        player.message(HouseOptions.LEAVE_OUTSIDE)
        return@on_button
    }
    player.moveTo(PlayerHouse.locationOf(player).exit)
}
