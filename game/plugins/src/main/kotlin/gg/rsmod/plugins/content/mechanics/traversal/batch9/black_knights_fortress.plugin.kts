package gg.rsmod.plugins.content.mechanics.traversal.batch9

import gg.rsmod.plugins.content.mechanics.doors.DoorCrossing
import gg.rsmod.plugins.content.skills.agility.*

/*
 * Black Knights' Fortress secret walls 2341 'Wall' / Push (obstacle batch 9, owner priority #1 2026-10-03): 3016,3517,0 and
 * 3030,3510,1 in this 667 map. Source: 2009scape content/region/asgarnia/falador/quest/blackknightsfortress/BKFortressPlugin.java
 * case 2341: message "You push against the wall. You find a secret passage." then DoorActionHandler.handleAutowalkDoor - the one
 * step through the wall as through a door. Here that step is the shared bounded door crossing (DoorCrossing.cross: the far side
 * of the wall edge from the tile the player stands on, never a slide from elsewhere; "The door is blocked." when not at the wall).
 */
shortcut(2341, "Push") {
    obstacle { wall ->
        player.message("You push against the wall. You find a secret passage.")
        DoorCrossing.cross(this, player, wall)
    }
}
