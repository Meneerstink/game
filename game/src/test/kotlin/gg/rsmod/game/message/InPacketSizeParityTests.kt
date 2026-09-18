package gg.rsmod.game.message

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.rsmod.net.codec.game.ClientProtSizes
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every client->server packet registered in `data/packets.yml` must have exactly the size the rev-667 client sends
 * (ClientProtSizes, from ClientProt.java). A wrong size, like an unregistered opcode before the decoder fallback,
 * desynchronises every following packet and the ISAAC opcode stream (owner 2026-09-18 P0).
 */
class InPacketSizeParityTests {
    @Test
    fun `registered in-packets match the client's own packet sizes`() {
        val file = listOf(File("../data/packets.yml"), File("data/packets.yml")).first { it.exists() }
        val root = ObjectMapper(YAMLFactory()).readTree(file)
        val offenders = mutableListOf<String>()
        root.path("in-packets").forEach { entry ->
            val name = entry.path("message").asText().substringAfterLast('.')
            val opcodes =
                if (entry.has("opcodes")) entry.path("opcodes").asText().split(',').map { it.trim().toInt() } else listOf(entry.path("opcode").asInt())
            val declared =
                when (entry.path("type").asText()) {
                    "VARIABLE_BYTE" -> ClientProtSizes.VARIABLE_BYTE
                    "VARIABLE_SHORT" -> ClientProtSizes.VARIABLE_SHORT
                    else -> entry.path("length").asInt(0)
                }
            opcodes.forEach { opcode ->
                val client = ClientProtSizes.SIZES[opcode]
                if (client == null) {
                    offenders += "$name opcode $opcode is not a client packet"
                } else if (client != declared) {
                    offenders += "$name opcode $opcode declared $declared, client sends $client"
                }
            }
        }
        assertEquals(emptyList(), offenders)
    }

    @Test
    fun `the size table covers the whole rev-667 client protocol`() {
        assertEquals(94, ClientProtSizes.SIZES.size)
        assertTrue(ClientProtSizes.SIZES[42] == 15, "OPLOCT is a fixed 15-byte packet")
    }
}
