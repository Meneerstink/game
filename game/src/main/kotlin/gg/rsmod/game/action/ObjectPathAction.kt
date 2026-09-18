package gg.rsmod.game.action

import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.message.impl.SetMapFlagMessage
import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.MovementQueue
import gg.rsmod.game.model.attr.INTERACTING_ITEM
import gg.rsmod.game.model.attr.INTERACTING_OBJ_ATTR
import gg.rsmod.game.model.attr.INTERACTING_OPT_ATTR
import gg.rsmod.game.model.collision.ObjectType
import gg.rsmod.game.model.entity.Entity
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.path.PathRequest
import gg.rsmod.game.model.path.Route
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.model.queue.TaskPriority
import gg.rsmod.game.model.timer.FROZEN_TIMER
import gg.rsmod.game.model.timer.STUN_TIMER
import gg.rsmod.game.plugin.Plugin
import gg.rsmod.util.AabbUtil
import gg.rsmod.util.DataConstants
import java.util.*

/**
 * This class is responsible for calculating distances and valid interaction
 * tiles for [GameObject] path-finding.
 *
 * @author Tom <rspsmods@gmail.com>
 */
internal fun executeWithObjectIdFallback(
    originalId: Int,
    transformedId: Int,
    execute: (Int) -> Boolean,
): Boolean {
    if (execute(transformedId)) {
        return true
    }
    return transformedId != originalId && execute(originalId)
}

/**
 * Resolves an optional interaction distance using the visually transformed id
 * first, then the definition id as a compatibility fallback.
 */
internal fun resolveInteractionDistance(
    originalId: Int,
    transformedId: Int,
    lookup: (Int) -> Int?,
): Int? {
    return lookup(transformedId)
        ?: if (transformedId != originalId) lookup(originalId) else null
}
object ObjectPathAction {
    /** Audit finding 7: opt-in diagnostic trail for the object-route/bank-distance chain. */
    private fun logRoute(
        player: Player,
        obj: GameObject,
        reason: String,
    ) {
        if (player.world.devContext.debugInteractions) {
            player.writeConsoleMessage(
                "[object-route] id=${obj.id} type=${obj.type} rot=${obj.rot} tile=${obj.tile} " +
                    "floor=${obj.tile.height} playerTile=${player.tile} playerFloor=${player.tile.height} reason=$reason",
            )
        }
    }

    fun walk(
        player: Player,
        obj: GameObject,
        lineOfSightRange: Int?,
        logic: Plugin.() -> Unit,
    ) {
        player.queue(TaskPriority.STANDARD) {
            terminateAction = {
                player.stopMovement()
                player.write(SetMapFlagMessage(255, 255))
            }

            val route = walkTo(obj, lineOfSightRange)
            if (route.success) {
                logRoute(player, obj, "route succeeded")
                if (lineOfSightRange == null || lineOfSightRange > 0) {
                    faceObj(player, obj)
                }
                player.executePlugin(logic)
            } else {
                val reason =
                    when {
                        player.timers.has(FROZEN_TIMER) -> "frozen"
                        player.timers.has(STUN_TIMER) -> "stunned"
                        else -> "no path found"
                    }
                logRoute(player, obj, "route failed ($reason)")
                player.faceTile(obj.tile)
                when {
                    player.timers.has(FROZEN_TIMER) -> player.writeMessage(Entity.MAGIC_STOPS_YOU_FROM_MOVING)
                    player.timers.has(STUN_TIMER) -> player.writeMessage(Entity.YOURE_STUNNED)
                    else -> player.writeMessage(Entity.YOU_CANT_REACH_THAT)
                }
                player.write(SetMapFlagMessage(255, 255))
            }
        }
    }

    val itemOnObjectPlugin: Plugin.() -> Unit = {
        val player = ctx as Player

        val item = player.attr[INTERACTING_ITEM]!!.get()!!
        val obj = player.attr[INTERACTING_OBJ_ATTR]!!.get()!!
        val transformedId = obj.getTransform(player)
        val lineOfSightRange = resolveInteractionDistance(obj.id, transformedId) { id ->
            player.world.plugins.getObjInteractionDistance(id)
        }

        walk(player, obj, lineOfSightRange) {
            player.faceTile(obj.tile)
            val handled = executeWithObjectIdFallback(obj.id, transformedId) { id ->
                player.world.plugins.executeItemOnObject(player, id, item.id)
            }
            if (!handled) {
                player.writeMessage(Entity.NOTHING_INTERESTING_HAPPENS)
                // Same bounded registry as unhandled options; the "option" column carries the used item id.
                UnhandledInteractions.record(
                    UnhandledInteractions.Key(obj.id, transformedId, item.id, obj.tile.x, obj.tile.z, obj.tile.height),
                    player.world.definitions.get(ObjectDef::class.java, transformedId).name,
                    "item ${item.id}",
                    obj.type,
                    obj.rot,
                    kind = "item",
                )
                if (player.world.devContext.debugObjects) {
                    player.writeConsoleMessage(
                        "Unhandled item on object: [item=$item, id=${obj.id}, type=${obj.type}, rot=${obj.rot}, x=${obj.tile.x}, z=${obj.tile.z}]",
                    )
                }
            }
        }
    }

    val objectInteractPlugin: Plugin.() -> Unit = {
        val player = ctx as Player

        val obj = player.attr[INTERACTING_OBJ_ATTR]!!.get()!!
        val opt = player.attr[INTERACTING_OPT_ATTR]
        val transformedId = obj.getTransform(player)
        val lineOfSightRange = resolveInteractionDistance(obj.id, transformedId) { id ->
            player.world.plugins.getObjInteractionDistance(id)
        }

        walk(player, obj, lineOfSightRange) {
            val handled = executeWithObjectIdFallback(obj.id, transformedId) { id ->
                player.world.plugins.executeObject(player, id, opt!!)
            } || player.world.plugins.executeObjectFallback(player, obj, opt!!)
            if (!handled) {
                player.writeMessage(Entity.NOTHING_INTERESTING_HAPPENS)
                val unhandledDef = player.world.definitions.get(ObjectDef::class.java, transformedId)
                UnhandledInteractions.record(
                    UnhandledInteractions.Key(obj.id, transformedId, opt!!, obj.tile.x, obj.tile.z, obj.tile.height),
                    unhandledDef.name,
                    unhandledDef.options.getOrNull(opt - 1),
                    obj.type,
                    obj.rot,
                )
                /*
                 * The item-on-object path above has always reported what it failed to handle; the
                 * plain option path never did, which is why every unbound door, gate, rift and
                 * dungeon entrance reported the same untraceable "Nothing interesting happens."
                 * The transformed id and the option index are both included because a multi-state
                 * object (varbit/varp transform) is usually bound under one id and clicked as
                 * another, and because the option index is what `on_obj_option` actually binds.
                 */
                if (player.world.devContext.debugObjects) {
                    val def = player.world.definitions.get(ObjectDef::class.java, transformedId)
                    val option = def.options.getOrNull(opt!! - 1)
                    player.writeConsoleMessage(
                        "Unhandled object action: [id=${obj.id}, transform=$transformedId, " +
                            "name=${def.name}, opt=$opt, option=$option, type=${obj.type}, " +
                            "rot=${obj.rot}, x=${obj.tile.x}, z=${obj.tile.z}, height=${obj.tile.height}]",
                    )
                }
            }
        }
    }

    /** Walks to the object, then runs the spell-on-object plugin bound for the used interface target (OpLocTHandler). */
    val spellOnObjectPlugin: Plugin.() -> Unit = {
        val player = ctx as Player
        val obj = player.attr[INTERACTING_OBJ_ATTR]!!.get()!!
        val parent = player.attr[gg.rsmod.game.model.attr.INTERACTING_COMPONENT_PARENT] ?: -1
        val child = player.attr[gg.rsmod.game.model.attr.INTERACTING_COMPONENT_CHILD] ?: -1
        val transformedId = obj.getTransform(player)
        walk(player, obj, null) {
            player.faceTile(obj.tile)
            val handled = executeWithObjectIdFallback(obj.id, transformedId) { id ->
                player.world.plugins.executeSpellOnObject(player, parent, child, id)
            }
            if (!handled) {
                player.writeMessage(Entity.NOTHING_INTERESTING_HAPPENS)
                UnhandledInteractions.record(
                    UnhandledInteractions.Key(obj.id, transformedId, (parent shl 16) or child, obj.tile.x, obj.tile.z, obj.tile.height),
                    player.world.definitions.get(ObjectDef::class.java, transformedId).name,
                    "interface $parent:$child",
                    obj.type,
                    obj.rot,
                    kind = "spell",
                )
            }
        }
    }

    private suspend fun QueueTask.walkTo(
        obj: GameObject,
        lineOfSightRange: Int?,
    ): Route {
        val pawn = ctx as Pawn

        val def = obj.getDef(pawn.world.definitions)
        var tile = obj.tile
        val type = obj.type
        val rot = obj.rot
        var width = def.width
        var length = def.length
        val clipMask = def.clipMask

        val wall = type == ObjectType.LENGTHWISE_WALL.value || type == ObjectType.DIAGONAL_WALL.value
        val diagonal = type == ObjectType.DIAGONAL_WALL.value || type == ObjectType.DIAGONAL_INTERACTABLE.value
        val wallDeco =
            type == ObjectType.INTERACTABLE_WALL_DECORATION.value || type == ObjectType.INTERACTABLE_WALL.value
        val blockDirections = EnumSet.noneOf(Direction::class.java)

        if (wallDeco) {
            width = 0
            length = 0
        } else if (!wall && (rot == 1 || rot == 3)) {
            width = def.length
            length = def.width
        }

        /*
         * Objects have a clip mask in their [ObjectDef] which can be used
         * to specify any directions that the object can't be 'interacted'
         * from.
         */
        val blockBits = 4
        val clipFlag = (DataConstants.BIT_MASK[blockBits] and (clipMask shl rot)) or (clipMask shr (blockBits - rot))

        if ((0x1 and clipFlag) != 0) {
            blockDirections.add(Direction.NORTH)
        }

        if ((0x2 and clipFlag) != 0) {
            blockDirections.add(Direction.EAST)
        }

        if ((0x4 and clipFlag) != 0) {
            blockDirections.add(Direction.SOUTH)
        }

        if ((clipFlag and 0x8) != 0) {
            blockDirections.add(Direction.WEST)
        }

        /*
         * Wall objects can't be interacted from certain directions due to
         * how they are visually placed in a tile.
         */
        val blockedWallDirections =
            when (rot) {
                0 -> EnumSet.of(Direction.NORTH)
                1 -> EnumSet.of(Direction.EAST)
                2 -> EnumSet.of(Direction.SOUTH)
                3 -> EnumSet.of(Direction.WEST)
                else -> throw IllegalStateException("Invalid object rotation: $rot")
            }

        /*
         * Diagonal walls have an extra direction set as 'blocked', this is to
         * avoid the player interacting with the door and having its opened
         * door object be spawned on top of them, which leads to them being
         * stuck.
         */
        if (wall && diagonal) {
            when (rot) {
                0 -> blockedWallDirections.add(Direction.NORTH)
                1 -> blockedWallDirections.add(Direction.EAST)
                2 -> blockedWallDirections.add(Direction.SOUTH)
                3 -> blockedWallDirections.add(Direction.WEST)
            }
        }

        if (wall) {
            /*
             * Check if the pawn is within interaction distance of the wall.
             */
            if (pawn.tile.isWithinRadius(tile, 1)) {
                val dir = Direction.between(tile, pawn.tile)
                if (dir !in blockedWallDirections &&
                    (
                        diagonal ||
                            !AabbUtil.areDiagonal(
                                pawn.tile.x,
                                pawn.tile.z,
                                pawn.getSize(),
                                pawn.getSize(),
                                tile.x,
                                tile.z,
                                width,
                                length,
                            )
                    )
                ) {
                    return Route(ArrayDeque(), success = true, tail = pawn.tile)
                }
            }

            blockDirections.addAll(blockedWallDirections)
        }

        // TODO: this will be fixed with new PF update
        if (def.name.contains("Furnace")) {
            tile =
                when (rot) {
                    0 -> tile.transform(0, width shr 1)
                    1 -> tile.transform(width shr 1, 0)
                    else -> tile
                }
        }

        val builder =
            PathRequest
                .Builder()
                .setPoints(pawn.tile, tile)
                .setSourceSize(pawn.getSize(), pawn.getSize())
                .setProjectilePath(lineOfSightRange != null)
                .setTargetSize(width, length)
                .clipPathNodes(node = true, link = true)
                .clipDirections(*blockDirections.toTypedArray())

        if (lineOfSightRange != null) {
            builder.setTouchRadius(lineOfSightRange)
        }

        /*
         * If the object is not a 'diagonal' object, you shouldn't be able to
         * interact with them from diagonal tiles.
         */
        if (!diagonal) {
            builder.clipDiagonalTiles()
        }

        if (diagonal && width < 2 && length < 2) {
            builder.clipDiagonalTiles()
        }

        /*
         * If the object is not a wall object, or if we have a line of sight range
         * set for the object, then we shouldn't clip the tiles that overlap the
         * object; otherwise we do clip them.
         */
        if (!wall && (lineOfSightRange == null || lineOfSightRange > 0)) {
            builder.clipOverlapTiles()
        }

        val route = pawn.createPathFindingStrategy().calculateRoute(builder.build())

        if (pawn.timers.has(FROZEN_TIMER) && !pawn.tile.sameAs(route.tail)) {
            return Route(ArrayDeque(), success = false, tail = pawn.tile)
        }

        pawn.walkPath(route.path, MovementQueue.StepType.NORMAL, detectCollision = true)

        val last = pawn.movementQueue.peekLast()

        while (last != null &&
            !pawn.tile.sameAs(last) &&
            !pawn.timers.has(FROZEN_TIMER) &&
            !pawn.timers.has(STUN_TIMER) &&
            pawn.lock.canMove()
        ) {
            wait(1)
        }

        if (pawn.timers.has(STUN_TIMER)) {
            pawn.stopMovement()
            return Route(ArrayDeque(), success = false, tail = pawn.tile)
        }

        if (pawn.timers.has(FROZEN_TIMER) && !pawn.tile.sameAs(route.tail)) {
            return Route(ArrayDeque(), success = false, tail = pawn.tile)
        }

        if (wall && !route.success && Direction.between(tile, pawn.tile) !in blockedWallDirections) {
            if (pawn is Player && pawn.world.devContext.debugInteractions) {
                pawn.writeConsoleMessage(
                    "[object-route] id=${obj.id} tile=${obj.tile} wall-route-exception: real route failed " +
                        "but pawn is bordering from a non-blocked direction - treated as success.",
                )
            }
            return Route(route.path, success = true, tail = route.tail)
        }

        return route
    }

    private fun faceObj(
        pawn: Pawn,
        obj: GameObject,
    ) {
        val def = pawn.world.definitions.get(ObjectDef::class.java, obj.id)
        val rot = obj.rot
        val type = obj.type

        when (type) {
            ObjectType.LENGTHWISE_WALL.value -> {
                if (!pawn.tile.sameAs(obj.tile)) {
                    pawn.faceTile(obj.tile)
                }
            }
            ObjectType.INTERACTABLE_WALL_DECORATION.value, ObjectType.INTERACTABLE_WALL.value -> {
                val dir =
                    when (rot) {
                        0 -> Direction.WEST
                        1 -> Direction.NORTH
                        2 -> Direction.EAST
                        3 -> Direction.SOUTH
                        else -> throw IllegalStateException("Invalid object rotation: $obj")
                    }
                pawn.faceTile(pawn.tile.step(dir))
            }
            else -> {
                var width = def.width
                var length = def.length
                if (rot == 1 || rot == 3) {
                    width = def.length
                    length = def.width
                }
                var tile = obj.tile
                if (width > 1 || length > 1) {
                    tile = tile.transform(width shr 1, length shr 1)
                }
                pawn.faceTile(tile, width, length)
            }
        }
    }
}
