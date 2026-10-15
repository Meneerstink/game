package gg.rsmod.plugins.content.cmd

import gg.rsmod.game.message.Message
import gg.rsmod.game.message.impl.MessageGameMessage
import gg.rsmod.game.model.BugShotUploads
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ChatMessageType
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T2 (owner 2026-10-13: "client maakt automatisch een screenshot bij elk bug-commando"): after every `bug <text>` the server
 * asks the reporter's client - with ONE console line "__RSPS_BUGSHOT__:<report stem>" - to save its next frame as
 * <stem>.png (client RspsBugShot, regression :runescape:bugShotRegression). Donor: RuneLite ScreenshotPlugin.takeScreenshot
 * (the client takes its own next frame on an event, file name per event). The class covered here, for every report:
 * - the stem names the report and its screenshot together, so two reports never share a stem (same player, same second);
 * - every stem the server can make passes the client's stem check (client refuses anything else: no paths from the server);
 * - exactly one console request per report, and the report says where the screenshot is;
 * - round 2 (Keurmeester FAIL: the PNG stayed on the reporter's PC): the request opens the server-side upload for exactly
 *   that report (BugShotUploads, core BugShotUploadsTests) and the line says "|upload"; above the hourly cap it does not;
 * - the `bug` command really goes through this (the plugin script is not reachable from a unit test: source guard).
 */
class BugShotTests {
    /** The client's accepted stem (RspsBugShot.STEM) - keep both in step. */
    private val clientStem = Regex("bug-\\d{8}-\\d{6}(-\\d{1,3})?-[A-Za-z0-9 _-]{1,20}")

    private val now: LocalDateTime = LocalDateTime.of(2026, 10, 14, 23, 46, 10)

    @Test
    fun `reports in the same second never share a stem with an earlier report or its screenshot`() {
        val dir = Files.createTempDirectory("bugshot").toFile()
        try {
            for (name in listOf("teamtest1", "mod ash", "a", "abcdefghijkl", "pk3r_99")) {
                val first = BugShot.reportFile(dir, now, name)
                first.writeText("first")
                val second = BugShot.reportFile(dir, now, name)
                assertTrue(second != first, "second report of $name in the same second overwrites ${first.name}")
                second.writeText("second")
                // the first report's screenshot may already be written by the client while the stem of a third is chosen
                File(dir, second.nameWithoutExtension + ".png").delete()
                File(dir, BugShot.reportFile(dir, now, name).nameWithoutExtension + ".png").writeText("png")
                val third = BugShot.reportFile(dir, now, name)
                assertTrue(third.name !in setOf(first.name, second.name), "third report of $name reuses ${third.name}")
                assertTrue(!File(dir, third.nameWithoutExtension + ".png").exists(), "third report of $name reuses a screenshot name ${third.name}")
                assertEquals("first", first.readText())
                assertEquals("second", second.readText())
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `every stem the server makes is one the client accepts, and the console line carries exactly that stem`() {
        val dir = Files.createTempDirectory("bugshot").toFile()
        try {
            for (name in listOf("teamtest1", "anudd", "mod ash", "a", "abcdefghijkl", "pk3r_99", "Zezima-2")) {
                repeat(3) {
                    val report = BugShot.reportFile(dir, now, name).also { it.writeText("x") }
                    assertTrue(report.name.endsWith(".txt") && report.parentFile == dir, "report ${report.path} is not a .txt in bugs")
                    val stem = report.nameWithoutExtension
                    assertTrue(clientStem.matches(stem), "client would refuse stem '$stem'")
                    assertEquals(BugShot.PREFIX + stem + "|upload", BugShot.consoleLine(report))
                    assertEquals(BugShot.PREFIX + stem, BugShot.consoleLine(report, upload = false))
                }
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `one bug report sends exactly one console request to the reporter, opens its upload, and the report names the screenshot`() {
        BugShotUploads.clear()
        BugShotUploads.executor = { it.run() }
        var gameThreadJobs = 0
        BugShot.onGameThread = { _, job ->
            gameThreadJobs++
            job()
        }
        val dir = Files.createTempDirectory("bugshot").toFile()
        try {
            val sent = mutableListOf<Any>()
            val player = mockk<Player>(relaxed = true)
            every { player.username } returns "teamtest1"
            every { player.isOnline } returns true
            every { player.write(*anyVararg<Message>()) } answers {
                it.invocation.args.forEach { a -> if (a is Array<*>) sent.addAll(a.filterNotNull()) else if (a != null) sent += a }
            }
            val report = File(dir, "bug-20261014-234610-teamtest1.txt").also { it.writeText("report\n") }
            BugShot.request(player, report)
            val shots = sent.filterIsInstance<MessageGameMessage>().filter { it.message.startsWith(BugShot.PREFIX) }
            assertEquals(1, shots.size, "console requests sent: $sent")
            assertEquals(ChatMessageType.CONSOLE.id, shots[0].type)
            assertEquals("__RSPS_BUGSHOT__:bug-20261014-234610-teamtest1|upload", shots[0].message)
            assertEquals(1, sent.size, "only the request, nothing else: $sent")

            // the upload the client then sends lands next to the report (whatever PC the client runs on)
            val png = java.io.ByteArrayOutputStream().also { javax.imageio.ImageIO.write(java.awt.image.BufferedImage(40, 30, 1), "png", it) }.toByteArray()
            assertEquals(BugShotUploads.Status.COMPLETE, BugShotUploads.receive("teamtest1", report.nameWithoutExtension, png.size, 0, png).status)
            assertTrue(png.contentEquals(File(dir, "bug-20261014-234610-teamtest1.png").readBytes()), "screenshot not next to the report")
            // round 3: the client hears that the server saved it - one console line, sent on the game thread
            val replies = sent.filterIsInstance<MessageGameMessage>().map { it.message }.drop(1)
            assertEquals(1, replies.size, "replies: $replies")
            assertTrue(replies[0].startsWith("__RSPS_BUGSHOT__:bug-20261014-234610-teamtest1|saved|bug-20261014-234610-teamtest1.png 40x30"), replies[0])
            assertEquals(1, gameThreadJobs, "the reply must be sent from the game thread")

            val line = BugShot.reportLine(report)
            assertTrue(line.startsWith("Screenshot: bug-20261014-234610-teamtest1.png"), line)
            assertTrue(line.contains("Screenshot received:"), line)

            // above the hourly cap: still one line, without |upload (the client keeps only its own copy)
            repeat(BugShotUploads.MAX_PER_HOUR) { BugShotUploads.expect("teamtest2", File(dir, "x$it.txt")) }
            every { player.username } returns "teamtest2"
            sent.clear()
            BugShot.request(player, File(dir, "bug-20261014-234611-teamtest2.txt"))
            assertEquals(listOf("__RSPS_BUGSHOT__:bug-20261014-234611-teamtest2"), sent.filterIsInstance<MessageGameMessage>().map { it.message })
        } finally {
            BugShotUploads.clear()
            dir.deleteRecursively()
        }
    }

    @Test
    fun `the reporter's client hears refused with the reason, also after a relog, an offline reporter hears nothing, and a reply never names a path`() {
        BugShotUploads.clear()
        BugShotUploads.executor = { it.run() }
        BugShot.onGameThread = { _, job -> job() }
        val dir = Files.createTempDirectory("bugshot").toFile()
        try {
            val sent = mutableListOf<String>()
            var online = true
            val world = mockk<World>(relaxed = true)
            var onlineNow: Player? = null
            every { world.getPlayerForName(any()) } answers { onlineNow }
            fun player(into: MutableList<String>): Player {
                val p = mockk<Player>(relaxed = true)
                every { p.username } returns "teamtest1"
                every { p.world } returns world
                every { p.write(*anyVararg<Message>()) } answers {
                    it.invocation.args.forEach { a -> (if (a is Array<*>) a.toList() else listOf(a)).filterIsInstance<MessageGameMessage>().forEach { m -> into += m.message } }
                }
                return p
            }
            val player = player(sent)
            every { player.isOnline } answers { online }
            onlineNow = player
            val a = File(dir, "bug-20261015-022906-teamtest1.txt").also { it.writeText("a\n") }
            val b = File(dir, "bug-20261015-022921-teamtest1.txt").also { it.writeText("b\n") }
            BugShot.request(player, a)
            BugShot.request(player, b)
            // A's upload breaks (a gap): its client is told to stop A - B stays open
            BugShotUploads.receive("teamtest1", a.nameWithoutExtension, 5000, 0, ByteArray(1000))
            BugShotUploads.receive("teamtest1", a.nameWithoutExtension, 5000, 2000, ByteArray(1000))
            assertEquals(
                listOf(
                    "__RSPS_BUGSHOT__:bug-20261015-022906-teamtest1|upload",
                    "__RSPS_BUGSHOT__:bug-20261015-022921-teamtest1|upload",
                    "__RSPS_BUGSHOT__:bug-20261015-022906-teamtest1|refused|a piece at 2000, expected 1000",
                ),
                sent,
            )
            assertEquals(listOf(b.nameWithoutExtension), BugShotUploads.openStems("teamtest1"))
            // the reporter logged out before B finished: nothing is sent to a player who is gone
            online = false
            onlineNow = null
            sent.clear()
            val png = java.io.ByteArrayOutputStream().also { javax.imageio.ImageIO.write(java.awt.image.BufferedImage(20, 10, 1), "png", it) }.toByteArray()
            assertEquals(BugShotUploads.Status.COMPLETE, BugShotUploads.receive("teamtest1", b.nameWithoutExtension, png.size, 0, png).status)
            assertEquals(emptyList(), sent)
            assertTrue(File(dir, b.nameWithoutExtension + ".png").isFile, "the server copy is kept even when the reporter is gone")
            // test server 2026-10-15 04:13: a relog in the middle of an upload - the client goes on with the same report on its new
            // session; the answer must reach the new session (a new Player object), not the logged-out one
            val c = File(dir, "bug-20261015-041238-teamtest1.txt").also { it.writeText("c\n") }
            online = true
            onlineNow = player
            BugShot.request(player, c)
            online = false
            val relogged = mutableListOf<String>()
            val again = player(relogged)
            every { again.isOnline } returns true
            onlineNow = again
            sent.clear()
            assertEquals(BugShotUploads.Status.COMPLETE, BugShotUploads.receive("teamtest1", c.nameWithoutExtension, png.size, 0, png).status)
            assertEquals(emptyList(), sent, "nothing to the logged-out session")
            assertEquals(1, relogged.size, "the relogged session hears the answer: $relogged")
            assertTrue(relogged[0].startsWith("__RSPS_BUGSHOT__:bug-20261015-041238-teamtest1|saved|"), relogged[0])
            for (line in listOf(BugShot.replyLine(a, BugShotUploads.Outcome.SAVED, "x.png 1x1"), BugShot.replyLine(a, BugShotUploads.Outcome.REFUSED, "C:\\RSPS\\bugs | x"))) {
                assertTrue(line.startsWith(BugShot.PREFIX + a.nameWithoutExtension + "|"), line)
                assertTrue(!line.contains("C:\\") && line.count { it == '|' } == 2, "reply carries a path or an extra field: $line")
            }
        } finally {
            BugShotUploads.clear()
            dir.deleteRecursively()
        }
    }

    @Test
    fun `the bug command writes its report through BugShot and requests exactly one screenshot`() {
        val script = File("src/main/kotlin/gg/rsmod/plugins/content/cmd/debug_toolkit.plugin.kts").readText()
        val command = script.substringAfter("on_command(\"bug\")").substringBefore("\nfun writeBugReport")
        assertEquals(1, Regex("BugShot\\.request\\(player, file\\)").findAll(command).count(), "bug command:\n$command")
        val writer = script.substringAfter("\nfun writeBugReport").substringBefore("\n}\n")
        assertTrue(writer.contains("BugShot.reportFile(dir, "), "writeBugReport must pick the stem through BugShot.reportFile")
        assertTrue(writer.contains("BugShot.reportLine(file)"), "writeBugReport must name the screenshot in the report")
        assertTrue(!writer.contains("File(dir, \"bug-"), "writeBugReport still builds its own file name")
    }
}
