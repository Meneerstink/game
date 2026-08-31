package gg.rsmod.plugins.content.mechanics.prayer

/**
 * BATCH 2: extended from unlock/turmoil-only to the full book: `::curse book normal|ancient`
 * switches books, `::curse <name>` (e.g. `::curse sap warrior`, `::curse deflect melee`) toggles
 * one of the 19 curses in [AncientCurse] by its display name (spaces allowed, case-insensitive).
 */
on_command("curse") {
    val args = player.getCommandArgs()
    val arg = args.getOrNull(0)?.lowercase()
    when (arg) {
        "unlock" -> AncientCurses.unlock(player)
        "turmoil" -> AncientCurses.toggleTurmoil(player)
        "book" -> {
            when (args.getOrNull(1)?.lowercase()) {
                "normal" -> AncientCurses.switchBook(player, AncientCurses.PrayerBook.NORMAL)
                "ancient" -> AncientCurses.switchBook(player, AncientCurses.PrayerBook.ANCIENT)
                else -> player.filterableMessage("Usage: ::curse book normal | ::curse book ancient")
            }
        }
        else -> {
            val curse = AncientCurse.byCommand(args.joinToString(" "))
            if (curse != null) {
                AncientCurses.toggleCurse(player, curse)
            } else {
                player.filterableMessage(
                    "Usage: ::curse unlock | ::curse book <normal|ancient> | ::curse turmoil | ::curse <curse name>",
                )
            }
        }
    }
}

on_logout {
    AncientCurses.deactivateAllCurses(player)
}

on_player_death {
    if (AncientCurses.isCurseActive(player, AncientCurse.WRATH)) {
        AncientCurses.wrathExplosion(player)
    }
    AncientCurses.deactivateAllCurses(player)
}
