package gg.rsmod.plugins.content.mechanics.traversal.batch4

import gg.rsmod.plugins.content.skills.agility.*
import java.util.concurrent.ThreadLocalRandom

/*
 * Mort Myre Swamp, Nature Grotto (obstacle batch 4, owner priority #1 2026-10-03). Mort Myre is reachable (walk in through the
 * Gate of Morytania / Canifis); the grotto sits at 3440,3337.
 *
 * Grotto bridge 3522 "Jump" (four 1x1 floor pieces: 3440/3441 x 3329 and 3331, the river at z 3330 between them): Void
 * content/area/morytania/mort_myre_swamp/grotto/Grotto.kt. OSRS Wiki (Shortcuts: "Bridge, Nature Grotto outside", level 1) agrees.
 * Success = Level.success(agility, 60..252) (agilitySuccess 60/252): stepping_stone_step 769 (anim delay 30), jump sound 2461, one
 * exact move over the river to the bridge piece on the far side (startDelay 58, delay 70), 15 xp. Failure: rope_walk_fall_right 771
 * (south) / _left 770 (north), a move into the river at 3439,3330, big_splash 68, pool_plop 1658, the swim render west and on to the
 * far bank (Void), "You nearly drown in the disgusting swamp.", Void damage(random(70)) = 0..6 on this server's hitpoint scale (the
 * LogBalance port divides Void damage by 10 the same way), 2 xp. Here the jump always starts on the bridge piece on the player's own
 * side, in the clicked piece's lane (Void walked onto the clicked piece, which from the far bank has no route).
 *
 * Grotto 3516 "Enter" / grotto door 3525 + 3526 "Exit": Void content/quest/member/myreque/nature_spirit/NatureSpirit.kt enterGrotto /
 * exitGrotto in the post-quest state (Nature Spirit has no quest plugin on this server, so its completed state is the only one):
 * "You prepare to enter the Druid's grotto.", one tick, "You see a beautifully tended small grotto area.", to 3442,9734 level 1 (the
 * completed-quest grotto copy; 3526 is the level-1 door). Exit: "You prepare to exit the Druid's grotto.", one tick, out, "You crawl
 * back out of the grotto.". Void teleports out onto 3440,3337 - the grotto loc's own tile, a blocked tile (1x1 blockwalk 2) - so
 * the exit lands on 3440,3336, the tile in front of the grotto where Void spawns Filliman for the same entrance.
 */

val GROTTO_BRIDGE = 3522
val GROTTO = 3516
val GROTTO_DOOR = 3525
val GROTTO_DOOR_COMPLETED = 3526

val ANIM_STEPPING_STONE_STEP = 769
val ANIM_ROPE_WALK_FALL_LEFT = 770
val ANIM_ROPE_WALK_FALL_RIGHT = 771
val SOUND_JUMP = 2461
val SOUND_POOL_PLOP = 1658
val GFX_BIG_SPLASH = 68

val RIVER_Z = 3330
val GROTTO_INSIDE = Tile(3442, 9734, 1)
val GROTTO_OUTSIDE = Tile(3440, 3336, 0)

on_world_init {
    shortcut(GROTTO_BRIDGE, "Jump") {
        obstacle { target ->
            val h = target.tile.height
            val direction = if (player.tile.z > RIVER_Z) Direction.SOUTH else Direction.NORTH
            val start = Tile(target.tile.x, if (direction == Direction.SOUTH) RIVER_Z + 1 else RIVER_Z - 1, h)
            val landing = start.add(direction, 2)
            walkToTile(player, start)
            face(player, direction)
            wait(1)
            if (agilitySuccess(player, 60, 252)) {
                player.animate(ANIM_STEPPING_STONE_STEP, delay = 30)
                player.playSound(SOUND_JUMP)
                exactMove(player, landing, delay = 70, direction = direction, startDelay = 58)
                player.addXp(Skills.AGILITY, 15.0)
            } else {
                player.animate(if (direction == Direction.SOUTH) ANIM_ROPE_WALK_FALL_RIGHT else ANIM_ROPE_WALK_FALL_LEFT, delay = 30)
                var river = Tile(3439, RIVER_Z, h)
                player.playSound(SOUND_JUMP)
                exactMove(player, river, delay = 60, direction = Direction.WEST, startDelay = 58)
                world.spawn(TileGraphic(player.tile, GFX_BIG_SPLASH, 0))
                player.playSound(SOUND_POOL_PLOP)
                player.setRenderAnimation(Render.SWIM)
                river = river.add(Direction.WEST)
                wait(1)
                player.faceTile(river)
                walkOverTile(player, river, Render.SWIM)
                river = river.add(direction)
                player.faceTile(river)
                walkOverTile(player, river, Render.SWIM)
                wait(1)
                player.resetRenderAnimation()
                walkOverTile(player, river.add(direction), Render.NONE)
                player.message("You nearly drown in the disgusting swamp.")
                player.hit(ThreadLocalRandom.current().nextInt(7))
                player.addXp(Skills.AGILITY, 2.0)
            }
        }
    }

    shortcut(GROTTO, "Enter") {
        player.lockingQueue(lockState = gg.rsmod.game.model.LockState.FULL) {
            player.message("You prepare to enter the Druid's grotto.")
            wait(1)
            player.message("You see a beautifully tended small grotto area.")
            player.moveTo(GROTTO_INSIDE)
        }
    }

    listOf(GROTTO_DOOR, GROTTO_DOOR_COMPLETED).forEach { door ->
        shortcut(door, "Exit") {
            player.lockingQueue(lockState = gg.rsmod.game.model.LockState.FULL) {
                player.message("You prepare to exit the Druid's grotto.")
                wait(1)
                player.moveTo(GROTTO_OUTSIDE)
                player.message("You crawl back out of the grotto.")
            }
        }
    }
}
