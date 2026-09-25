package gg.rsmod.game.message.handler

import gg.rsmod.game.message.MessageHandler
import gg.rsmod.game.message.impl.ReportAbuseMessage
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Client
import mu.KLogging
import java.io.File
import java.time.LocalDateTime
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Report Abuse (owner 2026-09-19: "report abuse button does not work"). The chat-bar button opens interface 594
 * (report_abuse.plugin.kts); its Send-report step sends this packet. Every report is appended to
 * `data/logs/abuse_reports.log` for staff review. The moderator mute flag is recorded; this server has no mute system yet.
 *
 * Audit S-14: one report per [COOLDOWN_CYCLES] per player, the comment is cut to [MAX_COMMENT_LENGTH] characters and the
 * file is written on a background thread instead of the game thread.
 */
class ReportAbuseHandler : MessageHandler<ReportAbuseMessage> {
    override fun handle(
        client: Client,
        world: World,
        message: ReportAbuseMessage,
    ) {
        if (!cooldownElapsed(client.attr[LAST_REPORT_CYCLE], world.currentCycle)) {
            client.writeMessage("You have already sent a report recently. Please wait a minute before sending another.")
            return
        }
        val offender = message.name.trim()
        if (offender.isEmpty() || !world.characterExists(offender)) {
            client.writeMessage("Invalid player name.")
            return
        }
        if (offender.equals(client.username, ignoreCase = true)) {
            client.writeMessage("You can't report yourself!")
            return
        }
        client.attr[LAST_REPORT_CYCLE] = world.currentCycle
        val line =
            "${LocalDateTime.now()}\treporter=${client.username}\toffender=$offender\trule=${message.rule}" +
                "\tmuteRequested=${message.mute}\tcomment=${sanitizeComment(message.comment)}"
        logger.info { "Abuse report: $line" }
        writer.execute {
            runCatching {
                val file = File("data/logs/abuse_reports.log")
                file.parentFile?.mkdirs()
                file.appendText(line + System.lineSeparator())
            }.onFailure { logger.error(it) { "Could not store abuse report: $line" } }
        }
        client.writeMessage("Thank-you, your abuse report has been received.")
    }

    companion object : KLogging() {
        /** 100 game cycles = 60 seconds. */
        const val COOLDOWN_CYCLES = 100

        const val MAX_COMMENT_LENGTH = 200

        /** Game cycle of the player's last accepted report; not saved, so a relog only restarts a one-minute wait. */
        private val LAST_REPORT_CYCLE = AttributeKey<Int>()

        /** One daemon thread keeps the log lines in order without blocking the game thread on disk IO. */
        private val writer: ExecutorService =
            Executors.newSingleThreadExecutor { r -> Thread(r, "abuse-report-log").apply { isDaemon = true } }

        /** Whether a report may be sent at [now] when the previous accepted one was at [last] (null = never). */
        fun cooldownElapsed(
            last: Int?,
            now: Int,
        ): Boolean = last == null || now - last >= COOLDOWN_CYCLES || now < last

        /** The comment as one log field: no tabs, line breaks or other control characters, at most [MAX_COMMENT_LENGTH] characters. */
        fun sanitizeComment(comment: String): String =
            comment.map { if (it.isISOControl()) ' ' else it }.joinToString("").trim().take(MAX_COMMENT_LENGTH)
    }
}
