package gg.rsmod.game.model.timer

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * A system responsible for storing and exposing [TimerKey]s and their associated
 * values. These values represent game cycles left for the timer to "complete".
 *
 * @author Tom <rspsmods@gmail.com>
 */
class TimerMap {
    private var timers: MutableMap<TimerKey, Int> = HashMap(0)

    /**
     * Keys that should not tick down on the owning pawn's per-cycle [gg.rsmod.game.model.entity.Pawn.timerCycle]
     * pass this cycle (Deadman PvP guards plan, 2026-09-16: the PK skull timer pauses while the
     * player is in an instanced area or has been standing on the same tile for about a minute).
     * Deliberately a small, generic, opt-in set on the existing timer map rather than a new
     * parallel timer system: every other [TimerKey] consumer is unaffected unless it explicitly
     * pauses a key. Not persisted - pause state is re-derived from live player state every cycle
     * by whichever plugin owns the paused key, so it does not need to survive logout/reconnect.
     */
    private val pausedKeys: MutableSet<TimerKey> = HashSet(0)

    fun pause(key: TimerKey) {
        pausedKeys.add(key)
    }

    fun resume(key: TimerKey) {
        pausedKeys.remove(key)
    }

    fun isPaused(key: TimerKey): Boolean = key in pausedKeys

    operator fun get(key: TimerKey): Int = timers[key]!!

    operator fun set(
        key: TimerKey,
        value: Int,
    ): TimerMap {
        timers[key] = value
        return this
    }

    fun has(key: TimerKey): Boolean = timers.getOrDefault(key, 0) > 0

    fun exists(key: TimerKey): Boolean = timers.containsKey(key)

    fun remove(key: TimerKey) {
        if (timers.containsKey(key)) {
            timers.remove(key)
        }
        pausedKeys.remove(key)
    }

    fun clear() {
        timers.clear()
        pausedKeys.clear()
    }

    fun removeIf(predicate: (TimerKey) -> Boolean) {
        val iterator = timers.iterator()
        while (iterator.hasNext()) {
            val attr = iterator.next()
            if (predicate(attr.key)) {
                iterator.remove()
            }
        }
    }

    fun toPersistentTimers(): List<PersistentTimer> =
        timers.filter { it.key.persistenceKey != null }.map {
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

    fun getTimers(): MutableMap<TimerKey, Int> = timers

    val isNotEmpty: Boolean
        get() = timers.isNotEmpty()

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
