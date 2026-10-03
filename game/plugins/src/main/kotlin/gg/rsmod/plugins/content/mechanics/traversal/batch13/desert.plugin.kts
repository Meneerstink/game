package gg.rsmod.plugins.content.mechanics.traversal.batch13

import gg.rsmod.game.model.attr.INTERACTING_OBJ_ATTR
import gg.rsmod.game.plugin.Plugin
import gg.rsmod.plugins.content.skills.agility.*
import java.lang.ref.WeakReference

/*
 * Obstacle batch 13 (owner priority #1, 2026-10-03), agent-handoffs/obstacle-batches/rest-batch-13.tsv: Kharidian Desert rows.
 * Every tile was checked on this 667 map with MapProbe (walkable, connected) on 2026-10-03. Quest gates follow QuestStatus: every
 * quest that is not one of the eight choice quests counts as completed (The Tourist Trap, Smoking Kills).
 */

// ---- Desert Mining Camp cliff route: rock 18871 'Climb' (3281,3037), cliff 18923 'Climb-up' (3278,3037), cliff 18924 'Climb-down'
// (3273,3039) ----------------------------------------------------------------------------------------------------------------------
// 2009scape content/region/desert/quest/thetouristrap/TouristTrapPlugin.java: rock "climb" = "You scrape your hands and knees as you
// climb up." + forceWalk with animation 5041; cliff 18923 "climb-up" (only from x > 3278) = animation 5039 then the top; cliff 18924
// "climb-down" (only from x > 3273) = forceWalk with animation 5040 to the foot of the cliff (3270,3039 in the 530 map).
// The 667 map re-shaped the rock mass (MapProbe 2026-10-03): x 3278-3281 on row 3037 is one blocked mass between the jail-house pocket
// (3282,3037, reached through the camp's prison door 2689) and the plateau 3274-3277 x 3037-3040; the cliff face x 3270-3273 on row
// 3039 lies between the plateau and the open desert (3269,3039, connected to the desert west of the camp). 2009scape's intermediate
// tiles (3279,3037 / 3278,3037 / 3270,3039) are blocked here, so each climb is one exact move over the mass from the walkable tile in
// front of it to the walkable tile behind it - never onto a blocked tile.
val TT_POCKET = Tile(3282, 3037, 0)
val TT_PLATEAU = Tile(3277, 3037, 0)
val TT_PLATEAU_WEST = Tile(3274, 3039, 0)
val TT_DESERT = Tile(3269, 3039, 0)
val ANIM_TT_ROCK_CLIMB = 5041
val ANIM_TT_CLIFF_UP = 5039
val ANIM_TT_CLIFF_DOWN = 5040

shortcut(18871, "Climb") {
    obstacle {
        val up = player.tile.x >= TT_POCKET.x - 1
        val start = if (up) TT_POCKET else TT_PLATEAU
        val landing = if (up) TT_PLATEAU else TT_POCKET
        val direction = if (up) Direction.WEST else Direction.EAST
        walkToTile(player, start)
        face(player, direction)
        wait(1)
        player.message("You scrape your hands and knees as you climb up.")
        player.animate(ANIM_TT_ROCK_CLIMB)
        exactMove(player, landing, delay = 150, direction = direction)
        player.animate(-1)
    }
}

shortcut(18923, "Climb-up") {
    val cliff = player.getInteractingGameObj()
    if (player.tile.x <= cliff.tile.x) {
        // 2009scape: from the plateau side (x <= 3278) the cliff does nothing - the player already stands at the top.
        player.message("You can't climb the rocks from here.")
        return@shortcut
    }
    obstacle {
        walkToTile(player, TT_POCKET)
        face(player, Direction.WEST)
        wait(1)
        player.animate(ANIM_TT_CLIFF_UP)
        exactMove(player, TT_PLATEAU, delay = 150, direction = Direction.WEST)
        player.animate(-1)
    }
}

shortcut(18924, "Climb-down") {
    val cliff = player.getInteractingGameObj()
    if (player.tile.x <= cliff.tile.x) {
        // 2009scape: from below (x <= 3273) the cliff does nothing.
        player.message("You can't climb the rocks from here.")
        return@shortcut
    }
    obstacle {
        walkToTile(player, TT_PLATEAU_WEST)
        face(player, Direction.WEST)
        wait(1)
        player.animate(ANIM_TT_CLIFF_DOWN)
        exactMove(player, TT_DESERT, delay = 150, direction = Direction.WEST)
        player.animate(-1)
    }
}

// ---- Pollnivneach slayer dungeon: boss-lair barriers 31436 and the wavering barrier 31314 -------------------------------------------
// Void area/kharidian_desert/pollnivneach/dungeon/PollnivneachDungeon.kt objectOperate("Pass", "pollnivneach_dungeon_barrier_north")
// (31436): the four barriers 3274,4334 (Mightiest Turoth) / 3314,4334 (Basilisk boss) / 3276,4372 (Kurask overlord) / 3318,4371
// (Monstrous cave crawler) - pollnivneach_dungeon.tables.toml dessert_dungeon_barriers. Into the lair: statement "This portal leads to the
// lair of a ferocious creature. Are you sure you want to do battle?" and choice "Do you want to head into the fray?" - "Yes, I feel
// brave." / "No way!"; then Void builds an instance. This server spawns the four bosses statically in the lairs behind the barriers
// (spawns_13123 / spawns_13124: 3282,4322 / 3308,4324 / 3273,4384 / 3314,4384 - MapProbe: each lair is the area right behind its
// barrier), so the crossing is the barrier's own pass: anim pass_through_barrier 10584, gfx pass_barrier_red 1659 and one exact move
// (delay 30) through the 3x1 barrier on the player's lane (shared straightCrossing). Out of a lair: the same pass without the question.
// Wavering mystic barrier 31314 (3355,9428, 8x1): RS3 Wiki "Wavering mystic barrier" + Transcript: after Smoking Kills it can be passed
// freely, "You pass through the mystic barrier, which feels odd." - the same pass as the mystic barrier 31435 (batch5).
val ANIM_PASS_BARRIER_13 = 10584
val GFX_PASS_BARRIER_RED_13 = 1659

/** The side of each boss barrier its lair lies on (pollnivneach_dungeon.tables.toml + the boss spawns of this server). */
val LAIR_SIDE =
    mapOf(
        (3274 to 4334) to Direction.SOUTH,
        (3314 to 4334) to Direction.SOUTH,
        (3276 to 4372) to Direction.NORTH,
        (3318 to 4371) to Direction.NORTH,
    )

fun Plugin.passMysticBarrier(message: String?) {
    obstacle { target ->
        val crossing = straightCrossing(world, target, player.tile)
        if (crossing == null) {
            gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.PLUGIN) {
                "mystic barrier ${target.id} at ${target.tile.x},${target.tile.z}: no lane from ${player.tile.x},${player.tile.z}"
            }
            player.message(gg.rsmod.game.model.entity.Entity.YOU_CANT_REACH_THAT)
            return@obstacle
        }
        walkToTile(player, crossing.start)
        face(player, crossing.direction)
        if (message != null) player.message(message)
        player.animate(ANIM_PASS_BARRIER_13)
        player.graphic(GFX_PASS_BARRIER_RED_13)
        exactMove(player, crossing.landing, delay = 30, direction = crossing.direction)
    }
}

shortcut(31436, "Pass") {
    val target = player.getInteractingGameObj()
    val crossing = straightCrossing(world, target, player.tile)
    val lair = LAIR_SIDE[target.tile.x to target.tile.z]
    if (crossing == null || crossing.direction != lair) {
        passMysticBarrier(null)
        return@shortcut
    }
    val plugin = this
    player.queue {
        messageBox("This portal leads to the lair of a ferocious creature. Are you sure you want to do battle?")
        if (options("Yes, I feel brave.", "No way!", title = "Do you want to head into the fray?") == 1) {
            player.attr[INTERACTING_OBJ_ATTR] = WeakReference(target)
            plugin.passMysticBarrier(null)
        }
    }
}

shortcut(31314, "Pass") {
    passMysticBarrier("You pass through the mystic barrier, which feels odd.")
}

// ---- Pollnivneach house portal 15479 (3338,3001) ------------------------------------------------------------------------------------
// Every house here is in Rimmington (areas/poh/PlayerHouse.kt). 2009scape PortalOptionPlugin: a portal of another town answers "Your
// house is in <location>." - the same answer batch 3 (Taverley 15477) and batch 9 (Rellekka/Brimhaven/Yanille) give.
shortcut(15479, "Enter") {
    player.message("<col=FF0000>Your house is in Rimmington.")
}

// ---- Ruins of Ullek low wall 28512 (3391,2818, level 1) -----------------------------------------------------------------------------
// RS3 Wiki "Ullek": "A low wall separates [the mummies] from the rest of the plateau; the player must click on the wall to climb over
// it." The wall is a 1x1 loc between 3390,2818 and 3392,2818 on level 1 (both sides open plateau, MapProbe). No donor gives the
// animation, so this is the shared low-wall / stile climb (Void Stiles.climbStile: rocks_pile_climb 839 and one slide over the loc from
// the tile in front of it to the tile behind it).
// Note: the plateau's own entrances (reeds 28474, fallen pillars 28515 / 28516 - 2009scape UllekListeners) are not bound on this server
// yet (they carry no census traversal verb); see the batch 13 report.
shortcut(28512, "Climb") {
    obstacle { target -> climbOver(player, target, 839) }
}
