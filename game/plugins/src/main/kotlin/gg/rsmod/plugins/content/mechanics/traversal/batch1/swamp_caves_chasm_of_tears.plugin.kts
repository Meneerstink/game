package gg.rsmod.plugins.content.mechanics.traversal.batch1

import gg.rsmod.plugins.content.skills.agility.add
import gg.rsmod.plugins.content.skills.agility.exactMove
import gg.rsmod.plugins.content.skills.agility.face
import gg.rsmod.plugins.content.skills.agility.obstacle
import gg.rsmod.plugins.content.skills.agility.shortcut
import gg.rsmod.plugins.content.skills.agility.walkToTile

/*
 * Obstacle batch 1 (owner priority #1, 2026-10-03): Lumbridge Swamp Caves and the Chasm of Tears.
 * Ported from Void `area/misthalin/lumbridge/swamp/LumbridgeSwampCave.kt` (stepping stone 5949) and
 * `area/misthalin/lumbridge/swamp/chams_of_tears/ChasmOfTears.kt` (rocks 6672 / 6673). Ids resolved through Void's toml:
 * stepping_stone_step 769, chasm_of_tears_climb_slope_up 2049, chasm_of_tears_climb_slope_down 2050 (anims), jump 2461,
 * climbing_loop 2454 (sounds). Reachable: the swamp caves are entered from Lumbridge Swamp, the chasm by tunnel 32944
 * (object-teleports.json, Void chasm_of_tears.teles.toml); the OSRS Wiki shortcut list has the stepping stones as the access
 * to the Chasm of Tears. Every crossing starts on the tile in front of the obstacle (collision-respecting approach) and is
 * a forced move to the tile behind it (E17: never a walk through a loc).
 */

val ANIM_STEPPING_STONE_STEP = 769
val ANIM_CLIMB_SLOPE_UP = 2049
val ANIM_CLIMB_SLOPE_DOWN = 2050
val SOUND_JUMP = 2461
val SOUND_CLIMBING_LOOP = 2454

// Void LumbridgeSwampCave: "Jump-across" lumbridge_cave_stepping_stone - two leaps over the stone, north or south.
shortcut(5949, "Jump-across") {
    obstacle { target ->
        val direction = if (player.tile.z > target.tile.z) Direction.SOUTH else Direction.NORTH
        val back = direction.getOpposite()
        walkToTile(player, target.tile.add(back).add(back))
        face(player, direction)
        player.filterableMessage("You leap across with a mighty leap!")
        player.animate(ANIM_STEPPING_STONE_STEP, delay = 30)
        player.playSound(SOUND_JUMP, delay = 35)
        exactMove(player, target.tile, delay = 70, direction = direction, startDelay = 58)
        wait(2)
        player.animate(ANIM_STEPPING_STONE_STEP, delay = 30)
        player.playSound(SOUND_JUMP, delay = 35)
        exactMove(player, target.tile.add(direction).add(direction), delay = 70, direction = direction, startDelay = 58)
    }
}

// Void ChasmOfTears: "Climb" chasm_of_tears_rocks - up (west) or down (east) the slope, one tile past the rock.
shortcut(6673, "Climb") {
    obstacle { target ->
        val direction = if (player.tile.x < target.tile.x) Direction.EAST else Direction.WEST
        walkToTile(player, target.tile.add(direction.getOpposite()))
        player.animate(if (direction == Direction.EAST) ANIM_CLIMB_SLOPE_DOWN else ANIM_CLIMB_SLOPE_UP)
        face(player, direction)
        player.playSound(SOUND_CLIMBING_LOOP, loops = 5)
        exactMove(player, target.tile.add(direction), delay = 100, direction = Direction.WEST)
    }
}

// Void ChasmOfTears: "Climb" chasm_of_tears_rocks_down - one way, down (east) only.
shortcut(6672, "Climb") {
    obstacle { target ->
        if (player.tile.x >= target.tile.x) {
            player.message("You could climb down here, but it is too uneven to climb up.")
            return@obstacle
        }
        val direction = Direction.EAST
        walkToTile(player, target.tile.add(direction.getOpposite()))
        player.animate(ANIM_CLIMB_SLOPE_DOWN)
        face(player, direction)
        player.playSound(SOUND_CLIMBING_LOOP, loops = 5)
        exactMove(player, target.tile.add(direction), delay = 100, direction = Direction.WEST)
    }
}
