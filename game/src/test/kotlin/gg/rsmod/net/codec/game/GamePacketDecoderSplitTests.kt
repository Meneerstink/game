package gg.rsmod.net.codec.game

import gg.rsmod.net.packet.GamePacket
import gg.rsmod.net.packet.IPacketMetadata
import gg.rsmod.net.packet.PacketType
import gg.rsmod.util.io.IsaacRandom
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * T2 AUDIT T2-93 (2026-10-15 05:22, owner window 1874x970): a 2.18 MB bug-report screenshot = 1741 var-short packets of
 * opcode 94. 65 s into the upload a TCP read ended between the two length bytes of one piece; decodeLength checked only
 * `isReadable` (1 byte) and read a short -> DecoderException "readerIndex(1) + length(2) exceeds writerIndex(2)"
 * (GamePacketDecoder.kt:117), the channel closed and the player was logged out. Over the internet a read can end at ANY
 * byte, so this is not about screenshots: every packet the client sends must decode the same however the stream is cut.
 *
 * Donor: 2009scape GameReadEvent.getPacketSize (core/net/event/GameReadEvent.java:126-140): for a var-byte header it waits
 * until 1 byte, for a var-short header until 2 bytes are there ("if (buffer.remaining() < 2) { queueBuffer(opcode); return -1 }")
 * and only then reads the length.
 *
 * Class covered: EVERY opcode of the 667 client (ClientProtSizes: fixed, empty, var-byte, var-short) on both decoder paths
 * (registered in packets.yml / skipped as unregistered), followed by a fixed packet, cut into two reads at EVERY byte and fed
 * one byte per read; plus a whole owner-window screenshot upload (1741 ISAAC-ciphered pieces of opcode 94) cut into random
 * reads of 1..1460 bytes. Every packet must come out once, whole and in order, with the connection still open.
 */
class GamePacketDecoderSplitTests {
    private fun metadata(registered: Boolean) =
        object : IPacketMetadata {
            override fun getType(opcode: Int): PacketType? {
                if (!registered && opcode != COUNT_DIALOG) return null
                return when (ClientProtSizes.SIZES[opcode]) {
                    null -> null
                    ClientProtSizes.VARIABLE_BYTE -> PacketType.VARIABLE_BYTE
                    ClientProtSizes.VARIABLE_SHORT -> PacketType.VARIABLE_SHORT
                    else -> PacketType.FIXED
                }
            }

            override fun getLength(opcode: Int): Int = ClientProtSizes.SIZES[opcode]?.coerceAtLeast(0) ?: 0

            override fun shouldIgnore(opcode: Int): Boolean = false
        }

    /** One packet as the client writes it: opcode, var-byte/var-short length, payload. */
    private fun packet(
        out: ByteBuf,
        opcode: Int,
        size: Int,
        payload: ByteArray,
    ) {
        out.writeByte(opcode)
        when (size) {
            ClientProtSizes.VARIABLE_BYTE -> out.writeByte(payload.size)
            ClientProtSizes.VARIABLE_SHORT -> out.writeShort(payload.size)
        }
        out.writeBytes(payload)
    }

    private fun payloadFor(
        opcode: Int,
        size: Int,
    ): ByteArray {
        val n =
            when (size) {
                ClientProtSizes.VARIABLE_BYTE -> 7
                // above 255 so both length bytes matter (0x01 0x2C)
                ClientProtSizes.VARIABLE_SHORT -> 300
                else -> size
            }
        return ByteArray(n) { (it * 31 + opcode).toByte() }
    }

    /** Feeds [stream] in the reads [cuts] makes; returns "opcode/size/sum" of every packet, or the failure. */
    private fun decode(
        stream: ByteArray,
        cuts: List<Int>,
        registered: Boolean,
    ): Pair<List<String>, String?> {
        val channel = EmbeddedChannel(GamePacketDecoder(null, metadata(registered)))
        val got = ArrayList<String>()
        var from = 0
        try {
            for (to in cuts + stream.size) {
                if (to > from) channel.writeInbound(Unpooled.wrappedBuffer(stream, from, to - from))
                from = to
                while (true) {
                    val p = channel.readInbound() as GamePacket? ?: break
                    try {
                        got += "${p.opcode}/${p.payload.readableBytes()}/${sum(p.payload)}"
                    } finally {
                        p.payload.release()
                    }
                }
            }
            if (!channel.isOpen) return got to "the connection was closed"
        } catch (e: Exception) {
            return got to "${e.javaClass.simpleName}: ${e.message}"
        } finally {
            runCatching { channel.finish() }
        }
        return got to null
    }

    private fun sum(buf: ByteBuf): Int {
        var s = 0
        for (i in buf.readerIndex() until buf.writerIndex()) s = s * 31 + buf.getByte(i)
        return s
    }

    @Test
    fun `every client packet decodes the same however the reads cut it`() {
        val failures = ArrayList<String>()
        var cases = 0
        val tail = byteArrayOf(0x0A, 0x0B, 0x0C, 0x0D)
        val tailSum = tail.fold(0) { s, b -> s * 31 + b }
        assertTrue(ClientProtSizes.SIZES.values.count { it == ClientProtSizes.VARIABLE_SHORT } >= 1, "ClientProtSizes lost its var-short packets")
        for (registered in listOf(true, false)) {
            for ((opcode, size) in ClientProtSizes.SIZES.toSortedMap()) {
                val payload = payloadFor(opcode, size)
                val buf = Unpooled.buffer()
                packet(buf, opcode, size, payload)
                packet(buf, COUNT_DIALOG, 4, tail)
                val stream = ByteArray(buf.readableBytes()).also { buf.readBytes(it) }
                val want = ArrayList<String>()
                if (registered || opcode == COUNT_DIALOG) want += "$opcode/${payload.size}/${payload.fold(0) { s, b -> s * 31 + b }}"
                want += "$COUNT_DIALOG/4/$tailSum"
                val label = "opcode $opcode (size $size, ${if (registered) "registered" else "unregistered"})"
                // two reads, cut at every byte
                for (cut in 1 until stream.size) {
                    cases++
                    val (got, error) = decode(stream, listOf(cut), registered)
                    if (error != null || got != want) {
                        failures += "$label cut after byte $cut of ${stream.size}: ${error ?: "got $got want $want"}"
                    }
                }
                // one byte per read
                cases++
                val (got, error) = decode(stream, (1 until stream.size).toList(), registered)
                if (error != null || got != want) failures += "$label one byte per read: ${error ?: "got $got want $want"}"
            }
        }
        assertTrue(
            failures.isEmpty(),
            "${failures.size} of $cases cut streams broke the decoder (first 40):\n" + failures.take(40).joinToString("\n"),
        )
    }

    /**
     * The audit case end to end: the owner's 1874x970 window gives a ~2.18 MB PNG = 1741 pieces of opcode 94 (stem + p4 total
     * + p4 offset + 1250 bytes, RspsBugShot.writeChunk), ISAAC-ciphered opcodes like the real client, with a few of the
     * player's own packets in between, cut into random reads of 1..1460 bytes (one TCP segment at most) - three seeds.
     */
    @Test
    fun `an owner-window screenshot upload survives random reads with ISAAC`() {
        val stem = "bug-20261015-052119-teamtest2".toByteArray(Charsets.ISO_8859_1)
        val total = 2_176_000
        val failures = ArrayList<String>()
        for (seed in 1..3) {
            val isaacSeed = intArrayOf(11 * seed, 22, 33, 44)
            val client = IsaacRandom(isaacSeed)
            val buf = Unpooled.buffer(total + 200_000)
            val want = ArrayList<String>()
            var offset = 0
            var n = 0
            while (offset < total) {
                val len = minOf(1250, total - offset)
                val payload = Unpooled.buffer()
                payload.writeByte(stem.size)
                payload.writeBytes(stem)
                payload.writeInt(total)
                payload.writeInt(offset)
                payload.writeBytes(ByteArray(len) { (offset + it).toByte() })
                val body = ByteArray(payload.readableBytes()).also { payload.readBytes(it) }
                buf.writeByte(BUGSHOT + client.nextInt())
                buf.writeShort(body.size)
                buf.writeBytes(body)
                want += "$BUGSHOT/${body.size}/${body.fold(0) { s, b -> s * 31 + b }}"
                offset += len
                if (++n % 97 == 0) {
                    // a click of the player's own in between (fixed 4 bytes)
                    buf.writeByte(COUNT_DIALOG + client.nextInt())
                    buf.writeInt(n)
                    val b = ByteArray(4).also { Unpooled.buffer().writeInt(n).readBytes(it) }
                    want += "$COUNT_DIALOG/4/${b.fold(0) { s, x -> s * 31 + x }}"
                }
            }
            val stream = ByteArray(buf.readableBytes()).also { buf.readBytes(it) }
            val rnd = Random(seed)
            val cuts = ArrayList<Int>()
            var at = 0
            while (true) {
                at += 1 + rnd.nextInt(1460)
                if (at >= stream.size) break
                cuts += at
            }
            val channel = EmbeddedChannel(GamePacketDecoder(IsaacRandom(isaacSeed), metadata(true)))
            val got = ArrayList<String>()
            var from = 0
            var error: String? = null
            try {
                for (to in cuts + stream.size) {
                    channel.writeInbound(Unpooled.wrappedBuffer(stream, from, to - from))
                    from = to
                    while (true) {
                        val p = channel.readInbound() as GamePacket? ?: break
                        try {
                            got += "${p.opcode}/${p.payload.readableBytes()}/${sum(p.payload)}"
                        } finally {
                            p.payload.release()
                        }
                    }
                }
                if (!channel.isOpen) error = "the connection was closed"
            } catch (e: Exception) {
                error = "${e.javaClass.simpleName}: ${e.message} after ${got.size} of ${want.size} packets"
            } finally {
                runCatching { channel.finish() }
            }
            if (error != null) {
                failures += "seed $seed (${cuts.size + 1} reads): $error"
            } else if (got != want) {
                failures += "seed $seed: ${got.size} packets decoded, ${want.size} sent, first difference at ${got.indices.firstOrNull { got[it] != want.getOrNull(it) }}"
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    private companion object {
        /** ClientProt RESUME_P_COUNTDIALOG (3, fixed 4 bytes). */
        const val COUNT_DIALOG = 3

        /** ClientProt RSPS_BUGSHOT_CHUNK (94, var-short) - BugShotUploads.OPCODE. */
        const val BUGSHOT = 94
    }
}
