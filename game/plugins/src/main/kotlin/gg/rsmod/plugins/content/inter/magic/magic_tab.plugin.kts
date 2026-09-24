package gg.rsmod.plugins.content.inter.magic


val FILTER_COMBAT_SPELLS_VARBIT = 6459
val FILTER_TELEPORT_SPELLS_VARBIT = 6462
val FILTER_MISC_SPELLS_VARBIT = 6461
val FILTER_SKILL_SPELLS_VARBIT = 6460

// Saves from before 2026-09-19 carry only the server spellbook varbit: mirror it into the client's spellbook varbit 357 so the
// autocast highlight (CS2 1121) follows the open book.
on_login {
    player.setVarbit(gg.rsmod.plugins.api.ext.CLIENT_SPELLBOOK_VARBIT, player.getSpellbook().id)
}

/*
 * Legacy 667 combat autocast is gone: combat spells are autocast only from the Combat Options tab (content/combat/magic/Autocast).
 * The spellbook's baked "Autocast" op (standard op1, Ancient op6) and the "Defensive Casting" toggle (192:2, 193:18, 430:20) are
 * stripped from the client menu by re-sending each component's baked event mask without that op, every time a spellbook is opened
 * (the open hook fires before the IfOpenSub, and the client reloads the baked masks on open, so it runs a cycle later). Casting a
 * spell on a target (the targeting bits) is untouched and stays a manual cast. No server handler exists for the stripped ops, so a
 * replayed legacy click changes nothing.
 */
val LEGACY_AUTOCAST_OPS: Map<Int, List<Pair<Int, Int>>> =
    mapOf(
        192 to (listOf(25, 28, 30, 32, 34, 39, 42, 45, 49, 52, 54, 56, 58, 63, 66, 67, 68, 70, 73, 77, 80, 84, 87, 89, 91, 98, 99).map { it to (0x005002 and 0x2.inv()) } +
            listOf(47 to (0x001002 and 0x2.inv()), 2 to (0x000402 and 0x2.inv()))),
        193 to ((20..35).map { it to (0x005040 and 0x40.inv()) } + (36..39).map { it to (0x005440 and 0x40.inv()) } + listOf(18 to (0x000402 and 0x2.inv()))),
        430 to listOf(20 to (0x000402 and 0x2.inv())),
    )

LEGACY_AUTOCAST_OPS.forEach { (book, components) ->
    on_interface_open(interfaceId = book) {
        val p = player
        world.queue {
            wait(1)
            if (!p.isOnline) return@queue
            components.forEach { (component, events) -> p.setInterfaceEvents(book, component, -1..-1, events) }
        }
    }
}

on_button(interfaceId = 192, 7) {
    player.toggleVarbit(FILTER_COMBAT_SPELLS_VARBIT)
}

on_button(interfaceId = 192, 9) {
    player.toggleVarbit(FILTER_TELEPORT_SPELLS_VARBIT)
}

on_button(interfaceId = 192, 11) {
    player.toggleVarbit(FILTER_MISC_SPELLS_VARBIT)
}

on_button(interfaceId = 192, 13) {
    player.toggleVarbit(FILTER_SKILL_SPELLS_VARBIT)
}
