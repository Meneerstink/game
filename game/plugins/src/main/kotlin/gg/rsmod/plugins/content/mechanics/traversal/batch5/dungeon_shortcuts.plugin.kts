package gg.rsmod.plugins.content.mechanics.traversal.batch5

import gg.rsmod.plugins.content.skills.agility.*

/*
 * Dungeon shortcuts (obstacle batch 5, owner priority #1 2026-10-03).
 *
 * Yanille Agility Dungeon balancing ledges 2303 (2580,9513) and 35969 (2580,9519): OSRS Wiki Shortcuts "Balancing ledge, Yanille
 * Agility Dungeon, 40 Agility, 22 xp" and 2009scape YanilleAgilityDungeon.handleBalancingLedge (level 40, 22.5 xp, 7 tiles past
 * the clicked ledge). This 667 map (MapProbe 2026-10-03): both ledge tiles are blocked, 2580,9514-9518 is a one-tile walkway
 * between them, so one crossing runs 2580,9512 <-> 2580,9520 over both ledges (from the walkway, to the far side of the clicked
 * ledge). The walk over is Void's balancing-ledge pattern (BarbarianOutpost: ledge_stand_right 753, render ledge_balance 1026,
 * walk over, ledge_stand_away_right 759). Experience 22.5 (2009scape; the OSRS Wiki shows 22).
 * Not ported, recorded instead of guessed: the failure (a fall into the poison spider pit) - no sourced failure rate.
 *
 * Dwarven Mine narrow crevice 5906 (3029,9806 rot 0 and 3034,9806 rot 2): OSRS Wiki Shortcuts "Narrow crevice, Dwarven Mine, 42
 * Agility, 0 xp, between the two arms of the mine". The 667 map blocks 3029-3034,9806; the crawl runs 3028,9806 <-> 3035,9806.
 * The crawl is Void's narrow-crevice pattern (FremennikSlayerDungeon slayer_dungeon_crevice: agility_shortcut_crack_enter 2594 onto
 * the crevice, agilty_shortcut_tunnel_walk 2590 through it, agility_shortcut_crack_leave 2595 out), from the tile in front of it.
 */

val LEDGE_SOUTH = 2303
val LEDGE_NORTH = 35969
val ANIM_LEDGE_STAND_RIGHT = 753
val ANIM_LEDGE_STAND_AWAY_RIGHT = 759
val DWARVEN_CREVICE = 5906
val ANIM_CRACK_IN = 2594
val ANIM_CRACK_WALK = 2590
val ANIM_CRACK_OUT = 2595

on_world_init {
    listOf(LEDGE_SOUTH, LEDGE_NORTH).forEach { id ->
        shortcut(id, "Walk-across") {
            if (!player.hasAgility(40, "You need an Agility level of at least 40 in order to do this.")) return@shortcut
            obstacle { target ->
                // South of both ledges -> north from 2580,9512; north of both -> south from 2580,9520; on the walkway between
                // them -> across the clicked ledge.
                val north =
                    when {
                        player.tile.z <= 9512 -> true
                        player.tile.z >= 9520 -> false
                        else -> target.tile.z > player.tile.z
                    }
                val start =
                    when {
                        player.tile.z <= 9512 -> Tile(2580, 9512)
                        player.tile.z >= 9520 -> Tile(2580, 9520)
                        else -> Tile(2580, if (north) 9518 else 9514)
                    }
                val landing = if (north) Tile(2580, 9520) else Tile(2580, 9512)
                val direction = if (north) Direction.NORTH else Direction.SOUTH
                walkToTile(player, start)
                face(player, direction)
                player.animate(ANIM_LEDGE_STAND_RIGHT)
                wait(1)
                walkOverTile(player, landing, Render.LEDGE_BALANCE)
                player.resetRenderAnimation()
                player.animate(ANIM_LEDGE_STAND_AWAY_RIGHT)
                player.addXp(Skills.AGILITY, 22.5)
            }
        }
    }

    shortcut(DWARVEN_CREVICE, "Squeeze-through") {
        if (!player.hasAgility(42)) return@shortcut
        obstacle {
            val east = player.tile.x <= 3031
            val direction = if (east) Direction.EAST else Direction.WEST
            val start = if (east) Tile(3028, 9806) else Tile(3035, 9806)
            val entry = if (east) Tile(3029, 9806) else Tile(3034, 9806)
            val exit = if (east) Tile(3034, 9806) else Tile(3029, 9806)
            val landing = if (east) Tile(3035, 9806) else Tile(3028, 9806)
            walkToTile(player, start)
            face(player, direction)
            player.animate(ANIM_CRACK_IN)
            exactMove(player, entry, direction = direction)
            player.animate(ANIM_CRACK_WALK)
            exactMove(player, exit, direction = direction)
            player.animate(ANIM_CRACK_OUT)
            exactMove(player, landing, direction = direction)
        }
    }
}
