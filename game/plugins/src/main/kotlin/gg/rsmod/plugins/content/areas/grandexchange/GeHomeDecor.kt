package gg.rsmod.plugins.content.areas.grandexchange

import gg.rsmod.game.model.Tile

/** Cache-verified decorative layout for the Grand Exchange Home's southern safe-zone entrance. */
object GeHomeDecor {
    const val SAFE_EDGE_Z = 3467
    const val CENTRE_X2 = 6329 // x = 3164.5, kept doubled so mirror checks stay integral.

    const val STANDING_TORCH = 724
    const val DANGER_SIGN = 1032
    const val VARROCK_STATUE = 24162
    const val GE_WALL_BANNER = 60279

    data class Placement(
        val objectId: Int,
        val tile: Tile,
        val type: Int = 10,
        val rotation: Int = 0,
    )

    /**
     * The two original south approaches remain completely open at x 3162..3163 and 3166..3167. The landmarks sit
     * outside those lanes: danger signs on the dangerous side, matching Varrock statues and warm light on the safe
     * side, plus Grand Exchange banners on the existing gatehouse walls. Every id is native revision-667 cache data,
     * has no interaction option, and the statues/banners are reused from this same Varrock/GE map square.
     */
    val SOUTH_GATE =
        listOf(
            Placement(DANGER_SIGN, Tile(3159, 3466, 0), rotation = 1),
            Placement(DANGER_SIGN, Tile(3170, 3466, 0), rotation = 3),
            Placement(VARROCK_STATUE, Tile(3159, 3468, 0), rotation = 1),
            Placement(VARROCK_STATUE, Tile(3170, 3468, 0), rotation = 3),
            Placement(STANDING_TORCH, Tile(3158, 3468, 0)),
            Placement(STANDING_TORCH, Tile(3171, 3468, 0)),
            Placement(GE_WALL_BANNER, Tile(3160, 3466, 0), type = 4, rotation = 3),
            Placement(GE_WALL_BANNER, Tile(3164, 3466, 0), type = 4, rotation = 3),
            Placement(GE_WALL_BANNER, Tile(3165, 3466, 0), type = 4, rotation = 3),
            Placement(GE_WALL_BANNER, Tile(3169, 3466, 0), type = 4, rotation = 3),
        )

    val SOUTH_APPROACH =
        setOf(
            Tile(3162, SAFE_EDGE_Z, 0),
            Tile(3163, SAFE_EDGE_Z, 0),
            Tile(3166, SAFE_EDGE_Z, 0),
            Tile(3167, SAFE_EDGE_Z, 0),
        )
}
