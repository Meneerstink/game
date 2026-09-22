package gg.rsmod.plugins.content.areas.grandexchange

/*
 * The Grand Exchange home hall (owner 2026-09-22: "At our Home Grand Exchange, create a SMALL but premium custom
 * building ... Put ALL current Grand Exchange Home NPCs inside the building ... Keep bankers / GE access practical").
 *
 * WHERE. The Grand Exchange's south-west lawn, x 3152-3162 by z 3470-3480 (11 x 11 tiles). `runRev667RegionProbeTool
 * locs 12598` reports nothing solid in that square - only ground decoration (grass tufts) and the GE's large non-solid
 * floor pieces - so no Varrock scenery is hidden or blocked. It stays clear of the south-west bank/clerk booth
 * (x 3147-3150, z 3477-3480), of the trees on z 3469 and of the south path into the exchange (x 3163-3167), which the
 * hall's east entrance faces. The north entrance opens towards the bank booth, so banking stays one step away.
 *
 * WHAT. Walls are the Grand Exchange's own stone wall (loc 23779, the wall of the exchange compound itself - 192
 * placements in this region as a straight wall, also used there as a corner), so the hall reads as part of the
 * exchange. The four corner pieces are the same loc as an L-corner (type 2). Two three-tile entrances (east and north)
 * are flanked by standing torches (724, animated flame). The centrepiece is the cache's `Carved fountain` (35469, 4x4).
 * `GeHomeHallMapTool` replaces the lawn with the exact grey Grand Exchange paving from the adjacent east approach and
 * removes the covered grass decorations in both caches; the former 121-tile red POH rug is intentionally gone. Four
 * potted plants soften the solid corners and cache-native GE wall banners (60279) give the long walls a focal point.
 * Every decorative id was checked to carry no menu option, so nothing offers a dead interaction.
 *
 * WHO. Every service npc of the old open-air hub row stands along the inside of the walls, facing the fountain. The
 * crowded west row is split across both side walls; Penguin/Ping/Pong are intentionally removed. Lucien uses the
 * cache's otherwise identical non-combat variant 273. Bankers, GE clerks, Skully and casino croupiers keep their posts.
 */

val HALL_X = GeHomeHall.X
val HALL_Z = GeHomeHall.Z
val HALL_SIZE = GeHomeHall.SIZE
val LAST = HALL_SIZE - 1

/** Local offsets (0..[LAST]) that stay open in the east and north walls. */
val ENTRANCE = 4..6

val HALL_WALL = 23779
val STANDING_TORCH = 724
val CARVED_FOUNTAIN = 35469
val POTTED_PLANT = 60035
val GE_WALL_BANNER = 60279

// ------------------------------------------------------------------ walls

// Straight walls on the outer edge of every border tile; rot 0 west, 1 north, 2 east, 3 south.
for (d in 1 until LAST) {
    spawn_obj(obj = HALL_WALL, x = HALL_X, z = HALL_Z + d, type = 0, rot = 0)
    spawn_obj(obj = HALL_WALL, x = HALL_X + d, z = HALL_Z, type = 0, rot = 3)
    if (d !in ENTRANCE) {
        spawn_obj(obj = HALL_WALL, x = HALL_X + d, z = HALL_Z + LAST, type = 0, rot = 1)
        spawn_obj(obj = HALL_WALL, x = HALL_X + LAST, z = HALL_Z + d, type = 0, rot = 2)
    }
}
// L-corners (type 2): rot 0 north-west, 1 north-east, 2 south-east, 3 south-west.
spawn_obj(obj = HALL_WALL, x = HALL_X, z = HALL_Z + LAST, type = 2, rot = 0)
spawn_obj(obj = HALL_WALL, x = HALL_X + LAST, z = HALL_Z + LAST, type = 2, rot = 1)
spawn_obj(obj = HALL_WALL, x = HALL_X + LAST, z = HALL_Z, type = 2, rot = 2)
spawn_obj(obj = HALL_WALL, x = HALL_X, z = HALL_Z, type = 2, rot = 3)

// ------------------------------------------------------------------ dressing

spawn_obj(obj = CARVED_FOUNTAIN, x = HALL_X + 4, z = HALL_Z + 4, type = 10, rot = 0)
listOf(0 to 0, 0 to LAST, LAST to 0, LAST to LAST).forEach { (dx, dz) ->
    spawn_obj(obj = POTTED_PLANT, x = HALL_X + dx, z = HALL_Z + dz, type = 10, rot = 0)
}
// Torches flank both entrances on the inside.
listOf(3 to LAST, 7 to LAST, LAST to 3, LAST to 7).forEach { (dx, dz) ->
    spawn_obj(obj = STANDING_TORCH, x = HALL_X + dx, z = HALL_Z + dz, type = 10, rot = 0)
}
// Two restrained heraldic accents on the uninterrupted west/south walls. Type 4 is non-solid wall decoration.
spawn_obj(obj = GE_WALL_BANNER, x = HALL_X, z = HALL_Z + 5, type = 4, rot = 0)
spawn_obj(obj = GE_WALL_BANNER, x = HALL_X + 5, z = HALL_Z, type = 4, rot = 3)

// ------------------------------------------------------------------ the npcs

/** One stall: an npc on the inside of a wall, facing into the hall. */
GeHomeHall.SERVICE_POSTS.forEach { post ->
    spawn_npc(
        npc = post.npc,
        x = HALL_X + post.dx,
        z = HALL_Z + post.dz,
        walkRadius = 0,
        direction = post.facing,
    )
}
