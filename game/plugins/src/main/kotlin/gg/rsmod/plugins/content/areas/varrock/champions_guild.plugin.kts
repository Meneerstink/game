package gg.rsmod.plugins.content.areas.varrock

on_obj_option(Objs.TRAPDOOR_10558, "Open") {
    // TODO Add opening when Champion's Challenge is released
    player.queue {
        wait(1)
        messageBox("The Champion's Challenge is not currently available.")
    }
}

on_obj_option(Objs.CLOSED_CHEST_24203, "Open") {
    player.lockingQueue(lockState = LockState.FULL) {
        val closedChest = player.getInteractingGameObj()
        wait(1)
        player.animate(Anims.REACH_FORWARD)
        wait(2)
        world.spawn(DynamicObject(Objs.OPEN_CHEST_24204, closedChest.type, closedChest.rot, closedChest.tile))
    }
}

on_obj_option(Objs.OPEN_CHEST_24204, "Search") {
    player.lockingQueue(lockState = LockState.FULL) {
        wait(1)
        player.message("You search the chest but find nothing.")
        wait(1)
    }
}

on_obj_option(Objs.OPEN_CHEST_24204, "Shut") {
    player.lockingQueue(lockState = LockState.FULL) {
        val closedChest = player.getInteractingGameObj()
        wait(1)
        player.animate(Anims.REACH_FORWARD)
        wait(2)
        world.spawn(DynamicObject(Objs.CLOSED_CHEST_24203, closedChest.type, closedChest.rot, closedChest.tile))
    }
}

on_obj_option(Objs.DOOR_1805, "Open") {
    if (!player.completedAllQuests()) {
        // TODO: Change once there are at least 33 quest points to the correct message
        player.queue {
            messageBox("You must complete all quests before you can enter the Champions Guild.")
        }
        return@on_obj_option
    }

    // Void ChampionsGuild `enterDoor`: up to the door with collision, the door swings open on its hinge, the player walks through
    // the doorway. The old script walked a collision-free line from the click tile (through the guild wall from any x but 3191) and
    // stood a loc-0 "blocking object" in the doorway (audit 2026-10-04 fork B).
    val door = player.getInteractingGameObj()
    val entering = player.tile.z >= 3363
    player.lockingQueue(lockState = LockState.FULL) {
        val opened = gg.rsmod.plugins.content.mechanics.doors.VoidDoors.knownClosedToOpened[Objs.DOOR_1805] ?: Objs.DOOR_23637
        val crossed =
            gg.rsmod.plugins.content.mechanics.doors.DoorCrossing.enter(
                this, player, door,
                open = { gg.rsmod.plugins.content.mechanics.doors.DoorCrossing.swingOpenFor(player, door, opened, ticks = 2) },
            )
        player.lock = LockState.NONE
        if (crossed && entering) {
            chatNpc(npc = Npcs.GUILDMASTER,
                message = arrayOf("Greetings bold adventurer. Welcome to the guild of Champions."),
                facialExpression = FacialExpression.HAPPY_TALKING,
                wrap = true
            )
        }
    }
}
