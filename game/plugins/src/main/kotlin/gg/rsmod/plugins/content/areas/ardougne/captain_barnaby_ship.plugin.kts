package gg.rsmod.plugins.content.areas.ardougne

import gg.rsmod.plugins.content.mechanics.pvp.SevenSecondAction
import gg.rsmod.plugins.content.mechanics.pvp.DeadmanTimerGate

/**
 *  Inside Gangplank on Captain Barnaby's ship, Ardougne
 */
on_obj_option(Objs.GANGPLANK_2086, "Cross") {
    DeadmanTimerGate.requestRoute(player, SevenSecondAction.Kind.TRANSPORT) {
        player.teleportTo(2683, 3271, 0)
    }
}

/**
 *  Outside Gangplank on Captain Barnaby's ship, Ardougne
 */
on_obj_option(Objs.GANGPLANK_2085, "Cross") {
    DeadmanTimerGate.requestRoute(player, SevenSecondAction.Kind.TRANSPORT) {
        player.teleportTo(2683, 3268, 1)
        player.message("You must speak to Captain Barnaby before it will set sail.")
    }
}

/**
 *  Top Ladder on Captain Barnaby's ship, Ardougne
 */
on_obj_option(Objs.SHIPS_LADDER_9745, "Climb-down") {
    player.queue {
        player.message("I don't think Captain Barnaby wants me going down there.")
        wait(1)
    }
}

/**
 *  Inside Gangplank on Captain Barnaby's ship, Brimhaven
 */
on_obj_option(Objs.GANGPLANK_2088, "Cross") {
    DeadmanTimerGate.requestRoute(player, SevenSecondAction.Kind.TRANSPORT) {
        player.teleportTo(2772, 3234, 0)
    }
}

/**
 *  Outside Gangplank on Captain Barnaby's ship, Brimhaven
 */
on_obj_option(Objs.GANGPLANK_2087, "Cross") {
    DeadmanTimerGate.requestRoute(player, SevenSecondAction.Kind.TRANSPORT) {
        player.teleportTo(2775, 3234, 1)
        player.message("You must speak to the Customs officer before it will set sail.")
    }
}
