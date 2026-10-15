package gg.rsmod.game.model

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.rsmod.game.message.MessageDecoderSet
import gg.rsmod.game.message.MessageStructureSet
import gg.rsmod.game.message.impl.BugShotChunkMessage
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.service.GameService
import gg.rsmod.game.system.GameSystem
import gg.rsmod.net.codec.game.ClientProtSizes
import gg.rsmod.net.codec.game.GamePacketDecoder
import gg.rsmod.net.packet.GamePacket
import gg.rsmod.net.packet.IPacketMetadata
import gg.rsmod.net.packet.PacketType
import io.mockk.every
import io.mockk.mockk
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.embedded.EmbeddedChannel
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.concurrent.BlockingQueue
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T2 round 2 (Keurmeester FAIL round 1: "the PNG appears only in <home>\78legacy\bugs, not next to bugs\bug-*.txt on the
 * server" - friends play from other PCs, the server moves to a VPS). The screenshot comes to the server over the game
 * connection (78 client RspsBugShot.pump, opcode 94) and [BugShotUploads] writes it next to the report.
 * Round 3 (Keurmeester FAIL round 2: two reports 15 s apart -> NO png for either; the newer report replaced the open request
 * while the older PNG was still being sent, the pieces carried no report id, the client said "sent" for both). The class:
 * - every PNG size the client can send arrives byte-exact as <report stem>.png next to ITS report, for every kind of
 *   username, and the reporter's client hears SAVED only after the file is on disk;
 * - any number of reports (up to MAX_OPEN waiting) made at ANY moment of an earlier upload - before its first piece, in the
 *   middle, on its last piece, after it - each get their own picture; pieces of one report never fill another;
 * - nothing is written for: no request, another player's report, an unknown or path-like stem, a piece without a stem (the
 *   round-2 layout), an expired request, a second upload, a gap / overlap / past-the-end piece, a size change, too big, not
 *   a PNG, a silly image size - and each of those closes only that report, tells the client REFUSED with the reason, and the
 *   rest of a closed upload is dropped quietly (no SAVED, no refusal per piece);
 * - never overwrites; MAX_OPEN per player (oldest closed and told); at most MAX_PER_HOUR per hour; a full disk -> REFUSED;
 * - the packet path: opcode 94 is a var-short client packet in packets.yml and ClientProtSizes, decodes byte-exact (stem,
 *   total, offset, data) from the bytes the client writes, never throws on a short packet, and GameSystem hands it to
 *   BugShotUploads without putting it in the per-cycle queue.
 */
class BugShotUploadsTests {
    private lateinit var dir: File
    private val clientChunk = 1250 // RspsBugShot.CHUNK

    /** Every notify call: "<stem> SAVED|REFUSED <detail>". */
    private val told = mutableListOf<String>()

    @BeforeTest
    fun setUp() {
        BugShotUploads.clear()
        BugShotUploads.executor = { it.run() }
        BugShotUploads.minFreeBytes = 1L shl 30
        dir = Files.createTempDirectory("bugshot-up").toFile()
        told.clear()
    }

    @AfterTest
    fun tearDown() {
        BugShotUploads.clear()
        BugShotUploads.minFreeBytes = 1L shl 30
        dir.deleteRecursively()
    }

    private fun report(
        user: String,
        stamp: String = "20261015-010631",
    ): File = File(dir, "bug-$stamp-$user.txt").also { it.writeText("BUG REPORT by $user\nScreenshot: ...\n") }

    private fun expect(
        user: String,
        rep: File,
        now: Long = System.currentTimeMillis(),
    ): Boolean =
        BugShotUploads.expect(user, rep, now) { outcome, detail ->
            // SAVED must only be said when the PNG really is on disk
            if (outcome == BugShotUploads.Outcome.SAVED) {
                assertTrue(dir.listFiles()!!.any { it.name.startsWith(rep.nameWithoutExtension) && it.name.endsWith(".png") }, "SAVED before the file exists: $detail")
            }
            told += "${rep.nameWithoutExtension} $outcome $detail"
        }

    private fun png(
        w: Int,
        h: Int,
        noisy: Boolean = false,
    ): ByteArray {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val r = java.util.Random(w * 31L + h)
        for (y in 0 until h) for (x in 0 until w) img.setRGB(x, y, if (noisy) r.nextInt() else (x * 7 + y * 13))
        return ByteArrayOutputStream().also { ImageIO.write(img, "png", it) }.toByteArray()
    }

    /** The pieces the client sends for one report: (offset, bytes). */
    private fun pieces(
        bytes: ByteArray,
        chunk: Int = clientChunk,
    ): List<Pair<Int, ByteArray>> = (bytes.indices step chunk).map { it to bytes.copyOfRange(it, minOf(bytes.size, it + chunk)) }

    /** What the client does: pieces of [chunk] bytes, in order, each naming [stem]. Returns the last result. */
    private fun send(
        user: String,
        stem: String,
        bytes: ByteArray,
        chunk: Int = clientChunk,
    ): BugShotUploads.Result {
        var last = BugShotUploads.Result(BugShotUploads.Status.REFUSED, "nothing sent")
        for ((off, piece) in pieces(bytes, chunk)) {
            last = BugShotUploads.receive(user, stem, bytes.size, off, piece)
            if (last.status == BugShotUploads.Status.REFUSED) return last
        }
        return last
    }

    private fun send(
        user: String,
        rep: File,
        bytes: ByteArray,
    ) = send(user, rep.nameWithoutExtension, bytes)

    private fun pngs() = dir.listFiles { f -> f.name.endsWith(".png") }!!.map { it.name }.sorted()

    private fun saved(rep: File) = told.count { it.startsWith("${rep.nameWithoutExtension} SAVED") }

    private fun refused(rep: File) = told.count { it.startsWith("${rep.nameWithoutExtension} REFUSED") }

    @Test
    fun `every screenshot size arrives byte-exact next to its report, for every kind of name`() {
        val sizes =
            listOf(
                png(20, 10), // one piece
                png(795, 503), // fixed client + sidebar
                png(974, 581, noisy = true), // resizable, ~1.7 MB, over a thousand pieces
            )
        val names = listOf("teamtest1", "Mod Ash", "a", "abcdefghijkl", "pk3r_99", "Zezima-2")
        var stamp = 100000
        for (name in names) {
            for (bytes in sizes) {
                val rep = report(name, "20261015-${stamp++}")
                assertTrue(expect(name, rep))
                val result = send(name.uppercase(), rep, bytes) // the login name's case does not matter
                assertEquals(BugShotUploads.Status.COMPLETE, result.status, "$name ${bytes.size} bytes: ${result.detail}")
                val out = File(dir, rep.nameWithoutExtension + ".png")
                assertTrue(out.isFile, "$name: ${out.name} not written; dir has ${pngs()}")
                assertTrue(bytes.contentEquals(out.readBytes()), "$name: ${out.name} is not the PNG the client sent")
                val line = rep.readLines().last()
                assertTrue(line.startsWith("Screenshot received: ${out.name} ("), "$name: report line '$line'")
                assertEquals(1, saved(rep), "$name: the client must hear SAVED once: $told")
                assertEquals(0, refused(rep), "$name: $told")
            }
        }
        // a PNG whose size is an exact multiple of the piece size (no short last piece)
        val base = png(64, 64, noisy = true)
        val padded = base.copyOf(base.size - base.size % clientChunk + clientChunk)
        val rep = report("teamtest1", "20261015-999999")
        assertTrue(expect("teamtest1", rep))
        assertEquals(0, padded.size % clientChunk)
        assertEquals(BugShotUploads.Status.COMPLETE, send("teamtest1", rep, padded).status)
        assertTrue(padded.contentEquals(File(dir, rep.nameWithoutExtension + ".png").readBytes()))
        assertTrue(dir.listFiles()!!.none { it.name.endsWith(".part") }, "temporary files left: ${dir.list()!!.toList()}")
    }

    @Test
    fun `a second report at any moment of the first upload - both reports get their own picture`() {
        // Keurmeester round 2: 'bug A', ~15 s later 'bug B' while A's pieces were still coming -> neither got a PNG.
        val a = png(300, 200, noisy = true)
        val b = png(200, 150, noisy = true)
        val aPieces = pieces(a)
        assertTrue(aPieces.size > 20, "A must be many pieces: ${aPieces.size}")
        // B is reported before A's first piece, after 1 piece, in the middle, just before and after A's last piece
        val moments = listOf(0, 1, aPieces.size / 2, aPieces.size - 1, aPieces.size)
        var stamp = 110000
        for (moment in moments) {
            told.clear()
            val user = "rapid$moment"
            val repA = report(user, "20261015-${stamp++}")
            val repB = report(user, "20261015-${stamp++}")
            assertTrue(expect(user, repA))
            for ((i, piece) in aPieces.withIndex()) {
                if (i == moment) assertTrue(expect(user, repB))
                val r = BugShotUploads.receive(user, repA.nameWithoutExtension, a.size, piece.first, piece.second)
                assertTrue(r.status != BugShotUploads.Status.REFUSED, "B at piece $moment: A refused at piece $i: ${r.detail}")
            }
            if (moment >= aPieces.size) assertTrue(expect(user, repB))
            // the client sends B after A (RspsBugShot: one upload after the other)
            assertEquals(BugShotUploads.Status.COMPLETE, send(user, repB, b).status, "B at piece $moment")
            assertTrue(a.contentEquals(File(dir, repA.nameWithoutExtension + ".png").readBytes()), "B at piece $moment: A's picture")
            assertTrue(b.contentEquals(File(dir, repB.nameWithoutExtension + ".png").readBytes()), "B at piece $moment: B's picture")
            assertEquals(listOf(1, 1), listOf(saved(repA), saved(repB)), "B at piece $moment: $told")
            assertEquals(listOf(0, 0), listOf(refused(repA), refused(repB)), "B at piece $moment: $told")
        }
    }

    @Test
    fun `three and more reports in a row each get their picture, the oldest beyond MAX_OPEN is closed and told`() {
        val shots = (0 until BugShotUploads.MAX_OPEN).map { png(40 + it * 30, 30 + it * 20, noisy = true) }
        val reps = shots.indices.map { report("teamtest1", "20261015-12000$it") }
        // all reported within seconds, before any piece arrives
        for (rep in reps) assertTrue(expect("teamtest1", rep))
        assertEquals(reps.map { it.nameWithoutExtension }, BugShotUploads.openStems("teamtest1"))
        for ((i, rep) in reps.withIndex()) assertEquals(BugShotUploads.Status.COMPLETE, send("teamtest1", rep, shots[i]).status, rep.name)
        for ((i, rep) in reps.withIndex()) {
            assertTrue(shots[i].contentEquals(File(dir, rep.nameWithoutExtension + ".png").readBytes()), "${rep.name}: wrong or missing picture")
            assertEquals(1, saved(rep), "${rep.name}: $told")
        }
        assertEquals(emptyList(), BugShotUploads.openStems("teamtest1"))

        // one report more than MAX_OPEN waiting: the oldest is closed (client told), the newest ones still work
        told.clear()
        val more = (0..BugShotUploads.MAX_OPEN).map { report("teamtest2", "20261015-13000$it") }
        for (rep in more) assertTrue(expect("teamtest2", rep))
        assertEquals(1, refused(more[0]), "the oldest must be told it was closed: $told")
        assertEquals(BugShotUploads.Status.REFUSED, send("teamtest2", more[0], shots[0]).status)
        for (rep in more.drop(1)) assertEquals(BugShotUploads.Status.COMPLETE, send("teamtest2", rep, shots[0]).status, rep.name)
        assertTrue(!File(dir, more[0].nameWithoutExtension + ".png").exists())
    }

    @Test
    fun `nothing is written without a request, for another player's report, an unknown stem or after the request expired`() {
        val bytes = png(100, 60)
        val rep = report("teamtest1")
        assertEquals(BugShotUploads.Status.REFUSED, send("teamtest1", rep, bytes).status, "no bug report, no upload")

        assertTrue(expect("teamtest1", rep))
        assertEquals(BugShotUploads.Status.REFUSED, send("teamtest2", rep, bytes).status, "another player cannot fill teamtest1's report")
        for (stem in listOf("", "../../game.yml", "..\\..\\x", "bug-20261015-010631-teamtest2", rep.nameWithoutExtension + ".png", rep.name, "x".repeat(300))) {
            assertEquals(BugShotUploads.Status.REFUSED, send("teamtest1", stem, bytes).status, "stem '$stem'")
        }
        assertEquals(emptyList(), pngs())
        assertEquals(emptyList(), told, "pieces of a report that is not open must not touch the open one")

        val old = report("teamtest3")
        val t0 = System.currentTimeMillis() - BugShotUploads.REQUEST_TTL_MS - 1
        assertTrue(expect("teamtest3", old, t0))
        assertEquals(BugShotUploads.Status.REFUSED, BugShotUploads.receive("teamtest3", old.nameWithoutExtension, bytes.size, 0, bytes).status, "expired")
        assertEquals(1, refused(old), "the client must hear the expiry: $told")
        assertEquals(emptyList(), pngs())

        // the real one still works, exactly once
        assertEquals(BugShotUploads.Status.COMPLETE, send("teamtest1", rep, bytes).status)
        assertEquals(listOf(rep.nameWithoutExtension + ".png"), pngs())
        assertEquals(BugShotUploads.Status.REFUSED, send("teamtest1", rep, bytes).status, "a second upload for the same report")
        assertEquals(listOf(rep.nameWithoutExtension + ".png"), pngs())
        assertEquals(1, saved(rep))
        assertEquals(0, refused(rep), "the rest of a finished upload is dropped quietly: $told")
    }

    @Test
    fun `a waiting report does not expire while an earlier upload of the player is still sending`() {
        val t0 = 1_000_000_000L
        val a = png(400, 300, noisy = true)
        val b = png(50, 40)
        val repA = report("teamtest1", "20261015-140000")
        val repB = report("teamtest1", "20261015-140001")
        assertTrue(expect("teamtest1", repA, t0))
        assertTrue(expect("teamtest1", repB, t0 + 1000))
        // A arrives slowly over more than REQUEST_TTL_MS (a big screenshot on a slow uplink)
        val aPieces = pieces(a)
        val step = (BugShotUploads.REQUEST_TTL_MS * 3 / 2) / aPieces.size
        var now = t0
        for ((off, piece) in aPieces) {
            now += step
            assertTrue(BugShotUploads.receive("teamtest1", repA.nameWithoutExtension, a.size, off, piece, now).status != BugShotUploads.Status.REFUSED)
        }
        assertTrue(now - t0 > BugShotUploads.REQUEST_TTL_MS)
        var last: BugShotUploads.Result? = null
        for ((off, piece) in pieces(b)) last = BugShotUploads.receive("teamtest1", repB.nameWithoutExtension, b.size, off, piece, now + 100)
        assertEquals(BugShotUploads.Status.COMPLETE, last!!.status, last.detail)
        // ... but a player who stopped sending altogether: the waiting report expires
        val repC = report("teamtest1", "20261015-140002")
        assertTrue(expect("teamtest1", repC, now))
        assertEquals(
            BugShotUploads.Status.REFUSED,
            BugShotUploads.receive("teamtest1", repC.nameWithoutExtension, b.size, 0, b, now + 100 + BugShotUploads.REQUEST_TTL_MS + 1).status,
        )
    }

    @Test
    fun `a broken stream writes nothing, closes only that report, tells the client and the rest is dropped quietly`() {
        val bytes = png(300, 200, noisy = true)
        val c = clientChunk
        val cases =
            linkedMapOf<String, (String, String) -> Unit>(
                "first piece not at 0" to { u, s -> BugShotUploads.receive(u, s, bytes.size, c, bytes.copyOfRange(c, 2 * c)) },
                "gap" to { u, s ->
                    BugShotUploads.receive(u, s, bytes.size, 0, bytes.copyOfRange(0, c))
                    BugShotUploads.receive(u, s, bytes.size, 2 * c, bytes.copyOfRange(2 * c, 3 * c))
                },
                "overlap" to { u, s ->
                    BugShotUploads.receive(u, s, bytes.size, 0, bytes.copyOfRange(0, c))
                    BugShotUploads.receive(u, s, bytes.size, 1000, bytes.copyOfRange(1000, 1000 + c))
                },
                "size changes" to { u, s ->
                    BugShotUploads.receive(u, s, bytes.size, 0, bytes.copyOfRange(0, c))
                    BugShotUploads.receive(u, s, bytes.size + 5, c, bytes.copyOfRange(c, 2 * c))
                },
                "past the end" to { u, s ->
                    BugShotUploads.receive(u, s, c + 100, 0, bytes.copyOfRange(0, c))
                    BugShotUploads.receive(u, s, c + 100, c, bytes.copyOfRange(c, 2 * c))
                },
                "piece too big" to { u, s -> BugShotUploads.receive(u, s, bytes.size, 0, bytes.copyOfRange(0, BugShotUploads.MAX_CHUNK + 1)) },
                "empty piece" to { u, s -> BugShotUploads.receive(u, s, bytes.size, 0, ByteArray(0)) },
                "too big" to { u, s -> BugShotUploads.receive(u, s, BugShotUploads.MAX_BYTES + 1, 0, bytes.copyOfRange(0, c)) },
                "negative size" to { u, s -> BugShotUploads.receive(u, s, -1, 0, bytes.copyOfRange(0, c)) },
                "not a PNG" to { u, s -> send(u, s, "GIF89a not a png at all, just bytes".repeat(100).toByteArray()) },
                "PNG of 0 pixels wide" to { u, s ->
                    val bad = png(10, 10).copyOf()
                    bad[16] = 0; bad[17] = 0; bad[18] = 0; bad[19] = 0
                    send(u, s, bad)
                },
                "PNG of 100000 pixels" to { u, s ->
                    val bad = png(10, 10).copyOf()
                    bad[17] = 0x01; bad[18] = 0x86.toByte(); bad[19] = 0xA0.toByte()
                    send(u, s, bad)
                },
            )
        var i = 0
        val good = png(30, 20)
        for ((what, attack) in cases) {
            told.clear()
            val user = "bad${i++}"
            val rep = report(user)
            val next = report(user, "20261015-010632")
            assertTrue(expect(user, rep), what)
            assertTrue(expect(user, next), what)
            attack(user, rep.nameWithoutExtension)
            assertEquals(1, refused(rep), "$what: the client must be told once: $told")
            // the rest of a correct upload afterwards is dropped quietly: one broken piece ends that report
            assertEquals(BugShotUploads.Status.REFUSED, send(user, rep, bytes).status, "$what: upload still open after a broken stream")
            assertEquals(1, refused(rep), "$what: told again for the rest of a closed upload: $told")
            assertEquals(0, saved(rep), what)
            assertTrue(rep.readLines().none { it.startsWith("Screenshot received") }, "$what: report claims a screenshot")
            // ... and the player's next report is not touched
            assertEquals(BugShotUploads.Status.COMPLETE, send(user, next, good).status, "$what: the next report lost its upload")
            assertEquals(listOf(next.nameWithoutExtension + ".png"), pngs(), what)
            File(dir, next.nameWithoutExtension + ".png").delete()
        }
    }

    @Test
    fun `never overwrites, hourly cap, full disk is told as refused`() {
        val a = png(50, 50)
        val rep = report("teamtest1")
        File(dir, rep.nameWithoutExtension + ".png").writeText("older file")
        assertTrue(expect("teamtest1", rep))
        assertEquals(BugShotUploads.Status.COMPLETE, send("teamtest1", rep, a).status)
        assertEquals("older file", File(dir, rep.nameWithoutExtension + ".png").readText())
        assertTrue(a.contentEquals(File(dir, rep.nameWithoutExtension + "-shot2.png").readBytes()))

        val now = System.currentTimeMillis()
        repeat(BugShotUploads.MAX_PER_HOUR) { assertTrue(BugShotUploads.expect("teamtest2", report("teamtest2", "20261015-02%04d".format(it)), now)) }
        assertTrue(!BugShotUploads.expect("teamtest2", report("teamtest2", "20261015-030000"), now), "request ${BugShotUploads.MAX_PER_HOUR + 1} in one hour")
        assertTrue(BugShotUploads.expect("teamtest2", report("teamtest2", "20261015-040000"), now + 60 * 60 * 1000L + 1), "an hour later again")
        assertTrue(BugShotUploads.expect("teamtest9", report("teamtest9")), "the cap is per player")

        BugShotUploads.minFreeBytes = Long.MAX_VALUE
        val full = report("teamtest3")
        assertTrue(expect("teamtest3", full))
        send("teamtest3", full, a)
        assertTrue(!File(dir, full.nameWithoutExtension + ".png").exists(), "written although the disk is full")
        assertEquals(listOf(0, 1), listOf(saved(full), refused(full)), "a full disk must be told as REFUSED: $told")
    }

    private fun wirePiece(
        stem: String,
        total: Int,
        offset: Int,
        data: ByteArray,
    ): ByteBuf {
        // exactly as RspsBugShot.writeChunk: p1 stem length, stem (ASCII), p4 total, p4 offset, data
        val b = Unpooled.buffer()
        val s = stem.toByteArray(Charsets.ISO_8859_1)
        b.writeByte(s.size)
        b.writeBytes(s)
        b.writeInt(total)
        b.writeInt(offset)
        b.writeBytes(data)
        return b
    }

    @Test
    fun `opcode 94 is the client's var-short screenshot packet and decodes byte-exact`() {
        assertEquals(ClientProtSizes.VARIABLE_SHORT, ClientProtSizes.SIZES[BugShotUploads.OPCODE])
        val yml = listOf(File("../data/packets.yml"), File("data/packets.yml")).first { it.exists() }
        val entry =
            ObjectMapper(YAMLFactory()).readTree(yml).path("in-packets").firstOrNull { it.path("opcode").asInt(-1) == BugShotUploads.OPCODE }
        assertNotNull(entry, "opcode 94 not in packets.yml in-packets")
        assertEquals("gg.rsmod.game.message.impl.BugShotChunkMessage", entry.path("message").asText())
        assertEquals("VARIABLE_SHORT", entry.path("type").asText())

        val structures = MessageStructureSet().load(yml)
        val decoders = MessageDecoderSet().also { it.init(structures) }
        assertNotNull(decoders.get(BugShotUploads.OPCODE), "no decoder for opcode 94")
        assertNotNull(decoders.getHandler(BugShotUploads.OPCODE), "no handler for opcode 94")

        val metadata =
            object : IPacketMetadata {
                override fun getType(opcode: Int): PacketType? = if (opcode == BugShotUploads.OPCODE) structures.get(opcode)?.type else if (opcode == 16) PacketType.FIXED else null

                override fun getLength(opcode: Int): Int = if (opcode == BugShotUploads.OPCODE) structures.get(opcode)?.length ?: 0 else 0

                override fun shouldIgnore(opcode: Int): Boolean = false
            }
        fun decode(payload: ByteBuf): BugShotChunkMessage {
            val wire = Unpooled.buffer()
            wire.writeByte(BugShotUploads.OPCODE)
            wire.writeShort(payload.readableBytes())
            wire.writeBytes(payload)
            wire.writeByte(16) // NO_TIMEOUT right behind it still decodes
            val channel = EmbeddedChannel(GamePacketDecoder(null, metadata))
            channel.writeInbound(wire)
            val packet = (channel.readInbound() as GamePacket)
            assertEquals(BugShotUploads.OPCODE, packet.opcode)
            val msg = decoders.get(BugShotUploads.OPCODE)!!.decode(packet.opcode, structures.get(packet.opcode)!!, gg.rsmod.net.packet.GamePacketReader(packet)) as BugShotChunkMessage
            packet.payload.release()
            assertEquals(16, (channel.readInbound() as GamePacket).opcode)
            assertNull(channel.readInbound())
            channel.finish()
            return msg
        }

        val data = ByteArray(clientChunk) { (it * 7).toByte() }
        for (stem in listOf("bug-20261015-022921-teamtest1", "bug-20261015-022921-999-abcdefghijklmnopqrst", "bug-20261015-022921-Mod Ash")) {
            val msg = decode(wirePiece(stem, 812_345, 2600, data))
            assertEquals(stem, msg.stem)
            assertEquals(812_345, msg.total)
            assertEquals(2600, msg.offset)
            assertTrue(data.contentEquals(msg.data))
            // the longest piece the client sends fits its 1350-byte write buffer (1 opcode + 2 size + payload)
            assertTrue(3 + 1 + stem.length + 8 + clientChunk <= 1350, "piece of $stem too big for the client's write buffer")
        }
        // the round-2 layout (no stem: p4 total first) and short / cut packets never throw and never name a report
        val old = Unpooled.buffer().writeInt(812_345).writeInt(0).writeBytes(data)
        assertEquals("", decode(old).stem)
        for (cut in listOf(Unpooled.buffer().writeByte(40).writeBytes("bug-1".toByteArray()), Unpooled.buffer().writeByte(3).writeBytes("bug".toByteArray()).writeInt(5))) {
            val msg = decode(cut)
            assertTrue(msg.total < 0 || msg.data.isEmpty(), "a cut packet decoded as a piece: $msg")
        }
    }

    @Test
    fun `GameSystem stores the pieces without using the per-cycle packet queue`() {
        val yml = listOf(File("../data/packets.yml"), File("data/packets.yml")).first { it.exists() }
        val structures = MessageStructureSet().load(yml)
        val decoders = MessageDecoderSet().also { it.init(structures) }
        val service = mockk<GameService>(relaxed = true)
        every { service.messageStructures } returns structures
        every { service.messageDecoders } returns decoders
        every { service.maxMessagesPerCycle } returns 30
        val client = mockk<Client>(relaxed = true)
        every { client.username } returns "teamtest1"
        val system = GameSystem(EmbeddedChannel(), mockk(relaxed = true), client, service)

        val bytes = png(400, 300, noisy = true)
        assertTrue(bytes.size > 100 * clientChunk, "test PNG must be hundreds of pieces: ${bytes.size}")
        val repA = report("teamtest1", "20261015-150000")
        val repB = report("teamtest1", "20261015-150001")
        assertTrue(expect("teamtest1", repA))
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        for ((i, piece) in pieces(bytes).withIndex()) {
            if (i == 50) assertTrue(expect("teamtest1", repB)) // a second report in the middle of the upload
            val payload = wirePiece(repA.nameWithoutExtension, bytes.size, piece.first, piece.second)
            system.receiveMessage(ctx, GamePacket(BugShotUploads.OPCODE, PacketType.VARIABLE_SHORT, payload))
            assertEquals(0, payload.refCnt(), "payload of piece at ${piece.first} not released")
        }
        for (piece in pieces(bytes)) {
            system.receiveMessage(ctx, GamePacket(BugShotUploads.OPCODE, PacketType.VARIABLE_SHORT, wirePiece(repB.nameWithoutExtension, bytes.size, piece.first, piece.second)))
        }
        val queue = GameSystem::class.java.getDeclaredField("messages").apply { isAccessible = true }.get(system) as BlockingQueue<*>
        assertEquals(0, queue.size, "screenshot pieces took ${queue.size} places in the click queue")
        assertTrue(bytes.contentEquals(File(dir, repA.nameWithoutExtension + ".png").readBytes()), "GameSystem did not store upload A")
        assertTrue(bytes.contentEquals(File(dir, repB.nameWithoutExtension + ".png").readBytes()), "GameSystem did not store upload B")
    }

    @Test
    fun `a player's connection reads at least twice the upload pace, so clicks are never read late`() {
        // live test server 2026-10-15 01:53: at the old 5 KB/s read limit the upload was throttled and the client's socket ring
        // overflowed ("Buffer overflow 1311 579") - the player was disconnected.
        assertTrue(
            gg.rsmod.game.protocol.ClientChannelInitializer.CHANNEL_READ_LIMIT >= 2L * BugShotUploads.CLIENT_RATE,
            "read limit ${gg.rsmod.game.protocol.ClientChannelInitializer.CHANNEL_READ_LIMIT} B/s",
        )
        val source = Files.readString(java.nio.file.Path.of("src", "main", "kotlin", "gg", "rsmod", "game", "protocol", "ClientChannelInitializer.kt"))
        assertTrue("ChannelTrafficShapingHandler(0, CHANNEL_READ_LIMIT, 1000)" in source, "the pipeline must use CHANNEL_READ_LIMIT")
        // the biggest screenshot still arrives at the client's pace before its request expires
        assertTrue(BugShotUploads.MAX_BYTES.toLong() * 1000 / BugShotUploads.CLIENT_RATE < BugShotUploads.REQUEST_TTL_MS)
    }
}
