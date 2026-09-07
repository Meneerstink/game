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
        val collision = pawn.world.collision

        var next = steps.poll()
        if (next != null) {
            var tile = pawn.tile

            var walkDirection: Direction?
            var runDirection: Direction? = null

            walkDirection = Direction.between(tile, next.tile)

            if (walkDirection != Direction.NONE &&
                (
                    !next.detectCollision ||
                        collision.canTraverse(
                            tile,
                            walkDirection,
                            projectile = false,
                            water =
                                (pawn.walkMask and 0x4) != 0,
                        )
                )
            ) {
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

                        if (!next.detectCollision ||
                            collision.canTraverse(
                                tile,
                                runDirection,
                                projectile = false,
                                water =
                                    (pawn.walkMask and 0x4) != 0,
                            )
                        ) {
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
