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
 * server" - friends play from other PCs, the server moves to a VPS). The screenshot now comes to the server over the game
 * connection (78 client RspsBugShot.pump, opcode 94) and [BugShotUploads] writes it next to the report. The whole class:
 * - every PNG size the client can send (one piece, exact multiples, a remainder, megabytes) arrives byte-exact as
 *   <report stem>.png next to the report, for every kind of username, and the report says "Screenshot received:";
 * - nothing is written for: no request, another player's report, an expired request, a second upload, a gap / overlap /
 *   past-the-end piece, a size change, too big, not a PNG, a silly image size; the file name is never the client's;
 * - never overwrites; a newer report replaces an open request; at most MAX_PER_HOUR requests per hour; a full disk writes nothing;
 * - the packet path: opcode 94 is a var-short client packet in packets.yml and ClientProtSizes, decodes byte-exact from the
 *   bytes the client writes, and GameSystem hands it to BugShotUploads without putting it in the per-cycle queue.
 */
class BugShotUploadsTests {
    private lateinit var dir: File
    private val clientChunk = 1300 // RspsBugShot.CHUNK

    @BeforeTest
    fun setUp() {
        BugShotUploads.clear()
        BugShotUploads.executor = { it.run() }
        BugShotUploads.minFreeBytes = 1L shl 30
        dir = Files.createTempDirectory("bugshot-up").toFile()
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

    /** What the client does: 1300-byte pieces, in order. Returns the last result. */
    private fun send(
        user: String,
        bytes: ByteArray,
        chunk: Int = clientChunk,
    ): BugShotUploads.Result {
        var last = BugShotUploads.Result(BugShotUploads.Status.REFUSED, "nothing sent")
        var off = 0
        while (off < bytes.size) {
            val n = minOf(chunk, bytes.size - off)
            last = BugShotUploads.receive(user, bytes.size, off, bytes.copyOfRange(off, off + n))
            off += n
            if (last.status == BugShotUploads.Status.REFUSED) return last
        }
        return last
    }

    private fun pngs() = dir.listFiles { f -> f.name.endsWith(".png") }!!.map { it.name }.sorted()

    @Test
    fun `every screenshot size arrives byte-exact next to its report, for every kind of name`() {
        val small = png(20, 10)
        val sizes =
            listOf(
                small, // one piece
                png(795, 503), // fixed client + sidebar
                png(974, 581, noisy = true), // resizable, ~1.7 MB, hundreds of pieces
            )
        val names = listOf("teamtest1", "Mod Ash", "a", "abcdefghijkl", "pk3r_99", "Zezima-2")
        var stamp = 100000
        for (name in names) {
            for (bytes in sizes) {
                val rep = report(name, "20261015-${stamp++}")
                assertTrue(BugShotUploads.expect(name, rep))
                val result = send(name.uppercase(), bytes) // the login name's case does not matter
                assertEquals(BugShotUploads.Status.COMPLETE, result.status, "$name ${bytes.size} bytes: ${result.detail}")
                val out = File(dir, rep.nameWithoutExtension + ".png")
                assertTrue(out.isFile, "$name: ${out.name} not written; dir has ${pngs()}")
                assertTrue(bytes.contentEquals(out.readBytes()), "$name: ${out.name} is not the PNG the client sent")
                val line = rep.readLines().last()
                assertTrue(line.startsWith("Screenshot received: ${out.name} ("), "$name: report line '$line'")
            }
        }
        // a PNG whose size is an exact multiple of the piece size (no short last piece)
        val base = png(64, 64, noisy = true)
        val padded = base.copyOf(base.size - base.size % clientChunk + clientChunk)
        val rep = report("teamtest1", "20261015-999999")
        assertTrue(BugShotUploads.expect("teamtest1", rep))
        assertEquals(0, padded.size % clientChunk)
        assertEquals(BugShotUploads.Status.COMPLETE, send("teamtest1", padded).status)
        assertTrue(padded.contentEquals(File(dir, rep.nameWithoutExtension + ".png").readBytes()))
        assertTrue(dir.listFiles()!!.none { it.name.endsWith(".part") }, "temporary files left: ${dir.list()!!.toList()}")
    }

    @Test
    fun `nothing is written without a request, for another player's report, or after the request expired`() {
        val bytes = png(100, 60)
        assertEquals(BugShotUploads.Status.REFUSED, send("teamtest1", bytes).status, "no bug report, no upload")

        val rep = report("teamtest1")
        assertTrue(BugShotUploads.expect("teamtest1", rep))
        assertEquals(BugShotUploads.Status.REFUSED, send("teamtest2", bytes).status, "another player cannot fill teamtest1's report")
        assertEquals(emptyList(), pngs())

        val old = report("teamtest3")
        val t0 = System.currentTimeMillis() - BugShotUploads.REQUEST_TTL_MS - 1
        assertTrue(BugShotUploads.expect("teamtest3", old, t0))
        assertEquals(BugShotUploads.Status.REFUSED, BugShotUploads.receive("teamtest3", bytes.size, 0, bytes).status, "expired")
        assertEquals(emptyList(), pngs())

        // the real one still works, exactly once
        assertEquals(BugShotUploads.Status.COMPLETE, send("teamtest1", bytes).status)
        assertEquals(listOf(rep.nameWithoutExtension + ".png"), pngs())
        assertEquals(BugShotUploads.Status.REFUSED, send("teamtest1", bytes).status, "a second upload for the same report")
        assertEquals(listOf(rep.nameWithoutExtension + ".png"), pngs())
    }

    @Test
    fun `a broken stream writes nothing and closes the upload`() {
        val bytes = png(300, 200, noisy = true)
        val cases =
            linkedMapOf<String, (String) -> Unit>(
                "first piece not at 0" to { u -> BugShotUploads.receive(u, bytes.size, 1300, bytes.copyOfRange(1300, 2600)) },
                "gap" to { u ->
                    BugShotUploads.receive(u, bytes.size, 0, bytes.copyOfRange(0, 1300))
                    BugShotUploads.receive(u, bytes.size, 2600, bytes.copyOfRange(2600, 3900))
                },
                "overlap" to { u ->
                    BugShotUploads.receive(u, bytes.size, 0, bytes.copyOfRange(0, 1300))
                    BugShotUploads.receive(u, bytes.size, 1000, bytes.copyOfRange(1000, 2300))
                },
                "size changes" to { u ->
                    BugShotUploads.receive(u, bytes.size, 0, bytes.copyOfRange(0, 1300))
                    BugShotUploads.receive(u, bytes.size + 5, 1300, bytes.copyOfRange(1300, 2600))
                },
                "past the end" to { u ->
                    BugShotUploads.receive(u, 1400, 0, bytes.copyOfRange(0, 1300))
                    BugShotUploads.receive(u, 1400, 1300, bytes.copyOfRange(1300, 2600))
                },
                "piece too big" to { u -> BugShotUploads.receive(u, bytes.size, 0, bytes.copyOfRange(0, BugShotUploads.MAX_CHUNK + 1)) },
                "empty piece" to { u -> BugShotUploads.receive(u, bytes.size, 0, ByteArray(0)) },
                "too big" to { u -> BugShotUploads.receive(u, BugShotUploads.MAX_BYTES + 1, 0, bytes.copyOfRange(0, 1300)) },
                "negative size" to { u -> BugShotUploads.receive(u, -1, 0, bytes.copyOfRange(0, 1300)) },
                "not a PNG" to { u -> send(u, "GIF89a not a png at all, just bytes".repeat(100).toByteArray()) },
                "PNG of 0 pixels wide" to { u ->
                    val bad = png(10, 10).copyOf()
                    bad[16] = 0; bad[17] = 0; bad[18] = 0; bad[19] = 0
                    send(u, bad)
                },
                "PNG of 100000 pixels" to { u ->
                    val bad = png(10, 10).copyOf()
                    bad[17] = 0x01; bad[18] = 0x86.toByte(); bad[19] = 0xA0.toByte()
                    send(u, bad)
                },
            )
        var i = 0
        for ((what, attack) in cases) {
            val user = "bad${i++}"
            val rep = report(user)
            assertTrue(BugShotUploads.expect(user, rep), what)
            attack(user)
            // the rest of a correct upload afterwards is refused too: one broken piece ends the request
            assertEquals(BugShotUploads.Status.REFUSED, send(user, bytes).status, "$what: upload still open after a broken stream")
            assertEquals(emptyList(), pngs(), "$what wrote a file")
            assertTrue(rep.readLines().none { it.startsWith("Screenshot received") }, "$what: report claims a screenshot")
        }
    }

    @Test
    fun `never overwrites, newest report wins, hourly cap, full disk`() {
        val a = png(50, 50)
        val rep = report("teamtest1")
        File(dir, rep.nameWithoutExtension + ".png").writeText("older file")
        assertTrue(BugShotUploads.expect("teamtest1", rep))
        assertEquals(BugShotUploads.Status.COMPLETE, send("teamtest1", a).status)
        assertEquals("older file", File(dir, rep.nameWithoutExtension + ".png").readText())
        assertTrue(a.contentEquals(File(dir, rep.nameWithoutExtension + "-shot2.png").readBytes()))

        val first = report("teamtest2", "20261015-010700")
        val second = report("teamtest2", "20261015-010701")
        assertTrue(BugShotUploads.expect("teamtest2", first))
        assertTrue(BugShotUploads.expect("teamtest2", second))
        assertEquals(BugShotUploads.Status.COMPLETE, send("teamtest2", a).status)
        assertTrue(File(dir, second.nameWithoutExtension + ".png").isFile, "the newest report gets the picture")
        assertTrue(!File(dir, first.nameWithoutExtension + ".png").exists())

        val now = System.currentTimeMillis()
        repeat(BugShotUploads.MAX_PER_HOUR - 2) { assertTrue(BugShotUploads.expect("teamtest2", report("teamtest2", "20261015-02%04d".format(it)), now)) }
        assertTrue(!BugShotUploads.expect("teamtest2", report("teamtest2", "20261015-030000"), now), "request ${BugShotUploads.MAX_PER_HOUR + 1} in one hour")
        assertTrue(BugShotUploads.expect("teamtest2", report("teamtest2", "20261015-040000"), now + 60 * 60 * 1000L + 1), "an hour later again")
        assertTrue(BugShotUploads.expect("teamtest9", report("teamtest9")), "the cap is per player")

        BugShotUploads.minFreeBytes = Long.MAX_VALUE
        val full = report("teamtest3")
        assertTrue(BugShotUploads.expect("teamtest3", full))
        send("teamtest3", a)
        assertTrue(!File(dir, full.nameWithoutExtension + ".png").exists(), "written although the disk is full")
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

        // the bytes exactly as RspsBugShot.writeChunk puts them on the wire (no ISAAC here): opcode, p2 size, p4 total, p4 offset, data
        val data = ByteArray(1300) { (it * 7).toByte() }
        val wire = Unpooled.buffer()
        wire.writeByte(BugShotUploads.OPCODE)
        wire.writeShort(8 + data.size)
        wire.writeInt(812_345)
        wire.writeInt(2600)
        wire.writeBytes(data)
        wire.writeByte(16) // NO_TIMEOUT right behind it still decodes
        val metadata =
            object : IPacketMetadata {
                override fun getType(opcode: Int): PacketType? = if (opcode == BugShotUploads.OPCODE) structures.get(opcode)?.type else if (opcode == 16) PacketType.FIXED else null

                override fun getLength(opcode: Int): Int = if (opcode == BugShotUploads.OPCODE) structures.get(opcode)?.length ?: 0 else 0

                override fun shouldIgnore(opcode: Int): Boolean = false
            }
        val channel = EmbeddedChannel(GamePacketDecoder(null, metadata))
        channel.writeInbound(wire)
        val packet = (channel.readInbound() as GamePacket)
        assertEquals(BugShotUploads.OPCODE, packet.opcode)
        val msg = decoders.get(BugShotUploads.OPCODE)!!.decode(packet.opcode, structures.get(packet.opcode)!!, gg.rsmod.net.packet.GamePacketReader(packet)) as BugShotChunkMessage
        packet.payload.release()
        assertEquals(812_345, msg.total)
        assertEquals(2600, msg.offset)
        assertTrue(data.contentEquals(msg.data))
        assertEquals(16, (channel.readInbound() as GamePacket).opcode)
        assertNull(channel.readInbound())
        channel.finish()
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
        val rep = report("teamtest1")
        assertTrue(BugShotUploads.expect("teamtest1", rep))
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        var off = 0
        while (off < bytes.size) {
            val n = minOf(clientChunk, bytes.size - off)
            val payload = Unpooled.buffer()
            payload.writeInt(bytes.size)
            payload.writeInt(off)
            payload.writeBytes(bytes, off, n)
            system.receiveMessage(ctx, GamePacket(BugShotUploads.OPCODE, PacketType.VARIABLE_SHORT, payload))
            assertEquals(0, payload.refCnt(), "payload of piece at $off not released")
            off += n
        }
        val queue = GameSystem::class.java.getDeclaredField("messages").apply { isAccessible = true }.get(system) as BlockingQueue<*>
        assertEquals(0, queue.size, "screenshot pieces took ${queue.size} places in the click queue")
        assertTrue(bytes.contentEquals(File(dir, rep.nameWithoutExtension + ".png").readBytes()), "GameSystem did not store the upload")
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
