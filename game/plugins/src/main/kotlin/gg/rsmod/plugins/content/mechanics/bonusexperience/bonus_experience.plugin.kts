package gg.rsmod.plugins.content.mechanics.bonusexperience

import gg.rsmod.game.model.timer.BONUS_EXPERIENCE_TIME_ELAPSED

/**
 * @author Alycia <https://github.com/alycii>
 */

val ENABLED_VARBIT = 7232
val TIME_ELAPSED_VARBIT = 7233
val REFRESH_BONUS_EXP_SCRIPT = 776
val XP_EARNED_VARP = 1878

on_timer(key = BONUS_EXPERIENCE_TIME_ELAPSED) {
    player.setVarbit(TIME_ELAPSED_VARBIT, player.getVarbit(TIME_ELAPSED_VARBIT).plus(1))
    player.runClientScript(REFRESH_BONUS_EXP_SCRIPT)
    player.timers[BONUS_EXPERIENCE_TIME_ELAPSED] = 100
}

// Owner 2026-10-11 (tab audit): the orb sprite sits on the gameframe top level itself, which every rebuild (window-mode switch,
// world map close) sends again with the cache's sprite - restore the bonus-XP orb while it is on.
gg.rsmod.plugins.api.ext.GameframeRebuild.register("bonus_experience") { p ->
    if (p.getVarbit(ENABLED_VARBIT) == 1) {
        p.setComponentSprite(interfaceId = 548, component = 0, sprite = 5568)
        p.setComponentSprite(interfaceId = 746, component = 229, sprite = 5568)
    }
}

on_login {
    /*
     * N92 (owner 2026-10-17 "VERVANGEN: alleen de Annihilation-rate telt"): the time-played bonus XP no longer exists -
     * Player.addXp gains at the Annihilation tier rate only - so the orb never promises a bonus, whatever the server's
     * bonus-experience flag or ::bonusxp say. A login clears what an older session left (timer, counter, time, orb).
     */
    if (player.timers.has(BONUS_EXPERIENCE_TIME_ELAPSED)) {
        player.timers.remove(BONUS_EXPERIENCE_TIME_ELAPSED)
    }

    // Set their bonus experience gained varp to 0
    player.setVarp(XP_EARNED_VARP, 0)

    // Set their time elapsed varbit to 0
    player.setVarbit(TIME_ELAPSED_VARBIT, 0)

    // Disable the bonus experience counter
    player.setVarbit(ENABLED_VARBIT, 0)

    // Refresh the orb
    player.runClientScript(REFRESH_BONUS_EXP_SCRIPT)
    player.setComponentSprite(interfaceId = 548, component = 0, sprite = 2730)
    player.setComponentSprite(interfaceId = 746, component = 229, sprite = 2730)
}
