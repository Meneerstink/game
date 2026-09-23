package gg.rsmod.game.service

import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Owns the hand-off between worker threads and the single game-cycle thread.
 *
 * A cycle drains one stable batch while submissions are locked out. Jobs offered
 * after that hand-off stay queued for the next cycle; none can be removed by a
 * trailing clear after the consumer has started iterating.
 */
internal class GameThreadJobQueue {
    private val lock = Any()
    private val jobs = ConcurrentLinkedQueue<() -> Unit>()

    fun offer(job: () -> Unit) {
        synchronized(lock) {
            jobs.offer(job)
        }
    }

    fun drain(): List<() -> Unit> = synchronized(lock) {
        if (jobs.isEmpty()) {
            return@synchronized emptyList()
        }

        val batch = ArrayList<() -> Unit>(jobs.size)
        while (true) {
            val job = jobs.poll() ?: break
            batch += job
        }
        batch
    }
}
