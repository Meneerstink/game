package gg.rsmod.plugins.content.mechanics.freeze

// PvP freeze range check (FreezeLeash): re-armed every tick while a player-on-player freeze lasts.
on_timer(FreezeLeash.CHECK_TIMER) {
    val target = pawn as? Player ?: return@on_timer
    FreezeLeash.check(target)
}
