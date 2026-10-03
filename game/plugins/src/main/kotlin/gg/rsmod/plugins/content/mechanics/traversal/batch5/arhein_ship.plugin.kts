package gg.rsmod.plugins.content.mechanics.traversal.batch5

import gg.rsmod.plugins.api.cfg.FacialExpression
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.content.skills.agility.shortcut

/*
 * Arhein's ship gangplank 69 at Catherby (2805,3421; obstacle batch 5, owner priority #1 2026-10-03).
 * Source: Void area/kandarin/catherby/Arheins.kt objectOperate("Cross", "arhein_ship_gangplank") - nobody boards: Arhein says
 * "Hey buddy! Get away from my ship alright?" and the player answers "Yeah... uh... sorry..." (Void npc/player Neutral). The ship
 * deck and its gangplank 70 are only for One Small Favour (not on this server, Void TODO).
 */

val ARHEIN_SHIP_GANGPLANK = 69

on_world_init {
    shortcut(ARHEIN_SHIP_GANGPLANK, "Cross") {
        player.queue {
            chatNpc("Hey buddy! Get away from my ship alright?", npc = Npcs.ARHEIN, facialExpression = FacialExpression.NORMAL)
            chatPlayer("Yeah... uh... sorry...", facialExpression = FacialExpression.NORMAL)
        }
    }
}
