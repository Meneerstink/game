package gg.rsmod.game.model.timer

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * A system responsible for storing and exposing [TimerKey]s and their associated
 * values. These values represent game cycles left for the timer to "complete".
 *
 * @author Tom <rspsmods@gmail.com>
 */
class TimerMap {
    /**
     * N127 (server memory, 1.6 million static objects each with a TimerMap): the timer map and the paused set are made on first
     * use, so the millions of maps nothing ever arms cost two null fields instead of a HashMap and a HashSet each.
     */
    private var stored: HashMap<TimerKey, Int>? = null

    /** Reads see an empty map until the first timer is set. */
    private val view: Map<TimerKey, Int> get() = stored ?: EMPTY

    private fun writable(): HashMap<TimerKey, Int> = stored ?: HashMap<TimerKey, Int>(4).also { stored = it }

    /**
     * Keys that should not tick down on the owning pawn's per-cycle [gg.rsmod.game.model.entity.Pawn.timerCycle]
     * pass this cycle (Deadman PvP guards plan, 2026-09-16: the PK skull timer pauses while the
     * player is in an instanced area or has been standing on the same tile for about a minute).
     * Deliberately a small, generic, opt-in set on the existing timer map rather than a new
     * parallel timer system: every other [TimerKey] consumer is unaffected unless it explicitly
     * pauses a key. Not persisted - pause state is re-derived from live player state every cycle
     * by whichever plugin owns the paused key, so it does not need to survive logout/reconnect.
     */
    private var pausedKeys: HashSet<TimerKey>? = null

    fun pause(key: TimerKey) {
        (pausedKeys ?: HashSet<TimerKey>(2).also { pausedKeys = it }).add(key)
    }

    fun resume(key: TimerKey) {
        pausedKeys?.remove(key)
    }

    fun isPaused(key: TimerKey): Boolean = pausedKeys?.contains(key) == true

    operator fun get(key: TimerKey): Int = view[key]!!

    operator fun set(
        key: TimerKey,
        value: Int,
    ): TimerMap {
        writable()[key] = value
        return this
    }

    fun has(key: TimerKey): Boolean = view.getOrDefault(key, 0) > 0

    fun exists(key: TimerKey): Boolean = view.containsKey(key)

    fun remove(key: TimerKey) {
        stored?.remove(key)
        pausedKeys?.remove(key)
    }

    fun clear() {
        stored?.clear()
        pausedKeys?.clear()
    }

    fun removeIf(predicate: (TimerKey) -> Boolean) {
        val iterator = (stored ?: return).iterator()
        while (iterator.hasNext()) {
            val attr = iterator.next()
            if (predicate(attr.key)) {
                iterator.remove()
            }
        }
    }

    fun toPersistentTimers(): List<PersistentTimer> =
        view.filter { it.key.persistenceKey != null }.map {
            PersistentTimer(
                it.key.persistenceKey,
                it.key.tickOffline,
                it.key.tickForward,
                it.key.resetOnDeath,
                it.value,
                System.currentTimeMillis(),
                it.key.removeOnZero,
            )
        }

    /** The live map (callers change entry values in place); made here when it does not exist yet. */
    fun getTimers(): MutableMap<TimerKey, Int> = writable()

    val isNotEmpty: Boolean
        get() = view.isNotEmpty()

    private companion object {
        val EMPTY: Map<TimerKey, Int> = emptyMap()
    }

    /**
     * Represents a persistent timer that will be saved through player sessions.
     */
    data class PersistentTimer(
        @JsonProperty("identifier") val identifier: String? = null,
        @JsonProperty("tickOffline") val tickOffline: Boolean = true,
        @JsonProperty("tickForward") val tickForward: Boolean = false,
        @JsonProperty("resetOnDeath") val resetOnDeath: Boolean = false,
        @JsonProperty("timeLeft") val timeLeft: Int,
        @JsonProperty("currentMs") val currentMs: Long,
        @JsonProperty("removeOnZero") val removeOnZero: Boolean = true,
    )
}
