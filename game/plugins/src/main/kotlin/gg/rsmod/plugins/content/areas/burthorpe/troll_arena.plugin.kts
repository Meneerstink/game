package gg.rsmod.plugins.content.areas.burthorpe

import gg.rsmod.plugins.content.mechanics.doors.DoorCrossing
import gg.rsmod.plugins.content.quests.foundation.bracket.TrollStronghold

// PROJECT48 run 4 O16 (God Wars Dungeon access): Dad's arena on the Trollheim pass (667 "Arena entrance" 34836/34839, west gate
// 2912,3609-3610 from Death Plateau, north gate 2927-2928,3619 to the Troll Stronghold). Before this the cache-derived door script
// swung a leaf open for the whole world (and it stayed open), so anyone walked past Dad to the stronghold. Now the gate is a
// per-player crossing (Void enterDoor, DoorCrossing), and the north gate out of the arena only lets a player through who has beaten
// Dad (Troll Stronghold step 2 or later). Sources: OSRS Wiki God Wars Dungeon ("requires partial completion of Troll Stronghold
// (defeating Dad)"), Transcript:Troll Stronghold "Entering Dad's Arena" (Dad's line), 2009scape TrollStrongholdListener (arena exit
// refused until Dad is defeated). The west gate stays open to everybody: no source refuses it in OSRS.

private val NORTH_GATE_Z = 3619
private val DAD_DEFEATED_STAGE = 2

listOf(34836, 34839).forEach { gate ->
    on_obj_option(obj = gate, option = "Open") {
        val door = player.getInteractingGameObj()
        val leavingArena = door.tile.z == NORTH_GATE_Z && player.tile.z < NORTH_GATE_Z
        if (leavingArena && TrollStronghold.stage(player) < DAD_DEFEATED_STAGE) {
            player.queue {
                chatNpc("No human pass through arena without defeating Dad!", npc = Npcs.DAD, facialExpression = FacialExpression.ANGRY)
            }
            return@on_obj_option
        }
        val other = if (door.id == 34836) 34839 else 34836
        player.lockingQueue(lockState = LockState.FULL) {
            DoorCrossing.enter(this, player, door, openedId = door.id, open = {
                DoorCrossing.swingOpenFor(player, door, door.id, ticks = 2, otherLeaves = mapOf(other to other))
            })
            player.lock = LockState.NONE
        }
    }
}
