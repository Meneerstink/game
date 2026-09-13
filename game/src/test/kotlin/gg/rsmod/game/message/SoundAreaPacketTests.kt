package gg.rsmod.game.message

import gg.rsmod.game.message.encoder.SoundAreaEncoder
import gg.rsmod.game.message.impl.SoundAreaMessage
import gg.rsmod.net.packet.DataType
import gg.rsmod.util.ServerProperties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.nio.file.Paths

/** Pins the revision-667 eight-byte SOUND_AREA contract used by ServerProt 65. */
class SoundAreaPacketTests {
    @Test
    fun `SOUND_AREA is registered with the client packet length and field order`() {
        val packet = outPacket("gg.rsmod.game.message.impl.SoundAreaMessage")
        assertEquals(65, packet.get<Int>("opcode"))
        assertEquals("FIXED", packet.get<String>("type"))

        val fields = packet.get<ArrayList<*>>("structure")!!
        val names = fields.map { (it as LinkedHashMap<*, *>)["name"] }
        val types = fields.map { DataType.valueOf((it as LinkedHashMap<*, *>)["type"] as String) }
        assertEquals(listOf("tile", "sound", "settings", "delay", "playback_volume", "rate"), names)
        assertEquals(
            listOf(DataType.BYTE, DataType.SHORT, DataType.BYTE, DataType.BYTE, DataType.BYTE, DataType.SHORT),
            types,
        )
        assertEquals("ServerProt.SOUND_AREA payload length", 8, types.sumOf { it.bytes })
    }

    @Test
    fun `SOUND_AREA encoder publishes all client fields`() {
        val message = SoundAreaMessage(0x35, 3888, 7, 1, 20, 255, 255)
        val encoder = SoundAreaEncoder()
        assertEquals(0x35, encoder.extract(message, "tile"))
        assertEquals(3888, encoder.extract(message, "sound"))
        assertEquals(0x71, encoder.extract(message, "settings"))
        assertEquals(20, encoder.extract(message, "delay"))
        assertEquals(255, encoder.extract(message, "playback_volume"))
        assertEquals(255, encoder.extract(message, "rate"))
    }

    private fun outPacket(message: String): ServerProperties {
        val properties = ServerProperties().loadYaml(Paths.get("..", "data", "packets.yml").toFile())
        val packets = properties.get<ArrayList<*>>("out-packets")!!
        val match = packets.map { it as LinkedHashMap<*, *> }.firstOrNull { it["message"] == message }
        assertNotNull("$message is not registered in packets.yml", match)
        @Suppress("UNCHECKED_CAST")
        return ServerProperties().loadMap(match as Map<String, Any>)
    }
}
