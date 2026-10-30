package gg.rsmod.game.action

import gg.rsmod.game.message.impl.SetMapFlagMessage
import gg.rsmod.game.model.MovementQueue
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.*
import gg.rsmod.game.model.entity.Entity
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.path.PathRequest
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.model.queue.TaskPriority
import gg.rsmod.game.model.timer.FROZEN_TIMER
import gg.rsmod.game.model.timer.RESET_PAWN_FACING_TIMER
import gg.rsmod.game.model.timer.STUN_TIMER
import gg.rsmod.game.plugin.Plugin
import gg.rsmod.util.AabbUtil
import java.lang.ref.WeakReference

/**
 * @author Tom <rspsmods@gmail.com>
 */
object PawnPathAction {
    private const val ITEM_USE_OPCODE = -1

    /** Ticks without a step after which [walkTo] stops waiting for a route that can no longer reach its tail. */
    const val STALL_TICKS = 2

    /** Audit finding 6: opt-in diagnostic trail for the double-click cancellation chain. */
    private fun logInteraction(
        pawn: Pawn,
        other: Pawn,
        opt: Int = -1,
        reason: String,
    ) {
        if (pawn is Player && pawn.world.devContext.debugInteractions) {
            pawn.writeConsoleMessage(
                "[interaction] target=${other::class.simpleName}(${if (other is Npc) other.id else other.index}) " +
                    "tile=${other.tile} opt=$opt reason=$reason",
            )
        }
    }

    val walkPlugin: Plugin.() -> Unit = {
        val pawn = ctx as Pawn
        val world = pawn.world
        val other = pawn.attr[INTERACTING_NPC_ATTR]?.get() ?: pawn.attr[INTERACTING_PLAYER_ATTR]?.get()!!
        val opt = pawn.attr[INTERACTING_OPT_ATTR]!!

        /*
         * Some interactions only require line-of-sight range, such as npcs
         * behind cells or booths. This allows for diagonal interaction.
         *
         * Set to null for default interaction range.
         */
        val lineOfSightRange = if (other is Npc) {
            val player = pawn as? Player
            if (player != null) {
                val transformedId = other.getTransform(player)
                resolveInteractionDistance(other.id, transformedId) { id ->
                    world.plugins.getNpcInteractionDistance(id)
                }
            } else {
                world.plugins.getNpcInteractionDistance(other.id)
            }
        } else {
            null
        }

        pawn.queue(TaskPriority.STANDARD) {
            terminateAction = {
                pawn.stopMovement()
                if (pawn is Player) {
                    pawn.write(SetMapFlagMessage(255, 255))
                }
            }

            walk(this, pawn, other, opt, lineOfSightRange)
        }
    }

    val itemUsePlugin: Plugin.() -> Unit = s@{
        val pawn = ctx as Pawn
        val world = pawn.world
        val other = pawn.attr[INTERACTING_NPC_ATTR]?.get() ?: pawn.attr[INTERACTING_PLAYER_ATTR]?.get()!!

        /*
         * Some interactions only require line-of-sight range, such as npcs
         * behind cells or booths. This allows for diagonal interaction.
         *
         * Set to null for default interaction range.
         */
        val lineOfSightRange = if (other is Npc) {
            val player = pawn as? Player
            if (player != null) {
                val transformedId = other.getTransform(player)
                resolveInteractionDistance(other.id, transformedId) { id ->
                    world.plugins.getNpcInteractionDistance(id)
                }
            } else {
                world.plugins.getNpcInteractionDistance(other.id)
            }
        } else {
            null
        }

        pawn.queue(TaskPriority.STANDARD) {
            terminateAction = {
                pawn.stopMovement()
                if (pawn is Player) {
                    pawn.write(SetMapFlagMessage(255, 255))
                }
            }

            walk(this, pawn, other, ITEM_USE_OPCODE, lineOfSightRange)
        }
    }

    private suspend fun walk(
        it: QueueTask,
        pawn: Pawn,
        other: Pawn,
        opt: Int,
        lineOfSightRange: Int?,
    ) {
        val world = pawn.world
        val initialTile = Tile(other.tile)

        pawn.facePawn(other)

        if (pawn is Player) {
            if (pawn.isResting()) {
                val standUpAnimation = 11788
                pawn.queue(TaskPriority.STRONG) {
                    pawn.lock()
                    pawn.animate(standUpAnimation, delay = 0)
                    wait(3)
                    pawn.varps.setState(173, if (pawn.isRunning()) 1 else 0)
                    pawn.unlock()
                }
            }
        }

        val pathFound =
            walkTo(
                it,
                pawn,
                other,
                interactionRange = lineOfSightRange ?: 1,
                lineOfSight =
                    lineOfSightRange != null,
            )
        if (!pathFound) {
            pawn.movementQueue.clear()
            if (pawn is Player) {
                val reason =
                    when {
                        pawn.timers.has(FROZEN_TIMER) -> "frozen"
                        pawn.timers.has(STUN_TIMER) -> "stunned"
                        else -> "no path found"
                    }
                logInteraction(pawn, other, opt, "route failed ($reason)")
                when {
                    pawn.timers.has(FROZEN_TIMER) -> pawn.writeMessage(Entity.MAGIC_STOPS_YOU_FROM_MOVING)
                    pawn.timers.has(STUN_TIMER) -> pawn.writeMessage(Entity.YOURE_STUNNED)
                    else -> pawn.writeMessage(Entity.YOU_CANT_REACH_THAT)
                }
                pawn.write(SetMapFlagMessage(255, 255))
            }
            pawn.resetFacePawn()
            return
        }

        pawn.stopMovement()

        if (pawn is Player) {
            if (pawn.attr[FACING_PAWN_ATTR]?.get() != other) {
                logInteraction(pawn, other, opt, "cancelled: facing-target changed after route completed")
                return
            }
            /*
             * If the npc has moved from the time this queue was added to
             * when it was actually invoked, we need to walk towards it again - unless it is in reach anyway. N214 (owner 2026-10-23:
             * Pick-up pet / Trade npc: "blijven allebei naar elkaar toe lopen"): a follower (or a wandering npc) moves every tick, so
             * "it moved" alone re-walked forever while the two stood next to each other.
             */
            if (!other.tile.sameAs(initialTile) && !reachedMovingTarget(pawn, other, lineOfSightRange ?: 1, lineOfSightRange != null)) {
                logInteraction(pawn, other, opt, "target moved (was $initialTile, now ${other.tile}) - re-walking")
                walk(it, pawn, other, opt, lineOfSightRange)
                return
            }

            if (other is Npc) {
                /*
                 * On 07, only one npc can be facing the player at a time,
                 * so if the last pawn that faced the player is still facing
                 * them, then we reset their face target.
                 */
                pawn.attr[NPC_FACING_US_ATTR]?.get()?.let {
                    if (it.attr[FACING_PAWN_ATTR]?.get() == pawn) {
                        it.resetFacePawn()
                        it.timers.remove(RESET_PAWN_FACING_TIMER)
                    }
                }
                pawn.attr[NPC_FACING_US_ATTR] = WeakReference(other)

                /*
                 * Stop the npc from walking while the player talks to it
                 * for [Npc.RESET_PAWN_FACE_DELAY] cycles.
                 */
                other.stopMovement()
                if (other.attr[FACING_PAWN_ATTR]?.get() != pawn && !other.static) {
                    other.facePawn(pawn)
                    other.timers[RESET_PAWN_FACING_TIMER] = Npc.RESET_PAWN_FACE_DELAY
                }

                val npcId = other.getTransform(pawn)
                val handled =
                    if (opt != ITEM_USE_OPCODE) {
                        executeWithObjectIdFallback(other.id, npcId) { id ->
                            world.plugins.executeNpc(pawn, id, opt)
                        }
                    } else {
                        val item = pawn.attr[INTERACTING_ITEM]?.get() ?: return
                        executeWithObjectIdFallback(other.id, npcId) { id ->
                            world.plugins.executeItemOnNpc(pawn, id, item.id)
                        }
                    }

                logInteraction(pawn, other, opt, if (handled) "handled" else "unhandled")
                if (!handled) {
                    if (opt != ITEM_USE_OPCODE) {
                        UnhandledInteractions.recordInteraction(
                            kind = if (other is Npc) "npc" else "player",
                            id = if (other is Npc) other.getTransform(pawn as Player) else other.index,
                            option = opt,
                            name = if (other is Npc) other.name else "player",
                            context = "tile=${other.tile}",
                        )
                    } else {
                        val item = pawn.attr[INTERACTING_ITEM]?.get()
                        UnhandledInteractions.recordInteraction(
                            kind = if (other is Npc) "item-on-npc" else "item-on-player",
                            id = item?.id ?: -1,
                            option = ITEM_USE_OPCODE,
                            name = "item",
                            context = "target=${if (other is Npc) other.getTransform(pawn as Player) else other.index} tile=${other.tile}",
                        )
                    }
                    pawn.writeMessage(Entity.NOTHING_INTERESTING_HAPPENS)
                }
            }

            if (other is Player) {
                if (opt != ITEM_USE_OPCODE) {
                    val option = pawn.options[opt - 1]
                    if (option != null) {
                        val handled = world.plugins.executePlayerOption(pawn, option)
                        logInteraction(pawn, other, opt, if (handled) "handled" else "unhandled")
                        if (!handled) {
                            UnhandledInteractions.recordInteraction(
                                kind = "player",
                                id = other.index,
                                option = opt,
                                name = "player",
                                context = "tile=${other.tile}",
                            )
                            pawn.writeMessage(Entity.NOTHING_INTERESTING_HAPPENS)
                        }
                    } else {
                        UnhandledInteractions.recordInteraction(
                            kind = "player",
                            id = other.index,
                            option = opt,
                            name = "player",
                            context = "tile=${other.tile} missing-option",
                        )
                    }
                } else {
                    val item = pawn.attr[INTERACTING_ITEM]?.get() ?: return
                    if (!world.plugins.executeItemOnPlayer(pawn, item.id)) {
                        UnhandledInteractions.recordInteraction(
                            kind = "item-on-player",
                            id = item.id,
                            option = ITEM_USE_OPCODE,
                            name = "item",
                            context = "target=${other.index} tile=${other.tile}",
                        )
                    }
                }
            }
            pawn.resetFacePawn()
            pawn.faceTile(other.tile)
        }
    }

    suspend fun walkTo(
        it: QueueTask,
        pawn: Pawn,
        target: Pawn,
        interactionRange: Int,
        lineOfSight: Boolean,
        waitForArrival: Boolean = true,
        reach: (() -> Int)? = null,
    ): Boolean {
        val sourceSize = pawn.getSize()
        val targetSize = target.getSize()
        val sourceTile = pawn.tile
        val targetTile = target.tile
        val projectile = interactionRange > 2

        val frozen = pawn.timers.has(FROZEN_TIMER)
        val stunned = pawn.timers.has(STUN_TIMER)

        if (pawn.attr[FACING_PAWN_ATTR]?.get() != target) {
            logInteraction(pawn, target, reason = "cancelled: facing-target changed before route calc")
            return false
        }

        if (stunned) {
            return false
        }

        if (holdsForApproachingPlayer(pawn, target)) {
            pawn.stopMovement()
            return reachedMovingTarget(pawn, target, interactionRange, lineOfSight || projectile)
        }

        if (frozen) {
            // A frozen pawn cannot walk, so it attacks only when the target is in reach from where it stands. Conformance
            // run 2026-09-29 (equipment reach): the melee branch passed the attack range as the TARGET SIZE to bordering()
            // (a frozen halberd never reached 2 tiles), and projectile attacks were never checked here at all - the route
            // below "succeeded", the wait loop gave up on the stalled pawn and returned success, so a frozen ranger shot
            // from one tile beyond its range (dart 4, knife 5, crossbow 8).
            if (overlap(sourceTile, sourceSize, targetTile, targetSize)) {
                return false
            }
            return inReachWithoutMoving(pawn, sourceTile, sourceSize, targetTile, targetSize, interactionRange, lineOfSight || projectile)
        }

        val builder =
            PathRequest
                .Builder()
                .setPoints(sourceTile, targetTile)
                .setSourceSize(sourceSize, sourceSize)
                .setTargetSize(targetSize, targetSize)
                .setProjectilePath(lineOfSight || projectile)
                .setTouchRadius(interactionRange)
                .clipPathNodes(node = true, link = true)

        if (!lineOfSight && !projectile) {
            builder.clipDiagonalTiles()
        }

        builder.clipOverlapTiles()

        val route = pawn.createPathFindingStrategy().calculateRoute(builder.build())
        if (pawn is gg.rsmod.game.model.entity.Npc && gg.rsmod.game.model.BreachTrace.watches(pawn.id)) {
            gg.rsmod.game.model.BreachTrace.log("route") {
                "npc=${pawn.id}#${pawn.index} strategy=${if (pawn.smartPathfinding) "smart" else if (pawn.naivePathfinding) "naive" else "simple"} from=${sourceTile.x},${sourceTile.z} " +
                    "target=${target.entityType}#${target.index}@${targetTile.x},${targetTile.z} range=$interactionRange los=$lineOfSight " +
                    "success=${route.success} steps=${route.path.size} tail=${route.tail.x},${route.tail.z} frozen=$frozen"
            }
        }
        // N417: count an npc's failed route searches in a row (npc_aggro.plugin.kts drops a target it keeps failing to reach).
        if (pawn is gg.rsmod.game.model.entity.Npc && !frozen) {
            if (route.success) {
                pawn.attr.remove(gg.rsmod.game.model.attr.NPC_ROUTE_FAILS)
            } else {
                pawn.attr[gg.rsmod.game.model.attr.NPC_ROUTE_FAILS] = (pawn.attr[gg.rsmod.game.model.attr.NPC_ROUTE_FAILS] ?: 0) + 1
            }
        }
        // Debug toolkit: any npc or player whose route to its target fails (stuck behind scenery, target unreachable).
        if (!route.success && !frozen) {
            gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.NPCPATH) {
                val who = if (pawn is gg.rsmod.game.model.entity.Npc) "npc ${pawn.id}#${pawn.index}" else (pawn as? gg.rsmod.game.model.entity.Player)?.username ?: "$pawn"
                "no route: $who at ${sourceTile.x},${sourceTile.z},${sourceTile.height} -> ${target.entityType}#${target.index} at ${targetTile.x},${targetTile.z} range=$interactionRange (stops at ${route.tail.x},${route.tail.z})"
            }
        }

        val stepType =
            if (pawn is gg.rsmod.game.model.entity.Npc && pawn.attr[gg.rsmod.game.model.attr.NPC_RUNS_ATTR] == true) {
                MovementQueue.StepType.FORCED_RUN
            } else {
                MovementQueue.StepType.NORMAL
            }
        pawn.walkPath(route.path, stepType, detectCollision = true)

        if (pawn.hasLineOfSightTo(target, true, interactionRange) && projectile) {
            return route.success
        }

        // The caller re-evaluates every tick (npc combat): the steps are queued, nothing to wait for.
        if (!waitForArrival) return route.success

        var lastTile = pawn.tile
        var stalled = 0
        while (!pawn.tile.sameAs(route.tail)) {
            if (holdsForApproachingPlayer(pawn, target)) {
                pawn.stopMovement()
                return reachedMovingTarget(pawn, target, reach?.invoke() ?: interactionRange, lineOfSight || projectile)
            }
            if (!targetTile.sameAs(target.tile)) {
                // N214: a moving target that came into reach is reached - routing again to its new tile chased a follower that
                // was already beside the player (pet and owner walking towards each other).
                if (reachedMovingTarget(pawn, target, reach?.invoke() ?: interactionRange, lineOfSight || projectile)) {
                    pawn.stopMovement()
                    return true
                }
                return walkTo(it, pawn, target, reach?.invoke() ?: interactionRange, lineOfSight, reach = reach)
            }
            it.wait(1)
            // [reach] is the caller's live attack range (combat). It can change on the way - a bow switched in while running at the
            // target (owner 2026-10-01, Deadman hybrid switching): stop where the weapon held NOW reaches, instead of walking on to
            // the tail planned for the old one (a melee tail: next to the target).
            val now = reach?.invoke()
            if (now != null && !overlap(pawn.tile, sourceSize, target.tile, targetSize) &&
                inReachWithoutMoving(pawn, pawn.tile, sourceSize, target.tile, targetSize, now, lineOfSight || now > 2)
            ) {
                pawn.stopMovement()
                return true
            }
            // Breach audit 2026-09-27 (B2): a route cut short (a pawn on the next tile, a freeze, a blocked second run step, a
            // projectile-only route) never reaches its tail, and this loop waited for it forever - the pawn stood still until a
            // new interaction replaced the queue. Stop waiting once the pawn has not moved for STALL_TICKS ticks with nothing
            // left to walk; the caller then routes again from where the pawn really is.
            if (pawn.tile.sameAs(lastTile)) {
                if ((++stalled >= STALL_TICKS && !pawn.hasMoveDestination()) || stalled >= STALL_TICKS * 2) return route.success
            } else {
                stalled = 0
                lastTile = pawn.tile
            }
        }
        return route.success
    }

    /**
     * Whether [pawn] has a route to melee distance of [target] from where it stands - the same request [walkTo] makes for a melee
     * approach. Npcs that "always attempt to melee the player unless they cannot reach them" (OSRS Wiki: Night beast; Durial321,
     * owner 2026-09-27) use magic only when this is false; they used to cast whenever the target was merely inside spell range,
     * and never walked in.
     */
    fun meleeRouteExists(
        pawn: Pawn,
        target: Pawn,
    ): Boolean {
        val request =
            PathRequest
                .Builder()
                .setPoints(pawn.tile, target.tile)
                .setSourceSize(pawn.getSize(), pawn.getSize())
                .setTargetSize(target.getSize(), target.getSize())
                .setProjectilePath(false)
                .setTouchRadius(1)
                .clipPathNodes(node = true, link = true)
                .clipDiagonalTiles()
                .clipOverlapTiles()
                .build()
        return pawn.createPathFindingStrategy().calculateRoute(request).success
    }

    /**
     * Whether a pawn standing still reaches the target: the footprints are 1..[range] tiles apart (Chebyshev), a range-1
     * non-projectile attack does not reach diagonally (it needs a shared edge), and a longer reach needs a clear line.
     */
    private fun inReachWithoutMoving(
        pawn: Pawn,
        source: Tile,
        sourceSize: Int,
        target: Tile,
        targetSize: Int,
        range: Int,
        projectile: Boolean,
    ): Boolean {
        val dx = maxOf(0, target.x - (source.x + sourceSize - 1), source.x - (target.x + targetSize - 1))
        val dz = maxOf(0, target.z - (source.z + sourceSize - 1), source.z - (target.z + targetSize - 1))
        val gap = maxOf(dx, dz)
        if (gap < 1 || gap > range) return false
        if (range == 1 && !projectile) return bordering(source, sourceSize, target, targetSize)
        return pawn.world.collision.raycast(source, target, projectile)
    }

    /**
     * N214: whether [pawn] already reaches [target] where both stand now - a melee interaction (range 1, no line of sight) needs a
     * shared edge without a wall between (MeleeReach, what the route aims for and what a follower stops at), anything longer the
     * usual reach.
     */
    fun reachedMovingTarget(
        pawn: Pawn,
        target: Pawn,
        range: Int,
        lineOfSight: Boolean,
    ): Boolean {
        if (overlap(pawn.tile, pawn.getSize(), target.tile, target.getSize())) return false
        if (range <= 1 && !lineOfSight) {
            return gg.rsmod.game.model.path.MeleeReach.bordersWithoutWall(
                pawn.world.collision, pawn.tile, pawn.getSize(), pawn.getSize(), target.tile, target.getSize(), target.getSize(),
            )
        }
        return inReachWithoutMoving(pawn, pawn.tile, pawn.getSize(), target.tile, target.getSize(), range, lineOfSight)
    }

    /**
     * N448 (owner 2026-10-30, Pest Control: "pathing issues met t attacken van npcs creert een lus"): every queue of a tick runs
     * before anyone moves (QueueHandlerTask, then the player and npc cycles), so an npc and the player attacking it both step
     * towards the other's OLD tile - from a diagonal they land diagonal again or on one tile, tick after tick (the diagonal dance;
     * N245 had the same with a follower). OSRS processes the npc first and the player re-routes to its new tile. Here the npc holds
     * while the player who is attacking or interacting with it (facing it) is already walking in within two tiles; the player then
     * arrives beside it and the npc fights from where it stands. A player running away or standing still is chased as before.
     */
    fun holdsForApproachingPlayer(
        pawn: Pawn,
        target: Pawn,
    ): Boolean {
        if (pawn !is gg.rsmod.game.model.entity.Npc || target !is Player) return false
        if (target.attr[FACING_PAWN_ATTR]?.get() != pawn || !target.hasMoveDestination()) return false
        if (pawn.tile.height != target.tile.height) return false
        val s = pawn.tile
        val t = target.tile
        val dx = maxOf(0, t.x - (s.x + pawn.getSize() - 1), s.x - (t.x + target.getSize() - 1))
        val dz = maxOf(0, t.z - (s.z + pawn.getSize() - 1), s.z - (t.z + target.getSize() - 1))
        return maxOf(dx, dz) <= 2
    }

    private fun overlap(
        tile1: Tile,
        size1: Int,
        tile2: Tile,
        size2: Int,
    ): Boolean = AabbUtil.areOverlapping(tile1.x, tile1.z, size1, size1, tile2.x, tile2.z, size2, size2)

    private fun bordering(
        tile1: Tile,
        size1: Int,
        tile2: Tile,
        size2: Int,
    ): Boolean = AabbUtil.areBordering(tile1.x, tile1.z, size1, size1, tile2.x, tile2.z, size2, size2)
}
