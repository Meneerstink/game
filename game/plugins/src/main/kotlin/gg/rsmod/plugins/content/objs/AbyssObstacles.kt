package gg.rsmod.plugins.content.objs

import gg.rsmod.game.model.Tile

/** Data of the Abyss outer-ring obstacles (abyss_obstacles.plugin.kts), Void `AbyssObstacles.positions`. */
object AbyssObstacles {
    /** Parent loc tile -> (the walk tile in the cleared gap, the inner-ring landing). */
    val POSITIONS: Map<Tile, Pair<Tile, Tile>> =
        mapOf(
            Tile(3038, 4853) to (Tile(3039, 4855) to Tile(3039, 4844)),
            Tile(3049, 4849) to (Tile(3050, 4851) to Tile(3047, 4844)),
            Tile(3058, 4839) to (Tile(3060, 4840) to Tile(3051, 4838)),
            Tile(3060, 4830) to (Tile(3062, 4831) to Tile(3052, 4831)),
            Tile(3057, 4821) to (Tile(3059, 4822) to Tile(3050, 4826)),
            Tile(3049, 4813) to (Tile(3050, 4812) to Tile(3045, 4821)),
            Tile(3041, 4811) to (Tile(3042, 4810) to Tile(3047, 4820)),
            Tile(3026, 4813) to (Tile(3027, 4812) to Tile(3042, 4819)),
            Tile(3018, 4821) to (Tile(3017, 4822) to Tile(3031, 4820)),
            Tile(3018, 4833) to (Tile(3017, 4834) to Tile(3026, 4826)),
            Tile(3021, 4842) to (Tile(3020, 4843) to Tile(3027, 4840)),
            Tile(3028, 4849) to (Tile(3029, 4851) to Tile(3032, 4844)),
        )
}
