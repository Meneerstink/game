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

suspend fun QueueTask.walkToTile(
    player: Player,
    tile: Tile,
) {
    if (player.tile == tile) return
    val distance = player.tile.getDistance(tile)
    player.walkTo(tile)
    wait(distance + 1)
}

suspend fun QueueTask.walkOverTile(
    player: Player,
    tile: Tile,
) {
    if (player.tile == tile) return
    val distance = player.tile.getDistance(tile)
    player.walkTo(tile, MovementQueue.StepType.FORCED_WALK, detectCollision = false)
    wait(distance + 1)
}

/** Void `exactMoveDelay(target, delay, direction, startDelay)`; delays are client cycles (30 per tick). */
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
    player.forceMove(this, move, cycleDuration = Math.round(delay / 30.0).toInt().coerceAtLeast(1))
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

fun Plugin.obstacle(block: suspend QueueTask.(GameObject) -> Unit) {
    val obj = player.getInteractingGameObj()
    player.lockingQueue(lockState = LockState.FULL) {
        // The whole obstacle is one transit: zone rules keep judging the near side until the player is across (Pawn.zoneTile).
        player.obstacleUntilUnlocked = true
        block(obj)
    }
}
