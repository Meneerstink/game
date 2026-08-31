package gg.rsmod.plugins.content.areas.home

/**
 * HOME_DESIGN_2.png ("2 - DE HERBOUWDE RUIN"): purely visual dressing for the home hub, layered
 * on top of the already-functional zone content (shops/pool/altar/summoning/pvm/transport/gates -
 * see the other home_*.plugin.kts files). The quadrant *placement* of real facilities already
 * matched the design image before this file existed; the concrete remaining gap was the missing
 * physical dressing (market props, a tree/obelisk near Summoning, banners/fence near PvM, a
 * plaza marker) called out for this pass.
 *
 * Every id below is a real, verified cache ObjectDef found via a temporary boot-time diagnostic
 * (`zz_scratch_decor_scan.plugin.kts`, deleted once this file was written) that scanned all
 * ObjectDef names for design-relevant keywords - never guessed. Two keywords the design image
 * suggested came back with zero cache matches and are recorded here so the gap isn't silently
 * dropped: "cobble"/"canopy"/"awning"/"paddock"/"carpet" all had 0 matches, so a real flagstone
 * ground texture or a paddock fence-enclosure/canopy-only prop does not exist as a spawnable
 * object in this cache - achieving true paved ground would need underlay/map tile editing, not
 * an object spawn, and is out of scope here.
 *
 * All new objects are placed either non-solid (the tent) or as single-tile props offset by just
 * one tile from an existing, already-proven-safe anchor tile used by the real facilities in the
 * other home_*.plugin.kts files, to avoid guessing at the octagon's exact wall/corner-cut
 * geometry (`BountyHunterHome.octagonVertices`). This has been compiled and boot-verified
 * (server starts cleanly, no ERROR/Exception), but - per the owner's standing requirement - not
 * yet visually confirmed in a live client against HOME_DESIGN_2.png, since this environment has
 * no client to log in with; that visual pass is still an open item.
 */
val home = world.gameContext.home

// Central Bank & GE: a real "Tent" ObjectDef (id 59439, 4x4, non-solid so it can never block
// movement regardless of exact anchor/footprint convention) as the covered-market roof the
// design shows over the bank area, replacing the previous bare "1 chest, 1 unlabelled object"
// look from bounty_hunter_home.plugin.kts.
spawn_obj(obj = 59439, x = home.x, z = home.z - 2, height = home.height, type = 10, rot = 0)

// Shops (NE): two standing torches flanking the shopkeeper row (home+2..4,1..3 in home_shops.kts).
spawn_obj(obj = 6406, x = home.x + 2, z = home.z + 3, height = home.height, type = 10, rot = 0)
spawn_obj(obj = 6408, x = home.x + 4, z = home.z + 1, height = home.height, type = 10, rot = 0)

// Summoning (W/NW): an inert obelisk (real ObjectDef, no options - can't trigger any unrelated
// obelisk-network handler bound elsewhere in the codebase) as the design's glowing ritual-circle
// prop, plus a tree, both one tile off Pikkupstix's spot (home-3,2 in home_summoning.kts).
spawn_obj(obj = 28735, x = home.x - 4, z = home.z + 2, height = home.height, type = 10, rot = 0)
spawn_obj(obj = 5004, x = home.x - 3, z = home.z + 3, height = home.height, type = 10, rot = 0)

// PvM & Minigames (SW): a hanging banner and a plain fence panel, one tile off the arena
// entrance/archery target anchors (home-3,-2 and home-2,-3 in home_pvm_minigames.kts).
spawn_obj(obj = 900, x = home.x - 4, z = home.z - 2, height = home.height, type = 10, rot = 0)
spawn_obj(obj = 46398, x = home.x - 1, z = home.z - 3, height = home.height, type = 10, rot = 0)

// Aankomst/arrival plaza (S): a real "Ship's wheel" ObjectDef as the design's ship's-wheel
// ground emblem, flanked by two more standing torches for plaza lighting. type=22 (floor
// decoration, same pattern as the bank's ground marker in bounty_hunter_home.plugin.kts) so it
// never adds collision - the wheel sits exactly on home+(0,-3), which home_verify.plugin.kts
// hard-checks as the arrival/death-respawn tile and asserts must stay walkable. A first attempt
// at type=10 here failed that boot-time check (IllegalStateException, caught and fixed before
// this file was ever considered done) - type=22 is also the closer visual match anyway: a flat
// ground emblem inlaid in the plaza floor, not a standing prop.
spawn_obj(obj = 5403, x = home.x, z = home.z - 3, height = home.height, type = 22, rot = 0)
spawn_obj(obj = 6410, x = home.x + 1, z = home.z - 3, height = home.height, type = 10, rot = 0)
spawn_obj(obj = 6412, x = home.x - 1, z = home.z - 4, height = home.height, type = 10, rot = 0)
