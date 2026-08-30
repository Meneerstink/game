package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Persisted per-player PvP progress. */
val CURRENT_KILLSTREAK_ATTR = AttributeKey<Int>(persistenceKey = "current_killstreak")
val BEST_KILLSTREAK_ATTR = AttributeKey<Int>(persistenceKey = "best_killstreak")
val PK_POINTS_ATTR = AttributeKey<Int>(persistenceKey = "pk_points")

/**
 * Wilderness PvP killstreak tracking, a PK-points economy, and a simple cross-restart
 * killstreak leaderboard.
 *
 * PROJECT_PLAN SS14: victim loot is the primary reward; PK points are a limited *secondary*
 * reward, never raw GP for a kill. Provisional here: 2 points per kill, +1 per 5 streak the
 * killer is on, capped bonus - see IMPLEMENTATION_STATUS.md.
 *
 * ponytail: the leaderboard is a flat "name:streak" text file (`data/killstreak_leaderboard.txt`),
 * not a database - fine at the ~20-50 concurrent player target. Anti-farming is a basic
 * same-pair cooldown (below), not full IP/account-link detection.
 */
object Killstreaks {
    private const val POINTS_PER_KILL = 2
    private const val REPEAT_KILL_COOLDOWN_CYCLES = 500 // ~5 minutes; same pair scores once per window
    private val leaderboardFile = File("data/killstreak_leaderboard.txt")
    private val leaderboard = ConcurrentHashMap<String, Int>()

    @Volatile private var loaded = false

    // Basic anti-farming: last time (in System.currentTimeMillis) a given killer/victim pair
    // scored points, so two accounts can't trade kills back and forth for free points.
    private val lastScoredPairMillis = ConcurrentHashMap<String, Long>()

    private fun load() {
        if (loaded) return
        loaded = true
        if (leaderboardFile.exists()) {
            leaderboardFile.readLines().forEach { line ->
                val parts = line.split(":")
                if (parts.size == 2) {
                    val streak = parts[1].toIntOrNull()
                    if (streak != null) leaderboard[parts[0]] = streak
                }
            }
        }
    }

    private fun save() {
        runCatching {
            leaderboardFile.parentFile?.mkdirs()
            leaderboardFile.writeText(leaderboard.entries.joinToString("\n") { "${it.key}:${it.value}" })
        }
    }

    /** Call from [gg.rsmod.plugins.content.mechanics.death.DeathExecutor] on a Wilderness PvP death. */
    fun onWildernessKill(
        killer: Player,
        victim: Player,
    ) {
        load()

        val newStreak = (killer.attr[CURRENT_KILLSTREAK_ATTR] ?: 0) + 1
        killer.attr[CURRENT_KILLSTREAK_ATTR] = newStreak
        val best = killer.attr[BEST_KILLSTREAK_ATTR] ?: 0
        if (newStreak > best) {
            killer.attr[BEST_KILLSTREAK_ATTR] = newStreak
            leaderboard[killer.username] = newStreak
            save()
        }
        killer.filterableMessage("Killstreak: $newStreak.")

        victim.attr[CURRENT_KILLSTREAK_ATTR] = 0

        val pairKey = pairKey(killer.username, victim.username)
        val now = System.currentTimeMillis()
        val last = lastScoredPairMillis[pairKey]
        if (last == null || now - last > REPEAT_KILL_COOLDOWN_CYCLES * 600L) {
            lastScoredPairMillis[pairKey] = now
            val streakBonus = (newStreak / 5).coerceAtMost(10)
            val points = POINTS_PER_KILL + streakBonus
            killer.attr[PK_POINTS_ATTR] = (killer.attr[PK_POINTS_ATTR] ?: 0) + points
            killer.filterableMessage("You have been awarded $points PK points.")
        } else {
            killer.filterableMessage("No PK points awarded - you've fought this player too recently.")
        }
    }

    private fun pairKey(
        a: String,
        b: String,
    ): String = if (a < b) "$a|$b" else "$b|$a"

    fun top(n: Int): List<Pair<String, Int>> {
        load()
        return leaderboard.entries.sortedByDescending { it.value }.take(n).map { it.key to it.value }
    }
}
