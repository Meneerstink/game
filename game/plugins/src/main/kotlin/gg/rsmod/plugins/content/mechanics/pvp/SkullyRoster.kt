package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.DynamicObject
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
        /** Owner-named tile (2026-09-17): used as-is whenever it is walkable, no snapping. */
        val exact: Boolean = false,
    )

    /**
     * Eleven sites (owner 2026-09-16 bank list + 2026-09-17: "plaats skully van edgeville op deze
     * coordinaten 3095,3499,0" and "plaats nog 1 skully op deze coordinaten 2943,3370,0"), five
     * names cycling through them.
     */
    val SITES: List<Site> =
        listOf(
            Site("Grand Exchange", Npcs.SKULLY, anchor = Tile(3165, 3487, 0), wanted = Tile(3161, 3484, 0)),
            Site("Varrock east bank", SKULLY_JR, anchor = Tile(3253, 3420, 0), wanted = Tile(3255, 3419, 0)),
            Site("Varrock west bank", SKULLY_SR, anchor = Tile(3185, 3436, 0), wanted = Tile(3187, 3435, 0)),
            Site("Edgeville bank", SKULLY_MAX, anchor = Tile(3094, 3491, 0), wanted = Tile(3095, 3499, 0), exact = true),
            Site("Seers' Village bank", SKULLY_BOB, anchor = Tile(2725, 3491, 0), wanted = Tile(2729, 3491, 0)),
            Site("Falador east bank", Npcs.SKULLY, anchor = Tile(3013, 3355, 0), wanted = Tile(3011, 3356, 0)),
            Site("Draynor bank", SKULLY_JR, anchor = Tile(3092, 3243, 0), wanted = Tile(3090, 3245, 0)),
            Site("Al Kharid bank", SKULLY_SR, anchor = Tile(3269, 3167, 0), wanted = Tile(3271, 3165, 0)),
            Site("Catherby bank", SKULLY_MAX, anchor = Tile(2809, 3441, 0), wanted = Tile(2811, 3440, 0)),
            Site("Lumbridge castle bank", SKULLY_BOB, anchor = Tile(3208, 3220, 2), wanted = Tile(3208, 3222, 2)),
            Site("Falador west bank", Npcs.SKULLY, anchor = Tile(2946, 3368, 0), wanted = Tile(2943, 3370, 0), exact = true),
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

    /** Chooses the Skully and chest tiles for [site], or null when nothing near [Site.wanted] is reachable. */
    fun place(
        world: World,
        site: Site,
    ): Placement? {
        if (site.exact && !world.collision.isClipped(site.wanted)) {
            // Owner-named tile: stand exactly there; the chest takes any walkable neighbour.
            val chest =
                Direction.NESW
                    .map { site.wanted.step(it) }
                    .firstOrNull { !world.collision.isClipped(it) && it != site.anchor }
                    ?: return null
            return Placement(site, site.wanted, chest)
        }
        val reachable = reachableFrom(world, site.anchor)
        if (reachable.isEmpty()) return null
        val skullyTile =
            reachable
                .filter { it != site.anchor }
                .minByOrNull { it.getDistance(site.wanted) * 16 + it.getDistance(site.anchor) }
                ?: return null
        val chestTile =
            Direction.NESW
                .map { skullyTile.step(it) }
                .filter { it in reachable && it != site.anchor }
                .minByOrNull { it.getDistance(site.wanted) }
                ?: reachable.filter { it != skullyTile && it != site.anchor }.minByOrNull { it.getDistance(skullyTile) }
                ?: return null
        return Placement(site, skullyTile, chestTile)
    }

    /** Spawns every site; returns the boot summary line. */
    fun spawnAll(world: World): String {
        var placed = 0
        val failed = ArrayList<String>()
        val lines = ArrayList<String>()
        SITES.forEach { site ->
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
            world.spawn(DynamicObject(LOOT_CHEST, 10, 0, placement.chest))
            placed++
            lines += "${site.label}: npc ${site.npcId} at ${placement.skully.x},${placement.skully.z},${placement.skully.height} chest ${placement.chest.x},${placement.chest.z}"
        }
        return "SkullyRoster: placed $placed/${SITES.size} sites [" + lines.joinToString("; ") + "]" +
            (if (failed.isEmpty()) "" else "; UNREACHABLE: $failed")
    }
}
