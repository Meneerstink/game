package gg.rsmod.game.message

import gg.rsmod.game.message.encoder.SynthSoundEncoder
import gg.rsmod.game.message.impl.SynthSoundMessage
import gg.rsmod.net.packet.DataType
import gg.rsmod.util.ServerProperties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.nio.file.Paths

/** Pins ServerProt.SYNTH_SOUND(106, 8) to Void/Novite's 667 field contract. */
class SynthSoundPacketTests {
    @Test
    fun `SYNTH_SOUND is registered with the client packet length and field order`() {
        val packet = outPacket("gg.rsmod.game.message.impl.SynthSoundMessage")
        assertEquals(106, packet.get<Int>("opcode"))
        assertEquals("FIXED", packet.get<String>("type"))

        val fields = packet.get<ArrayList<*>>("structure")!!
        val names = fields.map { (it as LinkedHashMap<*, *>) ["name"] }
        val types = fields.map { DataType.valueOf((it as LinkedHashMap<*, *>) ["type"] as String) }
        assertEquals(listOf("sound", "loops", "delay", "volume", "rate"), names)
        assertEquals(
            listOf(DataType.SHORT, DataType.BYTE, DataType.SHORT, DataType.BYTE, DataType.SHORT),
            types,
        )
        assertEquals(8, types.sumOf { it.bytes })
    }

    @Test
    fun `SYNTH_SOUND encoder keeps repeat volume and rate distinct`() {
        val message = SynthSoundMessage(sound = 3888, loops = 1, delay = 20, volume = 255, rate = 256)
        val encoder = SynthSoundEncoder()
        assertEquals(3888, encoder.extract(message, "sound"))
        assertEquals(1, encoder.extract(message, "loops"))
        assertEquals(20, encoder.extract(message, "delay"))
        assertEquals(255, encoder.extract(message, "volume"))
        assertEquals(256, encoder.extract(message, "rate"))
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
