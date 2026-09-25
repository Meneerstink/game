package gg.rsmod.game.service

import com.google.common.util.concurrent.ThreadFactoryBuilder
import mu.KLogging
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Audit T-11: one background thread for small persistence writes that used to block the game thread
 * (the Grand Exchange rewrote three JSON files on every offer mutation).
 *
 * The caller snapshots its data on the game thread - typically by serialising it to a string - and
 * submits only the disk write. Jobs run one at a time in submission order, so successive writes of the
 * same file can never be reordered and the last submitted state is the one on disk. A failing job is
 * logged and does not stop later jobs. [flush] waits for everything submitted so far, for shutdown and
 * for code that reads a file back.
 *
 * Player saves do not go through here.
 */
object BackgroundIo : KLogging() {
    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor(
            ThreadFactoryBuilder()
                .setNameFormat("background-io")
                .setDaemon(true)
                .setUncaughtExceptionHandler { t, e -> logger.error("Error with thread $t", e) }
                .build(),
        )

    /** Queues [job] behind every job submitted before it. [description] names it in the error log. */
    fun submit(
        description: String,
        job: () -> Unit,
    ) {
        executor.execute {
            try {
                job()
            } catch (e: Throwable) {
                logger.error("Background write failed: $description.", e)
            }
        }
    }

    /**
     * Blocks until every job submitted before this call has finished, or [timeoutMillis] passed.
     * Returns false on a timeout.
     */
    fun flush(timeoutMillis: Long = DEFAULT_FLUSH_TIMEOUT_MILLIS): Boolean {
        val marker = executor.submit(Runnable {})
        return try {
            marker.get(timeoutMillis, TimeUnit.MILLISECONDS)
            true
        } catch (e: TimeoutException) {
            logger.warn("Background writes still pending after {} ms.", timeoutMillis)
            false
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    private const val DEFAULT_FLUSH_TIMEOUT_MILLIS = 10_000L
}
