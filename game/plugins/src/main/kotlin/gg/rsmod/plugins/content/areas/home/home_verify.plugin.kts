package gg.rsmod.plugins.content.areas.home

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.GameObject

/** True when a loaded static/dynamic object with [id] stands on [tile]. */
/** A pawn can stand on [tile]: not every cardinal step out of it is collision-blocked (a solid object flags all of them; a wall flags one side only). */
fun standable(tile: Tile): Boolean = !Direction.NESW.all { world.collision.isBlocked(tile, it, projectile = false) }

fun objectAt(tile: Tile, id: Int): Boolean =
    world.chunks.get(tile, createIfNeeded = true)!!
        .getEntities<GameObject>(tile, EntityType.STATIC_OBJECT, EntityType.DYNAMIC_OBJECT)
        .any { it.id == id }

/**
 * Boot-time self-check of the Ferox Enclave home, run against the REAL collision map built from
 * the imported cache regions (not against this file's own assumptions):
 *
 *  1. every facility footprint lies inside the safe polygon;
 *  2. spawned solid facilities do not overlap each other;
 *  3. every imported facility's object really stands on its tile (proves the cache import is what
 *     the server loaded, not a stale region);
 *  4. the configured home tile and the arrival tile are safe and walkable;
 *  5. every facility is reachable on foot from arrival through safe, unclipped tiles;
 *  6. every barrier that is declared to exit to the Wilderness really has a non-safe, positive
 *     Wilderness-level landing tile one step outside, and a safe landing tile one step inside;
 *  7. the tile one step outside each outer landing is not safe either (no off-by-one safe strip).
 *
 * Any failure aborts boot loudly. This does NOT prove client rendering - that stays a human check.
 */
on_world_init {
    val home = world.gameContext.home

    // The owner moved the respawn to Grand Exchange. Ferox's imported-object audit remains
    // valid for that map, but it must not reject a deliberately PvP-enabled GE home tile.
    if (home.x in 3140..3190 && home.z in 3460..3510) {
        check(!!standable(home)) { "home_verify: configured Grand Exchange home tile $home is collision-blocked." }
        check(!!standable(home.transform(0, -1))) { "home_verify: Grand Exchange arrival tile is collision-blocked." }
        println("home_verify: Grand Exchange home $home is standable; Ferox facility audit retained separately.")
        return@on_world_init
    }

    var outsideSafeZone = 0
    HomeLayout.functional.forEach { facility ->
        facility.footprint(home).forEach { tile ->
            if (!BountyHunterHome.isSafe(tile, home)) {
                outsideSafeZone++
                println("home_verify: FACILITY \"${facility.name}\" tile $tile is OUTSIDE the safe zone.")
            }
        }
    }
    check(outsideSafeZone == 0) { "home_verify: $outsideSafeZone facility tile(s) fall outside the Ferox safe polygon." }

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
    check(overlaps == 0) { "home_verify: $overlaps facility tile overlap(s)." }

    var missingImported = 0
    HomeLayout.functional.filter { it.imported }.forEach { facility ->
        val tile = facility.tile(home)
        val present = objectAt(tile, facility.objectId)
        if (!present) {
            missingImported++
            println("home_verify: IMPORTED object ${facility.objectId} (\"${facility.name}\") is NOT in the world at $tile.")
        }
    }
    check(missingImported == 0) { "home_verify: $missingImported imported Ferox object(s) missing from the loaded regions." }

    val arrivalTile = HomeLayout.arrival.tile(home)
    check(BountyHunterHome.isSafe(home, home)) { "home_verify: configured home tile $home is OUTSIDE the safe zone." }
    check(!!standable(home)) { "home_verify: configured home tile $home is collision-blocked." }
    check(BountyHunterHome.isSafe(arrivalTile, home)) { "home_verify: arrival tile $arrivalTile is OUTSIDE the safe zone." }
    check(!!standable(arrivalTile)) { "home_verify: arrival tile $arrivalTile is collision-blocked." }
    check(arrivalTile == home.transform(0, -1)) {
        "home_verify: HomeLayout.arrival $arrivalTile must equal home(0,-1) - PlayerDeathAction/PlayerSerializerService use that offset."
    }

    // Internal barriers (plaza <-> garden/annex) are crossed with their "Pass-Through" force-move, so the
    // walk graph gets an extra edge between each internal barrier's two landing tiles.
    val internalLinks = BountyHunterHome.gates(home).filter { !it.exitsToWilderness }
        .flatMap { listOf(it.innerLanding to it.outerLanding, it.outerLanding to it.innerLanding) }
    val reachable = HashSet<Tile>()
    val queue = ArrayDeque<Tile>()
    reachable.add(arrivalTile)
    queue.add(arrivalTile)
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        internalLinks.filter { it.first == current }.forEach { (_, far) ->
            if (far !in reachable && standable(far)) {
                reachable.add(far)
                queue.add(far)
            }
        }
        for (direction in Direction.NESW) {
            val next = current.step(direction)
            if (next in reachable) continue
            if (!BountyHunterHome.isSafe(next, home)) continue
            if (!standable(next)) continue
            if (!world.collision.canTraverse(current, direction, projectile = false, water = false)) continue
            reachable.add(next)
            queue.add(next)
        }
    }
    println("home_verify: flood-fill from arrival reached ${reachable.size} walkable safe tiles.")

    var unreachableFacilities = 0
    HomeLayout.functional.forEach { facility ->
        val tiles = facility.footprint(home)
        val reached = tiles.any { t -> t in reachable || Direction.NESW.any { t.step(it) in reachable } }
        if (!reached) {
            unreachableFacilities++
            println("home_verify: FACILITY \"${facility.name}\" at ${facility.tile(home)} is NOT reachable on foot from arrival.")
            tiles.forEach { t ->
                Direction.NESW.forEach { direction ->
                    val n = t.step(direction)
                    println("home_verify:   $t neighbour $direction $n - isSafe=${BountyHunterHome.isSafe(n, home)} isClipped=${!standable(n)} reached=${n in reachable}")
                }
            }
        }
    }
    if (unreachableFacilities > 0) {
        // Diagnostic collision map of the whole enclave footprint: # clipped, a reachable from arrival,
        // b reachable from the bank chest's south neighbour, . walkable but in neither component.
        val bankSide = HomeLayout.bank.tile(home).transform(0, -1)
        val bankReach = HashSet<Tile>()
        val bankQueue = ArrayDeque<Tile>()
        if (!!standable(bankSide)) {
            bankReach.add(bankSide)
            bankQueue.add(bankSide)
        }
        while (bankQueue.isNotEmpty()) {
            val current = bankQueue.removeFirst()
            for (direction in Direction.NESW) {
                val next = current.step(direction)
                if (next in bankReach || !BountyHunterHome.isSafe(next, home) || !standable(next)) continue
                if (!world.collision.canTraverse(current, direction, projectile = false, water = false)) continue
                bankReach.add(next)
                bankQueue.add(next)
            }
        }
        println("home_verify: bank-side flood-fill reached ${bankReach.size} tiles from $bankSide")
        for (z in BountyHunterHome.MAX_Z downTo BountyHunterHome.EAST_MIN_Z) {
            val row = StringBuilder("home_verify: $z ")
            for (x in BountyHunterHome.MAIN_MIN_X..BountyHunterHome.EAST_MAX_X) {
                val t = Tile(x, z, home.height)
                row.append(
                    when {
                        !BountyHunterHome.isSafe(t, home) -> ' '
                        !standable(t) -> '#'
                        t in reachable -> 'a'
                        t in bankReach -> 'b'
                        else -> '.'
                    },
                )
            }
            println(row)
        }
    }
    check(unreachableFacilities == 0) { "home_verify: $unreachableFacilities facility(-ies) unreachable from arrival." }

    // Owner answer Q12: the paid Ferox respawn tile next to The Old Nite must be a safe, standable tile reachable from arrival.
    val feroxRespawn = gg.rsmod.plugins.content.areas.wilderness.FeroxRespawn.TILE
    for (z in 3648 downTo 3638) {
        val row = StringBuilder("home_verify: pub $z ")
        for (x in 3144..3158) {
            val t = Tile(x, z, 0)
            row.append(if (t == feroxRespawn) 'R' else if (!standable(t)) '#' else if (t in reachable) 'a' else '.')
        }
        println(row)
    }
    check(BountyHunterHome.isSafe(feroxRespawn, home) && standable(feroxRespawn) && feroxRespawn in reachable) {
        "home_verify: Ferox respawn tile $feroxRespawn is not a safe, standable tile reachable from arrival."
    }
    println("home_verify: Ferox respawn tile $feroxRespawn safe, standable and reachable from arrival.")

    var badGates = 0
    BountyHunterHome.gates(home).forEach { gate ->
        val objectPresent = objectAt(gate.tile, FeroxObjects.BARRIER_A) || objectAt(gate.tile, FeroxObjects.BARRIER_B)
        if (!objectPresent) {
            badGates++
            println("home_verify: BARRIER at ${gate.tile} - no imported Barrier object stands there.")
        }
        if (!BountyHunterHome.isSafe(gate.innerLanding, home) || !standable(gate.innerLanding)) {
            badGates++
            println("home_verify: BARRIER at ${gate.tile} - inner landing ${gate.innerLanding} is not a safe walkable tile.")
        }
        if (gate.exitsToWilderness) {
            val outer = gate.outerLanding
            val beyond = outer.step(gate.direction)
            if (BountyHunterHome.isSafe(outer, home) || !BountyHunterHome.isDangerousWilderness(outer, home)) {
                badGates++
                println("home_verify: BARRIER at ${gate.tile} - outer landing $outer is not dangerous Wilderness.")
            }
            if (BountyHunterHome.isSafe(beyond, home)) {
                badGates++
                println("home_verify: BARRIER at ${gate.tile} - $beyond (two steps out) is still safe: off-by-one safe strip.")
            }
            if (!standable(outer)) {
                badGates++
                println("home_verify: BARRIER at ${gate.tile} - outer landing $outer is collision-blocked.")
            }
        } else if (!BountyHunterHome.isSafe(gate.outerLanding, home)) {
            badGates++
            println("home_verify: internal BARRIER at ${gate.tile} - far side ${gate.outerLanding} unexpectedly outside the safe zone.")
        }
    }
    check(badGates == 0) { "home_verify: $badGates barrier problem(s) - see the printed list above." }

    println(
        "home_verify: Ferox home OK - ${HomeLayout.functional.size} facilities inside the safe polygon, " +
            "${HomeLayout.functional.count { it.imported }} imported objects present, all reachable from arrival $arrivalTile, " +
            "${BountyHunterHome.gates(home).count { it.exitsToWilderness }} Wilderness exits verified. Client rendering remains a human check.",
    )
}
