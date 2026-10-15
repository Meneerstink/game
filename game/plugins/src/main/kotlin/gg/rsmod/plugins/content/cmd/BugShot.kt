package gg.rsmod.plugins.content.cmd

import gg.rsmod.game.model.BugShotUploads
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.service.GameService
import gg.rsmod.plugins.api.ChatMessageType
import gg.rsmod.plugins.api.ext.message
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * T2 (owner 2026-10-13: "client maakt automatisch een screenshot bij elk bug-commando (bewijs bij de melding)").
 *
 * Donor: RuneLite ScreenshotPlugin.takeScreenshot (runelite-client plugins/screenshot): on an event the client asks for its
 * own NEXT rendered frame (drawManager.requestNextFrameListener) and an executor writes it to a file named after the event;
 * RuneLite ImageCapture.uploadScreenshot then sends it to a server. Here the event is the `bug` command: after the report is
 * written the server opens ONE upload for this report (core BugShotUploads.expect) and sends the reporter ONE console line
 * "__RSPS_BUGSHOT__:<report stem>|upload". The 78 client (RspsBugShot) takes its next frame, keeps a copy in the player's
 * own 78legacy\bugs and sends the PNG over the game connection; the server writes it as bugs\<stem>.png next to the report
 * and adds "Screenshot received: ..." (round 1 FAIL: the PNG stayed on the reporter's PC - friends / VPS).
 * Above BugShotUploads.MAX_PER_HOUR requests the line has no "|upload": the client only keeps its own copy.
 * Round 3 (Keurmeester FAIL round 2: two reports close together lost both pictures and the client still said "sent"): every
 * report has its own upload on the server, and the server answers the client per report with "<stem>|saved|<file size>"
 * (after the PNG is on disk) or "<stem>|refused|<why>" ([replyLine]) - the client stops sending a refused report and only
 * tells the player what the server really did.
 * A client without RspsBugShot only prints the line in its dev console. Tests: BugShotTests, BugShotUploadsTests (core),
 * :runescape:bugShotRegression (client).
 */
object BugShot {
    /** Console marker the client listens for (RspsBugShot.PREFIX). A new prefix: "__RSPS_SHOT__" stays the screenshot tour's. */
    const val PREFIX = "__RSPS_BUGSHOT__:"

    val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    /**
     * The report file for a new `bug`: bug-<yyyyMMdd-HHmmss>-<user>.txt, or bug-<stamp>-<n>-<user>.txt when that stem is
     * already taken in this second (by a report or by its screenshot) - a report and its PNG are never overwritten.
     */
    fun reportFile(
        dir: File,
        now: LocalDateTime,
        username: String,
    ): File {
        val stamp = now.format(STAMP)
        var n = 1
        while (true) {
            val stem = if (n == 1) "bug-$stamp-$username" else "bug-$stamp-$n-$username"
            if (n >= 999 || (!File(dir, "$stem.txt").exists() && !File(dir, "$stem.png").exists())) {
                return File(dir, "$stem.txt")
            }
            n++
        }
    }

    /** Marks a request the server will take the PNG of (BugShotUploads.expect said yes); without it the client keeps its copy. */
    const val UPLOAD = "|upload"

    fun consoleLine(
        report: File,
        upload: Boolean = true,
    ): String = PREFIX + report.nameWithoutExtension + if (upload) UPLOAD else ""

    /** The report's own line: where the screenshot goes. */
    fun reportLine(report: File): String =
        "Screenshot: ${report.nameWithoutExtension}.png (taken by the reporter's 78 client and sent to the server into this folder; " +
            "the line 'Screenshot received:' at the end of this report confirms it - missing = an old client, the upload was " +
            "refused (server log '[bugshot] PROBLEM') or more than ${BugShotUploads.MAX_PER_HOUR} reports in an hour; the " +
            "reporter keeps a copy in <home>\\78legacy\\bugs)"

    /**
     * Runs [job] on the game thread (GameService job queue): the upload ends on the network or the writer thread, a player is
     * only written to from the game thread. Tests run it inline.
     */
    @Volatile
    var onGameThread: (Player, () -> Unit) -> Unit = { player, job -> player.world.getService(GameService::class.java)?.submitGameThreadJob(job) }

    /** "<PREFIX><stem>|saved|<detail>" or "<PREFIX><stem>|refused|<why>" - [detail] never carries a '|' or a path separator. */
    fun replyLine(
        report: File,
        outcome: BugShotUploads.Outcome,
        detail: String,
    ): String {
        val word = if (outcome == BugShotUploads.Outcome.SAVED) "saved" else "refused"
        val clean = detail.replace('|', ' ').replace('\\', ' ').replace('/', ' ').take(120)
        return "$PREFIX${report.nameWithoutExtension}|$word|$clean"
    }

    /**
     * Opens the upload for [report] and asks the reporter's client for the screenshot: exactly one console line. When the
     * upload ends the client gets one more line ([replyLine]) if the reporter is online (also after a relog).
     */
    fun request(
        player: Player,
        report: File,
    ) {
        val upload =
            BugShotUploads.expect(player.username, report) { outcome, detail ->
                onGameThread(player) {
                    // a relog during the upload: the client goes on with this report on its new session (a new Player)
                    val target = if (player.isOnline) player else player.world.getPlayerForName(player.username)
                    target?.message(replyLine(report, outcome, detail), type = ChatMessageType.CONSOLE)
                }
            }
        player.message(consoleLine(report, upload), type = ChatMessageType.CONSOLE)
    }
}
