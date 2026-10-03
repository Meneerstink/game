package gg.rsmod.plugins.content.mechanics.traversal.batch9

import gg.rsmod.plugins.content.mechanics.lightsource.LightSource
import gg.rsmod.plugins.content.quests.QuestStatus
import gg.rsmod.plugins.content.quests.foundation.DesertTreasure
import gg.rsmod.plugins.content.skills.agility.Render
import gg.rsmod.plugins.content.skills.agility.doorStep
import gg.rsmod.plugins.content.skills.agility.exactMove
import gg.rsmod.plugins.content.skills.agility.face
import gg.rsmod.plugins.content.skills.agility.obstacle
import gg.rsmod.plugins.content.skills.agility.shortcut
import gg.rsmod.plugins.content.skills.agility.walkOverTile
import gg.rsmod.plugins.content.skills.agility.walkToTile

/*
 * Obstacle batch 9 (owner priority #1, 2026-10-03), long tail of agent-handoffs/obstacle-batches/fail-batch-9.tsv rows 153-291.
 * Pure teleports of the same rows are data: data/cfg/object-teleports/batch-9.json. Every landing below was checked on this 667 map
 * with MapProbe (walkable tile beside the counterpart / behind the obstacle).
 *
 * Quest gates: QuestStatus treats every quest that is not one of the server's choice quests as completed (QuestStatus.state), so
 * the post-quest branch of a source applies to Swan Song, Horror from the Deep and the Haunted Mine; Desert Treasure is a choice
 * quest here (foundation/FoundationQuests.kt) and is checked.
 */

// ---- Player-owned house portals of other towns: Rellekka 15480, Brimhaven 15481, Yanille 15482 ------------------------------------
// Every house here is in Rimmington (areas/poh/PlayerHouse.kt). 2009scape PortalOptionPlugin / HouseLocation: a portal of another
// town answers "Your house is in <location>." - the same answer batch 3 gives for the Taverley portal 15477.
for (portal in intArrayOf(15480, 15481, 15482)) {
    shortcut(portal, "Enter") {
        player.message("<col=FF0000>Your house is in Rimmington.")
    }
}

// ---- Haunted Mine (Abandoned Mine) cart tunnels -------------------------------------------------------------------------------
// 2009scape HauntedMineListeners: "Crawl-through" on mine level 1 animates HUMAN_CRAWL 844 and one tick later teleports to the
// surface (4920 -> 3441,3232; 4921 -> 3429,3233; 20524 -> 3428,3225); "Crawl-down" on the three surface cart tunnels enters level 1
// (4913 -> 3436,9637; 4914 -> 3405,9631; 4915 -> 3409,9623). Landings walkable on this map (MapProbe 2026-10-03). The mine is
// reached from the Lair of Tarn Razorlor (Passageway 20814 -> 3424,9660, batch-9.json) and from these surface tunnels; the Haunted
// Mine counts as completed here (QuestStatus), so the tunnels have no quest gate.
val HUMAN_CRAWL = 844
val CART_TUNNEL_OUT =
    mapOf(
        4920 to Tile(3441, 3232, 0),
        4921 to Tile(3429, 3233, 0),
        20524 to Tile(3428, 3225, 0),
    )
val CART_TUNNEL_IN =
    mapOf(
        4913 to Tile(3436, 9637, 0),
        4914 to Tile(3405, 9631, 0),
        4915 to Tile(3409, 9623, 0),
    )
for ((id, landing) in CART_TUNNEL_OUT) {
    shortcut(id, "Crawl-through") {
        obstacle { target ->
            player.faceTile(target.tile)
            player.animate(HUMAN_CRAWL)
            wait(1)
            player.moveTo(landing)
        }
    }
}
for ((id, landing) in CART_TUNNEL_IN) {
    shortcut(id, "Crawl-down") {
        obstacle { target ->
            player.faceTile(target.tile)
            player.animate(HUMAN_CRAWL)
            wait(1)
            player.moveTo(landing)
        }
    }
}

// ---- Jaldraocht Pyramid back door: Tunnel 6481 (3233,2888) -------------------------------------------------------------------------
// 2009scape DesertTreasureListeners "Backdoor": after Desert Treasure the tunnel leads to the pyramid's first level (3233,9313);
// before it: "This passage does not seem to lead anywhere...". The pyramid ladders are batch-9.json (same source).
shortcut(6481, "Enter") {
    if (!QuestStatus.isCompleted(player, DesertTreasure)) {
        player.message("This passage does not seem to lead anywhere...")
        return@shortcut
    }
    obstacle {
        player.moveTo(Tile(3233, 9313, 0))
    }
}

// ---- Lumbridge castle cellar: the two holes 6912 (3224,9603 / 3224,9601) -----------------------------------------------------------
// 2009scape LumbridgeBasementPlugin "squeeze-through": "You squeeze through the hole." and a forced move with animation 2240 from
// the hole at y 9603 to 3224,9601 (south) and from the hole at y 9601 to 3224,9603 (north). Map: 3224,9603 (wall on its south
// edge) and 3224,9601 (wall on its north edge) are walkable, 3224,9602 between them is the solid rock the player squeezes through.
val ANIM_SQUEEZE_HOLE = 2240
shortcut(6912, "Squeeze-through") {
    obstacle { target ->
        val south = target.tile.z == 9603
        val front = Tile(3224, if (south) 9603 else 9601, 0)
        val behind = Tile(3224, if (south) 9601 else 9603, 0)
        walkToTile(player, front)
        face(player, if (south) Direction.SOUTH else Direction.NORTH)
        player.message("You squeeze through the hole.")
        player.animate(ANIM_SQUEEZE_HOLE)
        exactMove(player, behind, delay = 60, direction = if (south) Direction.SOUTH else Direction.NORTH)
        player.animate(-1)
    }
}

// ---- Sophanem east wall: Hole 6620 (3321,2796) ---------------------------------------------------------------------------------------
// 2009scape region/desert/sophanem/handlers/WallShortcut: 21 Agility (dialogue "You need an Agility level of at least 21 to do
// this."), a forced move from 3320,2796 into the hole with CLIMB_DOWN 2589, then CRAWL_THROUGH 2590 and the player comes out at
// 3324,2796 outside the double city wall, CLIMB_UP 2591. Map: 3320,2796 / 3321,2796 / 3324,2796 walkable (3322 = the wall).
shortcut(6620, "Climb-through") {
    if (player.skills.getCurrentLevel(gg.rsmod.plugins.api.Skills.AGILITY) < 21) {
        player.queue { messageBox("You need an Agility level of at least 21 to do this.") }
        return@shortcut
    }
    obstacle {
        walkToTile(player, Tile(3320, 2796, 0))
        face(player, Direction.EAST)
        player.animate(2589)
        exactMove(player, Tile(3321, 2796, 0), delay = 30, direction = Direction.EAST)
        player.animate(2590)
        wait(1)
        player.moveTo(Tile(3324, 2796, 0))
        wait(1)
        player.animate(2591)
        wait(1)
    }
}

// ---- Piscatoris fishing colony fence holes 14922 (2344,3651 / 2344,3654) -----------------------------------------------------------
// 2009scape TunnelShortcut (14922, level 1, "enter", Swan Song required - completed here, see header): from the tile in front of the
// hole (loc + 1 in the loc's direction) CLIMB_DOWN 2589 into the hole, CRAWL_THROUGH 2590 under the fence, CLIMB_UP 2591 out on the
// far side, five tiles from the start. Map: the fence line is z 3652-3653; 2344,3650 (in front of the south hole) and 2344,3655 (in
// front of the north hole) are walkable. The crawl under the fence is a tunnel (the player is out of sight), so it is one move
// between the two hole fronts - never a slide through the fence.
shortcut(14922, "Enter") {
    obstacle { target ->
        val north = target.tile.z == 3651
        val front = Tile(2344, if (north) 3650 else 3655, 0)
        val out = Tile(2344, if (north) 3655 else 3650, 0)
        walkToTile(player, front)
        face(player, if (north) Direction.NORTH else Direction.SOUTH)
        player.animate(2589)
        wait(2)
        player.animate(2590)
        player.moveTo(out)
        wait(1)
        face(player, if (north) Direction.NORTH else Direction.SOUTH)
        player.animate(2591)
        wait(1)
    }
}

// ---- Karamja: the log over the stream 2332 (2907,3049 / 2909,3049) -------------------------------------------------------------------
// 2009scape LogBalanceShortcut (2332, level 1, xp 1, "cross"): ends 2910,3049 and 2906,3049, the player walks to the far end with
// the log-balance render 155 (same walk as the Coal Truck log 2296 in sourced_shortcuts). Map: both ends walkable, the logs lie on
// the river tiles between them.
shortcut(2332, "Cross") {
    obstacle {
        val east = Tile(2910, 3049, 0)
        val west = Tile(2906, 3049, 0)
        val start = if (player.tile.getDistance(east) <= player.tile.getDistance(west)) east else west
        val end = if (start == east) west else east
        walkToTile(player, start)
        walkOverTile(player, end, Render.ROPE_BALANCE)
        player.addXp(gg.rsmod.plugins.api.Skills.AGILITY, 1.0)
    }
}

// ---- Lighthouse doorway 4577 (2509,3636) ---------------------------------------------------------------------------------------------
// 2009scape HorrorFromTheDeepListeners: with Horror from the Deep complete (completed here, see header) the doorway is an ordinary
// walk-through door (DoorActionHandler.handleAutowalkDoor). Map: the doorway is the south wall of 2509,3636; 2509,3635 outside.
shortcut(4577, "Walk-through") {
    obstacle {
        val far = if (player.tile.z >= 3636) Tile(2509, 3635, 0) else Tile(2509, 3636, 0)
        doorStep(player, far)
    }
}

// ---- Temple of Ikov: the top stairs 35121 (2650,9804) --------------------------------------------------------------------------------
// 2009scape TempleOfIkovListeners: with a lit light source -> 2641,9763 (beside the lit stairs 96); without -> 2641,9740 and the
// dialogue "Hmm...bit dark down here! I'm not going to venture far!". Both landings walkable on this map.
shortcut(35121, "Climb-down") {
    obstacle {
        if (LightSource.getActiveLightSource(player) != null) {
            player.moveTo(Tile(2641, 9763, 0))
        } else {
            player.moveTo(Tile(2641, 9740, 0))
            messageBox("Hmm...bit dark down here! I'm not going to venture far!")
        }
    }
}

// ---- Port Phasmatys: the moored ship's gangplank 11209 / 11210 (3710,3496 / 3711,3496) -------------------------------------------
// 2009scape PortsObjectPlugin case 11209 ("phasmatys (other boat)"): the player says "I don't think that whoever owns this ship will
// be happy" / "with me wandering all over it." and stays on the quay. 11210 is the other half of the same gangplank.
for (plank in intArrayOf(11209, 11210)) {
    shortcut(plank, "Cross") {
        player.queue {
            chatPlayer("I don't think that whoever owns this ship will be happy", "with me wandering all over it.")
        }
    }
}
