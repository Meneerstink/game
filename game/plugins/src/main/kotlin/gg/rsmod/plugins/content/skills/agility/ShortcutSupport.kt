package gg.rsmod.plugins.content.skills.agility

import gg.rsmod.game.Server.Companion.logger
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.ForcedMovement
import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.MovementQueue
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.plugin.KotlinPlugin
import gg.rsmod.game.plugin.Plugin
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.*
import java.util.concurrent.ThreadLocalRandom

/*
 * Shared helpers for the Void-sourced shortcut scripts (sourced_shortcuts / grapple_shortcuts). Plugin
 * scripts compile to separate classes and cannot see each other's top-level functions, so these live
 * in a plain Kotlin file.
 */

/** Void `Level.success(level, chances)`: interpolate chances.first..last over levels 1..99, out of 256. */
fun agilitySuccess(
    player: Player,
    low: Int,
    high: Int,
): Boolean {
    val level = player.skills.getCurrentLevel(Skills.AGILITY).coerceIn(1, 99)
    val chance = (low * (99 - level) + high * (level - 1)) / 98
    return chance > ThreadLocalRandom.current().nextInt(256)
}

fun Player.hasAgility(
    level: Int,
    failure: String = "You need an Agility level of $level to negotiate this obstacle.",
): Boolean {
    if (skills.getCurrentLevel(Skills.AGILITY) >= level) {
        return true
    }
    message(failure)
    return false
}

fun Tile.add(
    direction: Direction,
    times: Int = 1,
): Tile = Tile(x + direction.getDeltaX() * times, z + direction.getDeltaZ() * times, height)

/**
 * Thrown by [walkToTile] / [walkOverTile] when the player did not reach the tile the obstacle needs: no route, movement
 * interrupted (frozen, stunned, dead, logged out) or too slow. [Plugin.obstacle] catches it and ends the obstacle cleanly - no
 * crossing, no experience, nothing left locked or animating (owner priority #1 2026-10-03, plan 8A: approach is normal
 * movement, the crossing is an explicit action that only starts from its real start tile).
 */
class ObstacleApproachFailed(
    val reason: String,
    val tile: Tile,
) : RuntimeException("approach to ${tile.x},${tile.z},${tile.height} failed: $reason")

/** The farthest a bounded collision-free crossing ([walkOverTile]) may carry a player: over a cliff loc and one landing beyond. */
const val MAX_WALK_OVER = 10

/**
 * The shared arrival contract of every obstacle approach. Routes the player to [tile] and returns only once he stands on it;
 * waits as long as the route really takes (a detour is not the bird's-eye distance), and gives up when the route fails, the
 * movement stops early (a freeze, stun, death, lock or a cleared queue) or the walk overruns twice its own length.
 * Owner priority #1 2026-10-03: the old helper waited `distance + 1` ticks blind and then animated from wherever the player
 * happened to be, so a blocked or slow approach crossed from the wrong tile or into scenery.
 */
suspend fun QueueTask.arriveAt(
    player: Player,
    tile: Tile,
    stepType: MovementQueue.StepType,
    detectCollision: Boolean,
) {
    if (player.tile.sameAs(tile)) return
    if (player.timers.has(gg.rsmod.game.model.timer.FROZEN_TIMER)) throw ObstacleApproachFailed("frozen", tile)
    if (player.timers.has(gg.rsmod.game.model.timer.STUN_TIMER)) throw ObstacleApproachFailed("stunned", tile)
    val route = player.walkTo(this, tile, stepType, detectCollision)
    if (!route.success || !route.tail.sameAs(tile)) {
        player.stopMovement()
        throw ObstacleApproachFailed("no route (ends at ${route.tail.x},${route.tail.z})", tile)
    }
    // The route's deque is consumed by the movement queue, so the bound comes from the distance: a detour of up to four times the
    // straight line, walking, plus a margin; and the walk must keep making progress (no tile change for 3 ticks = stalled).
    val limit = player.tile.getDistance(tile) * 4 + 10
    var waited = 0
    var stalled = 0
    var last = Tile(player.tile)
    while (!player.tile.sameAs(tile)) {
        if (player.isDead()) throw ObstacleApproachFailed("player died", tile)
        if (player.timers.has(gg.rsmod.game.model.timer.FROZEN_TIMER)) throw ObstacleApproachFailed("frozen", tile)
        if (player.timers.has(gg.rsmod.game.model.timer.STUN_TIMER)) throw ObstacleApproachFailed("stunned", tile)
        if (!player.movementQueue.hasDestination()) throw ObstacleApproachFailed("movement stopped at ${player.tile.x},${player.tile.z}", tile)
        wait(1)
        waited++
        if (player.tile.sameAs(last)) stalled++ else stalled = 0
        last = Tile(player.tile)
        if (waited > limit || stalled >= 3) {
            player.stopMovement()
            throw ObstacleApproachFailed("too slow (${waited} ticks, stalled $stalled at ${last.x},${last.z})", tile)
        }
    }
}

/** Normal, collision-respecting approach to the obstacle's start tile; see [arriveAt]. */
suspend fun QueueTask.walkToTile(
    player: Player,
    tile: Tile,
) = arriveAt(player, tile, MovementQueue.StepType.NORMAL, detectCollision = true)

/**
 * The bounded collision-free crossing over the obstacle itself (a cliff, a stile, a rock): at most [MAX_WALK_OVER] tiles from
 * where the player stands, and only inside an obstacle's own task, never as an approach. Arrival is checked like [walkToTile].
 */
suspend fun QueueTask.walkOverTile(
    player: Player,
    tile: Tile,
) {
    if (player.tile.sameAs(tile)) return
    val distance = player.tile.getDistance(tile)
    if (distance > MAX_WALK_OVER) throw ObstacleApproachFailed("crossing of $distance tiles is no bounded obstacle crossing", tile)
    arriveAt(player, tile, MovementQueue.StepType.FORCED_WALK, detectCollision = false)
}

/**
 * Void `exactMoveDelay(target, delay, direction, startDelay)`; delays are client cycles (30 per tick). The task resumes only
 * once the client slide has ended ([Player.slideCycles], rounded up), so a following slide or animation never replaces a
 * running one (plan 8B).
 */
suspend fun QueueTask.exactMove(
    player: Player,
    target: Tile,
    delay: Int = player.tile.getDistance(target) * 30,
    direction: Direction = Direction.between(player.tile, target),
    startDelay: Int = 0,
) {
    val move =
        ForcedMovement.of(
            player.tile,
            target,
            clientDuration1 = startDelay,
            clientDuration2 = delay,
            directionAngle = direction.ordinal,
            lockState = LockState.NONE,
        )
    player.forceMove(this, move, cycleDuration = Player.slideCycles(maxOf(delay, startDelay)))
}

fun QueueTask.face(
    player: Player,
    direction: Direction,
) = player.faceTile(player.tile.add(direction))

/**
 * Binds [option] on object [id] only when this cache advertises it and no other plugin already handles
 * it; otherwise logs and skips, so a donor id absent from revision 667 can never break boot.
 */
fun KotlinPlugin.shortcut(
    id: Int,
    option: String,
    logic: Plugin.() -> Unit,
) {
    val def = world.definitions.getNullable(ObjectDef::class.java, id)
    val slot = def?.options?.indexOfFirst { it?.lowercase() == option.lowercase() } ?: -1
    if (slot == -1) {
        logger.info("Sourced shortcuts: object {} has no '{}' option in this cache; skipped.", id, option)
        return
    }
    if ((slot + 1) in world.plugins.boundObjectOptions(id)) {
        logger.info("Sourced shortcuts: object {} '{}' is already handled elsewhere; skipped.", id, option)
        return
    }
    on_obj_option(obj = id, option = option, logic = logic)
}

/**
 * One obstacle = one locked task with one lifecycle (plan 8D): the player is locked for the whole crossing, zone rules keep
 * judging the near side until he is across (Pawn.zoneTile), and however the task ends - success, a failed approach
 * ([ObstacleApproachFailed]), a plugin error, termination by death/logout/a stronger action - the lock, any looping climb
 * animation ([Pawn.resetLoopingAnimation], released with the lock) and any pending forced walk are cleaned up, so the player
 * can walk and act at once from wherever he really is. A failed approach never crosses and never gives experience.
 */
fun Plugin.obstacle(block: suspend QueueTask.(GameObject) -> Unit) {
    val obj = player.getInteractingGameObj()
    player.lockingQueue(lockState = LockState.FULL) {
        // The whole obstacle is one transit: zone rules keep judging the near side until the player is across (Pawn.zoneTile).
        player.obstacleUntilUnlocked = true
        terminateAction = {
            // A terminated task never reaches its own cleanup: stop a collision-free walk in its tracks (plan 8D).
            player.stopMovement()
        }
        try {
            block(obj)
        } catch (e: ObstacleApproachFailed) {
            gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.PLUGIN) {
                "obstacle ${obj.id} at ${obj.tile.x},${obj.tile.z}: ${player.username} ${e.message} - crossing refused"
            }
            player.stopMovement()
        }
    }
}

/** How far past a climbable loc [crossingLanding] looks for the landing tile. */
const val MAX_CLIFF_CROSS = 6

/** Sideways lines [crossingLanding] tries when the straight one has no usable landing (the cliff face is not square). */
private val CROSS_SHIFTS = intArrayOf(0, 1, -1, 2, -2)

/** A landing must open onto at least this many walkable tiles (its own included) within [LANDING_AREA] tiles. */
const val MIN_LANDING_ROOM = 6
const val LANDING_AREA = 4

/**
 * Where a climb over a rock / cliff loc lands when no source gives the tile (the 2011 map re-placed the Trollheim rocks): crossing the
 * loc along the axis the player approaches it on, over the blocked tiles in this cache's own collision, to the first tile beyond that a
 * player can actually use ([isUsableLanding]). Null when there is none within reach.
 *
 * Owner 2026-09-30 ("Climb Cliffside ... lands against the wall and is stuck", reported many times): the old rule took the first tile
 * with no collision flag at all. Between the rock pieces of these cliffs there are unflagged single tiles walled in on every side by
 * their neighbours' walls and blocked tiles - the player landed in such a pocket and could not move. A landing now has to open onto
 * walkable ground ([MIN_LANDING_ROOM] tiles reachable by real steps) that is NOT the side the player came from; a pocket is skipped
 * and the search goes on past it, then along the lines one and two tiles to either side. Every cliffside and the rocks that use this
 * (sourced_shortcuts 9306) share it, in both directions.
 */
fun crossingLanding(
    world: gg.rsmod.game.model.World,
    obj: gg.rsmod.game.model.entity.GameObject,
    from: gg.rsmod.game.model.Tile,
): Tile? {
    val def = obj.getDef(world.definitions)
    val w = def.getRotatedWidth(obj)
    val l = def.getRotatedLength(obj)
    val outX = if (from.x < obj.tile.x) obj.tile.x - from.x else if (from.x >= obj.tile.x + w) from.x - (obj.tile.x + w - 1) else 0
    val outZ = if (from.z < obj.tile.z) obj.tile.z - from.z else if (from.z >= obj.tile.z + l) from.z - (obj.tile.z + l - 1) else 0
    // One axis: the side the player stands on (the farther one when approached from a corner).
    val alongX = outX >= outZ && outX > 0
    val dx = if (alongX) Integer.signum(obj.tile.x - from.x) else 0
    val dz = if (!alongX) Integer.signum(obj.tile.z - from.z) else 0
    if (dx == 0 && dz == 0) return null
    val nearSide = reachable(world, from, LANDING_AREA + 2)
    for (shift in CROSS_SHIFTS) {
        var tile: gg.rsmod.game.model.Tile = if (alongX) from.transform(0, shift) else from.transform(shift, 0)
        var crossed = false
        repeat(MAX_CLIFF_CROSS + maxOf(w, l)) {
            tile = tile.transform(dx, dz)
            val inLoc = tile.x >= obj.tile.x && tile.x < obj.tile.x + w && tile.z >= obj.tile.z && tile.z < obj.tile.z + l
            if (inLoc || world.collision.isClipped(tile)) {
                crossed = true
            } else if (crossed && tile !in nearSide && isUsableLanding(world, tile)) {
                return tile
            }
        }
    }
    return null
}

/**
 * The landing of a donor-sourced climb ([donor], a fixed offset over the rock): kept when it is a usable landing in this cache,
 * else the crossing rule ([crossingLanding]). Owner 2026-09-30 (Trollheim, "bugged in this exact position"): in this 667 cache
 * one side of every Trollheim chain rock - 9303 from the west (2857,3663), 9304 from the south (2873,3662), 3803 from the west
 * (2877,3671), 9305 (2895,3674) - was a single walled-in tile, and the player stood there unable to move. Null when neither
 * gives a landing.
 */
fun chainLanding(
    world: gg.rsmod.game.model.World,
    obj: gg.rsmod.game.model.entity.GameObject,
    from: gg.rsmod.game.model.Tile,
    donor: gg.rsmod.game.model.Tile,
): Tile? = donor.takeIf { isUsableLanding(world, it) } ?: crossingLanding(world, obj, from)

/** A tile a player can stand on and walk away from: not blocked itself, and opening onto [MIN_LANDING_ROOM] walkable tiles. */
fun isUsableLanding(
    world: gg.rsmod.game.model.World,
    tile: gg.rsmod.game.model.Tile,
): Boolean = reachable(world, tile, LANDING_AREA).size >= MIN_LANDING_ROOM

/** Tiles reachable from [start] by ordinary cardinal steps (this cache's collision) within [radius], [start] included when standable. */
private fun reachable(
    world: gg.rsmod.game.model.World,
    start: gg.rsmod.game.model.Tile,
    radius: Int,
): Set<gg.rsmod.game.model.Tile> {
    val seen = LinkedHashSet<gg.rsmod.game.model.Tile>()
    if (world.collision.isBlocked(start, Direction.NORTH, projectile = false) &&
        world.collision.isBlocked(start, Direction.SOUTH, projectile = false) &&
        world.collision.isBlocked(start, Direction.EAST, projectile = false) &&
        world.collision.isBlocked(start, Direction.WEST, projectile = false)
    ) {
        return seen
    }
    val queue = ArrayDeque<gg.rsmod.game.model.Tile>()
    seen += start
    queue += start
    while (queue.isNotEmpty()) {
        val tile = queue.removeFirst()
        for (dir in Direction.NESW) {
            if (!world.collision.canTraverse(tile, dir, projectile = false, water = false)) continue
            val next = tile.step(dir)
            if (maxOf(Math.abs(next.x - start.x), Math.abs(next.z - start.z)) > radius || !seen.add(next)) continue
            queue += next
        }
    }
    return seen
}
