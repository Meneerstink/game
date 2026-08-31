package gg.rsmod.plugins.content.areas.home

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.content.mechanics.practicepvp.PracticePvp

/**
 * R14.6/R14.7/HOME_DESIGN_2.png: real, functional PvM & Minigames content in the SW quadrant -
 * matching the design's archery-target/weapon-rack imagery with real, verified 2011 cache
 * objects (found via a targeted diagnostic this pass, same discipline as the pool/altar/board:
 * `Objs.DUMMY_823`("Attack")/`Objs.TARGET`(no real options, rejected)/`Objs.TARGET_1308`
 * ("Fire-at")/`Objs.ARCHERY_TARGET_13402`("Shoot-at","Remove")/`Objs.ARENA_ENTRANCE`("Open")
 * were checked; the two used below had the clearest real functional fit).
 *
 * Placing a decorative npc/object alone would not prove any service works (the owner's own
 * standing rule) - both objects below are wired to real, already-implemented mechanics.
 *
 * Audit finding 1 fix: the arena entrance's cache placement type was confirmed the OPPOSITE of
 * the archery target's - type 0, not 10 (this file previously used 10 for both). Fixed here;
 * see `home_walls.plugin.kts` for the matching wall-object fix from the same finding.
 */
val pvmTile = HomeLayout.pvmArenaEntrance.tile(world.gameContext.home)
val archeryTile = HomeLayout.pvmArcheryTarget.tile(world.gameContext.home)

// R14.7: a real, physical entry point into the existing Practice PvP system
// (`PracticePvp.queueUp`, R09.1) - previously command-only (`::practice`).
spawn_obj(obj = Objs.ARENA_ENTRANCE, x = pvmTile.x, z = pvmTile.z, height = pvmTile.height, type = 0, rot = 0)

on_obj_option(obj = Objs.ARENA_ENTRANCE, option = "open") {
    player.queue {
        val choice = options("Melee", "Ranged", "Magic", "Cancel", title = "Practice PvP")
        val preset =
            when (choice) {
                1 -> PracticePvp.Preset.MELEE
                2 -> PracticePvp.Preset.RANGE
                3 -> PracticePvp.Preset.MAGE
                else -> null
            }
        preset?.let { PracticePvp.queueUp(player, it) }
    }
}

// A light, real ranged-training minigame: shoot the target for a small amount of Ranged xp,
// rate-limited (1 shot per 3 cycles ~= 1.8s) so it can't be macro-spammed for free xp - a
// lightweight training convenience, not a real vanilla 2011 archery-target xp table (this is a
// custom minigame, not per-npc sourced data, so no wiki lookup applies here).
val LAST_TARGET_SHOT_ATTR = AttributeKey<Int>()
val TARGET_XP = 8.0
val TARGET_COOLDOWN_CYCLES = 3

spawn_obj(
    obj = Objs.ARCHERY_TARGET_13402,
    x = archeryTile.x,
    z = archeryTile.z,
    height = archeryTile.height,
    type = 10,
    rot = 0,
)

on_obj_option(obj = Objs.ARCHERY_TARGET_13402, option = "shoot-at") {
    val lastShot = player.attr[LAST_TARGET_SHOT_ATTR] ?: 0
    if (world.currentCycle - lastShot < TARGET_COOLDOWN_CYCLES) {
        return@on_obj_option
    }
    player.attr[LAST_TARGET_SHOT_ATTR] = world.currentCycle
    player.addXp(Skills.RANGED, TARGET_XP)
    player.filterableMessage("You practice your aim on the target.")
}
