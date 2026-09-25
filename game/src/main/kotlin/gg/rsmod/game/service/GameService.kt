package gg.rsmod.game.service

import com.google.common.util.concurrent.ThreadFactoryBuilder
import gg.rsmod.game.Server
import gg.rsmod.game.message.MessageDecoderSet
import gg.rsmod.game.message.MessageEncoderSet
import gg.rsmod.game.message.MessageStructureSet
import gg.rsmod.game.model.AvTrace
import gg.rsmod.game.model.World
import gg.rsmod.game.task.*
import gg.rsmod.game.task.rethrowIfFatal
import gg.rsmod.game.task.sequential.SequentialNpcCycleTask
import gg.rsmod.game.task.sequential.SequentialPlayerCycleTask
import gg.rsmod.game.task.sequential.SequentialPlayerPostCycleTask
import gg.rsmod.game.task.sequential.SequentialSynchronizationTask
import gg.rsmod.util.ServerProperties
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import mu.KLogging
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * The service used to schedule and execute logic needed for the game to run properly.
 *
 * @author Tom <rspsmods@gmail.com>
 */
class GameService : Service {
    /**
     * The associated world with our current game.
     */
    lateinit var world: World

    /**
     * The max amount of incoming [gg.rsmod.game.message.Message]s that can be
     * handled per cycle.
     */
    var maxMessagesPerCycle = 0

    /**
     * The scheduler for our game cycle logic as well as coroutine dispatcher.
     */
    private val executor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor(
            ThreadFactoryBuilder()
                .setNameFormat("game-context")
                .setUncaughtExceptionHandler { t, e -> logger.error("Error with thread $t", e) }
                .build(),
        )

    /**
     * Audit T-01: a daemon thread that reports a game loop which stopped completing cycles (an endless
     * loop in a plugin, a deadlock, or a loop that died) together with the game thread's stack.
     */
    private val watchdog: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor(
            ThreadFactoryBuilder()
                .setNameFormat("game-watchdog")
                .setDaemon(true)
                .build(),
        )

    /**
     * Audit T-01: ticks completed since boot (also the skipped ones before [loaded]); written only by the
     * game thread and read by the [watchdog].
     */
    @Volatile
    private var completedTicks = 0L

    /** Audit T-01: the thread running the game cycle, for the [watchdog]'s stack dump. */
    @Volatile
    private var gameThread: Thread? = null

    /**
     * Audit T-08: when the next tick is due, on the [System.nanoTime] clock. See [scheduleNextTick].
     */
    private var nextTickNanos = 0L

    /**
     * A list of jobs that will be executed on the next cycle after being
     * submitted.
     */
    private val gameThreadJobs = GameThreadJobQueue()

    /**
     * The amount of ticks that have gone by since the last debug log.
     */
    private var debugTick = 0

    /**
     * The total time, in milliseconds, that the past [TICKS_PER_DEBUG_LOG]
     * cycles have taken to complete.
     */
    private var cycleTime = 0

    /**
     * Monotonic start time of the previous game-cycle invocation. This is kept
     * separately from the measured cycle work time so a scheduler/GC pause
     * between invocations can be distinguished from slow game logic.
     */
    private var previousCycleStartNanos = 0L

    /**
     * The Kotlin Coroutine dispatcher to submit suspendable plugins.
     */
    val dispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()

    /**
     * The amount of time, in milliseconds, that each [GameTask] has taken away
     * from the game cycle.
     */
    private val taskTimes = Object2LongOpenHashMap<Class<GameTask>>()

    /**
     * The amount of time, in milliseconds, that [SequentialPlayerCycleTask]
     * has taken for each [gg.rsmod.game.model.entity.Player].
     */
    internal val playerTimes = Object2LongOpenHashMap<String>()

    /**
     * The amount of active [gg.rsmod.game.model.queue.QueueTask]s throughout
     * the [gg.rsmod.game.model.entity.Player]s.
     */
    internal var totalPlayerQueues = 0

    /**
     * The amount of active [gg.rsmod.game.model.queue.QueueTask]s throughout
     * the [gg.rsmod.game.model.entity.Npc]s.
     */
    internal var totalNpcQueues = 0

    /**
     * The amount of active [gg.rsmod.game.model.queue.QueueTask]s throughout
     * the [gg.rsmod.game.model.World].
     */
    internal var totalWorldQueues = 0

    /**
     * A list of tasks that will be executed per game cycle.
     */
    private val tasks = mutableListOf<GameTask>()

    internal val messageStructures = MessageStructureSet()

    internal val messageEncoders = MessageEncoderSet()

    internal val messageDecoders = MessageDecoderSet()

    /**
     * This flag indicates that the game cycles should pause.
     *
     * Should not be used without proper knowledge of how it works!
     */
    internal var pause = false

    /**
     * Boot gate (owner 2026-09-20 "gameserver is still hanging").
     *
     * [init] is called from `World.loadServices`, which runs long before the boot thread has
     * finished `DefinitionSet.loadRegions`, `PluginRepository.init` (static spawns) and
     * `World.postLoad` (`on_world_init`). All three mutate `World.chunks`, whose backing
     * `Object2ObjectOpenHashMap` is not thread-safe, so a cycle running [ChunkCreationTask] on the
     * game thread at the same time corrupted the map: once its chunk count crossed a rehash
     * boundary the boot thread died with `ArrayIndexOutOfBoundsException: Index 8192 out of bounds
     * for length 4097` inside `ChunkSet.get`. The game thread survived, so the server kept cycling
     * forever while `Server.startGame` never reached the game-port bind - a running process that
     * nothing can connect to.
     *
     * The world is single-threaded by design; this flag enforces that during boot too. Cycles are
     * skipped until the boot thread has finished loading, which costs nothing (there are no players
     * yet) and removes the race entirely.
     */
    @Volatile
    internal var loaded = false

    override fun init(
        server: Server,
        world: World,
        serviceProperties: ServerProperties,
    ) {
        this.world = world
        populateTasks()
        maxMessagesPerCycle = serviceProperties.getOrDefault("messages-per-cycle", 30)
        /*
         * Audit T-08: each tick schedules the next one (see scheduleNextTick) instead of
         * scheduleAtFixedRate, which ran every missed tick back to back after a slow cycle (a 3 s cycle
         * was followed by a burst of about four in which nobody could eat or switch prayers) and, on any
         * Throwable escaping a run, silently cancelled all future ticks (Audit T-01).
         */
        nextTickNanos = System.nanoTime()
        executor.schedule(Runnable { tick() }, 0, TimeUnit.NANOSECONDS)
        startWatchdog()
    }

    override fun postLoad(
        server: Server,
        world: World,
    ) {
    }

    override fun terminate(
        server: Server,
        world: World,
    ) {
    }

    private fun populateTasks() {
        tasks.addAll(
            arrayOf(
                MessageHandlerTask(),
                QueueHandlerTask(),
                SequentialPlayerCycleTask(),
                ChunkCreationTask(),
                WorldRemoveTask(),
                SequentialNpcCycleTask(),
                SequentialSynchronizationTask(),
                SequentialPlayerPostCycleTask(),
            ),
        )
    }

    override fun bindNet(
        server: Server,
        world: World,
    ) {
    }

    /**
     * Submits a job that must be performed on the game-thread.
     */
    fun submitGameThreadJob(job: Function0<Unit>) {
        gameThreadJobs.offer(job)
    }

    /**
     * Audit T-01/T-08: runs one game cycle and always schedules the next one. Nothing that escapes
     * [cycle] - not even a fatal JVM error - can end the game loop silently any more.
     */
    private fun tick() {
        gameThread = Thread.currentThread()
        try {
            cycle()
        } catch (t: Throwable) {
            logger.error("Game cycle aborted by an uncaught error; the next cycle is still scheduled.", t)
        } finally {
            completedTicks++
            scheduleNextTick()
        }
    }

    /**
     * Audit T-08: fixed period without catch-up - `next = max(previous deadline + period, now)`. After a
     * cycle that overran, the next one starts right away and the ones after it keep the normal spacing;
     * missed ticks are dropped instead of being run as a burst.
     */
    private fun scheduleNextTick() {
        val period = TimeUnit.MILLISECONDS.toNanos(world.gameContext.cycleTime.toLong())
        val now = System.nanoTime()
        nextTickNanos = nextCycleDeadline(nextTickNanos, period, now)
        try {
            executor.schedule(Runnable { tick() }, nextTickNanos - now, TimeUnit.NANOSECONDS)
        } catch (e: RejectedExecutionException) {
            logger.error("The game scheduler rejected the next cycle; the game loop has stopped.", e)
        }
    }

    /**
     * Audit T-01: checks every [WATCHDOG_INTERVAL_SECONDS] whether a tick completed and logs, once per
     * stall, the game thread's stack when none did for [WATCHDOG_STALL_SECONDS].
     */
    private fun startWatchdog() {
        var lastSeenTicks = -1L
        var lastProgressNanos = System.nanoTime()
        var reported = false
        watchdog.scheduleWithFixedDelay(
            Runnable {
                try {
                    val ticks = completedTicks
                    val now = System.nanoTime()
                    if (ticks != lastSeenTicks) {
                        if (reported) {
                            logger.warn("Game loop recovered after {} s without a completed cycle.", (now - lastProgressNanos) / 1_000_000_000L)
                        }
                        lastSeenTicks = ticks
                        lastProgressNanos = now
                        reported = false
                    } else if (!reported && now - lastProgressNanos >= TimeUnit.SECONDS.toNanos(WATCHDOG_STALL_SECONDS)) {
                        reported = true
                        val thread = gameThread
                        val stack = thread?.stackTrace?.joinToString(separator = "\n\tat ", prefix = "\tat ") ?: "(no game thread yet)"
                        logger.error(
                            "Game loop stalled: no cycle completed for {} s (world cycle {}). Game thread {} ({}):\n{}",
                            (now - lastProgressNanos) / 1_000_000_000L,
                            world.currentCycle,
                            thread?.name,
                            thread?.state,
                            stack,
                        )
                    }
                } catch (t: Throwable) {
                    logger.error("Game watchdog check failed.", t)
                }
            },
            WATCHDOG_INTERVAL_SECONDS,
            WATCHDOG_INTERVAL_SECONDS,
            TimeUnit.SECONDS,
        )
    }

    private fun cycle() {
        if (!loaded || pause) {
            return
        }
        val start = System.currentTimeMillis()
        val startNanos = System.nanoTime()
        val previousStartNanos = previousCycleStartNanos
        previousCycleStartNanos = startNanos
        val cycleGapMs =
            if (previousStartNanos == 0L) {
                -1.0
            } else {
                (startNanos - previousStartNanos) / 1_000_000.0
            }

        /*
         * Clear the time it has taken to complete [GameTask]s from last cycle.
         */
        taskTimes.clear()
        playerTimes.clear()

        /*
         * Execute any logic jobs that were submitted.
         */
        gameThreadJobs.drain().forEach { job ->
            try {
                job()
            } catch (e: Throwable) {
                // Audit T-01: any non-fatal throwable, not only Exception.
                e.rethrowIfFatal()
                logger.error("Error executing game-thread job.", e)
            }
        }

        /*
         * Go over the [tasks] and execute their logic. Log the time it took
         * each [GameTask] to complete. Some of the tasks may also calculate
         * their time for each player so that we can have the amount of time,
         * in milliseconds, that each player took to perform certain tasks.
         */
        tasks.forEach { task ->
            val taskStart = System.currentTimeMillis()
            try {
                task.execute(world, this)
            } catch (e: Throwable) {
                // Audit T-01: any non-fatal throwable, not only Exception; a fatal one aborts this cycle
                // and is logged by tick(), which still schedules the next cycle.
                e.rethrowIfFatal()
                logger.error("Error with task ${task.javaClass.simpleName}.", e)
            }
            taskTimes[task.javaClass] = System.currentTimeMillis() - taskStart
        }

        try {
            world.cycle()
        } catch (e: Throwable) {
            // Keep the game loop alive when world maintenance fails (Audit T-01: any non-fatal throwable).
            e.rethrowIfFatal()
            logger.error("Error with world cycle.", e)
        }

        /*
         * Calculate the time, in milliseconds, it took for this cycle to complete
         * and add it to [cycleTime].
         */
        cycleTime += (System.currentTimeMillis() - start).toInt()

        val workMs = (System.nanoTime() - startNanos) / 1_000_000.0
        val expectedCycleMs = world.gameContext.cycleTime.toDouble()
        if (cycleGapMs > expectedCycleMs + CYCLE_TIMING_TOLERANCE_MS || workMs > expectedCycleMs) {
            AvTrace.log {
                "cycle timing cycle=${world.currentCycle} expectedMs=$expectedCycleMs " +
                    "gapMs=$cycleGapMs workMs=$workMs " +
                    "schedulerDelayMs=${(cycleGapMs - expectedCycleMs).coerceAtLeast(0.0)} " +
                    "tasks=${taskTimes.toList().sortedByDescending { (_, value) -> value }.toMap()}"
            }
        }

        if (debugTick++ >= TICKS_PER_DEBUG_LOG) {
            val freeMemory = Runtime.getRuntime().freeMemory()
            val totalMemory = Runtime.getRuntime().totalMemory()
            val maxMemory = Runtime.getRuntime().maxMemory()

            /*
             * Description:
             *
             * Cycle time:
             * the average time it took for a game cycle to
             * complete the last [TICKS_PER_DEBUG_LOG] game cycles.
             *
             * Entities:
             * The amount of entities in the world.
             * p: players
             * n: npcs
             *
             * Map:
             * The amount of map entities that are currently active.
             * c: chunks [gg.rsmod.game.model.region.Chunk]
             * r: regions
             * i: instanced maps [gg.rsmod.game.model.instance.InstancedMap]
             *
             * Queues:
             * The amount of plugins that are being executed on this exact
             * game cycle.
             * p: players
             * n: npcs
             * w: world
             *
             * Mem Usage:
             * Memory usage statistics.
             * U: used memory, in megabytes
             * R: reserved memory, in megabytes
             * M: max memory available, in megabytes
             */
            logger.info(
                "[Cycle time: {}ms] [Entities: {}p / {}n] [Map: {}c / {}r / {}i] [Queues: {}p / {}n / {}w] [Mem usage: U={}MB / R={}MB / M={}MB].",
                cycleTime / TICKS_PER_DEBUG_LOG,
                world.players.count(),
                world.npcs.count(),
                world.chunks.getActiveChunkCount(),
                world.chunks.getActiveRegionCount(),
                world.instanceAllocator.activeMapCount,
                totalPlayerQueues,
                totalNpcQueues,
                totalWorldQueues,
                (totalMemory - freeMemory) / (1024 * 1024),
                totalMemory / (1024 * 1024),
                maxMemory / (1024 * 1024),
            )
            debugTick = 0
            cycleTime = 0
        }

        val freeTime = world.gameContext.cycleTime - (System.currentTimeMillis() - start)
        if (freeTime < 0) {
            /*
             * If the cycle took more than [GameContext.cycleTime]ms, we log the
             * occurrence as well as the time each [GameTask] took to complete,
             * as well as how long each [gg.rsmod.game.model.entity.Player] took
             * to process this cycle.
             */
            logger.error {
                "Cycle took longer than expected: ${(-freeTime) + world.gameContext.cycleTime}ms / ${world.gameContext.cycleTime}ms!"
            }
            logger.error { taskTimes.toList().sortedByDescending { (_, value) -> value }.toMap() }
            logger.error { playerTimes.toList().sortedByDescending { (_, value) -> value }.toMap() }
        }
    }

    companion object : KLogging() {
        /**
         * The amount of ticks that must go by for debug info to be logged.
         */
        private const val TICKS_PER_DEBUG_LOG = 10

        private const val CYCLE_TIMING_TOLERANCE_MS = 50.0

        /** Audit T-01: how often the watchdog checks the game loop. */
        private const val WATCHDOG_INTERVAL_SECONDS = 5L

        /** Audit T-01: how long the game loop may go without a completed cycle before it is reported. */
        private const val WATCHDOG_STALL_SECONDS = 10L

        /**
         * Audit T-08: the deadline of the next tick - one [periodNanos] after the previous deadline, or
         * [nowNanos] when that is already past. Missed ticks are dropped instead of run back to back.
         */
        internal fun nextCycleDeadline(
            previousDeadlineNanos: Long,
            periodNanos: Long,
            nowNanos: Long,
        ): Long = maxOf(previousDeadlineNanos + periodNanos, nowNanos)
    }
}
