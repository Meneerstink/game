package gg.rsmod.plugins.content.areas.home

/**
 * HOME_DESIGN_2.png ("2 - DE HERBOUWDE RUIN"): purely visual dressing for the home hub, layered
 * on top of the already-functional zone content (shops/pool/altar/summoning/pvm/transport/gates -
 * see the other home_*.plugin.kts files).
 *
 * Every id below is a real, verified cache ObjectDef found via temporary boot-time diagnostics in
 * an earlier pass (`zz_scratch_decor_scan*.plugin.kts`, deleted once found) that scanned all
 * ObjectDef names for design-relevant keywords - never guessed. "cobble"/"canopy"/"awning"/
 * "paddock"/"carpet" all had 0 real cache matches, so a real flagstone ground texture or a
 * paddock fence-enclosure/canopy-only prop does not exist as a spawnable object in this cache;
 * "Rug" is used as the closest real analogue to "carpet".
 *
 * BATCH 1: every tile below now comes from [HomeLayout]'s named decor anchors instead of an
 * inline `home+N` literal, so this file can never drift out of sync with the (now much larger)
 * functional layout - `home_verify.plugin.kts` cross-checks every decor tile against every
 * functional facility's footprint at boot.
 */
val home = world.gameContext.home

// Central Bank & GE: a real "Tent" ObjectDef (id 59439, 4x4, non-solid so it can never block
// movement) as the covered-market roof over the bank.
val roofTile = HomeLayout.bankRoof.tile(home)
spawn_obj(obj = 59439, x = roofTile.x, z = roofTile.z, height = roofTile.height, type = 10, rot = 0)

// Shops (NE): two standing torches flanking the shopkeeper row.
val shopTorch1 = HomeLayout.decorShopTorch1.tile(home)
val shopTorch2 = HomeLayout.decorShopTorch2.tile(home)
spawn_obj(obj = 6406, x = shopTorch1.x, z = shopTorch1.z, height = shopTorch1.height, type = 10, rot = 0)
spawn_obj(obj = 6408, x = shopTorch2.x, z = shopTorch2.z, height = shopTorch2.height, type = 10, rot = 0)

// Summoning (NW): an inert obelisk (real ObjectDef, no options - can't trigger any unrelated
// obelisk-network handler bound elsewhere) plus a tree, near Pikkupstix's spot.
val summoningObelisk = HomeLayout.decorSummoningObelisk.tile(home)
val summoningTree = HomeLayout.decorSummoningTree.tile(home)
spawn_obj(obj = 28735, x = summoningObelisk.x, z = summoningObelisk.z, height = summoningObelisk.height, type = 10, rot = 0)
spawn_obj(obj = 5004, x = summoningTree.x, z = summoningTree.z, height = summoningTree.height, type = 10, rot = 0)

// PvM & Minigames (SW): a hanging banner and a plain fence panel, near the arena entrance/target.
val pvmBanner = HomeLayout.decorPvmBanner.tile(home)
val pvmFence = HomeLayout.decorPvmFence.tile(home)
spawn_obj(obj = 900, x = pvmBanner.x, z = pvmBanner.z, height = pvmBanner.height, type = 10, rot = 0)
spawn_obj(obj = 46398, x = pvmFence.x, z = pvmFence.z, height = pvmFence.height, type = 10, rot = 0)

// Aankomst/arrival plaza (S): a real "Ship's wheel" ObjectDef as the design's ship's-wheel ground
// emblem, sitting exactly on the arrival tile itself (type=22 floor decoration, same pattern as
// the bank's ground marker - never adds collision), flanked by two more standing torches.
val arrivalWheel = HomeLayout.decorArrivalWheel.tile(home)
val arrivalTorch1 = HomeLayout.decorArrivalTorch1.tile(home)
val arrivalTorch2 = HomeLayout.decorArrivalTorch2.tile(home)
spawn_obj(obj = 5403, x = arrivalWheel.x, z = arrivalWheel.z, height = arrivalWheel.height, type = 22, rot = 0)
spawn_obj(obj = 6410, x = arrivalTorch1.x, z = arrivalTorch1.z, height = arrivalTorch1.height, type = 10, rot = 0)
spawn_obj(obj = 6412, x = arrivalTorch2.x, z = arrivalTorch2.z, height = arrivalTorch2.height, type = 10, rot = 0)

// Ruin scatter (whole enclave): real "Rubble" (12812) and "Ruined Pillar" (36697) ObjectDefs,
// both 1x1/non-interactive, near the Transport and PvM clusters respectively.
val ruinRubble = HomeLayout.decorRuinRubble.tile(home)
val ruinPillar = HomeLayout.decorRuinPillar.tile(home)
spawn_obj(obj = 12812, x = ruinRubble.x, z = ruinRubble.z, height = ruinRubble.height, type = 10, rot = 0)
spawn_obj(obj = 36697, x = ruinPillar.x, z = ruinPillar.z, height = ruinPillar.height, type = 10, rot = 0)

// Vervoer (SE): real display-only props near home_transport.plugin.kts's NPC/shop - never on an
// adjacent tile of it, so its own reachability check in home_verify.plugin.kts can't be affected.
// A real 4x4 "Gnome glider" ObjectDef (5825) exists but is deliberately skipped: at 4x4/solid it
// is too large to place here without visual confirmation that it clears the transport cluster.
val transportCart = HomeLayout.decorTransportCart.tile(home)
val transportBalloon = HomeLayout.decorTransportBalloon.tile(home)
val transportRug = HomeLayout.decorTransportRug.tile(home)
spawn_obj(obj = 4974, x = transportCart.x, z = transportCart.z, height = transportCart.height, type = 10, rot = 0) // Mine cart
spawn_obj(obj = 123, x = transportBalloon.x, z = transportBalloon.z, height = transportBalloon.height, type = 10, rot = 0) // Party Balloon
spawn_obj(obj = 13590, x = transportRug.x, z = transportRug.z, height = transportRug.height, type = 10, rot = 0) // Rug
