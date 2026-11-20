package gg.rsmod.plugins.content.mechanics.pvp

/*
 * DMM-ARMAGEDDON D8: a new bracket's protection hour that waited for the end of a fight starts as soon as the player is out of
 * combat (DeadmanBrackets.grantPendingProtection); a relog in between keeps it waiting.
 */
on_timer(DeadmanBrackets.PROTECTION_CHECK_TIMER) {
    if (!DeadmanBrackets.grantPendingProtection(player) && player.attr[DeadmanBrackets.PENDING_PROTECTION] == true) {
        player.timers[DeadmanBrackets.PROTECTION_CHECK_TIMER] = DeadmanBrackets.CHECK_CYCLES
    }
}

on_login {
    if (player.attr[DeadmanBrackets.PENDING_PROTECTION] == true) player.timers[DeadmanBrackets.PROTECTION_CHECK_TIMER] = 1
}
