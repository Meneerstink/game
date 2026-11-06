package gg.rsmod.plugins.content.activity.toa

/** OSRS Wiki ToA, Strategies, 01-route.tsv and 21-supplies-dood.tsv. Tick values are game cycles, never wall time. */
class ToaRaidProgress(
    val settings: ToaRaidSettings,
    private val event: (ToaRoomEvent, ToaRoomRun) -> Unit = { _, _ -> },
) {
    private val cleared = linkedSetOf<ToaPath>()
    val completedPaths: Set<ToaPath> get() = cleared.toSet()
    private val challenges = mutableSetOf<ToaPath>()
    var activePath: ToaPath? = null
        private set
    var current: ToaRoomRun? = null
        private set
    var failedAttempts = 0
        private set
    var failed = false
        private set
    var finished = false
        private set
    var startCycle: Int? = null
        private set
    var finishCycle: Int? = null
        private set
    private var generation = 0
    private var pendingWipe: Pair<ToaRoomRun, Int>? = null
    private var closed = false
    val wardensUnlocked: Boolean get() = cleared.size == ToaPath.values().size
    fun challengeCleared(path: ToaPath): Boolean = path in challenges

    fun elapsedCycles(cycle: Int): Int = startCycle?.let { ((finishCycle ?: cycle) - it).coerceAtLeast(0) } ?: 0

    fun challenge(path: ToaPath, cycle: Int): ToaRoomRun? {
        if (closed || failed || finished || path in cleared || (activePath != null && activePath != path)) return null
        val key = ToaRoomKey(path, false)
        val previous = current
        if (previous?.key == key && previous.status == ToaRoomStatus.RUNNING) return previous
        if (path in challenges) return null
        activePath = path
        if (startCycle == null) startCycle = cycle
        return start(key)
    }

    fun boss(path: ToaPath): ToaRoomRun? {
        if (closed || failed || finished || activePath != path || path !in challenges || path in cleared) return null
        val key = ToaRoomKey(path, true)
        if (current?.key == key && current?.status == ToaRoomStatus.RUNNING) return current
        return start(key)
    }

    fun wardens(): ToaRoomRun? {
        if (closed || !wardensUnlocked || failed || finished) return null
        val key = ToaRoomKey(null, true)
        if (current?.key == key && current?.status == ToaRoomStatus.RUNNING) return current
        return start(key)
    }

    private fun start(key: ToaRoomKey): ToaRoomRun {
        val run = ToaRoomRun(key, ++generation, this)
        current = run
        event(ToaRoomEvent.START, run)
        return run
    }

    fun clear(run: ToaRoomRun, cycle: Int): Boolean {
        if (current !== run || run.status != ToaRoomStatus.RUNNING || failed || finished) return false
        if (pendingWipe?.let { it.first === run && it.second < cycle } == true) return false
        run.status = ToaRoomStatus.CLEARED
        pendingWipe = null
        val path = run.key.path
        when {
            path == null -> { finished = true; finishCycle = cycle }
            run.key.boss -> { cleared += path; activePath = null }
            else -> challenges += path
        }
        event(ToaRoomEvent.CLEAR, run)
        return true
    }

    /** Settle only after the whole death/clear tick; clear wins in either callback order. */
    fun wipe(run: ToaRoomRun, cycle: Int): Boolean {
        if (current !== run || run.status != ToaRoomStatus.RUNNING || failed || finished || pendingWipe != null) return false
        pendingWipe = run to cycle
        return true
    }

    fun cycle(cycle: Int): Boolean {
        val (run, at) = pendingWipe ?: return false
        if (cycle <= at) return false
        pendingWipe = null
        if (current !== run || run.status != ToaRoomStatus.RUNNING) return false
        failedAttempts++
        failed = settings.maximumAttempts?.let { failedAttempts >= it } ?: false
        run.status = ToaRoomStatus.RESET
        event(ToaRoomEvent.WIPE, run)
        event(ToaRoomEvent.RESET, run)
        current = null
        return true
    }

    fun close() {
        if (closed) return
        closed = true
        current?.takeIf { it.status == ToaRoomStatus.RUNNING }?.status = ToaRoomStatus.RESET
        current = null
        pendingWipe = null
    }
}

data class ToaRoomKey(val path: ToaPath?, val boss: Boolean) {
    init { require(path != null || boss) }
    val wardens: Boolean get() = path == null
}

enum class ToaRoomStatus { RUNNING, CLEARED, RESET }
enum class ToaRoomEvent { START, CLEAR, WIPE, RESET }

/** A generation token prevents a delayed projectile/death from completing or wiping a later retry. */
class ToaRoomRun internal constructor(val key: ToaRoomKey, val generation: Int, private val progress: ToaRaidProgress) {
    var status = ToaRoomStatus.RUNNING
        internal set
    fun clear(cycle: Int): Boolean = progress.clear(this, cycle)
    fun wipe(cycle: Int): Boolean = progress.wipe(this, cycle)
}

/** Later puzzle/boss cards implement this contract. Empty rooms never award clears or spawn pretend bosses. */
interface ToaRoom {
    fun start(run: ToaRoomRun) {}
    fun clear(run: ToaRoomRun) {}
    fun wipe(run: ToaRoomRun) {}
    fun reset(run: ToaRoomRun) {}
}
