package gg.rsmod.plugins.content.skills.agility

import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.queue.QueueTask

/**
 * Grapple shortcuts ported from Void `content/skill/agility/shortcut/Grapple.kt`: River Lum raft,
 * Falador wall, Water Obelisk island, Catherby cliff and Yanille wall. Ids are Void's string ids
 * resolved through its toml data (objects, animations 4230/4466/4467/4455/4460/4468/2586/2588,
 * gfx 760, sounds 2928/2929/2462, mithril grapple 9419, crossbows 9174-9185); requirements, tiles,
 * areas (`catherby.areas.toml`) and messages are Void's. Temporary rope objects and tree swaps are
 * spawned as dynamic objects for Void's tick counts and then restored.
 *
 * Bindings go through `shortcut()` from sourced_shortcuts.plugin.kts, so an id or option missing from
 * this cache, or already bound elsewhere, is skipped and logged.
 */

val MITHRIL_GRAPPLE = 9419
val CROSSBOWS = intArrayOf(9174, 9176, 9177, 9179, 9181, 9183, 9185)

val ANIM_CROSSBOW_ACCURATE = 4230
val ANIM_GRAPPLE_ENTER_WATER = 4466
val ANIM_GRAPPLE_EXIT_WATER = 4467
val ANIM_GRAPPLE_WALL_CLIMB = 4455
val ANIM_GRAPPLE_AIM_FIRE = 4460
val ANIM_WATER_OBELISK_SWIM = 4468
val ANIM_JUMP_DOWN = 2586
val ANIM_JUMP_LAND = 2588
val GFX_GRAPPLE_WALL_CLIMB = 760
val SOUND_GRAPPLE_SHOOT = 2928
val SOUND_GRAPPLE_SPLASH_WATER = 2929

val OBJ_GRAPPLE_ROPE = 17034
val OBJ_STRONG_TREE = 17036
val OBJ_STRONG_TREE_ROPE = 17037
val OBJ_STRONG_TREE_GRAPPLE = 17038
val OBJ_STRONG_YEW = 17039
val OBJ_STRONG_YEW_ROPE = 17040
val OBJ_STRONG_YEW_GRAPPLE = 17041
val OBJ_CATHERBY_ROCKS_GRAPPLE = 17043
val OBJ_CATHERBY_CROSSBOW_TREE_GRAPPLE = 17063
val OBJ_CATHERBY_ROCKS_ROPE = 17065
val OBJ_CATHERBY_GRAPPLE_ROPE = 17067

val GROUND_DECOR = 22

/** Void `water_obselisk_island` polygon. */
val OBELISK_ISLAND_X = intArrayOf(2833, 2833, 2847, 2849, 2849)
val OBELISK_ISLAND_Z = intArrayOf(3416, 3427, 3427, 3422, 3416)

fun inPolygon(
    tile: Tile,
    xs: IntArray,
    zs: IntArray,
): Boolean {
    var inside = false
    var j = xs.size - 1
    for (i in xs.indices) {
        if ((zs[i] > tile.z) != (zs[j] > tile.z) &&
            tile.x < (xs[j] - xs[i]) * (tile.z - zs[i]).toDouble() / (zs[j] - zs[i]) + xs[i]
        ) {
            inside = !inside
        }
        j = i
    }
    // Void areas are inclusive of their edge tiles.
    return inside || (tile.x in xs.minOrNull()!!..xs.maxOrNull()!! && tile.z in zs.minOrNull()!!..zs.maxOrNull()!! &&
        (tile.x == xs.minOrNull() || tile.z == zs.minOrNull()))
}

suspend fun QueueTask.hasGrappleRequirements(
    player: Player,
    ranged: Int,
    agility: Int,
    strength: Int,
): Boolean {
    if (player.skills.getCurrentLevel(Skills.RANGED) < ranged ||
        player.skills.getCurrentLevel(Skills.AGILITY) < agility ||
        player.skills.getCurrentLevel(Skills.STRENGTH) < strength
    ) {
        messageBox("You need at least $ranged Ranged, $agility Agility and $strength Strength to do that.")
        return false
    }
    if (player.equipment[EquipmentType.AMMO.id]?.id != MITHRIL_GRAPPLE) {
        player.message("You need a mithril grapple tipped bolt with a rope to do that.")
        return false
    }
    if (player.equipment[EquipmentType.WEAPON.id]?.id !in CROSSBOWS.toList()) {
        player.message("You need a crossbow equipped to do that.")
        return false
    }
    return true
}

/** Spawns a temporary ground-decor object, removed after [ticks]. */
fun temporaryObject(
    id: Int,
    tile: Tile,
    rot: Int,
    ticks: Int,
) {
    val obj = DynamicObject(id, GROUND_DECOR, rot, tile)
    world.spawn(obj)
    world.queue {
        wait(ticks)
        world.remove(obj)
    }
}

/** Replaces the object [id] on [tile] with [replacement] for [ticks], then restores it. */
fun temporaryReplace(
    id: Int,
    tile: Tile,
    replacement: Int,
    ticks: Int,
) {
    val chunk = world.chunks.get(tile, createIfNeeded = true) ?: return
    val original =
        chunk.getEntities<GameObject>(tile, EntityType.STATIC_OBJECT, EntityType.DYNAMIC_OBJECT).firstOrNull { it.id == id } ?: return
    world.spawn(DynamicObject(original, replacement))
    world.queue {
        wait(ticks)
        world.spawn(DynamicObject(original))
    }
}

fun lumbridgeTree(grapple: Boolean) {
    temporaryReplace(OBJ_STRONG_YEW, Tile(3244, 3179), if (grapple) OBJ_STRONG_YEW_GRAPPLE else OBJ_STRONG_YEW_ROPE, 8)
    for (x in 3246..3251) temporaryObject(OBJ_GRAPPLE_ROPE, Tile(x, 3179), 0, 8)
}

fun alKharidTree(grapple: Boolean) {
    temporaryReplace(OBJ_STRONG_TREE, Tile(3260, 3179), if (grapple) OBJ_STRONG_TREE_GRAPPLE else OBJ_STRONG_TREE_ROPE, 8)
    for (x in 3254..3259) temporaryObject(OBJ_GRAPPLE_ROPE, Tile(x, 3180), 0, 8)
}

suspend fun QueueTask.climbGrappleWall(
    player: Player,
    destination: Tile,
) {
    player.animate(ANIM_GRAPPLE_WALL_CLIMB)
    player.graphic(GFX_GRAPPLE_WALL_CLIMB)
    player.playSound(SOUND_GRAPPLE_SHOOT)
    wait(11)
    player.animate(-1)
    player.moveTo(destination)
}

suspend fun QueueTask.jumpDownWall(
    player: Player,
    destination: Tile,
) {
    if (player.skills.getCurrentLevel(Skills.AGILITY) < 4) {
        player.message("You need an agility level of at least 4 to climb down this wall.")
        return
    }
    player.animate(ANIM_JUMP_DOWN)
    wait(1)
    player.animate(ANIM_JUMP_LAND)
    player.moveTo(destination)
}

on_world_init {
    // ---- River Lum broken raft ----
    shortcut(17068, "Grapple") {
        obstacle { target ->
            if (!hasGrappleRequirements(player, ranged = 37, agility = 8, strength = 17)) return@obstacle
            val direction = if (player.tile.x < 3253) Direction.EAST else Direction.WEST
            if (player.tile.getDistance(target.tile) > 2) {
                val start = if (direction == Direction.EAST) Tile(3246, 3179) else Tile(3259, 3180)
                if (player.tile.getDistance(start) > 1) {
                    player.message("I can't do that from here, get closer.")
                    return@obstacle
                }
                player.faceTile(target.tile)
                wait(2)
                player.animate(ANIM_CROSSBOW_ACCURATE)
                player.playSound(SOUND_GRAPPLE_SHOOT)
                wait(3)
                player.filterableMessage("You successfully grapple the raft and tie the rope to a tree.")
                if (direction == Direction.EAST) lumbridgeTree(grapple = false) else alKharidTree(grapple = false)
                walkOverTile(player, start.add(direction))
                player.animate(ANIM_GRAPPLE_ENTER_WATER)
                player.playSound(SOUND_GRAPPLE_SPLASH_WATER)
                exactMove(player, Tile(start.x + direction.getDeltaX() * 6, start.z), 120, direction)
            }
            if (direction == Direction.EAST) {
                walkToTile(player, Tile(3252, 3180))
                walkToTile(player, Tile(3253, 3180))
                player.faceTile(Tile(3260, 3180))
            } else {
                walkToTile(player, Tile(3252, 3180))
                player.faceTile(Tile(3244, 3179))
            }
            wait(2)
            player.animate(ANIM_CROSSBOW_ACCURATE)
            player.playSound(SOUND_GRAPPLE_SHOOT)
            wait(3)
            player.filterableMessage("You successfully grapple the tree on the opposite bank.")
            if (direction == Direction.EAST) alKharidTree(grapple = true) else lumbridgeTree(grapple = true)
            wait(1)
            player.animate(ANIM_GRAPPLE_EXIT_WATER)
            player.playSound(SOUND_GRAPPLE_SPLASH_WATER)
            exactMove(player, if (direction == Direction.EAST) Tile(3258, 3180) else Tile(3248, 3179), 160, direction)
            walkOverTile(player, if (direction == Direction.EAST) Tile(3259, 3180) else Tile(3246, 3179))
        }
    }

    // ---- Falador wall ----
    // Owner 2026-09-17 ("3006,3396 shortcut werkt niet"): this cache places the 2011 hidey-hole
    // variants of the wall (ObjectPlacementProbeTool: 213 at 3006,3395 north face, 214 at 3005,3392
    // south face, options Grapple / Build hidey-hole), not Void's 17049/17050, so the bindings were
    // silently skipped. Both faces are bound; the wall-top 17051/17052 "Jump" objects are present.
    listOf(Triple(213, Tile(3006, 3395), Direction.SOUTH), Triple(214, Tile(3005, 3393), Direction.NORTH)).forEach { (id, stand, facing) ->
        shortcut(id, "Grapple") {
            obstacle {
                walkToTile(player, stand)
                face(player, facing)
                wait(1)
                if (!hasGrappleRequirements(player, ranged = 19, agility = 11, strength = 37)) return@obstacle
                climbGrappleWall(player, Tile(stand.x, 3394, 1))
            }
        }
    }
    listOf(Triple(17051, Tile(3006, 3394, 1), Tile(3006, 3395, 0)), Triple(17052, Tile(3005, 3394, 1), Tile(3005, 3393, 0))).forEach { (id, stand, landing) ->
        shortcut(id, "Jump") {
            obstacle {
                walkToTile(player, stand)
                jumpDownWall(player, landing)
            }
        }
    }

    // ---- Water Obelisk island -> Catherby ----
    shortcut(17062, "Grapple") {
        obstacle { target ->
            if (!hasGrappleRequirements(player, ranged = 39, agility = 36, strength = 22)) return@obstacle
            if (!inPolygon(player.tile, OBELISK_ISLAND_X, OBELISK_ISLAND_Z)) {
                player.message("I can't do that from here.")
                return@obstacle
            }
            walkToTile(player, Tile(2841, 3425))
            face(player, Direction.NORTH)
            wait(1)
            player.animate(ANIM_GRAPPLE_AIM_FIRE)
            wait(2)
            player.animate(ANIM_CROSSBOW_ACCURATE)
            player.playSound(SOUND_GRAPPLE_SHOOT)
            wait(3)
            for (z in 3427..3433) temporaryObject(OBJ_GRAPPLE_ROPE, Tile(2841, z), 1, 14)
            temporaryObject(OBJ_CATHERBY_ROCKS_ROPE, Tile(2841, 3426), 1, 14)
            temporaryReplace(target.id, target.tile, OBJ_CATHERBY_CROSSBOW_TREE_GRAPPLE, 14)
            wait(4)
            player.animate(ANIM_WATER_OBELISK_SWIM)
            player.playSound(SOUND_GRAPPLE_SPLASH_WATER)
            exactMove(player, Tile(2841, 3432), 160, Direction.NORTH)
        }
    }

    // ---- Catherby cliff ----
    shortcut(17042, "Grapple") {
        obstacle {
            if (!hasGrappleRequirements(player, ranged = 35, agility = 32, strength = 35)) return@obstacle
            if (player.tile.x !in 2860..2866 || player.tile.z !in 3427..3432) {
                player.message("I can't do that from here.")
                return@obstacle
            }
            walkToTile(player, Tile(2866, 3429))
            face(player, Direction.EAST)
            wait(1)
            player.animate(ANIM_GRAPPLE_AIM_FIRE)
            player.playSound(SOUND_GRAPPLE_SHOOT)
            wait(2)
            for (x in 2867..2869) temporaryObject(OBJ_CATHERBY_GRAPPLE_ROPE, Tile(x, 3429), 0, 14)
            temporaryObject(OBJ_CATHERBY_ROCKS_GRAPPLE, Tile(2869, 3429), 0, 14)
            wait(1)
            walkOverTile(player, Tile(2868, 3429))
            walkOverTile(player, Tile(2869, 3430))
        }
    }

    // ---- Yanille wall ----
    shortcut(17047, "Grapple") {
        obstacle { target ->
            val direction = if (player.tile.z >= target.tile.z) Direction.SOUTH else Direction.NORTH
            walkToTile(player, target.tile)
            face(player, direction)
            wait(1)
            if (!hasGrappleRequirements(player, ranged = 21, agility = 39, strength = 38)) return@obstacle
            val destination = if (direction != Direction.NORTH) target.tile.add(direction) else target.tile
            climbGrappleWall(player, Tile(destination.x, destination.z, 1))
        }
    }
    shortcut(17048, "Jump") {
        obstacle { target ->
            val direction = if (player.tile.z == target.tile.z) Direction.SOUTH else Direction.NORTH
            walkToTile(player, target.tile)
            val destination = if (direction == Direction.SOUTH) target.tile.add(direction) else target.tile
            jumpDownWall(player, Tile(destination.x, destination.z, 0))
        }
    }
}
