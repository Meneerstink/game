package gg.rsmod.game.model.priv

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import java.util.concurrent.TimeUnit

/**
 * Owner 2026-10-10 (staff tools: "mute / ban / jail"): the punishment state, ported from Void `content.social.report.Mute` /
 * `Ban` - an "until" time in epoch seconds on the player, [PERMANENT] = no end ("Max value rather than -1 as small numbers are
 * loaded back from saves as ints"). Kept in the game module so the chat handlers can refuse a muted player's text; the commands
 * live in the plugins' moderation package. Bans are [gg.rsmod.game.service.world.BannedPlayers] (checked before the save loads).
 */
object Punishments {
    const val PERMANENT = Int.MAX_VALUE

    /** Void `muted_until`: typed public, global, friends/clan chat and private messages are refused until then (quick chat stays). */
    val MUTED_UNTIL = AttributeKey<Int>(persistenceKey = "muted_until")

    /** Novite `jailed`: the player stays in the jail until then (moderation plugin). */
    val JAILED_UNTIL = AttributeKey<Int>(persistenceKey = "jailed_until")

    fun epochSeconds(): Int = (System.currentTimeMillis() / 1000).toInt()

    /** Epoch seconds [hours] from now, or [PERMANENT] for 0 or less. */
    fun untilHours(hours: Int): Int =
        if (hours <= 0) PERMANENT else (epochSeconds().toLong() + TimeUnit.HOURS.toSeconds(hours.toLong())).coerceAtMost(PERMANENT - 1L).toInt()

    /** Epoch seconds [minutes] from now, or [PERMANENT] for 0 or less. */
    fun untilMinutes(minutes: Int): Int =
        if (minutes <= 0) PERMANENT else (epochSeconds().toLong() + TimeUnit.MINUTES.toSeconds(minutes.toLong())).coerceAtMost(PERMANENT - 1L).toInt()

    fun isMuted(player: Player): Boolean = (player.attr[MUTED_UNTIL] ?: 0) > epochSeconds()

    fun isJailed(player: Player): Boolean = (player.attr[JAILED_UNTIL] ?: 0) > epochSeconds()

    /** "permanently" or "for another 2 hours 5 minutes" for an until time. */
    fun remaining(until: Int): String {
        if (until == PERMANENT) return "permanently"
        val left = (until - epochSeconds()).coerceAtLeast(0)
        val days = left / 86_400
        val hours = left % 86_400 / 3_600
        val minutes = (left % 3_600 + 59) / 60
        val parts =
            listOfNotNull(
                days.takeIf { it > 0 }?.let { "$it day${if (it == 1) "" else "s"}" },
                hours.takeIf { it > 0 }?.let { "$it hour${if (it == 1) "" else "s"}" },
                minutes.takeIf { it > 0 || (days == 0 && hours == 0) }?.let { "$it minute${if (it == 1) "" else "s"}" },
            )
        return "for another " + parts.joinToString(" ")
    }

    /** Void `sendMuteMessage`: why a muted player's chat was not sent. */
    fun muteMessage(player: Player): String = "You are muted ${remaining(player.attr[MUTED_UNTIL] ?: PERMANENT)} for breaking a rule."

    /** True (and the player is told) when a muted [player] tries to chat. */
    fun refuseChat(player: Player): Boolean {
        if (!isMuted(player)) return false
        player.writeMessage(muteMessage(player))
        return true
    }
}
