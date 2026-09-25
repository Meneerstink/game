package gg.rsmod.game.model.queue

import gg.rsmod.game.task.rethrowIfFatal
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import mu.KLogging
import java.util.*
import kotlin.coroutines.createCoroutine

/**
 * A system responsible for task coroutine logic.
 *
 * @author Tom <rspsmods@gmail.com>
 */
abstract class QueueTaskSet {
    protected val queue: LinkedList<QueueTask> = LinkedList()

    val size: Int get() = queue.size

    /**
     * RCV-012 B11: releases the owning pawn's lock. Only set for players; npc locks (Corporeal Beast core,
     * Giant Mole burrow) are held without a task and keep their own explicit unlock.
     */
    var releaseLock: (() -> Unit)? = null

    /** The task whose coroutine is currently executing inside [cycle], if any. */
    protected var running: QueueTask? = null

    abstract fun cycle()

    fun queue(
        ctx: Any,
        dispatcher: CoroutineDispatcher,
        priority: TaskPriority,
        block: suspend QueueTask.(CoroutineScope) -> Unit,
        lock: Boolean = false,
        persistent: Boolean = false,
    ) {
        val task = QueueTask(ctx, priority)
        val suspendBlock = suspend { block(task, CoroutineScope(dispatcher)) }

        task.lock = lock
        task.ownsLock = lock
        task.persistent = persistent
        task.coroutine = suspendBlock.createCoroutine(completion = task)

        if (priority == TaskPriority.STRONG) {
            // A STRONG action replaces other actions, never the ongoing combat state (RC-1).
            // RCV-012 B11: the lock a replaced task held passes to the replacing task, so it is released when that
            // task ends - never left stuck, and never dropped while a teleport/death started from the replaced task
            // still needs it.
            if (terminate(keepPersistent = true)) {
                task.ownsLock = true
            }
        }

        queue.addFirst(task)
    }

    /**
     * In-game events sometimes must return a value to a plugin. An example are
     * dialogs which must return values such as input, button click, etc.
     *
     * @param value
     * The return value that the plugin has asked for.
     */
    fun submitReturnValue(value: Any) {
        val task = queue.peek() ?: return // Shouldn't call this method without a queued task.
        task.requestReturnValue = value
    }

    /**
     * Remove all [QueueTask] from our [queue], invoking each task's [QueueTask.terminate]
     * before-hand. With [keepPersistent], [QueueTask.persistent] tasks are left running.
     * A lock owned by a removed task is released unless a remaining task still owns it.
     */
    fun terminateTasks(keepPersistent: Boolean = false) {
        if (terminate(keepPersistent)) {
            releaseLockIfUnowned()
        }
    }

    /**
     * RCV-012 B11 lock ownership. Called by the pawn whenever its lock changes. A non-NONE lock set while one of
     * this set's tasks is executing belongs to that task; setting NONE explicitly clears every ownership.
     * A lock set outside any of this pawn's tasks (trade session, an npc script, a message handler) stays unowned
     * and keeps its explicit unlock.
     */
    fun onLockChanged(locked: Boolean) {
        if (!locked) {
            queue.forEach { it.ownsLock = false }
            return
        }
        val task = running ?: return
        if (!task.terminated) {
            task.ownsLock = true
        }
    }

    /** Called when an owning task leaves the queue; the lock ends with its last owner. */
    protected fun releaseLockIfUnowned() {
        if (queue.none { it.ownsLock }) {
            releaseLock?.invoke()
        }
    }

    /**
     * Conditions run outside the coroutine completion boundary. Remove only a failed task so
     * one stale callback cannot abort every later pawn/world queue.
     */
    protected fun failTask(
        task: QueueTask,
        error: Throwable,
        remove: () -> Unit,
    ) {
        // Audit T-01: callers pass every non-fatal Throwable (a condition's TODO(), a StackOverflowError).
        logger.error("Error with queued task context ${task.ctx}; terminating it.", error)
        try {
            task.terminate()
        } catch (terminationError: Throwable) {
            terminationError.rethrowIfFatal()
            logger.error("Error terminating failed queued task context ${task.ctx}.", terminationError)
        } finally {
            remove()
            if (task.ownsLock) {
                task.ownsLock = false
                releaseLockIfUnowned()
            }
        }
    }

    companion object : KLogging()

    /** @return true when a removed task owned the pawn's lock. */
    private fun terminate(keepPersistent: Boolean): Boolean {
        var ownerRemoved = false
        val iterator = queue.iterator()
        while (iterator.hasNext()) {
            val task = iterator.next()
            if (keepPersistent && task.persistent) {
                continue
            }
            task.terminate()
            iterator.remove()
            if (task.ownsLock) {
                ownerRemoved = true
                task.ownsLock = false
            }
        }
        return ownerRemoved
    }
}
