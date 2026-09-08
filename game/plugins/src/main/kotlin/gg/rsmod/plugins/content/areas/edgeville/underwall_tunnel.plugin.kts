package gg.rsmod.plugins.content.areas.edgeville

/**
 * The underwall tunnel on the road between Edgeville and the Grand Exchange.
 *
 * Both mouths carry `Climb-into` and neither had a binding, so the shortcut answered with
 * `Nothing interesting happens`.
 *
 * Placement evidence
 * (`./gradlew :game:runObjectPlacementProbeTool --args="<cache> <xteas> name underwall tunnel"`):
 * six placements exist in the whole world, of which exactly one pair is in the Edgeville region
 * (12598) - 9311 at 3139,3516 and 9312 at 3143,3514, both `type=10 orientation=2`. A tile sweep of
 * the same area shows object 23795 forming two parallel diagonal walls between them, running
 * north-east through 3139,3514 -> 3142,3517 and 3139,3512 -> 3143,3516. The two mouths sit on
 * opposite sides of that pair of walls, which is what the tunnel passes under.
 *
 * Exit tiles are derived from that geometry: each mouth exits beside the other mouth, on the other
 * mouth's side of the wall. The mouths themselves are `INTERACTABLE` objects and block their own
 * tile, so the player cannot be put on one.
 *
 * PROVISIONAL, no cache source: this revision's caches carry no level requirement, experience award
 * or animation for a shortcut, so none is applied - the tunnel is a plain passage. The two remaining
 * underwall tunnel pairs (9301/9302 at 2575,3108 and 9309/9310 at 2948,3310) are deliberately left
 * unbound: they are separate shortcuts elsewhere in the world whose requirements are not derivable
 * from the cache either, and guessing a requirement is worse than leaving them reported.
 */

private val NORTH_WEST_MOUTH = Tile(3139, 3516)

private val SOUTH_EAST_MOUTH = Tile(3143, 3514)

on_obj_option(obj = Objs.UNDERWALL_TUNNEL_9311, option = "climb-into") {
    player.moveTo(SOUTH_EAST_MOUTH.x + 1, SOUTH_EAST_MOUTH.z)
}

on_obj_option(obj = Objs.UNDERWALL_TUNNEL_9312, option = "climb-into") {
    player.moveTo(NORTH_WEST_MOUTH.x - 1, NORTH_WEST_MOUTH.z)
}
