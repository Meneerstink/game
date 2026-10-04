package gg.rsmod.plugins.content.mechanics.traversal.batch1

import gg.rsmod.plugins.content.skills.agility.Render
import gg.rsmod.plugins.content.skills.agility.add
import gg.rsmod.plugins.content.skills.agility.darkanForceMove
import gg.rsmod.plugins.content.skills.agility.exactMove
import gg.rsmod.plugins.content.skills.agility.face
import gg.rsmod.plugins.content.skills.agility.hasAgility
import gg.rsmod.plugins.content.skills.agility.obstacle
import gg.rsmod.plugins.content.skills.agility.shortcut
import gg.rsmod.plugins.content.skills.agility.walkOverTile
import gg.rsmod.plugins.content.skills.agility.walkToTile

/*
 * Obstacle batch 1 (owner priority #1, 2026-10-03).
 *
 * Wrecked ghost ship near Port Phasmatys (OSRS Wiki shortcut list: "Rocks - Wrecked ghost ship near Port Phasmatys", level 25),
 * Void `area/morytania/port_phasmatys/WreckedGhostShip.kt`: gangplanks 5285 / 5286 and the rocks 5269. Ids through Void's
 * toml: rock_jump 1604 (anim), jump 2461 (sound). Reachable: Port Phasmatys by the Ports ship, the wrecked ship's ladders
 * 5265 / 5266 are in object-teleports.json. Void marks the jump's failure rate "unknown", so only the success path is ported
 * (as sourced_shortcuts does for every "Unknown rate").
 *
 * Rocky handholds in the Rellekka Hunter area (OSRS Wiki shortcut list: "Rocky handholds - Rellekka Hunter area, 35 Agility,
 * north-east access to the Polar eagle cave"), Void `quest/member/eagles_peak/EaglesEyries.kt`: 19846 (bottom) / 19847 (top).
 * rocks_climb_down 740 (anim), "climbing" render (Render.CLIMBING).
 */

val ANIM_ROCK_JUMP = 1604
val ANIM_ROCKS_CLIMB_DOWN = 740
val SOUND_JUMP = 2461

// ---- Wrecked ghost ship gangplanks (Void: walkOverDelay onto the plank, then tele) ----
shortcut(5285, "Cross") {
    player.lockingQueue(lockState = gg.rsmod.game.model.LockState.FULL) {
        wait(1)
        player.moveTo(3605, 3548, 0)
        player.filterableMessage("You cross the gangplank.")
    }
}

shortcut(5286, "Cross") {
    player.lockingQueue(lockState = gg.rsmod.game.model.LockState.FULL) {
        wait(1)
        player.moveTo(3605, 3545, 1)
        player.filterableMessage("You cross the gangplank.")
    }
}

// ---- Wrecked ghost ship rocks (Void WreckedGhostShip.rocks: each rock jumps two tiles to its partner rock) ----
val GHOST_SHIP_ROCKS =
    mapOf(
        (3604 to 3550) to Direction.WEST,
        (3602 to 3550) to Direction.EAST,
        (3599 to 3552) to Direction.WEST,
        (3597 to 3552) to Direction.EAST,
        (3595 to 3554) to Direction.NORTH,
        (3595 to 3556) to Direction.SOUTH,
        (3597 to 3559) to Direction.NORTH,
        (3597 to 3561) to Direction.SOUTH,
        (3599 to 3564) to Direction.EAST,
        (3601 to 3564) to Direction.WEST,
    )

// Darkan ShipAgility.kt:41-75 (mapShipRocks / jumpGap): route to the clicked rock, then anim 3067, drain 5 % of the run energy,
// forceMove(the partner rock's tile, 5, 60) from the route tile, 10 xp. Replaces Void's walk onto the clicked rock + 1604 (delay 26)
// and a 47/59 slide. Void's level 25 is kept.
shortcut(5269, "Jump-to") {
    obstacle { target ->
        val direction = GHOST_SHIP_ROCKS[target.tile.x to target.tile.z] ?: return@obstacle
        if (!player.hasAgility(25, "You need level 25 agility to make that jump.")) return@obstacle
        val partner = target.tile.add(direction, 2)
        player.runEnergy = (player.runEnergy * 0.95).coerceAtLeast(0.0)
        player.playSound(SOUND_JUMP)
        darkanForceMove(player, Tile(partner.x, partner.z, player.tile.height), startCycles = 5, speedCycles = 60, anim = 3067)
        player.addXp(Skills.AGILITY, 10.0)
    }
}

// ---- Rellekka Hunter area rocky handholds (Void EaglesEyries) ----
shortcut(19846, "Climb") {
    obstacle { target ->
        if (!player.hasAgility(35, "You must have an Agility level of at least 35 to climb these rocks.")) {
            return@obstacle
        }
        val h = target.tile.height
        walkToTile(player, Tile(2740, 3830, h))
        wait(1)
        face(player, Direction.EAST)
        walkOverTile(player, Tile(2744, 3830, h), Render.CLIMBING)
        player.addXp(Skills.AGILITY, 1.0)
    }
}

shortcut(19847, "Climb") {
    obstacle { target ->
        if (!player.hasAgility(35, "You must have an Agility level of at least 35 to climb these rocks.")) {
            return@obstacle
        }
        val h = target.tile.height
        walkToTile(player, Tile(2744, 3830, h))
        face(player, Direction.EAST)
        player.animate(ANIM_ROCKS_CLIMB_DOWN, delay = 15)
        exactMove(player, Tile(2740, 3830, h), delay = 90, direction = Direction.EAST)
        player.addXp(Skills.AGILITY, 1.0)
    }
}
