package gg.rsmod.plugins.content.areas.wilderness.bosses

/**
 * N522 (owner 2026-11-04 17:0x): Callisto's Den, Vet'ion's Rest, Hunter's End and the Skeletal Tomb ([WildernessLairs.LAIRS]).
 * The entrances share the lair fee ([WildernessLairs.enter]); "Peek" like the spider lairs (OSRS Wiki: 20 kills of the boss); the
 * big lairs leave into the Escape Caves (same point within 10 seconds per lair), the small ones back to the surface.
 */

WildernessLairs.LAIRS.forEach { lair ->
    // OSRS ops: "Enter" on the caves and the Memorial, "Jump-Down" on Vet'ion's Crevice (cache 2686 loc defs 47140/47141/46996/46995).
    val enter = if (lair.entrance == WildernessLairs.VETION_REST_ENTRANCE) "jump-down" else "enter"
    on_obj_option(obj = lair.entrance, option = enter) {
        WildernessLairs.enter(player) { WildernessLairs.crawl(player, lair.arrival) }
    }

    on_obj_option(obj = lair.entrance, option = "peek") {
        val kc = lair.bosses.firstOrNull()?.let { runCatching { player.getNpcKillCount(it) }.getOrDefault(0) } ?: 0
        if (kc < 20) {
            // The messages: Near Reality WildernessBossEntrance (as for the Silk / Web Chasm).
            player.message(
                "You don't know this cave well enough to glean any useful information. You feel that you'll be more succesful once " +
                    "you've killed whatever is inside, ${20 - kc} times.",
            )
            return@on_obj_option
        }
        val count = world.players.count { lair.contains(it.tile) }
        player.message(
            if (count > 0) "You peek into the darkness and can make out some movement. There is activity inside."
            else "You peek into the darkness and can make out no movement. There is no activity inside.",
        )
    }

    on_obj_option(obj = lair.entrance, option = "check-fee") {
        player.queue { itemMessageBox(WildernessLairs.checkFeeText(player), item = gg.rsmod.plugins.api.cfg.Items.COINS_995, amountOrZoom = 100) }
    }
}

// The exits: one loc type serves two lairs each, told apart by its tile.
listOf(WildernessLairs.DEN_EXIT, WildernessLairs.REST_EXIT).forEach { id ->
    on_obj_option(obj = id, option = "exit") {
        val lair = WildernessLairs.lairByExit(id, player.getInteractingGameObj().tile) ?: return@on_obj_option
        val dest =
            lair.surfaceExit
                ?: WildernessLairs.lairExitPoint(lair.entrance, world.currentCycle) { world.random(WildernessLairs.LAIR_EXIT_POINTS.size - 1) }
        WildernessLairs.crawl(player, dest)
    }
}

/**
 * N522 nachtrun deel 6: OSRS Wiki "Escape Caves": logging out inside a boss lair "counts as an exit", "the player arriving at one of the
 * three boss lair exits at random" (the same one within 10 seconds per lair, [WildernessLairs.lairExitPoint]). The big lairs only - the
 * small ones (Hunter's End, Skeletal Tomb, Web Chasm) leave to the surface and the Wiki names no logout rule for them.
 */
on_logout {
    WildernessLairs.logoutExit(player.tile, world.currentCycle) { world.random(WildernessLairs.LAIR_EXIT_POINTS.size - 1) }?.let { player.moveTo(it) }
}
