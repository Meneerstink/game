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
 * Deliberately still unbound, because the interface it opens is not established from the cache:
 * component 8 `Open House Options`.
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
