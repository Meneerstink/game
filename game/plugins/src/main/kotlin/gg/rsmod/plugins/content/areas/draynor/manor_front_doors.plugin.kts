package gg.rsmod.plugins.content.areas.draynor

import gg.rsmod.plugins.content.mechanics.doors.DoorCrossing

val closedDoorIds = listOf(Objs.LARGE_DOOR_47421, Objs.LARGE_DOOR_47424)

/** Closed leaf -> opened leaf of the manor's front double door. */
val MANOR_DOOR_LEAVES = mapOf(Objs.LARGE_DOOR_47421 to Objs.LARGE_DOOR_47423, Objs.LARGE_DOOR_47424 to Objs.LARGE_DOOR_47425)

/** Void draynor_manor sounds: big_door_close. */
val SFX_BIG_DOOR_CLOSE = 45

closedDoorIds.forEach { id ->
    on_obj_option(id, "Open") {
        val door = player.getInteractingGameObj()
        if (player.tile.z <= 3353) {
            // Void DraynorManor `enterDoor`: up to the doorway with collision, both leaves swing open on their hinges, the player
            // walks through, the doors slam shut. The old script teleported to x 3108/3109 from wherever the click left the player
            // and stood loc 0 "blocking objects" in the doorway (audit 2026-10-04 fork B).
            player.lockingQueue(lockState = LockState.FULL) {
                val crossed =
                    DoorCrossing.enter(
                        this, player, door,
                        open = { DoorCrossing.swingOpenFor(player, door, MANOR_DOOR_LEAVES.getValue(id), ticks = 2, otherLeaves = MANOR_DOOR_LEAVES) },
                    )
                if (crossed) {
                    wait(2)
                    player.message("The doors slam shut behind you.")
                    player.playSound(SFX_BIG_DOOR_CLOSE)
                }
            }
        } else {
            player.lockingQueue(lockState = LockState.FULL) {
                player.message("The doors won't open.")
                wait(1)
            }
        }
    }
}
