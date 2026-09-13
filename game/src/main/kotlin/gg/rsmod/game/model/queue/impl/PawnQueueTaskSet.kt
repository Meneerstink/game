package gg.rsmod.game.model.queue.impl

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.model.queue.QueueTaskSet
import gg.rsmod.game.model.queue.TaskPriority
import kotlin.coroutines.resume

/**
 * A [QueueTaskSet] implementation for [gg.rsmod.game.model.entity.Pawn]s.
 * Each [gg.rsmod.game.model.queue.QueueTask] is handled one at a time.
 *
 * @author Tom <rspsmods@gmail.com>
 */
class PawnQueueTaskSet : QueueTaskSet() {
    override fun cycle() {
        val cycled = ArrayList<QueueTask>(2)
        while (true) {
            val task = queue.peekFirst() ?: break

            if (isPaused(task)) {
                break
            }

            cycled.add(task)
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
                if (!task.persistent || task.terminated || cycled.any { it === task } || isPaused(task)) {
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
        if (!task.invoked) {
            task.invoked = true
            task.coroutine.resume(Unit)
        }

        task.cycle()

        if (task.suspended()) {
            return true
        }

        /*
         * Task is no longer in a suspended state, which means its job is
         * complete.
         */
        queue.remove(task)

        /*
         * If the task locked the player, then unlock them on complete
         */
        if (task.lock && task.ctx is Player) {
            task.ctx.unlock()
        }
        return false
    }

    private fun Player.hasMenuOpen(): Boolean = world.plugins.isMenuOpened(this)
}
