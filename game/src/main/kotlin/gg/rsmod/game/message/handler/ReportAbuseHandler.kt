package gg.rsmod.game.message.handler

import gg.rsmod.game.message.MessageHandler
import gg.rsmod.game.message.impl.ReportAbuseMessage
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Client
import mu.KLogging
import java.io.File
import java.time.LocalDateTime

/**
 * Report Abuse (owner 2026-09-19: "report abuse button does not work"). The chat-bar button opens interface 594
 * (report_abuse.plugin.kts); its Send-report step sends this packet. Every report is appended to
 * `data/logs/abuse_reports.log` for staff review. The moderator mute flag is recorded; this server has no mute system yet.
 */
class ReportAbuseHandler : MessageHandler<ReportAbuseMessage> {
    override fun handle(
        client: Client,
        world: World,
        message: ReportAbuseMessage,
    ) {
        val offender = message.name.trim()
        if (offender.isEmpty() || !world.characterExists(offender)) {
            client.writeMessage("Invalid player name.")
            return
        }
        if (offender.equals(client.username, ignoreCase = true)) {
            client.writeMessage("You can't report yourself!")
            return
        }
        val line =
            "${LocalDateTime.now()}\treporter=${client.username}\toffender=$offender\trule=${message.rule}" +
                "\tmuteRequested=${message.mute}\tcomment=${message.comment.replace('\t', ' ').replace('\n', ' ')}"
        runCatching {
            val file = File("data/logs/abuse_reports.log")
            file.parentFile?.mkdirs()
            file.appendText(line + System.lineSeparator())
        }.onFailure { logger.error(it) { "Could not store abuse report: $line" } }
        logger.info { "Abuse report: $line" }
        client.writeMessage("Thank-you, your abuse report has been received.")
    }

    companion object : KLogging()
}
