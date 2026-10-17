package gg.rsmod.plugins.content.inter.magic

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

LEGACY_AUTOCAST_OPS.forEach { (book, legacy) ->
    // N76: plus the player-only / NPC-only target masks (Tele Block offers no NPC target, OSRS 2019) - same open, same cycle.
    val components = legacy + SpellTargetMasks.overrides(book)
    on_interface_open(interfaceId = book) {
        val p = player
        world.queue {
            wait(1)
            if (!p.isOnline) return@queue
            components.forEach { (component, events) -> p.setInterfaceEvents(book, component, -1..-1, events) }
        }
    }
}

/*
 * N72: the OSRS spell filters (SpellFilters) - one saved state in varp 1376 shared by all three books. Every 667 bottom-row Filter
 * and Sort button of every book is handled (Novite ButtonHandler; before N72 only the Standard Filter buttons were bound), and the
 * client's OSRS filter menu (RspsSpellFilters: tab-stone "Enable/Disable spell filtering", the Filters button's menu) arrives as
 * IF_BUTTON1 on the book's full-size layer with the action as slot.
 */
on_login {
    SpellFilters.refresh(player)
}

SpellFilters.Book.values().forEach { book ->
    book.filterButtons.forEach { (component, category) ->
        on_button(interfaceId = book.interfaceId, component = component) {
            SpellFilters.clickLegacyFilter(player, category)
        }
    }
    book.sortButtons.forEachIndexed { index, component ->
        on_button(interfaceId = book.interfaceId, component = component) {
            SpellFilters.clickSort(player, index)
        }
    }
    on_button(interfaceId = book.interfaceId, component = book.menuLayer) {
        SpellFilters.clickMenu(player, player.attr[gg.rsmod.game.model.attr.INTERACTING_SLOT_ATTR] ?: -1)
    }
}
