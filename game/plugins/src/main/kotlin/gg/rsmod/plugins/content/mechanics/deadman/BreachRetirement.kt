package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.persistNow
import gg.rsmod.plugins.content.mechanics.pvp.breach.BreachPoints
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue

/** Release flag defaults OFF. No save files are scanned or edited; migration is one player snapshot at login. */
object BreachRetirement {
    val enabled: Boolean get() = java.lang.Boolean.getBoolean("rsps.dmm2.release")
    const val BREACH_PER_DP = 200
    val MIGRATED = AttributeKey<Int>("dmm2_breach_migrated")
    val OLD_BALANCE = AttributeKey<Int>("dmm2_breach_original_balance")
    val OLD_EARNED = AttributeKey<Int>("dmm2_breach_original_earned")
    private val DP = StoreCatalogue.Currency.DEADMAN.attr
    enum class Result { DISABLED, ALREADY_DONE, OK, OVERFLOW }
    /** Advice: Annihilation rune pouch note 100,000 archaic points / our existing rune pouch 500 DP; round upward by <1 DP. */
    fun converted(balance: Int): Long {
        val normalized = if (balance < 0) Int.MAX_VALUE.toLong() else balance.toLong() // N111
        return (normalized + BREACH_PER_DP - 1) / BREACH_PER_DP
    }
    fun migrate(player: Player, release: Boolean = enabled): Result {
        if (!release) return Result.DISABLED
        if (player.attr[MIGRATED] == 1) return Result.ALREADY_DONE
        val old = player.attr[BreachPoints.LEGACY_BALANCE] ?: 0
        val earned = player.attr[BreachPoints.LEGACY_EARNED] ?: 0
        val current = player.attr[DP] ?: 0
        val normalizedDp = if (current < 0) Int.MAX_VALUE.toLong() else current.toLong()
        val total = normalizedDp + converted(old)
        if (total > Int.MAX_VALUE) return Result.OVERFLOW
        // ROOT_CAUSES #4: a balance is written through PointBalance only.
        gg.rsmod.game.model.attr.PointBalance.set(player.attr, DP, total)
        player.attr[OLD_BALANCE] = old
        player.attr[OLD_EARNED] = earned
        player.attr.remove(BreachPoints.LEGACY_BALANCE)
        player.attr.remove(BreachPoints.LEGACY_EARNED)
        player.attr[MIGRATED] = 1
        player.persistNow()
        return Result.OK
    }
}
