package gg.rsmod.plugins.content.quests.tab

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach
import gg.rsmod.plugins.content.mechanics.pvp.chest.DeadmanChestIds
import gg.rsmod.plugins.content.mechanics.pvp.chest.DeadmanChestWorld
import gg.rsmod.plugins.content.mechanics.pvp.emblem.DeadmanEmblem
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue
import java.time.Duration
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Locale

/**
 * The six lines of the quest tab's Deadman panel (owner mission 2026-09-26, N102 list 2026-10-18). Every value is read from the one
 * place the game itself uses - the store balances from [StoreCatalogue.Currency] (the attributes the three stores spend), the emblem
 * from [DeadmanEmblem.ownedTier], the breach from [DeadmanBreach], the chest from [DeadmanChestWorld] - so the panel can never show a
 * copy that drifted.
 */
object DeadmanTabValues {
    enum class Row(val title: String) {
        LOYALTY("Loyalty Points"),
        DONATOR("Donator Points"),
        DEADMAN("Deadman Points"),
        EMBLEM("Archaic Emblem"),
        BREACH("Next Breach"),
        CHEST("Deadman's Chest"),
    }

    fun number(value: Int): String = String.format(Locale.US, "%,d", value)

    fun balance(player: Player, currency: StoreCatalogue.Currency): Int = player.attr[currency.attr] ?: 0

    /** "Tier X - N pts" (N = the emblem's Deadman Points value), "None" without an emblem. */
    fun emblemText(tier: Int): String = if (tier in 1..DeadmanEmblem.MAX_TIER) "Tier $tier - ${number(DeadmanEmblem.points(tier))} pts" else "None"

    /**
     * "11 days 22 hours", "5 hours 12 minutes", "12 minutes" (singular for one). Rounded up to the minute, so the last minute before
     * an opening reads "1 minute", never "0 minutes".
     */
    fun duration(seconds: Long): String {
        val minutes = (seconds.coerceAtLeast(1) + 59) / 60
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

    /** Time until [next] ([duration]), "Active" while a breach is open. */
    fun breachText(active: Boolean, now: ZonedDateTime, next: ZonedDateTime): String =
        if (active) "Active" else duration(Duration.between(now, next).seconds)

    /**
     * The chest line pair: "Active" over the spot of the timed chest that can still be looted, else "in 12 minutes" over "Random
     * spot" (the spot is drawn when it spawns and broadcast then). "Unavailable" when the chest is not in the cache or not running.
     */
    fun chestText(enabled: Boolean, activeSpot: String?, ticksUntil: Int): String =
        when {
            !enabled -> "Unavailable"
            activeSpot != null -> "Active<br>${activeSpot.replaceFirstChar { it.titlecase(Locale.US) }}"
            ticksUntil < 0 -> "Unavailable"
            else -> "in ${duration((ticksUntil * 6L + 9) / 10)}<br>Random spot"
        }

    fun value(player: Player, row: Row, now: ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC)): String =
        when (row) {
            Row.LOYALTY -> number(balance(player, StoreCatalogue.Currency.LOYALTY))
            Row.DONATOR -> number(balance(player, StoreCatalogue.Currency.DONATOR))
            Row.DEADMAN -> number(balance(player, StoreCatalogue.Currency.DEADMAN))
            Row.EMBLEM -> emblemText(DeadmanEmblem.ownedTier(player))
            Row.BREACH -> breachText(DeadmanBreach.active != null, now, DeadmanBreach.nextOpening)
            Row.CHEST -> chestText(DeadmanChestIds.closedLoc > 0, DeadmanChestWorld.activeTimed()?.name, DeadmanChestWorld.ticksUntilTimed)
        }
}
