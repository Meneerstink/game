package gg.rsmod.plugins.content.areas.home

import gg.rsmod.game.model.Direction

/**
 * R02.1/R02.2/R14.5/HOME_DESIGN_2.png: automated, boot-time, non-visual verification that goes
 * beyond "the boot didn't crash" - the owner's own explicit standard. Runs after every other
 * home file's `on_world_init` (registration order = file load order in this loader, and this
 * file's name sorts after `home_*` alphabetically... actually not guaranteed - see the
 * `check()` failures below, which would fail LOUD at boot if ordering ever changes and a wall/
 * gate hadn't been placed yet when this runs).
 *
 * Confirms, with real tile-level assertions (not just spawn counts):
 * 1. every placed home facility's tile is inside the real safe-zone polygon
 *    ([BountyHunterHome.isSafe]) - reuses the SAME shape the wall/collision boundary traces,
 *    so this can never silently disagree with the physical barrier.
 * 2. every facility tile also has at least one non-clipped orthogonal neighbour - a rough,
 *    still-non-visual proxy for "a player can actually stand next to this and click it", not
 *    just "the tile is inside the polygon" (audit finding 4: the old version only checked
 *    polygon membership, which says nothing about whether the object's own footprint or a
 *    neighbouring facility's footprint has silently boxed it in).
 * 3. the arrival tile (used by new-account start and death respawn) is safe and NOT
 *    collision-blocked (a player must be able to actually stand and move from it).
 * 4. each gate's immediate outward neighbour tile is real Wilderness (NOT safe) - confirming
 *    the gates are genuine transitions, not decorative openings in a wall that doesn't
 *    actually enclose anything.
 *
 * What this still does NOT prove (audit finding 4, honestly disclosed, unchanged this pass):
 * actual client rendering, model presence, or a real click reaching the option handler - only
 * tile-level polygon/collision facts. See OWNER_TASK_STATUS.md for the open visual-verification
 * limitation.
 */
on_world_init {
    val home = world.gameContext.home

    val facilityTiles =
        mapOf(
            "pool" to home.transform(3, -1),
            "altar" to home.transform(3, -2),
            "board" to home.transform(1, -2),
            "shop-general" to home.transform(2, 1),
            "shop-runes" to home.transform(2, 2),
            "shop-armour" to home.transform(3, 2),
            "shop-archery" to home.transform(3, 3),
            "shop-staffs" to home.transform(4, 2),
            "transport" to home.transform(2, -4),
            "summoning" to home.transform(-3, 2),
            "pvm-arena-entrance" to home.transform(-3, -2),
            "pvm-archery-target" to home.transform(-2, -3),
        )

    var unsafeFacilities = 0
    var boxedInFacilities = 0
    facilityTiles.forEach { (name, tile) ->
        if (!BountyHunterHome.isSafe(tile, home)) {
            unsafeFacilities++
            println("home_verify: FACILITY \"$name\" at $tile is OUTSIDE the safe zone.")
        }
        val hasOpenNeighbour = Direction.NESW.any { !world.collision.isClipped(tile.step(it)) }
        if (!hasOpenNeighbour) {
            boxedInFacilities++
            println("home_verify: FACILITY \"$name\" at $tile has NO open adjacent tile - it looks boxed in.")
        }
    }
    check(unsafeFacilities == 0) {
        "home_verify: $unsafeFacilities home facility tile(s) fall outside the safe octagon - " +
            "see the printed list above."
    }
    check(boxedInFacilities == 0) {
        "home_verify: $boxedInFacilities home facility tile(s) have no open adjacent tile - " +
            "see the printed list above."
    }

    val arrivalTile = home.transform(0, -3)
    check(BountyHunterHome.isSafe(arrivalTile, home)) {
        "home_verify: arrival tile $arrivalTile is OUTSIDE the safe zone."
    }
    check(!world.collision.isClipped(arrivalTile)) {
        "home_verify: arrival tile $arrivalTile is collision-blocked - a player could not stand there."
    }

    var badGates = 0
    BountyHunterHome.gateTiles(home).forEach { gate ->
        val outward =
            when {
                gate.z > home.z -> gate.transform(0, 1)
                gate.z < home.z -> gate.transform(0, -1)
                gate.x > home.x -> gate.transform(1, 0)
                else -> gate.transform(-1, 0)
            }
        if (BountyHunterHome.isSafe(outward, home)) {
            badGates++
            println("home_verify: GATE at $gate does not actually transition to Wilderness ($outward is still safe).")
        }
    }
    check(badGates == 0) { "home_verify: $badGates gate(s) do not lead to real Wilderness - see the printed list above." }

    println(
        "home_verify: all ${facilityTiles.size} home facility tiles pass safe-zone+open-" +
            "neighbour checks, arrival tile safe+unblocked, all 4 gates confirmed as real " +
            "safe->Wilderness transitions. This does NOT prove client-visible rendering or a " +
            "real click reaching each handler - see OWNER_TASK_STATUS.md.",
    )
}
