package gg.rsmod.plugins.content.inter.gameframe

/**
 * The chatbox filter tabs of 751 (owner 2026-10-01: every tab's On / Friends / Off / Hide must work and survive a relog).
 *
 * Their onOp is CS2 175 (751:N onOp = [175, op, tab]); for On/Friends/Off/Hide it calls CS2 184 (tab, mode 0..3), decoded
 * 2026-10-01 with its switch table: Game (tab 1) sets varbit 6161 (1 = "Filter", 0 = "All"), Friends Chat (tab 4) varp 2159,
 * and Clan (tab 7) varp 1054 = the mode (the Assist tab 6 is hidden, A21: the Assist System is removed); Public / Private / Trade go through CHAT_SETFILTER, the
 * SET_CHATFILTERSETTINGS packet the server already saves (ChatFilterHandler). The client wrote those four vars only on its
 * own side, so the server's login push (0) reset them at every relog. Every one of these components also sends IF_BUTTON
 * with the same op, so the server now writes the same value; non-zero varps are saved with the player and pushed at login.
 * op1 "View" only switches the shown tab on the client.
 */
/** Baked IF_BUTTON opcodes of 751 (op2=64, op3=4, op4=52, op5=81) -> mode: On, Friends, Off, Hide. */
private val MODE_BY_OPCODE = mapOf(64 to 0, 4 to 1, 52 to 2, 81 to 3)

private val VARP_TABS =
    mapOf(
        8 to 2159, // Friends Chat
        22 to 1054, // Clan
    )

VARP_TABS.forEach { (component, varp) ->
    on_button(interfaceId = 751, component = component) {
        val mode = MODE_BY_OPCODE[player.getInteractingOpcode()] ?: return@on_button
        player.setVarp(varp, mode)
    }
}

// Game: op2 "All" (mode 0) / op4 "Filter" (mode 2) -> varbit 6161 = mode > 0.
on_button(interfaceId = 751, component = 31) {
    val mode = MODE_BY_OPCODE[player.getInteractingOpcode()] ?: return@on_button
    player.setVarbit(6161, if (mode > 0) 1 else 0)
}

// Public / Private / Trade: the filter itself arrives as SET_CHATFILTERSETTINGS; the button click is only its notification.
listOf(19, 25, 28).forEach { component ->
    on_button(interfaceId = 751, component = component) {}
}
