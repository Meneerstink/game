package gg.rsmod.game.model.queue

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.coroutine.*
import mu.KLogging
import kotlin.coroutines.*

/**
 * Represents a task that can be paused, or suspended, and resumed at any point
 * in the future.
 *
 * @author Tom <rspsmods@gmail.com>
 */
data class QueueTask(
    val ctx: Any,
    val priority: TaskPriority,
) : Continuation<Unit> {
    lateinit var coroutine: Continuation<Unit>

    /**
     * If the task's logic has already been invoked.
     */
    var invoked = false

    /**
     * A value that can be requested by a task, such as an input for dialogs.
     */
    var requestReturnValue: Any? = null

    /**
     * Represents an action that should be executed if, and only if, this task
     * was terminated via [terminate].
     */
    var terminateAction: ((QueueTask).() -> Unit)? = null

    /**
     * The next [SuspendableStep], if any, that must be handled once a [SuspendableCondition]
     * returns [SuspendableCondition.resume] as true.
     */
    private var nextStep: SuspendableStep? = null

    /**
     * If the queue task is locking the player
     */
    var lock = false

    /**
     * RCV-012 B11: this task owns the pawn's current lock - it was queued as a locking queue, the lock was set while
     * this task executed, or it replaced (STRONG) a task that owned it. The lock is released when the last owner
     * leaves the queue for any reason: completion, a plugin exception, termination or replacement.
     */
    var ownsLock = false

    /**
     * RC-1 (RCV-005): a persistent task is an ongoing pawn state rather than a one-off action -
     * the combat loop. It survives a [TaskPriority.STRONG] queue and the soft interruption of an
     * unrelated action (prayer/curse toggle, eating, equipping, a familiar command, a dialog),
     * keeps cycling while another task holds the head of the queue, and ends only on a hard
     * interruption: [QueueTaskSet.terminateTasks] without `keepPersistent` (walking, a new
     * entity interaction, a new attack, death, logout) or its own loop condition (teleport lock,
     * lost target).
     */
    var persistent = false

    /**
     * True once [terminate] has run. A task keeps executing synchronously until its next
     * suspension point after being terminated (for example the plugin that started a
     * [TaskPriority.STRONG] teleport is still on the stack), so dialog helpers check this flag
     * and refuse to open a prompt that could never be answered.
     */
    var terminated = false
        private set

    /**
     * The [CoroutineContext] implementation for our task.
     */
    override val context: CoroutineContext = EmptyCoroutineContext

    /**
     * When the [nextStep] [SuspendableCondition.resume] returns true, this
     * method is called.
     */
    override fun resumeWith(result: Result<Unit>) {
        nextStep = null
        result.exceptionOrNull()?.let { e -> logger.error("Error with plugin!", e) }
    }

    /**
     * The logic in each [SuspendableStep] must be game-thread-safe, so we use
     * this method to keep them in-sync.
     */
    internal fun cycle() {
        val next = nextStep ?: return

        if (next.condition.resume()) {
            next.continuation.resume(Unit)
            requestReturnValue = null
        }
    }

    /**
     * Terminate any further execution of this task, during any state,
     * and invoke [terminateAction] if applicable (not null).
     */
    fun terminate() {
        terminated = true
        nextStep = null
        requestReturnValue = null
        terminateAction?.invoke(this)
    }

    /**
     * If the task has been "paused" (aka suspended).
     */
    fun suspended(): Boolean = nextStep != null

    /**
     * Wait for the specified amount of game cycles [cycles] before
     * continuing the logic associated with this task.
     */
    suspend fun wait(cycles: Int): Unit =
        suspendCoroutine {
            check(cycles > 0) { "Wait cycles must be greater than 0." }
            nextStep = SuspendableStep(WaitCondition(cycles), it)
        }

    /**
     * Wait for [predicate] to return true.
     */
    suspend fun wait(predicate: () -> Boolean): Unit =
        suspendCoroutine {
            nextStep = SuspendableStep(PredicateCondition { predicate() }, it)
        }

    /**
     * Wait for our [ctx] to reach [tile]. Note that [ctx] MUST be an instance
     * of [Pawn] and that the height of the [tile] and [Pawn.tile] must be equal,
     * as well as the x and z coordinates.
     *
     * Audit T-09: the condition reads the pawn's *current* tile each time. It captured the [Tile]
     * object the pawn stood on when the wait started, but movement replaces [Pawn.tile] with a new
     * object, so the captured tile never matched and the task never resumed.
     */
    suspend fun waitTile(tile: Tile): Unit =
        suspendCoroutine {
            val pawn = ctx as Pawn
            nextStep = SuspendableStep(PredicateCondition { pawn.tile.sameAs(tile) }, it)
        }

    /**
     * Wait for our [ctx] as [Player] to close the [interfaceId].
     */
    suspend fun waitInterfaceClose(interfaceId: Int): Unit =
        suspendCoroutine {
            nextStep = SuspendableStep(PredicateCondition { !(ctx as Player).interfaces.isVisible(interfaceId) }, it)
        }

    /**
     * Wait for <strong>any</strong> return value to be available before
     * continuing.
     */
    suspend fun waitReturnValue(): Unit =
        suspendCoroutine {
            nextStep = SuspendableStep(PredicateCondition { requestReturnValue != null }, it)
        }

    override fun equals(other: Any?): Boolean {
        val o = other as? QueueTask ?: return false
        return super.equals(o) && o.coroutine == coroutine
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + coroutine.hashCode()
        return result
    }

    class EmptyReturnValue

    companion object : KLogging() {
        val EMPTY_RETURN_VALUE = EmptyReturnValue()
    }
}
