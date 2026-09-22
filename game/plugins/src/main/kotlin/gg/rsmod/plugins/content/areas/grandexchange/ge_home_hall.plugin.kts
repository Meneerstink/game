package gg.rsmod.plugins.content.areas.grandexchange

import gg.rsmod.game.model.Direction

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
 * are flanked by standing torches (724, animated flame). The centrepiece is the cache's `Carved fountain` (35469, 4x4);
 * the floor is the Construction opulent rug laid as one carpet (corner/edge/centre pieces, rotations as in
 * `casino_grandexchange.plugin.kts`); potted plants (60035) stand in the four corners. Every decorative id was checked
 * to carry no menu option, so nothing in the hall offers a dead "Examine-only" action.
 *
 * WHO. Every service npc of the old open-air hub row stands along the inside of the walls, facing the fountain, with
 * the new Quartermaster (home PvP/PvM supplies) in the middle of the west wall opposite the east entrance. The bankers,
 * GE clerks, Skully and the casino croupiers keep their own posts (banks, booths, the casino pit).
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

// ------------------------------------------------------------------ floor

/** The opulent rug as one carpet: a piece's rotation names the side of the carpet it belongs to (N0 E1 S2 W3). */
fun hallRug(
    dx: Int,
    dz: Int,
): Pair<Int, Int> {
    val west = dx == 0
    val east = dx == LAST
    val south = dz == 0
    val north = dz == LAST
    return when {
        north && west -> Objs.RUG_13594 to 0
        north && east -> Objs.RUG_13594 to 1
        south && east -> Objs.RUG_13594 to 2
        south && west -> Objs.RUG_13594 to 3
        north -> Objs.RUG_13595 to 0
        east -> Objs.RUG_13595 to 1
        south -> Objs.RUG_13595 to 2
        west -> Objs.RUG_13595 to 3
        else -> Objs.RUG_13596 to 0
    }
}

for (dx in 0..LAST) {
    for (dz in 0..LAST) {
        val (piece, rot) = hallRug(dx, dz)
        spawn_obj(obj = piece, x = HALL_X + dx, z = HALL_Z + dz, type = 22, rot = rot)
    }
}

// ------------------------------------------------------------------ dressing

spawn_obj(obj = CARVED_FOUNTAIN, x = HALL_X + 4, z = HALL_Z + 4, type = 10, rot = 0)
listOf(0 to 0, 0 to LAST, LAST to 0, LAST to LAST).forEach { (dx, dz) ->
    spawn_obj(obj = POTTED_PLANT, x = HALL_X + dx, z = HALL_Z + dz, type = 10, rot = 0)
}
// Torches flank both entrances on the inside.
listOf(3 to LAST, 7 to LAST, LAST to 3, LAST to 7).forEach { (dx, dz) ->
    spawn_obj(obj = STANDING_TORCH, x = HALL_X + dx, z = HALL_Z + dz, type = 10, rot = 0)
}

// ------------------------------------------------------------------ the npcs

/** One stall: an npc on the inside of a wall, facing into the hall. */
fun stall(
    npc: Int,
    dx: Int,
    dz: Int,
    facing: Direction,
) {
    spawn_npc(npc = npc, x = HALL_X + dx, z = HALL_Z + dz, walkRadius = 0, direction = facing)
}

// West wall, facing east (the Quartermaster opposite the east entrance).
stall(Npcs.SIR_TIFFY_CASHIEN, 0, 1, Direction.EAST)
stall(Npcs.MAX, 0, 2, Direction.EAST)
stall(Npcs.AVA, 0, 3, Direction.EAST)
stall(Npcs.ALECK, 0, 4, Direction.EAST)
stall(GeHomeHall.QUARTERMASTER, 0, 5, Direction.EAST)
stall(Npcs.PIKKUPSTIX, 0, 6, Direction.EAST)
stall(Npcs.BOB, 0, 7, Direction.EAST)
stall(Npcs.MANDRITH, 0, 8, Direction.EAST)
stall(Npcs.PERDU, 0, 9, Direction.EAST)

// South wall, facing north. The three 78 Store keepers (x 1-3) are spawned by StoreNpcs from its POSTS.
stall(Npcs.PARTY_PETE, 4, 0, Direction.NORTH)
stall(Npcs.KURADAL_9085, 5, 0, Direction.NORTH)
stall(Npcs.WISE_OLD_MAN, 6, 0, Direction.NORTH)
stall(Npcs.EVIL_DAVE, 7, 0, Direction.NORTH)
stall(Npcs.TOOL_LEPRECHAUN, 8, 0, Direction.NORTH)
stall(Npcs.DRUNKEN_DWARF, 9, 0, Direction.NORTH)

// North wall either side of the north entrance, facing south.
stall(Npcs.AZZANADRA, 1, LAST, Direction.SOUTH)
stall(Npcs.ARCHAEOLOGIST, 2, LAST, Direction.SOUTH)
stall(Npcs.ONEIROMANCER, 8, LAST, Direction.SOUTH)
stall(Npcs.KING_NARNODE_SHAREEN, 9, LAST, Direction.SOUTH)

// East wall either side of the east entrance, facing west.
stall(Npcs.LUCIEN, LAST, 1, Direction.WEST)
stall(Npcs.PENGUIN_5428, LAST, 2, Direction.WEST)
stall(Npcs.PING, LAST, 8, Direction.WEST)
stall(Npcs.PONG, LAST, 9, Direction.WEST)
