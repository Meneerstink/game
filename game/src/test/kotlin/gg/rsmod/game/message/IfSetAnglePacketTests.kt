package gg.rsmod.game.message

import gg.rsmod.game.message.encoder.IfSetAngleEncoder
import gg.rsmod.game.message.impl.IfSetAngleMessage
import gg.rsmod.net.packet.DataType
import gg.rsmod.util.ServerProperties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.nio.file.Paths

/**
 * A4: pins IF_SETANGLE (2009scape sendAngleOnInterface) to the 667 client reader, ServerConnectionReader IF_SETANGLE
 * (ServerProt 122, size 10): zoom2d g2_alt2 (big-endian, low byte +128), yan2d g2_alt3 (little-endian, low byte +128),
 * idAndSlot g4_alt1 (little-endian int), xan2d g2_alt3.
 */
class IfSetAnglePacketTests {
    @Test
    fun `IF_SETANGLE is registered with the client packet length, field order and byte orders`() {
        val packet = outPacket("gg.rsmod.game.message.impl.IfSetAngleMessage")
        assertEquals(122, packet.get<Int>("opcode"))
        assertEquals("FIXED", packet.get<String>("type"))
        val fields = packet.get<ArrayList<*>>("structure")!!.map { it as LinkedHashMap<*, *> }
        assertEquals(listOf("zoom", "yan", "hash", "xan"), fields.map { it["name"] })
        assertEquals(listOf("SHORT", "SHORT", "INT", "SHORT"), fields.map { it["type"] })
        assertEquals(listOf(null, "LITTLE", "LITTLE", "LITTLE"), fields.map { it["order"] })
        assertEquals(listOf("ADD", "ADD", null, "ADD"), fields.map { it["trans"] })
        assertEquals("ServerProt.IF_SETANGLE payload length", 10, fields.sumOf { DataType.valueOf(it["type"] as String).bytes })
    }

    @Test
    fun `IF_SETANGLE encoder publishes every field`() {
        val message = IfSetAngleMessage(hash = (243 shl 16) or 2, zoom = 3700, xan = 40, yan = 1882)
        val encoder = IfSetAngleEncoder()
        assertEquals((243 shl 16) or 2, encoder.extract(message, "hash"))
        assertEquals(3700, encoder.extract(message, "zoom"))
        assertEquals(40, encoder.extract(message, "xan"))
        assertEquals(1882, encoder.extract(message, "yan"))
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
