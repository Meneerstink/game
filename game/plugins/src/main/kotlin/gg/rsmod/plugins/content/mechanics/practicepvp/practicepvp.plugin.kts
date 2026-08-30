package gg.rsmod.plugins.content.mechanics.practicepvp

// Best-effort: player-pre-death plugins run in registration order, which this codebase
// doesn't guarantee relative to death.plugin.kts's handler - see PracticePvp.kt's doc
// comment for the residual (bounded, low-value-gear) risk this leaves if this handler
// happens to run after the normal loot/gravestone resolution for the same death.
on_player_pre_death {
    PracticePvp.cleanup(player)
}

on_logout {
    PracticePvp.cleanup(player)
}

on_command("practice") {
    val arg = player.getCommandArgs().getOrNull(0)?.lowercase()
    val preset =
        when (arg) {
            "range", "ranged" -> PracticePvp.Preset.RANGE
            "mage", "magic" -> PracticePvp.Preset.MAGE
            else -> PracticePvp.Preset.MELEE
        }
    PracticePvp.queueUp(player, preset)
}
