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

// Walls, fountain, plants, torches and banners live in data/cfg/home_decor.txt (home_decor_live.plugin.kts).

val HALL_X = GeHomeHall.X
val HALL_Z = GeHomeHall.Z

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
