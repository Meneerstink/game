package gg.rsmod.plugins.content.mechanics.prayer

/**
 * BATCH 2: extended from unlock/turmoil-only to the full book: `curse book normal|ancient`
 * switches books, `curse <name>` (e.g. `curse sap warrior`, `curse deflect melee`) toggles
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
                else -> player.filterableMessage("Usage: curse book normal | curse book ancient")
            }
        }
        else -> {
            val curse = AncientCurse.byCommand(args.joinToString(" "))
            if (curse != null) {
                AncientCurses.toggleCurse(player, curse)
            } else {
                player.filterableMessage(
                    "Usage: curse unlock | curse book <normal|ancient> | curse turmoil | curse <curse name>",
                )
            }
        }
    }
}

on_logout {
    AncientCurses.deactivateAllCurses(player)
    AncientCurses.clearDrainState(player)
}

/** Sap/Leech escalation restarts from the base drain on a fresh target. */
on_npc_killed { _, npc -> AncientCurses.clearDrainState(npc) }

// The death hook runs after PlayerDeathAction has respawned and reset the player. Wrath fires at the moment of death, before
// the death animation, just like Retribution (Void PlayerDeath wrath(this)).
on_player_death_start {
    if (AncientCurses.isCurseActive(player, AncientCurse.WRATH)) {
        AncientCurses.wrathExplosion(player)
    }
}

on_player_death {
    AncientCurses.deactivateAllCurses(player)
    AncientCurses.clearDrainState(player)
}
