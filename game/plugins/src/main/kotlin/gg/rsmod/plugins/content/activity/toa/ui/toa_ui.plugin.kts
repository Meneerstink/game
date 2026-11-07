package gg.rsmod.plugins.content.activity.toa.ui

import gg.rsmod.plugins.content.activity.toa.ToaLocs

// N462: the ported ToA windows (ToaInterfaces reads data/cfg/toa/interfaces.tsv; nothing binds while they are not imported).

// Test client only (admin): open each window with sample content to compare it with OSRS screenshots.
on_command("toaui", gg.rsmod.game.model.priv.Privilege.ADMIN_POWER) {
    val which = player.getCommandArgs().getOrNull(0)?.lowercase()
    val party = gg.rsmod.plugins.content.activity.toa.ToaLobby.of(world).let { it.party(player.uid) ?: it.create(player.uid) }
    when (which) {
        "party" -> if (party != null) ToaPartyDetailsUi.open(player, party)
        "board" -> ToaInvocationsUi.open(player)
        "hud" -> {
            ToaHudUi.open(player)
            ToaHudUi.drawParty(player, listOf(ToaHudUi.Slot(player.username, 20), ToaHudUi.Slot("Alpha", 31), ToaHudUi.Slot("Bravo", 30)), self = 0)
            ToaHudUi.drawRaid(player, 150, 500, 0, intArrayOf(1, 2, 0, 3))
        }
        "supplies" -> ToaSuppliesUi.open(player, listOf(listOfNotNull(gg.rsmod.plugins.content.activity.toa.ToaIds.item(27315)?.let { it to 5 }),
            emptyList(), emptyList())) { player.message("chose bundle $it") }
        "summary" -> ToaSummaryUi.open(player, ToaSummaryUi.Times(300, 400, 500, 600, 700, 200, 250, 260, 270, 980, 3000), 150, 5)
        "rewards" -> ToaRewardsUi.open(player, listOf(995 to 10000)) { player.message("chest action $it") }
        "bag" -> ToaBagUi.open(player, listOf(gg.rsmod.plugins.content.activity.toa.ToaIds.item(27315)?.let { it to 1 })) { slot, a -> player.message("bag $slot $a") }
        "lobby" -> { ToaLobbyHudUi.open(player); ToaLobbyHudUi.draw(player, ToaLobbyHudUi.Status.PARTY, listOf(player.username)) }
        "list" -> ToaPartyListUi.open(player, listOf(ToaPartyListUi.Row(player.username, 1, 0, 0, 0, 0))) { player.message("view party $it") }
        else -> player.message("::toaui party|board|hud|supplies|summary|rewards|bag|lobby|list")
    }
}

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

ToaInterfaces.of(ToaInterfaces.BAG)?.let { iface ->
    (listOf(ToaBagUi.CLOSE) + (0 until ToaBagUi.SLOT_COUNT).map { ToaBagUi.SLOTS + it }).forEach { component ->
        on_button(iface, component) {
            ToaBagUi.click(player, component, gg.rsmod.plugins.content.inter.slayer.SlayerRewardsUi.opOf(player.getInteractingOpcode()))
        }
    }
}

ToaInterfaces.of(ToaInterfaces.REWARDS)?.let { iface ->
    listOf(ToaRewardsUi.CLOSE, ToaRewardsUi.TAKE_ALL, ToaRewardsUi.BANK_ALL, ToaRewardsUi.DISCARD_ALL).forEach { component ->
        on_button(iface, component) { ToaRewardsUi.click(player, component) }
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
