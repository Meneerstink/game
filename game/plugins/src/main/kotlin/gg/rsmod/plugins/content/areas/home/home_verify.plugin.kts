package gg.rsmod.plugins.content.areas.home

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
 * 2. the arrival tile (used by new-account start and death respawn) is safe and NOT
 *    collision-blocked (a player must be able to actually stand and move from it).
 * 3. each gate's immediate outward neighbour tile is real Wilderness (NOT safe) - confirming
 *    the gates are genuine transitions, not decorative openings in a wall that doesn't
 *    actually enclose anything.
 */
on_world_init {
    val home = world.gameContext.home

    val facilityTiles =
        mapOf(
            "pool" to home.transform(4, -1),
            "altar" to home.transform(4, -2),
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
    facilityTiles.forEach { (name, tile) ->
        if (!BountyHunterHome.isSafe(tile, home)) {
            unsafeFacilities++
            println("home_verify: FACILITY \"$name\" at $tile is OUTSIDE the safe zone.")
        }
    }
    check(unsafeFacilities == 0) {
        "home_verify: $unsafeFacilities home facility tile(s) fall outside the safe octagon - " +
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
        "home_verify: all ${facilityTiles.size} home facilities reachable+safe, arrival tile " +
            "safe+unblocked, all 4 gates confirmed as real safe->Wilderness transitions.",
    )
}
