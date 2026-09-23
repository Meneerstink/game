package gg.rsmod.plugins.content.areas.grandexchange

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Npc

/*
 * Grand Exchange home audit (owner 2026-09-19: "make sure our home grand exchange every npc is not bugged, make sure everything
 * is visually perfect"). Runs one tick after boot, when every static spawn, Skully and the 78 Store npcs stand, against the REAL
 * collision map of the loaded regions:
 *
 *  - an npc standing inside an object (clipped tile) or on / touching another npc's footprint is moved to the nearest free,
 *    walkable tile that customers can reach, at least one tile of space from every other npc, facing the plaza centre;
 *  - a service npc that customers cannot walk up to is moved the same way (bankers and exchange clerks work across their counters
 *    and are left alone);
 *  - service npcs (no Attack option) show no "(level N)" in the safe home;
 *  - cache options without an npc-specific handler are listed (clicking them may do nothing) - a report, never silently hidden.
 *
 * Prints one summary line plus the details and an ASCII map (N = npc, # = blocked, . = reachable floor) so the next pass can be
 * checked without the client. Visual proof stays the owner's in-game check.
 */

val GE_MIN_X = 3140
val GE_MAX_X = 3192
val GE_MIN_Z = 3468
val GE_MAX_Z = 3515
val GE_CENTRE = Tile(3164, 3490, 0)
val GE_FLOOR = gg.rsmod.plugins.content.mechanics.store.StoreNpcs.EXCHANGE_FLOOR

fun inGe(tile: Tile) = tile.height == 0 && tile.x in GE_MIN_X..GE_MAX_X && tile.z in GE_MIN_Z..GE_MAX_Z

fun footprint(npc: Npc, at: Tile = npc.tile): List<Tile> {
    val size = maxOf(1, npc.def.size)
    return (0 until size).flatMap { dx -> (0 until size).map { dz -> Tile(at.x + dx, at.z + dz, at.height) } }
}

/** Bankers and exchange clerks are reached across their counter, so their own tile is meant to be behind it. */
fun worksAcrossCounter(npc: Npc): Boolean {
    val name = npc.def.name.lowercase()
    return "banker" in name || "exchange clerk" in name
}

fun reachableFloor(): Set<Tile> {
    val seen = HashSet<Tile>()
    val start = if (!world.collision.isClipped(GE_FLOOR)) GE_FLOOR else return seen
    val queue = ArrayDeque<Tile>()
    seen += start
    queue += start
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        for (direction in Direction.NESW) {
            if (!world.collision.canTraverse(current, direction, projectile = false, water = false)) continue
            val next = current.step(direction)
            if (next in seen || !inGe(next)) continue
            seen += next
            queue += next
        }
    }
    return seen
}

fun facingCentre(tile: Tile): Direction {
    val sx = Integer.signum(GE_CENTRE.x - tile.x)
    val sz = Integer.signum(GE_CENTRE.z - tile.z)
    return if (sx == 0 && sz == 0) Direction.SOUTH else Direction.between(tile, Tile(tile.x + sx, tile.z + sz, tile.height))
}

fun relocate(npc: Npc, to: Tile): Npc {
    world.remove(npc)
    val moved =
        Npc(npc.id, to, world).also {
            it.respawnOverride = npc.respawnOverride
            it.static = npc.static
            it.walkRadius = npc.walkRadius
        }
    world.spawn(moved)
    facingCentre(to).let {
        moved.setSpawnFacing(it)
        moved.faceTile(to.step(it))
    }
    return moved
}

on_world_init {
    world.queue {
        wait(1)
        val floor = reachableFloor()
        val npcs = ArrayList<Npc>()
        world.npcs.forEach { if (inGe(it.tile)) npcs += it }
        val problems = ArrayList<String>()
        val fixes = ArrayList<String>()

        fun taken(except: Npc): Set<Tile> = npcs.filter { it !== except }.flatMap { footprint(it) }.toSet()

        fun freeSpot(npc: Npc): Tile? {
            val others = taken(npc)
            // Space: no other npc on or next to any footprint tile.
            fun fits(at: Tile) =
                footprint(npc, at).all { t ->
                    t in floor && !world.collision.isClipped(t) &&
                        (-1..1).all { dx -> (-1..1).all { dz -> Tile(t.x + dx, t.z + dz, 0) !in others } }
                }
            return floor.filter { fits(it) }.minWithOrNull(compareBy({ it.getDistance(npc.tile) }, { it.x }, { it.z }))
        }

        // Later spawns yield when two share a spot. Code-placed npcs (Skully + his chest, the 78 Store npcs: respawnOverride)
        // have their own placement rules and are never moved; the npc next to them moves instead.
        for (npc in npcs.toList().asReversed()) {
            if (npc.respawnOverride == true) continue
            // The home hall's stalls stand shoulder to shoulder along its walls on purpose (ge_home_hall.plugin.kts).
            if (GeHomeHall.contains(npc.tile)) continue
            // Bankers and exchange clerks stand inside their booth on purpose (live boot 2026-09-19 moved all 16 out: wrong).
            if (worksAcrossCounter(npc)) continue
            val tiles = footprint(npc)
            val others = taken(npc)
            val clipped = tiles.any { world.collision.isClipped(it) }
            val overlap = tiles.any { it in others }
            val touching = !overlap && tiles.any { t -> Direction.RS_ORDER.any { t.step(it) in others } } && npc.walkRadius == 0
            val unreachable = !worksAcrossCounter(npc) && tiles.none { t -> t in floor || Direction.NESW.any { t.step(it) in floor } }
            val reason = listOfNotNull(
                "inside an object".takeIf { clipped },
                "on another npc".takeIf { overlap },
                "cannot be reached".takeIf { unreachable },
                "pressed against another npc".takeIf { touching },
            )
            if (reason.isEmpty()) continue
            val spot = freeSpot(npc)
            if (spot == null) {
                problems += "${npc.def.name} (${npc.id}) at ${npc.tile.x},${npc.tile.z}: ${reason.joinToString()} - NO free tile found"
                continue
            }
            val moved = relocate(npc, spot)
            npcs[npcs.indexOf(npc)] = moved
            fixes += "${npc.def.name} (${npc.id}) ${npc.tile.x},${npc.tile.z} -> ${spot.x},${spot.z} (${reason.joinToString()})"
        }

        // No combat level on service npcs in the safe home.
        var levelsHidden = 0
        npcs.filter { n -> n.def.options.none { it.equals("Attack", ignoreCase = true) } && n.def.combatLevel > 0 }.forEach {
            it.setCombatLevel(0)
            levelsHidden++
        }

        // Advertised options without an npc-specific handler.
        val dead = ArrayList<String>()
        npcs.distinctBy { it.id }.forEach { npc ->
            val bound = world.plugins.boundNpcOptions(npc.id)
            val missing = npc.def.options.withIndex()
                .filter { (i, opt) -> !opt.isNullOrBlank() && !opt.equals("Attack", true) && !opt.equals("Examine", true) && (i + 1) !in bound }
                .map { it.value }
            if (missing.isNotEmpty()) dead += "${npc.def.name} (${npc.id}): ${missing.joinToString("/")}"
        }

        println(
            "ge_home_audit: ${npcs.size} npcs at the Grand Exchange, ${fixes.size} moved, ${problems.size} unresolved, " +
                "$levelsHidden combat levels hidden, ${dead.size} npc types with options lacking an npc-specific handler.",
        )
        fixes.forEach { println("ge_home_audit: MOVED $it") }
        problems.forEach { println("ge_home_audit: UNRESOLVED $it") }
        dead.forEach { println("ge_home_audit: OPTIONS $it") }
        val occupied = npcs.flatMap { footprint(it) }.toSet()
        for (z in GE_MAX_Z downTo GE_MIN_Z) {
            val row = StringBuilder("ge_home_audit: $z ")
            for (x in GE_MIN_X..GE_MAX_X) {
                val t = Tile(x, z, 0)
                row.append(
                    when {
                        t in occupied -> 'N'
                        t in floor -> '.'
                        world.collision.isClipped(t) -> '#'
                        else -> ' '
                    },
                )
            }
            println(row)
        }
        npcs.sortedWith(compareBy({ -it.tile.z }, { it.tile.x })).forEach {
            println("ge_home_audit: NPC ${it.def.name} (${it.id}) ${it.tile.x},${it.tile.z} walk=${it.walkRadius}")
        }
    }
}
