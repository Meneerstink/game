package gg.rsmod.game.model.queue.impl

import gg.rsmod.game.model.queue.QueueTaskSet
import gg.rsmod.game.task.rethrowIfFatal
import kotlin.coroutines.resume

/**
 * A [QueueTaskSet] implementation for [gg.rsmod.game.model.World].
 * All [gg.rsmod.game.model.queue.QueueTask]s are handled every tick.
 *
 * @author Tom <rspsmods@gmail.com>
 */
class WorldQueueTaskSet : QueueTaskSet() {
    /*
     * Iterates a snapshot: a world task that queues another world task while it runs (e.g. a Deadman
     * breach firing a projectile, 2026-09-19 live log "ConcurrentModificationException ... Error cycling
     * world queues") used to invalidate the live iterator, and failTask then killed the running task.
     * A task queued during a cycle now starts on the next cycle.
     */
    override fun cycle() {
        for (task in ArrayList(queue)) {
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
                continue
            }

            if (!task.suspended()) {
                /*
                 * Task is no longer in a suspended state, which means its job is
                 * complete.
                 */
                queue.remove(task)
            }
        }
    }
}
