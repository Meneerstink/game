package gg.rsmod.game.model

import gg.rsmod.game.model.MovementQueue.Step
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.sync.block.UpdateBlockType
import java.util.*

/**
 * Responsible for handling a queue of [Step]s for a [Pawn].
 *
 * @author Tom <rspsmods@gmail.com>
 */
class MovementQueue(
    val pawn: Pawn,
) {
    /**
     * A [Deque] of steps.
     */
    private val steps: Deque<Step> = ArrayDeque()

    /**
     * If any step is queued.
     */
    fun hasDestination(): Boolean = steps.isNotEmpty()

    /**
     * Get the last tile in our [steps] without removing it.
     */
    fun peekLast(): Tile? = peekLastStep()?.tile

    fun peekLastStep(): Step? = if (steps.isNotEmpty()) steps.peekLast() else null

    /**
     * The next tile this queue's owner will actually move onto once [cycle] next runs, i.e. the
     * tile it will occupy at the *end* of the current game cycle - as opposed to [pawn.tile],
     * which is still last cycle's resting tile until [cycle] executes. Needed by anything that
     * must react to where a pawn is *about* to be this same cycle (e.g. a follower pathing
     * towards its owner) rather than one cycle stale.
     */
    fun peekFirstStep(): Step? = if (steps.isNotEmpty()) steps.peekFirst() else null

    /**
     * The next [limit] queued steps in the order [cycle] will consume them. A follower needs this
     * to know where its target will actually stand at the *end* of this cycle: [cycle] consumes
     * two steps for a running pawn and one for a walking one, so only the first one or two entries
     * matter, and [peekLast] (the whole clicked destination, possibly many tiles away) is the
     * wrong tile to chase.
     */
    fun peekSteps(limit: Int): List<Step> = steps.take(limit)

    fun clear() {
        steps.clear()
    }

    fun addStep(
        step: Tile,
        type: StepType,
        detectCollision: Boolean,
    ) {
        val current = if (steps.any()) steps.peekLast().tile else pawn.tile
        addStep(current, step, type, detectCollision)
    }

    fun cycle() {
        var next = steps.poll()
        if (next != null) {
            var tile = pawn.tile

            var walkDirection: Direction?
            var runDirection: Direction? = null

            walkDirection = Direction.between(tile, next.tile)

            if (walkDirection != Direction.NONE && canStep(tile, walkDirection, next.detectCollision)) {
                if (pawn is Npc && !pawn.ignoresEntityCollision) {
                    val entitiesClipped = mutableListOf<Pawn>()

                    pawn.world.chunks
                        .get(next.tile, createIfNeeded = true)!!
                        .getEntities<Npc>(next.tile, EntityType.NPC)
                        .filter { it.tile == next.tile }
                        .let { entitiesClipped.addAll(it) }

                    pawn.world.chunks
                        .get(next.tile, createIfNeeded = true)!!
                        .getEntities<Player>(next.tile, EntityType.CLIENT)
                        .filter { it.tile == next.tile }
                        .let { entitiesClipped.addAll(it) }

                    if (entitiesClipped.isNotEmpty()) {
                        entitiesClipped.clear()
                        clear()
                        return
                    }
                }
                tile = Tile(next.tile)
                pawn.lastFacingDirection = walkDirection

                val running =
                    when (next.type) {
                        StepType.NORMAL -> pawn.isRunning()
                        StepType.FORCED_RUN -> true
                        StepType.FORCED_WALK -> false
                    }
                if (running) {
                    next = steps.poll()
                    if (next != null) {
                        runDirection = Direction.between(tile, next.tile)

                        if (canStep(tile, runDirection, next.detectCollision)) {
                            tile = Tile(next.tile)
                            pawn.lastFacingDirection = runDirection
                        } else {
                            clear()
                            runDirection = null
                        }
                    }
                }
            } else {
                walkDirection = null
                clear()
            }

            if (walkDirection != null && walkDirection != Direction.NONE) {
                pawn.steps = StepDirection(walkDirection, runDirection)
                pawn.tile = Tile(tile)
                if (pawn is Player) {
                    pawn.addBlock(UpdateBlockType.MOVEMENT)
                }
            }
        }
    }

    /**
     * Whether [pawn] may take one step from [from] in [direction].
     *
     * A pawn's collision is its whole **footprint**, not just its south-west corner tile. This
     * used to test the corner alone, which meant every pawn bigger than 1x1 could step through a
     * wall that only its other tiles touched. It is the reason familiars still walked through
     * scenery after their routing had been moved onto a real breadth-first search: 43 of the 78
     * familiars are `size=2` in this cache, and [gg.rsmod.game.model.path.strategy.BFSPathFindingStrategy.isStepBlocked]
     * had always tested every tile of the footprint while the mover that executed its route did
     * not. The pathfinder was strict and the mover was not, so any step the mover synthesised
     * itself - [addStep] interpolates a straight line between two non-adjacent queued tiles -
     * bypassed the collision the route had been built to respect.
     *
     * The loop below is deliberately the same test `isStepBlocked` performs, applied to the same
     * tiles, so a route the pathfinder considers walkable is always executable and a step it
     * would have rejected is always refused. Making the two agree is the point; being marginally
     * stricter than necessary on a diagonal only ever stops a pawn, it can never clip one.
     */
    private fun canStep(
        from: Tile,
        direction: Direction,
        detectCollision: Boolean,
    ): Boolean {
        if (!detectCollision) {
            return true
        }
        val collision = pawn.world.collision
        val water = (pawn.walkMask and 0x4) != 0
        val size = pawn.getSize().coerceAtLeast(1)
        for (x in 0 until size) {
            for (z in 0 until size) {
                if (!collision.canTraverse(
                        from.transform(x, z),
                        direction,
                        projectile = false,
                        water = water,
                    )
                ) {
                    return false
                }
            }
        }
        return true
    }

    private fun addStep(
        current: Tile,
        next: Tile,
        type: StepType,
        detectCollision: Boolean,
    ) {
        var dx = next.x - current.x
        var dz = next.z - current.z
        val delta = Math.max(Math.abs(dx), Math.abs(dz))

        for (i in 0 until delta) {
            if (dx < 0) {
                dx++
            } else if (dx > 0) {
                dx--
            }

            if (dz < 0) {
                dz++
            } else if (dz > 0) {
                dz--
            }

            val step = next.transform(-dx, -dz)
            steps.add(Step(step, type, detectCollision))
        }
    }

    data class StepDirection(
        val walkDirection: Direction?,
        val runDirection: Direction?,
    )

    data class Step(
        val tile: Tile,
        val type: StepType,
        val detectCollision: Boolean,
    )

    enum class StepType {
        NORMAL,
        FORCED_WALK,
        FORCED_RUN,
    }
}
