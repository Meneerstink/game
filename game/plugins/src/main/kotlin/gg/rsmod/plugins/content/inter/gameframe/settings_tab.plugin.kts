package gg.rsmod.plugins.content.inter.gameframe

/**
 * The settings tab's four toggles.
 *
 * Each of these components bakes `op1=IF_BUTTON1` into the cache, so the clicks were already
 * reaching the server; nothing was listening, which is why only the graphics and audio buttons
 * appeared to respond. The client redraws each switch from a varp of its own - the component's
 * `varpTriggers`, read with
 * `./gradlew :game:runInterfaceHookProbeTool --args="../data/cache interface 261"` - so setting that
 * varp is the whole of the fix, and because non-zero varps are written to the player's save the
 * setting also survives a logout.
 *
 * Component 8 `Open House Options` opens the House Options panel 398 (areas/poh/house_options.plugin.kts).
 */
private val TOGGLES =
    mapOf(
        3 to 1438, // Toggle Profanity Filter
        4 to 171, // Toggle Chat Effects
        6 to 170, // Toggle Number of Mouse Buttons
        7 to 427, // Toggle Accept Aid
    )

TOGGLES.forEach { (component, varp) ->
    on_button(interfaceId = 261, component = component) {
        player.toggleVarp(varp)
    }
}

on_button(interfaceId = 261, component = 14) {
    player.openInterface(interfaceId = 742, dest = InterfaceDestination.MAIN_SCREEN)
}

on_button(interfaceId = 261, component = 16) {
    player.openInterface(interfaceId = 743, dest = InterfaceDestination.MAIN_SCREEN)
}

/**
 * `Adventurer's Log Options`. Interface 623 is the page behind it - it is the only other place in
 * the cache that mentions the Adventurer's Log (component 66 reads "Adventurers Log Settings"),
 * found with `runInterfaceHookProbeTool --args="../data/cache search adventurer"`.
 *
 * Its own twenty-odd `Toggle On/Off` switches are left unbound on purpose: they all read varp 2396
 * and pass an index to clientscript 4732, so which bit each one owns is not established from the
 * cache, and the Adventurer's Log they would configure does not exist on this server yet.
 */
on_button(interfaceId = 261, component = 18) {
    player.openInterface(interfaceId = 623, dest = InterfaceDestination.MAIN_SCREEN)
}

on_button(261, 5) {
    player.openInterface(982, InterfaceDestination.SETTINGS_TAB)
}

on_button(982, 5) {
    player.openInterface(261, InterfaceDestination.SETTINGS_TAB)
}

/*
 * Chat Setup (982) colour buttons (owner 2026-09-24: "Zorg al onze tandwiel instellingen in game correct werken"). Every "Select
 * colour" click already reached the server and nothing listened, so a chosen colour was only set client-side (CS2 2733 / 4427 / 4585 /
 * 3417 write the var locally) and was gone after a relog. The server now writes the same var, which is saved with the player.
 * The button -> value table is the one CS2 83 registers (982:17 value 0, 982:18 value 1, ... 982:33 value 20; decoded 2026-09-24):
 * Clan Chat varbit 3612 (buttons 17-36), Friends Chat varbit 9188 (72-91), Guest Chat varbit 9191 (97-116), Private chat varp 287
 * (buttons 49-66 = 1..18; "No split" 41 = 0, CS2 2735 draws <= 0 as not split).
 */
val GROUP_VALUES = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 20, 17, 18, 19)

GROUP_VALUES.forEachIndexed { index, value ->
    on_button(982, 17 + index) { player.setVarbit(3612, value) }
    on_button(982, 72 + index) { player.setVarbit(9188, value) }
    on_button(982, 97 + index) { player.setVarbit(9191, value) }
}

(1..18).forEach { value ->
    on_button(982, 48 + value) { player.setVarp(287, value) }
}

on_button(982, 41) { player.setVarp(287, 0) }
