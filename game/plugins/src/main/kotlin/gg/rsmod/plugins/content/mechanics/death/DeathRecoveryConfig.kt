package gg.rsmod.plugins.content.mechanics.death

/**
 * Configurable PvM death-recovery parameters: how long a player has to
 * reclaim their non-protected items after a non-Wilderness death, and the
 * coin fee charged to reclaim them.
 *
 * Both the gravestone duration and the reclaim fee are open product values
 * that have not been decided by the project owner. Rather than invent final
 * numbers, they are threaded through as an explicit, injectable parameter to
 * [DeathExecutor.execute] / [DeathRecoveryService.reclaim] so the running
 * server can operate end-to-end and be tested with explicit values today,
 * and so a real config source can replace [PLACEHOLDER] at this single
 * call site once the owner confirms production numbers - no calculation or
 * execution logic needs to change.
 */
data class DeathRecoveryConfig(
    val recoveryDurationMs: Long,
    val reclaimFee: Int,
) {
    companion object {
        /**
         * Audit D-09: reclaim fee by the total guide value waiting in Death's Domain, following the
         * OSRS Death's Office tiers (free up to 100k, 5k up to 1M, 50k up to 10M, 500k up to 100M,
         * then 1%) - tier values niet geverifieerd against the wiki (unreachable from the audit).
         */
        fun feeFor(value: Long): Int =
            when {
                value <= 100_000L -> 0
                value <= 1_000_000L -> 5_000
                value <= 10_000_000L -> 50_000
                value <= 100_000_000L -> 500_000
                else -> (value / 100L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            }

        /**
         * NOT a finalized product value - see class doc. Exists only so the
         * server has something to run with before the owner decides.
         */
        val PLACEHOLDER =
            DeathRecoveryConfig(
                recoveryDurationMs = 15 * 60 * 1000L,
                reclaimFee = 0,
            )
    }
}
