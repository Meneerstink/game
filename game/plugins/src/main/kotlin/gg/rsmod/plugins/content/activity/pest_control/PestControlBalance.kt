package gg.rsmod.plugins.content.activity.pest_control

import gg.rsmod.game.model.Tile

/**
 * N232, owner 2026-10-31: fewer pests and findable spinners on EVERY boat, scaled to the remaining players.
 * Baseline: https://oldschool.runescape.wiki/w/Pest_Control (5..25 players, tiered pests, spinners heal portals).
 * Small-team numbers below are the requested server adaptation, not OSRS values. Tier still selects NPC stats/portal HP.
 *
 *                 players  wave ticks  pests/portal  total cap  knight shifters  spinners/portal  damage share  HP/6s
 *                 1        70          1             12         1                1                1/6          4
 *                 2        55          1             20         1                1                .30          8
 *                 3        45          2             28         2                2                .50          12
 *                 4        40          2             36         3                2                .70          16
 *                 5+       35          tier + team    48..80     4                3                1            unlimited
 *
 * Solo: at most 4 HP per ten ticks, even if every pest hits for its maximum. In 400 ticks (4 minutes), <=164 HP
 * is lost from the knight's 200. This leaves 2m15 after the final shield at 1m45 to travel/fight; portal kills heal it.
 * No invulnerability/grace period and no free AFK win: continued attacks can still kill the knight well before 20 minutes.
 */
object PestControlBalance {
    data class Profile(
        val waveTicks: Int,
        val waveSize: Int,
        val populationCap: Int,
        val knightShifters: Int,
        val spinnersPerPortal: Int,
        val damageShare: Double,
        val knightDamagePerWindow: Int,
    )

    const val DAMAGE_WINDOW_TICKS = 10
    const val SOLO_MIN_SURVIVAL_TICKS = 400

    private val smallTeams = listOf(
        Profile(70, 1, 12, 1, 1, 1.0 / 6, 4),
        Profile(55, 1, 20, 1, 1, 0.30, 8),
        Profile(45, 2, 28, 2, 2, 0.50, 12),
        Profile(40, 2, 36, 3, 2, 0.70, 16),
    )

    fun profile(players: Int, tier: PestControlTier): Profile {
        val count = players.coerceIn(1, PC_MAX_TEAM_SIZE)
        if (count < 5) return smallTeams[count - 1]
        val amount = tier.difficulty + 1 + count / 10 + if (tier == PestControlTier.NOVICE) 1 else 0
        return Profile(35, amount, minOf(80, 48 + (count - 5) * 2), KNIGHT_MAX_SHIFTERS, 3, 1.0, Int.MAX_VALUE)
    }

    /** Shared across every hit source in one game, including already queued hits after a player leaves. */
    class KnightDamageBudget {
        private var window = -1
        private var spent = 0

        fun apply(tick: Int, damage: Int, limit: Int): Int {
            if (damage <= 0) return damage
            val current = tick / DAMAGE_WINDOW_TICKS
            if (current != window) {
                window = current
                spent = 0
            }
            val allowed = minOf(damage, (limit - spent).coerceAtLeast(0))
            spent = (spent.toLong() + allowed).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            return allowed
        }
    }

    /** A ring one tile clear of the portal's entire footprint; every tile stays in the existing five-tile heal range. */
    fun spinnerTiles(portal: Tile, size: Int): List<Tile> =
        (-2..size + 1).flatMap { x ->
            (-2..size + 1).filter { z -> x == -2 || z == -2 || x == size + 1 || z == size + 1 }
                .map { z -> portal.transform(x, z, 0) }
        }
}
