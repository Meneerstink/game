package gg.rsmod.plugins.content.mechanics.pvp

/** Polling is used because this revision has no generic player-step event. */
on_login {
    player.timers[BankSecurity.BANK_ENTRY_MONITOR] = 1
}

on_timer(BankSecurity.BANK_ENTRY_MONITOR) {
    BankSecurity.monitor(player)
    // Deadman PvP guards plan (2026-09-16): the reactive spawn-on-skulled-entry guard pair and
    // Wizguard freeze cover every guarded zone (not only literal bank tiles), so this runs on the
    // same existing per-cycle poll rather than adding a second timer.
    CityGuards.onZoneCheck(player)
    // Movement-interruption check for an in-progress 7-second logout/teleport/portal/transport
    // countdown - same existing per-cycle poll, no dedicated timer added.
    SevenSecondAction.onZoneCheck(player)
    player.timers[BankSecurity.BANK_ENTRY_MONITOR] = 1
}
