package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.collision.CollisionUpdate
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.plugins.api.cfg.Npcs

/**
 * Skully and his Loot Chest at every bank site (owner 2026-09-17: "zorg ervoor dat ze niet in een
 * bank staan op een object of ... op een plek waar 'I can't reach'", "haal de combat level weg en
 * hernoem ze naar Skully, Skully Jr, Skully Sr, Skully Max, Skully Bob").
 *
 * Placement: every site names an ANCHOR - a tile on the customer side of that bank that players
 * stand on every day (the classic banking tiles) - and a WANTED tile a few steps away from the
 * booths. At boot Skully is put on the walkable tile nearest to WANTED that is reachable on foot
 * from the anchor (a bounded flood fill over the real collision map, doors excluded), so he can
 * never end up behind a counter, inside a booth or on the wrong side of a wall; the chest takes the
 * walkable neighbour of that tile that is reachable the same way. If no such tile exists inside
 * the search radius the site is reported in the boot line instead of guessed.
 *
 * Names: the five owner-given names are cache npc definitions. [Npcs.SKULLY] (OSRS import 10382) is
 * "Skully"; the other four are clones of that definition with only the name changed
 * (`OsrsNpcImportTool` batch `skully-family`). None of them carries a combat level, and
 * `Npc.init` publishes level 0 for such npcs so the client shows no "(level N)".
 */
object SkullyRoster {
    const val LOOT_CHEST = 62582

    /** Clones of the Skully definition with the owner's names (see the class doc). */
    const val SKULLY_JR = 14410
    const val SKULLY_SR = 14411
    const val SKULLY_MAX = 14412
    const val SKULLY_BOB = 14413

    val NPC_IDS: Set<Int> = setOf(Npcs.SKULLY, SKULLY_JR, SKULLY_SR, SKULLY_MAX, SKULLY_BOB)

    class Site(
        val label: String,
        val npcId: Int,
        /** Customer-side standing tile of this bank (reachability reference). */
        val anchor: Tile,
        /** Preferred Skully tile; the nearest reachable walkable tile to it is used. */
        val wanted: Tile,
        /**
         * Owner-named tile: used exactly as given, with no wall test, no reachability test and no snapping.
         *
         * The wall rule below is a heuristic for the sites nobody has hand-placed. When the owner names a tile it
         * is not a hint - on 2026-09-20 he asked for Edgeville's Skully "in 3095, 3499, 0 position facing south"
         * after the heuristic had quietly moved him four tiles to 3095,3495 and into a bank booth, because
         * 3095,3499 does not pass `isAgainstWall`. An owner coordinate now wins outright.
         */
        val exact: Boolean = false,
        /** Owner-named facing; when null the open side nearest the anchor is used. */
        val face: Direction? = null,
        /** Owner-named chest tile (exact sites); when null the best free cardinal neighbour is used. */
        val chest: Tile? = null,
        /** Cache scenery taken out of the bank before Skully is put there (owner: "remove the desk ... put skully there"). */
        val clear: List<Tile> = emptyList(),
        /** Owner-named chest front when it must differ from Skully's facing. */
        val chestFront: Direction? = null,
    )

    /**
     * Eleven sites (owner 2026-09-16 bank list + 2026-09-17: "plaats skully van edgeville op deze
     * coordinaten 3095,3499,0" and "plaats nog 1 skully op deze coordinaten 2943,3370,0"), five
     * names cycling through them.
     */
    val SITES: List<Site> =
        listOf(
            Site("Grand Exchange", Npcs.SKULLY, anchor = Tile(3165, 3487, 0), wanted = Tile(3161, 3484, 0)),
            // 2026-09-23 bank pass: the heuristic had put him on the staircase tile (3255,3421). North-west corner of
            // the customer side, back to the west wall, chest in the corner.
            Site("Varrock east bank", SKULLY_JR, anchor = Tile(3253, 3420, 0), wanted = Tile(3250, 3422, 0), exact = true, face = Direction.EAST, chest = Tile(3250, 3423, 0)),
            // 2026-09-23 bank pass: he stood in the east booth line with the chest behind the counter. North wall of the
            // customer hall, west of the north passage.
            Site("Varrock west bank", SKULLY_SR, anchor = Tile(3185, 3436, 0), wanted = Tile(3185, 3446, 0), exact = true, face = Direction.SOUTH, chest = Tile(3184, 3446, 0)),
            // Owner 2026-09-23: remove the desk on the customer-side south wall (3092,3488) and put Skully Max + chest
            // there, back to the wall, facing into the bank (was 3095,3499 facing south).
            Site(
                "Edgeville bank", SKULLY_MAX, anchor = Tile(3094, 3491, 0), wanted = Tile(3092, 3488, 0), exact = true,
                face = Direction.NORTH, chest = Tile(3093, 3488, 0), clear = listOf(Tile(3092, 3488, 0)),
            ),
            // Owner 2026-09-23 (Camelot): remove the west-wall desk and put Skully Bob there, facing into the bank.
            Site(
                "Seers' Village bank", SKULLY_BOB, anchor = Tile(2725, 3491, 0), wanted = Tile(2721, 3491, 0), exact = true,
                face = Direction.EAST, chest = Tile(2721, 3490, 0), clear = listOf(Tile(2721, 3491, 0), Tile(2721, 3490, 0)),
            ),
            // 2026-09-23 bank pass: the heuristic had put him outside the south wall. West wall of the customer side.
            Site("Falador east bank", Npcs.SKULLY, anchor = Tile(3013, 3355, 0), wanted = Tile(3008, 3357, 0), exact = true, face = Direction.EAST, chest = Tile(3008, 3358, 0)),
            // 2026-09-23 bank pass: the chest stood on the counter (3091,3245). South wall of the customer side.
            Site("Draynor bank", SKULLY_JR, anchor = Tile(3092, 3243, 0), wanted = Tile(3094, 3240, 0), exact = true, face = Direction.NORTH, chest = Tile(3093, 3240, 0)),
            // 2026-09-23 bank pass: the heuristic put him in the east entrance (3273,3165). North-east corner, back to the east wall.
            Site("Al Kharid bank", SKULLY_SR, anchor = Tile(3269, 3167, 0), wanted = Tile(3272, 3171, 0), exact = true, face = Direction.WEST, chest = Tile(3272, 3170, 0)),
            // 2026-09-23 bank pass: he stood on the east booth (2812,3439). East wall, between the two closed booths.
            Site("Catherby bank", SKULLY_MAX, anchor = Tile(2809, 3441, 0), wanted = Tile(2812, 3441, 0), exact = true, face = Direction.WEST, chest = Tile(2812, 3440, 0)),
            // 2026-09-23 bank pass: he stood on a closed booth (3209,3221). East wall of the customer side.
            Site("Lumbridge castle bank", SKULLY_BOB, anchor = Tile(3208, 3220, 2), wanted = Tile(3210, 3219, 2), exact = true, face = Direction.WEST, chest = Tile(3210, 3218, 2)),
            // Owner 2026-09-23: "verplaats skully naar 2943, 3368, 0", deposit box removed, "de chest en hij correct kijkt
            // naar de uitgang van de bank" - the exit is the north-wall opening (2945-2946, 3373): Skully faces north-east
            // towards it, the chest takes the deposit box tile beside him with its front to the north.
            Site(
                "Falador west bank", Npcs.SKULLY, anchor = Tile(2946, 3368, 0), wanted = Tile(2943, 3368, 0), exact = true,
                face = Direction.NORTH_EAST, chest = Tile(2943, 3369, 0), clear = listOf(Tile(2943, 3369, 0)),
                // Owner 2026-09-23: "de chest staat omgedraaid" with its front north - turned 180 degrees.
                chestFront = Direction.SOUTH,
            ),
            // Owner 2026-09-20: "i want another skully on 2653, 3280, 0" (Ardougne south bank). Twelfth site, so
            // the five names have cycled back round to Skully Jr.
            Site("Ardougne south bank", SKULLY_JR, anchor = Tile(2655, 3283, 0), wanted = Tile(2653, 3280, 0), exact = true),
        )

    const val SEARCH_RADIUS = 8

    /** Walkable tiles reachable on foot from [anchor] within [SEARCH_RADIUS] (Chebyshev), same height. */
    fun reachableFrom(
        world: World,
        anchor: Tile,
    ): Set<Tile> {
        val start = if (!world.collision.isClipped(anchor)) anchor else CityGuards.nearestWalkable(world, anchor, radius = 2) ?: return emptySet()
        val visited = LinkedHashSet<Tile>()
        val queue = ArrayDeque<Tile>()
        visited += start
        queue += start
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (direction in Direction.NESW) {
                if (!world.collision.canTraverse(current, direction, projectile = false, water = false)) continue
                val next = current.step(direction)
                if (next in visited) continue
                if (kotlin.math.abs(next.x - anchor.x) > SEARCH_RADIUS || kotlin.math.abs(next.z - anchor.z) > SEARCH_RADIUS) continue
                visited += next
                queue += next
            }
        }
        return visited
    }

    class Placement(
        val site: Site,
        val skully: Tile,
        val chest: Tile,
    )

    /**
     * Owner 2026-09-18 (#7): "every Skully must stand pinned against a bank wall, not in the middle
     * of the bank". A tile is against a wall when a cardinal step off it is blocked by the collision
     * map while the tile beyond is not itself occupied - that is a wall edge, not a booth, counter or
     * other object (those clip the neighbouring tile). Standing beside a booth would also block the
     * banking tiles, so only true wall edges count.
     */
    fun isAgainstWall(
        world: World,
        tile: Tile,
    ): Boolean =
        Direction.NESW.any { direction ->
            world.collision.isBlocked(tile, direction, projectile = false) && !world.collision.isClipped(tile.step(direction))
        }

    /**
     * Chooses the Skully and chest tiles for [site], or null when nothing near [Site.wanted] is
     * reachable. The wall rule ([isAgainstWall]) is applied at every site, the owner-named exact
     * tiles included: an exact tile that is already against a wall is used as-is, otherwise the
     * wall tile nearest to it wins (the boot line reports the snap).
     */
    fun place(
        world: World,
        site: Site,
    ): Placement? {
        val reachable = reachableFrom(world, site.anchor)
        if (site.exact) {
            // An owner-named tile is used exactly as given - no wall test, no reachability test, no snapping.
            // The chest takes the owner-named tile, else the best unclipped cardinal neighbour the customer side can reach.
            val chest =
                site.chest ?: Direction.NESW
                    .map { site.wanted.step(it) }
                    .filter { !world.collision.isClipped(it) && it != site.anchor }
                    .maxByOrNull { (if (it in reachable) 2 else 0) + (if (isAgainstWall(world, it)) 1 else 0) }
                    ?: return null
            return Placement(site, site.wanted, chest)
        }
        if (reachable.isEmpty()) return null
        val candidates = reachable.filter { it != site.anchor }
        val skullyTile =
            candidates
                .filter { isAgainstWall(world, it) }
                .minByOrNull { it.getDistance(site.wanted) * 16 + it.getDistance(site.anchor) }
                ?: candidates.minByOrNull { it.getDistance(site.wanted) * 16 + it.getDistance(site.anchor) }
                ?: return null
        // The chest stands beside him along the same wall where possible.
        val chestTile =
            Direction.NESW
                .map { skullyTile.step(it) }
                .filter { it in reachable && it != site.anchor }
                .minByOrNull { (if (isAgainstWall(world, it)) 0 else 8) + it.getDistance(site.wanted) }
                ?: reachable.filter { it != skullyTile && it != site.anchor }.minByOrNull { it.getDistance(skullyTile) }
                ?: return null
        return Placement(site, skullyTile, chestTile)
    }

    /** The open (unblocked) cardinal neighbour of [tile] nearest to [anchor] - the tile Skully looks at. */
    fun facing(
        world: World,
        tile: Tile,
        anchor: Tile,
        exclude: Tile? = null,
    ): Tile? =
        Direction.NESW
            .filter { !world.collision.isBlocked(tile, it, projectile = false) && !world.collision.isClipped(tile.step(it)) }
            .filter { tile.step(it) != exclude }
            .map { tile.step(it) }
            .minByOrNull { it.getDistance(anchor) }

    /**
     * Owner 2026-09-23: "make sure nobody can noclip him ... all skullys". Npcs never clip in this engine, so a
     * Skully tile is flagged exactly like a solid 1x1 scenery object: players path round him and never stand in him.
     */
    fun blockTile(
        world: World,
        tile: Tile,
    ) {
        val update = CollisionUpdate.Builder()
        update.setType(CollisionUpdate.Type.ADD)
        update.putTile(tile, false, *Direction.NESW)
        world.collision.applyUpdate(update.build())
    }

    /**
     * Rotation for the Loot Chest (loc 62582) so its lid/front faces [front]. Derived from the OSRS Ferox Enclave
     * placement (3138,3626 rotation 2, customers south of it): rotation 0 faces north, 1 east, 2 south, 3 west.
     */
    fun chestRotation(front: Direction): Int =
        when (front) {
            Direction.EAST -> 1
            Direction.SOUTH -> 2
            Direction.WEST -> 3
            else -> 0
        }

    /** Removes the cache centrepiece scenery (types 10/11) on [tile], e.g. the desk a Skully replaces. */
    fun clearScenery(
        world: World,
        tile: Tile,
    ): Int {
        val targets =
            world.chunks.getOrCreate(tile)
                .getEntities<GameObject>(tile, EntityType.STATIC_OBJECT)
                .filter { it.type == 10 || it.type == 11 }
                .toList()
        targets.forEach { world.remove(it) }
        return targets.size
    }

    /** Spawns every site; returns the boot summary line. */
    fun spawnAll(world: World): String {
        var placed = 0
        val failed = ArrayList<String>()
        val lines = ArrayList<String>()
        SITES.forEach { site ->
            site.clear.forEach { clearScenery(world, it) }
            val placement = place(world, site)
            if (placement == null) {
                failed += "${site.label} (anchor ${site.anchor.x},${site.anchor.z},${site.anchor.height})"
                return@forEach
            }
            val skully =
                Npc(site.npcId, placement.skully, world).also {
                    it.respawnOverride = true
                    it.static = true
                    it.walkRadius = 0
                }
            world.spawn(skully)
            // Owner 2026-09-17: "haal alle combat levels weg bij alle skullys" - publish level 0
            // explicitly (the client suppresses "(level N)" only for exactly 0) whatever the cache says.
            skully.setCombatLevel(0)
            // Owner 2026-09-18: "correctly smoothly positioned" - with his back to the wall he faces
            // into the bank (the open side nearest the customer tiles). The face-tile block is kept
            // in the npc's block buffer, so every player who arrives later sees the same facing.
            // An owner-named facing wins over the computed one; otherwise face the open side nearest the anchor.
            val faceTile = if (site.face != null) placement.skully.step(site.face) else facing(world, placement.skully, site.anchor, exclude = placement.chest)
            // The chest's front faces the same way Skully does (into the bank), never the wall.
            val front = site.chestFront ?: site.face ?: faceTile?.let { Direction.between(placement.skully, it) } ?: Direction.NORTH
            world.spawn(DynamicObject(LOOT_CHEST, 10, chestRotation(front), placement.chest))
            blockTile(world, placement.skully)
            faceTile?.let {
                // Both the add-npc orientation (what a player who arrives later sees first) and the
                // face-tile block, so no viewer ever sees him turned into the wall.
                skully.setSpawnFacing(site.face ?: Direction.between(placement.skully, it))
                skully.faceTile(it)
            }
            placed++
            val wall = if (isAgainstWall(world, placement.skully)) "wall" else "NO WALL"
            lines += "${site.label}: npc ${site.npcId} at ${placement.skully.x},${placement.skully.z},${placement.skully.height} ($wall) chest ${placement.chest.x},${placement.chest.z}"
        }
        return "SkullyRoster: placed $placed/${SITES.size} sites [" + lines.joinToString("; ") + "]" +
            (if (failed.isEmpty()) "" else "; UNREACHABLE: $failed")
    }
}
