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
    // Darkan AgilityDungeon.java:81-97 (handleLedge 2303 / 35969): level 40; north start (2580,9520), south start (2580,9512); north =
    // player.y > 9516; on !skillSuccess(level, -67, 416) crossLedge(start, start +- 2, 20, north) and 6 ticks after the click
    // tele(2572,9568) + a 20 % max-hitpoints hit; else crossLedge(start, the other end, 20, north) = route to the start, face, anim
    // 752 (from the north) / 753 + BAS 156 / 157, the collision-free walk, stop anim 758 / 759 (DarkanShortcuts.darkanCrossLedge).
    // Experience 22.5 (2009scape; OSRS Wiki 22) kept.
    listOf(LEDGE_SOUTH, LEDGE_NORTH).forEach { id ->
        shortcut(id, "Walk-across") {
            if (!player.hasAgility(40, "You need an Agility level of at least 40 in order to do this.")) return@shortcut
            obstacle {
                val north = player.tile.z > 9516
                val start = if (north) Tile(2580, 9520) else Tile(2580, 9512)
                if (!darkanSkillSuccess(player, -67, 416)) {
                    darkanCrossLedge(player, start, start.transform(0, if (north) -2 else 2), 0.0, north)
                    wait(2)
                    player.moveTo(Tile(2572, 9568, 0))
                    player.darkanPercentHit(0.2)
                    return@obstacle
                }
                darkanCrossLedge(player, start, if (north) Tile(2580, 9512) else Tile(2580, 9520), 22.5, north)
            }
        }
    }

    // Darkan DwarvenMine.java:49-61 (5906 @3029,9806 r0 / 3034,9806 r2): level 42; from the route tile three chained forced moves -
    // obj.x +-2 (2594, 0/120), obj.x +-5 (2590, 0/120), obj.x +-6 (2595, 0/75) (minus for rotation 2). Was Void's three 1-tile-per-30
    // slides from a fixed start.
    shortcut(DWARVEN_CREVICE, "Squeeze-through") {
        if (!player.hasAgility(42, "You need an agility level of 42 to use this obstacle.")) return@shortcut
        obstacle { target ->
            val sign = if (target.rot == 2) -1 else 1
            val z = target.tile.z
            darkanForceMove(player, Tile(target.tile.x + 2 * sign, z, 0), startCycles = 0, speedCycles = 120, anim = ANIM_CRACK_IN)
            darkanForceMove(player, Tile(target.tile.x + 5 * sign, z, 0), startCycles = 0, speedCycles = 120, anim = ANIM_CRACK_WALK)
            darkanForceMove(player, Tile(target.tile.x + 6 * sign, z, 0), startCycles = 0, speedCycles = 75, anim = ANIM_CRACK_OUT)
        }
    }
}
