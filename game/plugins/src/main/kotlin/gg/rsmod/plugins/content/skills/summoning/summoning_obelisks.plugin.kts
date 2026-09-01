package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.plugins.api.cfg.Objs

/** Revision-667 Summoning obelisks that expose the real Renew-Points action. */
val summoningObelisks = intArrayOf(
    Objs.SUMMONING_OBELISK,
    Objs.SUMMONING_OBELISK_50205,
    Objs.SUMMONING_OBELISK_50206,
    Objs.SUMMONING_OBELISK_50207,
    Objs.SUMMONING_OBELISK_53883,
    Objs.SUMMONING_OBELISK_54650,
    Objs.SUMMONING_OBELISK_55605,
    Objs.SUMMONING_OBELISK_56083,
    Objs.SUMMONING_OBELISK_56084,
    Objs.SUMMONING_OBELISK_56085,
    Objs.SUMMONING_OBELISK_56086,
)

summoningObelisks.filter { if_obj_has_option(it, "Renew-Points") }.forEach { obelisk ->
    on_obj_option(obelisk, "Renew-Points") {
        Familiar.restorePoints(player)
        Familiar.restoreSpecialPoints(player, Familiar.MAX_SPECIAL_POINTS)
        player.message("You renew your Summoning points and special-move energy.")
    }
}
