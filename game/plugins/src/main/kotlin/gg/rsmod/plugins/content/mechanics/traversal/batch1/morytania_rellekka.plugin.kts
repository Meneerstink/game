package gg.rsmod.plugins.content.mechanics.traversal.batch1

import gg.rsmod.plugins.content.skills.agility.Render
import gg.rsmod.plugins.content.skills.agility.add
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

shortcut(5269, "Jump-to") {
    obstacle { target ->
        val direction = GHOST_SHIP_ROCKS[target.tile.x to target.tile.z] ?: return@obstacle
        // Void objectApproach: the jump goes from the clicked rock to its partner when the player comes from the rock's own
        // side; from the partner's side Void only walks onto the clicked rock.
        val sameSide =
            when (direction) {
                Direction.NORTH -> player.tile.z <= target.tile.z
                Direction.EAST -> player.tile.x <= target.tile.x
                Direction.SOUTH -> player.tile.z >= target.tile.z
                Direction.WEST -> player.tile.x >= target.tile.x
                else -> false
            }
        walkToTile(player, target.tile)
        if (!sameSide) {
            return@obstacle
        }
        wait(1)
        if (!player.hasAgility(25, "You need level 25 agility to make that jump.")) {
            player.queue { messageBox("You need level 25 agility to make that jump.") }
            return@obstacle
        }
        // Void: 500 of 10000 run energy = 5%.
        if (player.runEnergy < 5.0) {
            player.message("You don't have enough energy to make that jump")
            return@obstacle
        }
        face(player, direction)
        player.animate(ANIM_ROCK_JUMP, delay = 26)
        player.playSound(SOUND_JUMP)
        exactMove(player, target.tile.add(direction, 2), delay = 59, direction = direction, startDelay = 47)
        player.runEnergy = (player.runEnergy - 5.0).coerceAtLeast(0.0)
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
