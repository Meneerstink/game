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
 * not a database - fine at the ~20-50 concurrent player target.
 *
 * Audit D-08: a kill only raises the streak and pays PK / Deadman Points when [ValidPkKill] judges it
 * [ValidPkKill.Verdict.VALID] - the same verdict the emblems use: 1M+ risk, no same address/machine, the persisted
 * pair and per-victim cooldowns, the daily cap and no fed kills. The old in-memory 5-minute pair cooldown (wiped by
 * every restart) is gone; the persisted [ValidPkKill] cooldowns replace it.
 */
object Killstreaks {
    private const val POINTS_PER_KILL = 2
    private val leaderboardFile = File("data/killstreak_leaderboard.txt")
    private val leaderboard = ConcurrentHashMap<String, Int>()

    @Volatile private var loaded = false

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
            gg.rsmod.util.io.AtomicFiles.writeText(leaderboardFile, leaderboard.entries.joinToString("\n") { "${it.key}:${it.value}" })
        }
    }

    /**
     * Legacy call from [gg.rsmod.plugins.content.mechanics.death.DeathExecutor] on a Wilderness PvP death, made before
     * the kill is judged. Audit D-08: it only ends the victim's streak now; the killer is credited by [onJudgedKill]
     * (from `DeadmanEmblem.onPvpDeath`, which owns the single [ValidPkKill.evaluate] of the death). Safe to drop.
     */
    fun onWildernessKill(
        killer: Player,
        victim: Player,
    ) {
        if (killer !== victim) victim.attr[CURRENT_KILLSTREAK_ATTR] = 0
    }

    /**
     * Audit D-08: the killstreak and point reward for one resolved PvP death, with the death's single [ValidPkKill]
     * [verdict]. The victim's streak always ends; the killer's streak and points only move on a valid kill.
     */
    fun onJudgedKill(
        killer: Player,
        victim: Player,
        verdict: ValidPkKill.Verdict,
    ) {
        if (killer === victim) return
        victim.attr[CURRENT_KILLSTREAK_ATTR] = 0
        if (!verdict.valid) {
            killer.filterableMessage("No PK or Deadman Points awarded - ${verdict.reason}.")
            return
        }
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

        val streakBonus = (newStreak / 5).coerceAtMost(10)
        val points = POINTS_PER_KILL + streakBonus
        killer.attr[PK_POINTS_ATTR] = (killer.attr[PK_POINTS_ATTR] ?: 0) + points
        killer.filterableMessage("You have been awarded $points PK points.")
        // Deadman Points (store currency, earned only through Deadman/PvP kills). PROVISIONAL amount.
        val deadman = gg.rsmod.plugins.content.mechanics.store.StoreCatalogue.Currency.DEADMAN.attr
        val deadmanPoints = DEADMAN_POINTS_PER_KILL + streakBonus
        killer.attr[deadman] = (killer.attr[deadman] ?: 0) + deadmanPoints
        killer.filterableMessage("You have been awarded $deadmanPoints Deadman Points.")
    }

    const val DEADMAN_POINTS_PER_KILL = 10

    /** True when both players are connected from the same IP address or machine (one person farming kills on a second account). */
    fun sameConnectionAddress(
        a: Player,
        b: Player,
    ): Boolean = ValidPkKill.sameAddress(a, b)

    fun top(n: Int): List<Pair<String, Int>> {
        load()
        return leaderboard.entries.sortedByDescending { it.value }.take(n).map { it.key to it.value }
    }
}
