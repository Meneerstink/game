package gg.rsmod.plugins.content.quests.tab

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach
import gg.rsmod.plugins.content.mechanics.pvp.emblem.DeadmanEmblem
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue
import java.time.Duration
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Locale

/**
 * The five values of the quest tab's Deadman panel (owner mission 2026-09-26). Every value is read from the one place the
 * game itself uses - the store balances from [StoreCatalogue.Currency] (the attributes the three stores spend), the emblem
 * from [DeadmanEmblem.ownedTier], the breach from [DeadmanBreach] - so the panel can never show a copy that drifted.
 */
object DeadmanTabValues {
    enum class Block(val title: String) {
        LOYALTY("Loyalty Points"),
        DONATOR("Donator Points"),
        DEADMAN("Deadman Points"),
        EMBLEM("Archaic Emblem"),
        BREACH("Next Breach"),
    }

    fun number(value: Int): String = String.format(Locale.US, "%,d", value)

    fun balance(player: Player, currency: StoreCatalogue.Currency): Int = player.attr[currency.attr] ?: 0

    /** "Tier X - N pts" (N = the emblem's Deadman Points value), "None" without an emblem. */
    fun emblemText(tier: Int): String = if (tier in 1..DeadmanEmblem.MAX_TIER) "Tier $tier - ${number(DeadmanEmblem.points(tier))} pts" else "None"

    /**
     * Time until [next]: "11 days 22 hours", "5 hours 12 minutes", "12 minutes" (singular for one), "Active" while a breach is
     * open. Rounded up to the minute, so the last minute before an opening reads "1 minute", never "0 minutes".
     */
    fun breachText(active: Boolean, now: ZonedDateTime, next: ZonedDateTime): String {
        if (active) return "Active"
        val seconds = Duration.between(now, next).seconds.coerceAtLeast(1)
        val minutes = (seconds + 59) / 60
        val days = minutes / (24 * 60)
        val hours = minutes % (24 * 60) / 60
        val mins = minutes % 60
        fun unit(n: Long, word: String) = "$n $word" + if (n == 1L) "" else "s"
        return when {
            days >= 1 -> "${unit(days, "day")} ${unit(hours, "hour")}"
            hours >= 1 -> "${unit(hours, "hour")} ${unit(mins, "minute")}"
            else -> unit(mins, "minute")
        }
    }

    fun value(player: Player, block: Block, now: ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC)): String =
        when (block) {
            Block.LOYALTY -> number(balance(player, StoreCatalogue.Currency.LOYALTY))
            Block.DONATOR -> number(balance(player, StoreCatalogue.Currency.DONATOR))
            Block.DEADMAN -> number(balance(player, StoreCatalogue.Currency.DEADMAN))
            Block.EMBLEM -> emblemText(DeadmanEmblem.ownedTier(player))
            Block.BREACH -> breachText(DeadmanBreach.active != null, now, DeadmanBreach.nextOpening)
        }
}
