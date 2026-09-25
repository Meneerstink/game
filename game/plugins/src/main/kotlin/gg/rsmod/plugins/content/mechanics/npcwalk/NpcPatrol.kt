package gg.rsmod.plugins.content.mechanics.npcwalk

import gg.rsmod.game.model.MovementQueue
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.FACING_PAWN_ATTR
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.content.mechanics.pvp.CityGuards

/**
 * Npcs that walk a fixed loop of waypoints for ever (owner 2026-09-24: "zet ze in grand ex laat ze rondjes lopen" - the
 * Workman with his wheelbarrow, Jalal the Drunk and the Tramp). The engine only had random roaming inside a radius
 * ([NpcRandomWalk]); a route is the second, independent walking mode. A route is keyed by the npc's spawn tile, so a respawn
 * picks it up again.
 *
 * Every tick the npc heads for its current waypoint through the normal path finder; within one tile of it the next waypoint
 * becomes current. A waypoint that turns out to be blocked is replaced by the nearest tile the whole footprint fits on, and an
 * npc that has not moved for [STUCK_TICKS] skips to the next waypoint, so a player standing in the way never parks it. While
 * the npc faces someone (a conversation) it waits.
 */
object NpcPatrol {
    class Route(
        val waypoints: List<Tile>,
        val run: Boolean = false,
    )

    val TIMER = TimerKey()
    private val INDEX = AttributeKey<Int>()
    private val STILL_TICKS = AttributeKey<Int>()
    private val LAST_TILE = AttributeKey<Tile>()
    private val RESOLVED = AttributeKey<List<Tile>>()
    private const val STUCK_TICKS = 6
    private val routes = HashMap<Tile, Route>()

    fun register(
        spawn: Tile,
        route: Route,
    ) {
        require(route.waypoints.size >= 2) { "a patrol needs at least two waypoints" }
        routes[spawn] = route
    }

    fun routeOf(npc: Npc): Route? = routes[npc.spawnTile]

    fun start(npc: Npc) {
        if (routeOf(npc) == null) return
        npc.walkRadius = 0
        npc.attr[INDEX] = 0
        npc.timers[TIMER] = 1
    }

    fun tick(npc: Npc) {
        val route = routeOf(npc) ?: return
        if (!npc.isActive() || !npc.lock.canMove() || npc.attr[FACING_PAWN_ATTR]?.get() != null) return
        val waypoints = npc.attr[RESOLVED] ?: resolve(npc, route).also { npc.attr[RESOLVED] = it }
        var index = (npc.attr[INDEX] ?: 0) % waypoints.size

        val still = if (npc.attr[LAST_TILE]?.sameAs(npc.tile) == true) (npc.attr[STILL_TICKS] ?: 0) + 1 else 0
        npc.attr[LAST_TILE] = Tile(npc.tile)
        npc.attr[STILL_TICKS] = still

        if (npc.tile.getDistance(waypoints[index]) <= 1 || still >= STUCK_TICKS) {
            index = (index + 1) % waypoints.size
            npc.attr[INDEX] = index
            npc.attr[STILL_TICKS] = 0
            npc.stopMovement()
        }
        if (!npc.hasMoveDestination()) {
            npc.walkMask = npc.def.walkMask
            val step = if (route.run) MovementQueue.StepType.FORCED_RUN else MovementQueue.StepType.NORMAL
            npc.walkTo(waypoints[index], stepType = step)
        }
    }

    /** Each waypoint moved to the nearest tile the npc's whole footprint can stand on (a blocked waypoint would stall it). */
    private fun resolve(
        npc: Npc,
        route: Route,
    ): List<Tile> {
        val size = npc.getSize()
        return route.waypoints.map { waypoint ->
            CityGuards.nearestWalkable(npc.world, waypoint, radius = 3) { tile ->
                (0 until size).all { dx -> (0 until size).all { dz -> !npc.world.collision.isClipped(tile.transform(dx, dz)) } }
            } ?: waypoint
        }
    }
}
