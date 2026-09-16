package gg.rsmod.plugins.api.ext

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World

fun Tile.isMulti(world: World): Boolean {
    val region = regionId
    val chunk = chunkCoords.hashCode()
    return world.getMultiCombatChunks().contains(chunk) || world.getMultiCombatRegions().contains(region)
}

val wildernessRegionIds =
    listOf(
        11831,
        11832,
        11833,
        11834,
        11835,
        11836,
        11837,
        12087,
        12088,
        12089,
        12090,
        12091,
        12092,
        12093,
        12190,
        12343,
        12344,
        12345,
        12346,
        12347,
        12348,
        12349,
        12445,
        12446,
        12599,
        12600,
        12601,
        12602,
        12603,
        12604,
        12605,
        12855,
        12856,
        12857,
        12858,
        12859,
        12860,
        12861,
        13111,
        13112,
        13113,
        13114,
        13115,
        13116,
        13117,
        13367,
        13368,
        13369,
        13370,
        13371,
        13372,
        13373,
    )

/**
 * Deadman PvP guards plan (2026-09-16) root-cause fix: the previous version only excluded `z <=
 * 3524` and then unconditionally treated every `z > 6400` as the underground copy with no upper
 * bound and no final clamp, so any tile in a listed region but outside the real surface/
 * underground Wilderness height ranges (e.g. the unused 3525-6400 underground gap, or past the
 * top of either real range) produced an unbounded, sometimes negative, number - the reported
 * "-2 or other nonsense values". Both project reference donors independently bound this by an
 * explicit y-coordinate (here: `z`) range per zone and clamp the result:
 * - `Donors/void/game/src/main/kotlin/content/area/wilderness/Wilderness.kt` (same 9920
 *   underground offset already used here): surface `y in 3525..3967`, underground
 *   `y in 9920..10367`, final `coerceIn(0..60)`.
 * - `Donors/Novite/.../Wilderness.java` `getWildLevel` uses two separate underground ranges
 *   (`y 10302..10357` offset 9912, and `y 10050..10179` offset 10048/level+17) instead of one.
 * SOURCE_CONFLICT: the two donors disagree on the exact underground range/offset (void: one
 * continuous 9920-10367 range; Novite: two distinct sub-ranges with different offsets). This fix
 * adopts void's single-range model since it already shares this codebase's existing 9920
 * underground offset constant; the Novite alternative is recorded here, not silently discarded,
 * in case real revision-667 map geometry differs - see the M1 handoff for the open note.
 */
fun Tile.getWildernessLevel(): Int {
    if (!wildernessRegionIds.contains(regionId)) {
        return 0
    }
    val level =
        when (z) {
            in 3525..3967 -> (z - 3520) / 8 + 1
            in 9920..10367 -> (z - 9920) / 8 + 1
            else -> 0
        }
    return level.coerceIn(0, 60)
}
