package gg.rsmod.game.model.queue.impl

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.model.queue.QueueTaskSet
import gg.rsmod.game.model.queue.TaskPriority
import gg.rsmod.game.task.rethrowIfFatal
import kotlin.coroutines.resume

/**
 * A [QueueTaskSet] implementation for [gg.rsmod.game.model.entity.Pawn]s.
 * Each [gg.rsmod.game.model.queue.QueueTask] is handled one at a time.
 *
 * @author Tom <rspsmods@gmail.com>
 */
class PawnQueueTaskSet : QueueTaskSet() {
    override fun cycle() {
        // Most NPCs have no queue at all. Keep the common path allocation-free; the old
        // ArrayList was created for every pawn on every tick even when there was nothing to
        // compare in the persistent-task pass below.
        var firstCycled: QueueTask? = null
        var additionalCycled: ArrayList<QueueTask>? = null
        while (true) {
            val task = queue.peekFirst() ?: break

            if (isPaused(task)) {
                break
            }

            if (firstCycled == null) {
                firstCycled = task
            } else {
                (additionalCycled ?: ArrayList<QueueTask>(2).also { additionalCycled = it }).add(task)
            }
            if (!step(task)) {
                /*
                 * Since this task is complete, let's handle any upcoming
                 * task now instead of waiting until next cycle.
                 */
                continue
            }
            break
        }

        /*
         * RC-1: a persistent task (the combat loop) keeps its own cadence while another task
         * holds the head - a prayer toggle waiting out a lock, a Soul Split follow-up, a dialog.
         * Only the head task used to run, so every such task froze combat behind it.
         */
        if (queue.size > 1) {
            for (task in queue.toTypedArray()) {
                if (
                    !task.persistent ||
                        task.terminated ||
                        task === firstCycled ||
                        additionalCycled?.any { it === task } == true ||
                        isPaused(task)
                ) {
                    continue
                }
                step(task)
            }
        }
    }

    private fun isPaused(task: QueueTask): Boolean =
        task.priority == TaskPriority.STANDARD && task.ctx is Player && task.ctx.hasMenuOpen()

    /**
     * Runs one cycle of [task]; returns true while it is still suspended.
     */
    private fun step(task: QueueTask): Boolean {
        val previous = running
        running = task
        try {
            try {
                if (!task.invoked) {
                    task.invoked = true
                    task.coroutine.resume(Unit)
                }

                task.cycle()
            } catch (e: Throwable) {
                // Audit T-01: every non-fatal throwable fails only this task.
                e.rethrowIfFatal()
                failTask(task, e) { queue.remove(task) }
                return false
            }
        } finally {
            running = previous
        }

        if (task.suspended()) {
            return true
        }

        /*
         * Task is no longer in a suspended state, which means its job is
         * complete - finished normally, or ended by a plugin exception.
         */
        queue.remove(task)

        /*
         * RCV-012 B11: a lock the task owned ends with it, including a `lock()` whose trailing `unlock()` never ran.
         */
        if (task.ownsLock) {
            task.ownsLock = false
            if (!task.lock) {
                gg.rsmod.game.model.AvTrace.log { "lock released at task end (lock set in task without unlock) ctx=${task.ctx}" }
            }
            releaseLockIfUnowned()
        }
        return false
    }

    private fun Player.hasMenuOpen(): Boolean = world.plugins.isMenuOpened(this)
}
