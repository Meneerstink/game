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
 * SUPERSEDED (RCV-007): the earlier provisional plain passage is replaced by the Void-sourced
 * tunnel (level 21, climb-into/crawl/climb-out animations and the donor's start/end tiles) in
 * skills/agility/sourced_shortcuts.plugin.kts, together with the Yanille (9301/9302) and Falador
 * (9309/9310) tunnels. No bindings remain in this file.
 */
