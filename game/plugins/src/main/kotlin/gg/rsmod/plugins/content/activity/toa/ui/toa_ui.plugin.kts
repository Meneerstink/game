package gg.rsmod.plugins.content.activity.toa.ui

import gg.rsmod.plugins.content.activity.toa.ToaLocs

// N462: the ported ToA windows (ToaInterfaces reads data/cfg/toa/interfaces.tsv; nothing binds while they are not imported).

ToaInterfaces.of(ToaInterfaces.PARTY_DETAILS)?.let { iface ->
    (listOf(ToaPartyDetailsUi.CLOSE) + ToaInvocationTable.ENTRIES.indices.map { ToaPartyDetailsUi.tile(it) }).forEach { component ->
        on_button(iface, component) { ToaPartyDetailsUi.click(player, component) }
    }
}

ToaInterfaces.of(ToaInterfaces.SUPPLIES)?.let { iface ->
    (listOf(ToaSuppliesUi.CLOSE) + ToaSuppliesUi.BUTTONS.toList()).forEach { component ->
        on_button(iface, component) { ToaSuppliesUi.click(player, component) }
    }
}

ToaInterfaces.of(ToaInterfaces.PARTY_LIST)?.let { iface ->
    (listOf(ToaPartyListUi.CLOSE) + (0 until ToaPartyListUi.ROWS).map { ToaPartyListUi.row(it) }).forEach { component ->
        on_button(iface, component) { ToaPartyListUi.click(player, component) }
    }
}

ToaInterfaces.of(ToaInterfaces.SUMMARY)?.let { iface ->
    on_button(iface, 88) { ToaSummaryUi.close(player) }
}

// OSRS Invocation Board (46073, op "Read"): the list of every invocation with its effect and raid level (toa_invocations 776).
ToaInterfaces.of(ToaInterfaces.INVOCATIONS)?.let { iface ->
    on_obj_option(ToaLocs.INVOCATION_BOARD, "Read") { ToaInvocationsUi.open(player) }
    on_button(iface, ToaInvocationsUi.CLOSE) { ToaInvocationsUi.click(player, ToaInvocationsUi.CLOSE) }
}
