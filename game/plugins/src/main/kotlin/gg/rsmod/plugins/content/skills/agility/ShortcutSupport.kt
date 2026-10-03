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

/**
 * Void `Level.success(level, maxLevel, minChance)` (the other overload): the chance climbs from [minChance] at level 1 to 256 at
 * [maxLevel] and never fails past it (Void Interpolation.interpolate, level clamped to 1..99), out of 256.
 */
fun agilitySuccessUntil(
    player: Player,
    maxLevel: Int,
    minChance: Int = 1,
): Boolean {
    val level = player.skills.getCurrentLevel(Skills.AGILITY).coerceIn(1, 99)
    val chance = (minChance * (maxLevel - level) + 256 * (level - 1)) / (maxLevel - 1)
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

/** [walkOverTile] in the ordinary walk (a passage: a barrier field, a doorway) for callers that are not a QueueTask extension. */
suspend fun walkOverTileFrom(
    task: QueueTask,
    player: Player,
    tile: Tile,
) = task.walkOverTile(player, tile, Render.NONE)

/** [walkToTile] for callers that are not a QueueTask extension. */
suspend fun walkToTileFrom(
    task: QueueTask,
    player: Player,
    tile: Tile,
) = task.walkToTile(player, tile)

/** Normal, collision-respecting approach to the obstacle's start tile; see [arriveAt]. */
suspend fun QueueTask.walkToTile(
    player: Player,
    tile: Tile,
) = arriveAt(player, tile, MovementQueue.StepType.NORMAL, detectCollision = true)

/**
 * Void's render emotes (`data/entity/player/render_emotes.toml`) = body animation sets (BAS) of the 667 cache. A collision-free
 * walk over an obstacle always shows one of these in Void (`renderEmote(..)` before `walkOverDelay`): the climb up a rock is the
 * "climbing" set - BAS 0 here, ready 738 HUMAN_CLIMBING_READY / walk 737 HUMAN_CLIMBING (RuneLite gameval, cache probe 2026-10-03).
 * The port had dropped "climbing" as "id 0 is not a usable render", so the rocks' up direction was a plain walk through the rock
 * (owner FAIL 2026-10-03 02:24, OB7).
 */
object Render {
    /** No render emote: the source walks this step in the ordinary walk (an approach over open ground). */
    const val NONE = -1
    const val CLIMBING = 0
    const val TREAD_WATER = 152
    const val ROPE_BALANCE = 155
    const val DROWNING = 163
    const val SWIM = 164
    const val TREAD_WATER_2 = 188
    const val FALLING = 189
    const val BEAM_BALANCE = 330
    const val MONKEY_BARS = 662
    const val LEDGE_BALANCE = 1026
    const val LEDGE_BALANCE_2 = 1427
}

/**
 * The bounded collision-free crossing over the obstacle itself (a cliff, a stile, a rock, a log): at most [MAX_WALK_OVER] tiles
 * from where the player stands, and only inside an obstacle's own task, never as an approach. [render] is required - the source's
 * render emote for this walk ([Render]), so no crossing can fall back to the plain walk through a loc again (the movement queue
 * counts that as Player.plainWalksThroughScenery). [obstacle] restores the player's own render when the obstacle ends.
 * Arrival is checked like [walkToTile].
 */
suspend fun QueueTask.walkOverTile(
    player: Player,
    tile: Tile,
    render: Int,
) {
    if (player.tile.sameAs(tile)) return
    val distance = player.tile.getDistance(tile)
    if (distance > MAX_WALK_OVER) throw ObstacleApproachFailed("crossing of $distance tiles is no bounded obstacle crossing", tile)
    if (render != Render.NONE && player.appearance.renderAnim != render) player.setRenderAnimation(render)
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
        // The player's own render emote (a weapon's stance, a transformation) comes back however the obstacle ends.
        val ownRender = player.appearance.renderAnim
        val restoreRender = { if (player.appearance.renderAnim != ownRender) player.setRenderAnimation(ownRender) }
        terminateAction = {
            // A terminated task never reaches its own cleanup: stop a collision-free walk in its tracks (plan 8D).
            player.stopMovement()
            restoreRender()
        }
        try {
            block(obj)
        } catch (e: ObstacleApproachFailed) {
            gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.PLUGIN) {
                "obstacle ${obj.id} at ${obj.tile.x},${obj.tile.z}: ${player.username} ${e.message} - crossing refused"
            }
            player.stopMovement()
        } finally {
            restoreRender()
        }
    }
}

/** A crossing straight through an obstacle: the tile right in front of it on the player's side, the tile right behind it. */
data class Crossing(
    val start: Tile,
    val landing: Tile,
    val direction: Direction,
)

/**
 * The crossing of a rock / cliffside / climbing-rock loc of this 667 map (owner priority #1 2026-10-03, E17 "a player may never
 * noclip"): a straight cardinal lane through the loc's own footprint, from the tile right in front of it to the tile right behind
 * it - never a slide from wherever the click left the player, never over other scenery. The 2011 map re-placed the Trollheim rocks
 * and added the cliffsides (no donor has their tiles); the map dumps of every placement (2026-10-03) show each loc filling a gap
 * between two walkable areas, so the lane is read from this cache's own collision:
 *  - the axis is the side the player stands on (the farther one from a corner);
 *  - lanes are the footprint's rows/columns, nearest to the player first;
 *  - a lane counts when both ends can be stood on and walked away from ([isUsableLanding]).
 * Null when the loc has no such lane from this side (traced by the caller).
 */
fun straightCrossing(
    world: gg.rsmod.game.model.World,
    obj: GameObject,
    from: Tile,
): Crossing? {
    val def = obj.getDef(world.definitions)
    val w = def.getRotatedWidth(obj)
    val l = def.getRotatedLength(obj)
    val x0 = obj.tile.x
    val z0 = obj.tile.z
    val outX = if (from.x < x0) x0 - from.x else if (from.x >= x0 + w) from.x - (x0 + w - 1) else 0
    val outZ = if (from.z < z0) z0 - from.z else if (from.z >= z0 + l) from.z - (z0 + l - 1) else 0
    val alongX = outX >= outZ && outX > 0
    if (!alongX && outZ == 0) return null
    val h = obj.tile.height
    if (alongX) {
        val east = from.x < x0
        val direction = if (east) Direction.EAST else Direction.WEST
        val lanes = (z0 until z0 + l).sortedBy { Math.abs(it - from.z) }
        for (z in lanes) {
            val start = Tile(if (east) x0 - 1 else x0 + w, z, h)
            val landing = Tile(if (east) x0 + w else x0 - 1, z, h)
            if (isUsableLanding(world, start) && isUsableLanding(world, landing)) return Crossing(start, landing, direction)
        }
    } else {
        val north = from.z < z0
        val direction = if (north) Direction.NORTH else Direction.SOUTH
        val lanes = (x0 until x0 + w).sortedBy { Math.abs(it - from.x) }
        for (x in lanes) {
            val start = Tile(x, if (north) z0 - 1 else z0 + l, h)
            val landing = Tile(x, if (north) z0 + l else z0 - 1, h)
            if (isUsableLanding(world, start) && isUsableLanding(world, landing)) return Crossing(start, landing, direction)
        }
    }
    return null
}

/**
 * [straightCrossing] across the short side of a long loc (the Wilderness ditch segments are 3x2): from the nearer side of that
 * axis at the player's own lane, so a click from the end of a segment still jumps across the ditch, never along it.
 */
fun shortAxisCrossing(
    world: gg.rsmod.game.model.World,
    obj: GameObject,
    from: Tile,
): Crossing? {
    val def = obj.getDef(world.definitions)
    val w = def.getRotatedWidth(obj)
    val l = def.getRotatedLength(obj)
    val x0 = obj.tile.x
    val z0 = obj.tile.z
    val h = obj.tile.height
    val probe =
        if (l <= w) {
            Tile(from.x.coerceIn(x0, x0 + w - 1), if (from.z * 2 < z0 * 2 + l - 1) z0 - 1 else z0 + l, h)
        } else {
            Tile(if (from.x * 2 < x0 * 2 + w - 1) x0 - 1 else x0 + w, from.z.coerceIn(z0, z0 + l - 1), h)
        }
    return straightCrossing(world, obj, probe)
}

/**
 * [straightCrossing] along the long side of a loc a fence or wall runs straight through (a 1x2 stile: the fence crosses between its
 * two tiles, map dumps 2026-10-03), from the nearer end at the player's lane. A 1x1 loc (the crumbling wall, a broken fence) keeps
 * whichever axis has a lane from the player's side, else the other one.
 */
fun longAxisCrossing(
    world: gg.rsmod.game.model.World,
    obj: GameObject,
    from: Tile,
): Crossing? {
    val def = obj.getDef(world.definitions)
    val w = def.getRotatedWidth(obj)
    val l = def.getRotatedLength(obj)
    val x0 = obj.tile.x
    val z0 = obj.tile.z
    val h = obj.tile.height
    val alongZ = Tile(from.x.coerceIn(x0, x0 + w - 1), if (from.z * 2 < z0 * 2 + l - 1) z0 - 1 else z0 + l, h)
    val alongX = Tile(if (from.x * 2 < x0 * 2 + w - 1) x0 - 1 else x0 + w, from.z.coerceIn(z0, z0 + l - 1), h)
    return when {
        l > w -> straightCrossing(world, obj, alongZ)
        w > l -> straightCrossing(world, obj, alongX)
        else -> straightCrossing(world, obj, from) ?: straightCrossing(world, obj, alongZ) ?: straightCrossing(world, obj, alongX)
    }
}

/**
 * Climbing over a stile / low wall / fence (Void Stiles.climbStile and trollheim_rocks_advanced): walk to the tile right in front of
 * it, face across, [anim] (rocks_pile_climb 839, anim delay 30) and one slide over the loc to the tile right behind it (94 cycles,
 * start 30). Void walked onto the stile's own tile in the plain walk first and climbed one tile from there; on this map the stile's
 * tiles are part of the fence (walls on both sides), so the climb starts in front of it (owner priority #1: never a walk into a loc).
 * Returns false with a PROBLEM trace and a message when the loc has no lane.
 */
suspend fun QueueTask.climbOver(
    player: Player,
    target: GameObject,
    anim: Int = 839,
): Boolean {
    val crossing = longAxisCrossing(player.world, target, player.tile)
    if (crossing == null) {
        gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.PLUGIN) {
            "climb-over ${target.id} at ${target.tile.x},${target.tile.z}: no lane from ${player.tile.x},${player.tile.z}"
        }
        player.message("You can't climb over from here.")
        return false
    }
    walkToTile(player, crossing.start)
    face(player, crossing.direction)
    wait(1)
    player.animate(anim, delay = 30)
    exactMove(player, crossing.landing, delay = 94, direction = crossing.direction, startDelay = 30)
    return true
}

/** A loc's own animation (the swinging rope, Void `target.anim(..)`) for every player who can see it. */
fun animateLoc(
    world: gg.rsmod.game.model.World,
    obj: GameObject,
    anim: Int,
) {
    world.players.forEach { p ->
        if (p.tile.height == obj.tile.height && p.tile.isWithinRadius(obj.tile, 15)) {
            p.write(gg.rsmod.game.message.impl.LocAnimMessage(gameObject = obj, animation = anim))
        }
    }
}

/**
 * Course progress (Void Course.kt `agilityStage`): an obstacle moves the lap on only from the stage right before it, so skipping or
 * failing an obstacle (Void skips the stage on a failure) means no lap bonus at the end.
 */
fun Player.courseStage(key: gg.rsmod.game.model.attr.AttributeKey<Int>): Int = attr[key] ?: 0

fun Player.advanceCourse(
    key: gg.rsmod.game.model.attr.AttributeKey<Int>,
    stage: Int,
) {
    if (courseStage(key) + 1 == stage) attr[key] = stage
}

/**
 * Void `enterDoor(door)` for a course gate or door: the door shows open ([openedId]) for a moment and the player takes the one
 * step through it (DoorCrossing: the far side of a wall door from the tile in front of it or on it). Returns false - the door was
 * not crossed - when the player does not stand right at it; the caller then stops the obstacle.
 */
suspend fun QueueTask.passDoor(
    player: Player,
    door: GameObject,
    openedId: Int,
): Boolean {
    val world = player.world
    world.spawn(gg.rsmod.game.model.entity.DynamicObject(door, openedId))
    world.queue {
        wait(3)
        world.spawn(gg.rsmod.game.model.entity.DynamicObject(door))
    }
    return gg.rsmod.plugins.content.mechanics.doors.DoorCrossing.cross(this, player, door)
}

/** The static or dynamic loc [id] on [tile], if any. */
fun locAt(
    world: gg.rsmod.game.model.World,
    tile: Tile,
    id: Int,
): GameObject? =
    world.chunks.get(tile, createIfNeeded = true)
        ?.getEntities<GameObject>(tile, gg.rsmod.game.model.EntityType.STATIC_OBJECT, gg.rsmod.game.model.EntityType.DYNAMIC_OBJECT)
        ?.firstOrNull { it.id == id }

/**
 * A climb between two rocks on either side of one cliff band (Arandar 9296/9297, the Karamja rocks 2231, the God Wars rocky
 * handholds 26323/26324 and 26327/26328, Mort'ton 9335/9336): from the tile beside rock [a] farthest from rock [b], over the band, to
 * the tile beside [b] farthest from [a]. The 2009scape ports slid from wherever the player stood onto the other rock's own tile -
 * a blocked tile (conform traversal 2026-10-03: "landed in a pocket" on every one of them). Null when either end has no usable tile.
 */
fun pairCrossing(
    world: gg.rsmod.game.model.World,
    a: Tile,
    b: Tile,
): Crossing? {
    fun around(t: Tile) = (-1..1).flatMap { dx -> (-1..1).map { dz -> t.transform(dx, dz) } }.filter { !it.sameAs(t) }
    fun d2(p: Tile, q: Tile) = (p.x - q.x) * (p.x - q.x) + (p.z - q.z) * (p.z - q.z)
    // Behind the rock (away from the other one), on the straight line through both rocks first, then the farthest.
    fun pick(from: Tile, away: Tile): Tile? {
        val usable = around(from).filter { !it.sameAs(away) && isUsableLanding(world, it) }
        val behind = usable.filter { (it.x - from.x) * (away.x - from.x) + (it.z - from.z) * (away.z - from.z) < 0 }
        return (behind.ifEmpty { usable })
            .sortedWith(compareBy<Tile> { Math.abs((it.x - from.x) * (away.z - from.z) - (it.z - from.z) * (away.x - from.x)) }.thenByDescending { d2(it, away) })
            .firstOrNull()
    }
    val start = pick(a, b) ?: return null
    val landing = pick(b, a) ?: return null
    if (d2(start, landing) <= d2(a, b)) return null
    return Crossing(start, landing, Direction.between(start, landing))
}

/**
 * [climbCliff] for a rock pair ([pairCrossing]): walk to the tile beside the clicked rock, then down (the climb-down sequence and
 * one slide), up (the climbing render over the band) or level (839 and a slide) by this map's terrain heights.
 */
suspend fun QueueTask.climbPair(
    player: Player,
    target: GameObject,
    partner: Tile,
    downAnim: Int,
): Boolean {
    val world = player.world
    val crossing = pairCrossing(world, target.tile, partner)
    if (crossing == null) {
        gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.PLUGIN) {
            "rock pair ${target.id} at ${target.tile.x},${target.tile.z} -> ${partner.x},${partner.z}: no usable ends"
        }
        player.message("You can't climb the rocks from here.")
        return false
    }
    walkToTile(player, crossing.start)
    player.faceTile(crossing.landing)
    wait(1)
    val distance = crossing.start.getDistance(crossing.landing)
    when {
        levelCrossing(world, crossing) -> {
            player.animate(839, delay = 30)
            exactMove(player, crossing.landing, delay = maxOf(94, distance * 40), direction = crossing.direction, startDelay = 30)
        }
        goesDown(world, crossing) -> {
            player.animate(downAnim, delay = 10)
            exactMove(player, crossing.landing, delay = maxOf(120, distance * 40), direction = crossing.direction)
        }
        else -> {
            walkOverTile(player, crossing.landing, Render.CLIMBING)
            player.resetRenderAnimation()
        }
    }
    return true
}

/**
 * The walk-through of a hand-written door / gate script (owner hard rule E17, audit-2026-10-03-forcedmove-vs-donors.md: about
 * twenty scripts walked a collision-free line to [far] from wherever the click left the player, diagonally through the wall or
 * two tiles past the door): walk with collision to the tile right before [far] on the player's side and arrive there, then the one
 * bounded step to [far] (Void enterDoor). On the player's own plane. False - nothing crossed - when the player cannot get there.
 */
/** [doorStep] for callers that are not a QueueTask extension (PlayerExt.handleTemporaryDoor). */
suspend fun doorStepFrom(
    task: QueueTask,
    player: Player,
    far: Tile,
): Boolean = task.doorStep(player, far)

suspend fun QueueTask.doorStep(
    player: Player,
    far: Tile,
): Boolean {
    val target = Tile(far.x, far.z, player.tile.height)
    val dx = target.x - player.tile.x
    val dz = target.z - player.tile.z
    val near = if (Math.abs(dx) >= Math.abs(dz)) Tile(target.x - Integer.signum(dx), target.z, target.height) else Tile(target.x, target.z - Integer.signum(dz), target.height)
    return try {
        walkToTile(player, near)
        walkOverTile(player, target, Render.NONE)
        true
    } catch (e: ObstacleApproachFailed) {
        player.stopMovement()
        player.message("I can't reach that!")
        false
    }
}

/**
 * Terrain height of a tile from this cache's map file (more negative = higher ground, the client's convention). Read lazily per
 * region; used to tell the way up a cliff from the way down instead of guessing it.
 */
object TerrainHeight {
    private val regions = java.util.concurrent.ConcurrentHashMap<Int, net.runelite.cache.region.Region>()

    fun of(
        world: gg.rsmod.game.model.World,
        tile: Tile,
    ): Int? {
        val rx = tile.x shr 6
        val rz = tile.z shr 6
        val id = (rx shl 8) or rz
        val region =
            regions[id] ?: run {
                val data = world.filestore.data(net.runelite.cache.IndexType.MAPS.number, "m${rx}_$rz") ?: return null
                net.runelite.cache.region.Region(id).also {
                    it.loadTerrain(net.runelite.cache.definitions.loaders.MapLoader().load(rx, rz, data))
                    regions[id] = it
                }
            }
        return region.getTileHeight(tile.height, tile.x and 63, tile.z and 63)
    }
}

/**
 * [straightCrossing] for a cliff: when the loc joins ground of two heights (the 2011 Trollheim rocks and cliffsides, measured
 * 2026-10-03), the crossing runs across that height step whatever side the player clicked from - he walks to the start tile on
 * the nearer of the two sides first. A loc with level ground on both axes (the climbing rocks 3748) keeps the player's own axis.
 */
fun cliffCrossing(
    world: gg.rsmod.game.model.World,
    obj: GameObject,
    from: Tile,
): Crossing? {
    val def = obj.getDef(world.definitions)
    val w = def.getRotatedWidth(obj)
    val l = def.getRotatedLength(obj)
    val x0 = obj.tile.x
    val z0 = obj.tile.z
    val h = obj.tile.height
    // The lane on each axis from the nearer side of that axis at the player's own row / column.
    val xLane = straightCrossing(world, obj, Tile(if (from.x * 2 < x0 * 2 + w - 1) x0 - 1 else x0 + w, from.z.coerceIn(z0, z0 + l - 1), h))
    val zLane = straightCrossing(world, obj, Tile(from.x.coerceIn(x0, x0 + w - 1), if (from.z * 2 < z0 * 2 + l - 1) z0 - 1 else z0 + l, h))
    if (xLane == null || zLane == null) return xLane ?: zLane
    // Both axes have a lane: the cliff's is the one whose ends differ in height; level on both, the player's own side decides.
    fun step(c: Crossing): Int = Math.abs((TerrainHeight.of(world, c.start) ?: 0) - (TerrainHeight.of(world, c.landing) ?: 0))
    val xs = step(xLane)
    val zs = step(zLane)
    if (xs != zs) return if (xs > zs) xLane else zLane
    return straightCrossing(world, obj, from) ?: xLane
}

/** True when [crossing] goes down: its landing lies on lower ground than its start (heights are negative upwards). */
fun goesDown(
    world: gg.rsmod.game.model.World,
    crossing: Crossing,
): Boolean = (TerrainHeight.of(world, crossing.landing) ?: 0) > (TerrainHeight.of(world, crossing.start) ?: 0)

/** True when start and landing are on the same height. */
fun levelCrossing(
    world: gg.rsmod.game.model.World,
    crossing: Crossing,
): Boolean = TerrainHeight.of(world, crossing.landing) == TerrainHeight.of(world, crossing.start)

/**
 * One climb over a cliff loc (the Trollheim rocks 3803 / 9303-9306 and the cliffsides 34877 / 34878 / 34889), the Void Trollheim
 * pattern on this map's own lanes: walk to the fixed start tile in front of the loc, face across, then
 *  - down: the climb-down sequence ([downAnim], Void anim delay 10) and one slide to the landing behind the loc;
 *  - up: the "climbing" render ([Render.CLIMBING], BAS 0) over the loc to the landing, as Void's renderEmote + walkOverDelay;
 *  - level ground: rocks_pile_climb 839 (anim delay 30) and a 94-cycle slide (Void trollheim_rocks_advanced).
 * Returns false (with a PROBLEM trace and a message) when this loc has no lane from the player's side.
 */
suspend fun QueueTask.climbCliff(
    player: Player,
    target: GameObject,
    downAnim: Int,
): Boolean {
    val world = player.world
    val crossing = cliffCrossing(world, target, player.tile)
    if (crossing == null) {
        gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.PLUGIN) {
            "cliff ${target.id} at ${target.tile.x},${target.tile.z}: no straight lane from ${player.tile.x},${player.tile.z}"
        }
        player.message("You can't climb the rocks from here.")
        return false
    }
    walkToTile(player, crossing.start)
    face(player, crossing.direction)
    wait(1)
    val distance = crossing.start.getDistance(crossing.landing)
    when {
        levelCrossing(world, crossing) -> {
            player.animate(839, delay = 30)
            exactMove(player, crossing.landing, delay = 94, direction = crossing.direction, startDelay = 30)
        }
        goesDown(world, crossing) -> {
            player.animate(downAnim, delay = 10)
            exactMove(player, crossing.landing, delay = maxOf(120, distance * 40), direction = crossing.direction)
        }
        else -> {
            walkOverTile(player, crossing.landing, Render.CLIMBING)
            player.resetRenderAnimation()
        }
    }
    return true
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
