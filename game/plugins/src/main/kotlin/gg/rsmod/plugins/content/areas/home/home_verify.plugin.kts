package gg.rsmod.plugins.content.areas.home

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile

/**
 * R02.1/R02.2/R14.5/HOME_DESIGN_2.png/BATCH 1: automated, boot-time, non-visual verification that
 * goes beyond "the boot didn't crash" - the owner's own explicit standard. Runs after every other
 * home file's `on_world_init` (registration order = file load order in this loader, not strictly
 * guaranteed by filename - see the `check()` failures below, which would fail LOUD at boot if
 * ordering ever changes and a wall/gate hadn't been placed yet when this runs).
 *
 * BATCH 1 strengthened this from a per-tile "inside the polygon + one open neighbour" spot-check
 * into:
 * 1. every [HomeLayout.functional] facility's full declared footprint is inside the real safe-zone
 *    polygon ([BountyHunterHome.isSafe]) - reuses the SAME shape the wall/collision boundary
 *    traces, so this can never silently disagree with the physical barrier.
 * 2. no two solid [HomeLayout.functional] facilities claim the same tile (real overlap check
 *    against this file's OWN layout plan, not each object's live cache footprint/rotation, which
 *    stays the responsibility of the plugin file that actually spawns the object).
 * 3. every solid facility is actually reachable on foot: a real 4-directional flood-fill from the
 *    arrival tile across every non-clipped safe-zone tile, then checking each facility (or an
 *    orthogonal neighbour of it) landed in that reachable set - not just "has one open neighbour",
 *    which says nothing about whether that neighbour connects back to the rest of the hub.
 * 4. the flood-fill also reaches at least 90% of all safe-zone tiles - a lightweight, honest proxy
 *    for "wide open routes, nothing bottlenecked into a 1-tile maze corridor" (this hub places
 *    isolated small facilities across an otherwise open field rather than building corridors, so a
 *    real narrow-corridor bug would show up as a materially smaller reachable fraction; it does
 *    NOT geometrically prove a literal 3-tile minimum width everywhere - ponytail: upgrade to a
 *    real route-width rasteriser only if a future layout actually adds corridor-like walls).
 * 5. every decor tile either lands on a functional facility's tile ONLY when intentionally marked
 *    non-solid (e.g. the arrival ground emblem), or on a completely free tile - never silently
 *    covering a different functional facility.
 * 6. the arrival tile (new-account start and death respawn) is safe and NOT collision-blocked.
 * 7. each gate's immediate outward neighbour tile is real Wilderness (NOT safe) - confirming the
 *    gates are genuine transitions, not decorative openings in a wall that doesn't actually
 *    enclose anything.
 *
 * What this still does NOT prove (honestly disclosed, unchanged this pass): actual client
 * rendering, model presence, or a real click reaching the option handler - only tile-level
 * polygon/collision facts. See OWNER_TASK_STATUS.md for the open visual-verification limitation.
 */
on_world_init {
    val home = world.gameContext.home

    // ---- 1. every functional facility's footprint is inside the safe polygon ----
    var outsideSafeZone = 0
    HomeLayout.functional.forEach { facility ->
        facility.footprint(home).forEach { tile ->
            if (!BountyHunterHome.isSafe(tile, home)) {
                outsideSafeZone++
                println("home_verify: FACILITY \"${facility.name}\" tile $tile is OUTSIDE the safe zone.")
            }
        }
    }
    check(outsideSafeZone == 0) {
        "home_verify: $outsideSafeZone home facility tile(s) fall outside the safe octagon - see the printed list above."
    }

    // ---- 2. no two solid facilities claim the same tile ----
    val tileOwner = HashMap<Tile, String>()
    var overlaps = 0
    HomeLayout.functional.filter { it.solid }.forEach { facility ->
        facility.footprint(home).forEach { tile ->
            val existing = tileOwner.putIfAbsent(tile, facility.name)
            if (existing != null) {
                overlaps++
                println("home_verify: OVERLAP - \"${facility.name}\" and \"$existing\" both claim tile $tile.")
            }
        }
    }
    check(overlaps == 0) { "home_verify: $overlaps facility tile overlap(s) - see the printed list above." }

    // ---- 3/4. real flood-fill reachability from the arrival tile ----
    val arrivalTile = HomeLayout.arrival.tile(home)
    val reachable = HashSet<Tile>()
    val queue = ArrayDeque<Tile>()
    reachable.add(arrivalTile)
    queue.add(arrivalTile)
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        for (direction in Direction.NESW) {
            val next = current.step(direction)
            if (next in reachable) continue
            if (!BountyHunterHome.isSafe(next, home)) continue
            if (world.collision.isClipped(next)) continue
            reachable.add(next)
            queue.add(next)
        }
    }

    var totalSafeTiles = 0
    for (x in -BountyHunterHome.SAFE_RADIUS..BountyHunterHome.SAFE_RADIUS) {
        for (z in -BountyHunterHome.SAFE_RADIUS..BountyHunterHome.SAFE_RADIUS) {
            if (BountyHunterHome.isSafe(home.transform(x, z), home)) totalSafeTiles++
        }
    }
    val reachablePct = reachable.size * 100.0 / totalSafeTiles
    println(
        "home_verify: flood-fill from arrival reached ${reachable.size}/$totalSafeTiles safe-zone " +
            "tiles (%.1f%%).".format(reachablePct),
    )
    check(reachablePct >= 90.0) {
        "home_verify: only %.1f%% of the safe zone is reachable from arrival - the layout is ".format(reachablePct) +
            "bottlenecked or boxed in somewhere."
    }

    var unreachableFacilities = 0
    HomeLayout.functional.filter { it.solid }.forEach { facility ->
        val tile = facility.tile(home)
        val reached = tile in reachable || Direction.NESW.any { tile.step(it) in reachable }
        if (!reached) {
            unreachableFacilities++
            println("home_verify: FACILITY \"${facility.name}\" at $tile is NOT reachable on foot from arrival.")
            Direction.NESW.forEach { direction ->
                val n = tile.step(direction)
                println(
                    "home_verify:   neighbour $direction $n - isSafe=${BountyHunterHome.isSafe(n, home)} " +
                        "isClipped=${world.collision.isClipped(n)} inReachableSet=${n in reachable}",
                )
            }
        }
    }
    check(unreachableFacilities == 0) {
        "home_verify: $unreachableFacilities facility(-ies) unreachable from arrival - see the printed list above."
    }

    // ---- 5. decor never silently covers a different functional facility ----
    var badDecor = 0
    HomeLayout.decor.forEach { decor ->
        val tile = decor.tile(home)
        val owner = tileOwner[tile]
        val allowed = owner == null || (decor.name == "decor-arrival-wheel" && owner == HomeLayout.arrival.name)
        if (!allowed) {
            badDecor++
            println("home_verify: DECOR \"${decor.name}\" at $tile silently covers functional facility \"$owner\".")
        }
    }
    check(badDecor == 0) { "home_verify: $badDecor decor tile(s) cover an unrelated functional facility - see above." }

    // ---- 6. arrival tile itself is safe and walkable ----
    check(BountyHunterHome.isSafe(arrivalTile, home)) { "home_verify: arrival tile $arrivalTile is OUTSIDE the safe zone." }
    check(!world.collision.isClipped(arrivalTile)) {
        "home_verify: arrival tile $arrivalTile is collision-blocked - a player could not stand there."
    }

    // ---- 7. every gate's outward neighbour is real Wilderness ----
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

    // ---- 8. real 3-tile-wide route connectivity (owner instruction: "drie tegels brede routes") ----
    // A tile counts as "wide" only if it AND all 4 orthogonal neighbours are safe+unclipped - a
    // real (if approximate) 3-tile-clearance test, replacing check 4's honest "90% reachable"
    // proxy for this specific requirement rather than just re-using it.
    fun isWide(t: Tile): Boolean {
        if (!BountyHunterHome.isSafe(t, home) || world.collision.isClipped(t)) return false
        return Direction.NESW.all { d ->
            val n = t.step(d)
            BountyHunterHome.isSafe(n, home) && !world.collision.isClipped(n)
        }
    }
    val wideStart = (listOf(arrivalTile) + Direction.NESW.map { arrivalTile.step(it) }).firstOrNull { isWide(it) }
    check(wideStart != null) { "home_verify: arrival tile has no 3-tile-wide neighbourhood at all." }
    val wideReachable = HashSet<Tile>()
    val wideQueue = ArrayDeque<Tile>()
    wideReachable.add(wideStart)
    wideQueue.add(wideStart)
    while (wideQueue.isNotEmpty()) {
        val current = wideQueue.removeFirst()
        for (direction in Direction.NESW) {
            val next = current.step(direction)
            if (next in wideReachable || !isWide(next)) continue
            wideReachable.add(next)
            wideQueue.add(next)
        }
    }
    var narrowFacilities = 0
    HomeLayout.functional.filter { it.solid }.forEach { facility ->
        val tile = facility.tile(home)
        // Radius 2 (not just the immediate touching neighbour): a facility's own solid tile
        // always blocks one side of its direct neighbours, so requiring the doorstep tile
        // itself to be 3-wide-clear would fail for every facility by construction. Radius 2
        // tests the actual APPROACH route instead of the unavoidable last half-step.
        val hasWideAccess = (-2..2).any { dx -> (-2..2).any { dz -> tile.transform(dx, dz) in wideReachable } }
        if (!hasWideAccess) {
            narrowFacilities++
            println("home_verify: FACILITY \"${facility.name}\" at $tile has NO 3-tile-wide route from arrival.")
        }
    }
    check(narrowFacilities == 0) {
        "home_verify: $narrowFacilities facility(-ies) lack a 3-tile-wide route from arrival - see above."
    }
    println("home_verify: 3-tile-wide route flood-fill from arrival reached ${wideReachable.size} wide tiles; all ${HomeLayout.functional.count { it.solid }} facilities have wide access.")

    println(
        "home_verify: all ${HomeLayout.functional.size} functional facilities pass safe-zone+no-" +
            "overlap+reachability+3-tile-wide-route checks, arrival tile safe+unblocked, all 4 " +
            "gates confirmed as real safe->Wilderness transitions. This does NOT prove client-" +
            "visible rendering or a real click reaching each handler - see OWNER_TASK_STATUS.md.",
    )
}
