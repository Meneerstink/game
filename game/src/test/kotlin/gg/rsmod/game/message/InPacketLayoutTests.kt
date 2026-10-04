package gg.rsmod.game.message

import gg.rsmod.game.message.decoder.WorldMapCloseDecoder
import gg.rsmod.game.message.impl.WorldMapCloseMessage
import gg.rsmod.net.packet.GamePacket
import gg.rsmod.net.packet.GamePacketReader
import gg.rsmod.net.packet.PacketType
import io.netty.buffer.Unpooled
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Trace PROBLEM 2026-10-04 "packet opcode=89 WorldMapCloseMessage left 4 of 4 bytes unread" (ROOT_CAUSES #34 class): a decoder
 * that does not read its whole fixed-size packet reads the wrong layout. Guards every registered fixed-size in-packet with the
 * real `data/packets.yml` and `MessageDecoderSet`, and the world map click's own layout (client CLICKWORLDMAP, p4_alt3).
 */
class InPacketLayoutTests {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "data/packets.yml").isFile }
    private val structures = MessageStructureSet().load(File(root, "data/packets.yml"))
    private val decoders = MessageDecoderSet().apply { init(structures) }

    private fun reader(
        opcode: Int,
        payload: ByteArray,
    ) = GamePacketReader(GamePacket(opcode, PacketType.FIXED, Unpooled.wrappedBuffer(payload)))

    @Test
    fun `every registered fixed-size in-packet is read to its last byte`() {
        val offenders = mutableListOf<String>()
        for (opcode in 0 until 256) {
            val structure = structures.get(opcode) ?: continue
            if (structure.ignore || structure.type != PacketType.FIXED || structure.length <= 0) continue
            val decoder = decoders.get(opcode) ?: continue
            val reader = reader(opcode, ByteArray(structure.length))
            try {
                decoder.decode(opcode, structure, reader)
            } catch (e: Exception) {
                offenders += "opcode $opcode ${decoder.javaClass.simpleName} threw $e"
                continue
            }
            if (reader.readableBytes != 0) offenders += "opcode $opcode ${decoder.javaClass.simpleName} left ${reader.readableBytes} of ${structure.length} bytes unread"
        }
        assertTrue(offenders.isEmpty(), offenders.joinToString("\n"))
    }

    @Test
    fun `the world map click decodes the tile the client packed`() {
        // Client MainLogicManager: p4_alt3(clickedY | clickedLevel << 28 | clickedX << 14); p4_alt3 writes v>>16, v>>24, v, v>>8.
        val x = 3157
        val z = 3482
        val level = 1
        val value = z or (level shl 28) or (x shl 14)
        val payload = byteArrayOf((value shr 16).toByte(), (value shr 24).toByte(), value.toByte(), (value shr 8).toByte())
        val opcode = 89
        val structure = structures.get(opcode)!!
        val reader = reader(opcode, payload)
        val message = WorldMapCloseDecoder().decode(opcode, structure, reader)
        assertEquals(WorldMapCloseMessage(x = x, z = z, level = level), message)
        assertEquals(0, reader.readableBytes)
        assertEquals(4, structure.length)
    }
}
