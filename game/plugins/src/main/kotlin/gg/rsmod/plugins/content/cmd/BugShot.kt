package gg.rsmod.plugins.content.cmd

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ChatMessageType
import gg.rsmod.plugins.api.ext.message
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * T2 (owner 2026-10-13: "client maakt automatisch een screenshot bij elk bug-commando (bewijs bij de melding)").
 *
 * Donor: RuneLite ScreenshotPlugin.takeScreenshot (runelite-client plugins/screenshot): on an event the client asks for its
 * own NEXT rendered frame (drawManager.requestNextFrameListener) and an executor writes it to a file named after the event.
 * Here the event is the `bug` command: after the report is written the server sends the reporter ONE console line
 * "__RSPS_BUGSHOT__:<report stem>" and the 78 client (RspsBugShot) saves its next frame as <stem>.png - in C:\RSPS\bugs next
 * to the report on the server PC, else in the player's own 78legacy\bugs folder. A client without RspsBugShot only prints
 * the line in its dev console. Tests: BugShotTests (server), :runescape:bugShotRegression (client).
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

    fun consoleLine(report: File): String = PREFIX + report.nameWithoutExtension

    /** The report's own line: where the reporter's client puts the screenshot. */
    fun reportLine(report: File): String =
        "Screenshot: ${report.nameWithoutExtension}.png (made by the reporter's 78 client: next to this report when that client runs " +
            "on the server PC, else on the reporter's PC in <home>\\78legacy\\bugs)"

    /** Asks the reporter's client for the screenshot: exactly one console line, nothing else. */
    fun request(
        player: Player,
        report: File,
    ) {
        player.message(consoleLine(report), type = ChatMessageType.CONSOLE)
    }
}
