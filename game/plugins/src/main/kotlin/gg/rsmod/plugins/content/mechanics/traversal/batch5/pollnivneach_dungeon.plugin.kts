package gg.rsmod.plugins.content.mechanics.traversal.batch5

import gg.rsmod.plugins.content.skills.agility.*

/*
 * Pollnivneach slayer dungeon, upper level: the mystic barriers 31435 (obstacle batch 5, owner priority #1 2026-10-03).
 * Source: Void area/kharidian_desert/pollnivneach/dungeon/PollnivneachDungeon.kt objectOperate("Pass", "pollnivneach_dungeon_barrier")
 * (id 31435, pollnivneach_dungeon.objs.toml), the branch for a barrier that is not a boss lair exit: face the barrier, walk to the
 * player's lane of it, anim pass_through_barrier 10584 (ancient_cavern.anims.toml), gfx pass_barrier_red 1659
 * (pollnivneach_dungeon.gfx.toml), message "You pass through the mystic barrier, which feels odd." and one exact move (delay 30)
 * across the barrier's tile to the other side of it.
 * The four placements of this 667 map (3347,9374 / 3370,9374 rot 2, 3349,9420 / 3368,9420 rot 0) are 1x3 locs crossed along x
 * (MapProbe 2026-10-03). The lane is read from this cache with the shared straightCrossing (the tile right in front of the barrier
 * at the player's row and the tile right behind it), so the slide never starts from wherever the click left the player.
 * The level is reached through the well (31359 -> 3358,9354, object-teleports.json). Void's other branch (31435 inside a boss
 * instance -> clear the instance) does not apply to these placements: the boss lairs (3082,4334 etc.) are not in this batch.
 */

val POLLNIVNEACH_BARRIER = 31435
val ANIM_PASS_BARRIER = 10584
val GFX_PASS_BARRIER_RED = 1659

on_world_init {
    shortcut(POLLNIVNEACH_BARRIER, "Pass") {
        obstacle { target ->
            val crossing = straightCrossing(world, target, player.tile)
            if (crossing == null) {
                gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.PLUGIN) {
                    "mystic barrier ${target.id} at ${target.tile.x},${target.tile.z}: no lane from ${player.tile.x},${player.tile.z}"
                }
                return@obstacle
            }
            walkToTile(player, crossing.start)
            face(player, crossing.direction)
            player.message("You pass through the mystic barrier, which feels odd.")
            player.animate(ANIM_PASS_BARRIER)
            player.graphic(GFX_PASS_BARRIER_RED)
            exactMove(player, crossing.landing, delay = 30, direction = crossing.direction)
        }
    }
}
