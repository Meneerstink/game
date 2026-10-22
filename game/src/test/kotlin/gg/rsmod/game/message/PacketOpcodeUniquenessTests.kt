package gg.rsmod.game.message

import gg.rsmod.game.message.impl.OpLocTMessage
import gg.rsmod.net.packet.GamePacket
import gg.rsmod.net.packet.GamePacketReader
import gg.rsmod.net.packet.PacketType
import gg.rsmod.util.ServerProperties
import io.netty.buffer.Unpooled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Item PKT / N2 (new root-cause class: "two packets.yml entries for one opcode - the later put() silently replaces the
 * earlier decoder + handler"). `MessageStructureSet.load` and `MessageDecoderSet.put` index by opcode, so a second
 * in-packet entry at an opcode wins without a word. That is how ClientProt OPLOCT (42) - the ONE packet the 667 client
 * sends for both "Use item -> object" and "Cast spell / familiar special -> object" (MiniMenu TGT_LOC) - ended up at
 * OpLocUHandler, which drops every spell click: Charge orb on an obelisk, Compost mound / Beaver / Hydra specials.
 *
 * Guards the whole class: every client opcode has exactly one owner, a duplicate fails at load, server-side duplicates
 * are only the known variants of one ServerProt packet, and OPLOCT decodes both of its uses through one handler.
 */
class PacketOpcodeUniquenessTests {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "data/packets.yml").isFile }
    private val packetsFile = File(root, "data/packets.yml")

    /** opcode -> every message class registered at it, for one section of packets.yml (opcode and opcodes lists). */
    private fun owners(section: String): Map<Int, List<String>> {
        val packets = ServerProperties().loadYaml(packetsFile).get<ArrayList<Any>>(section)!!
        val result = linkedMapOf<Int, MutableList<String>>()
        packets.forEach { packet ->
            val values = packet as LinkedHashMap<*, *>
            val cls = (values["message"] as String).substringAfterLast('.')
            val opcodes =
                when {
                    values.containsKey("opcodes") -> values["opcodes"].toString().trim().split(",").map { it.trim().toInt() }
                    values.containsKey("opcode") -> listOf(values["opcode"] as Int)
                    else -> emptyList()
                }
            opcodes.forEach { result.getOrPut(it) { mutableListOf() }.add(cls) }
        }
        return result
    }

    @Test
    fun `every client opcode is owned by exactly one in-packet entry`() {
        val duplicates = owners("in-packets").filterValues { it.size > 1 }
        assertTrue("client opcodes registered twice (the later entry silently wins): $duplicates", duplicates.isEmpty())
    }

    @Test
    fun `server opcodes shared by two classes are only the known variants of one ServerProt packet`() {
        // Client ServerProt has one packet at each of these; the server writes it through two message layouts.
        val variants =
            mapOf(
                0 to setOf("MusicEffectMessage", "RebuildLoginMessage"), // MIDI_JINGLE (RebuildLogin goes in the login block)
                81 to setOf("HintArrowMessage", "HintArrowTileMessage"), // HINT_ARROW: npc/player vs tile layout
                91 to setOf("PublicChatMessage", "QuickChatPublicOutMessage"), // MESSAGE_PUBLIC: text vs quick chat
            )
        val unexpected =
            owners("out-packets").filterValues { it.size > 1 }.filter { (op, classes) ->
                classes.size != classes.toSet().size || variants[op] != classes.toSet()
            }
        assertTrue("server opcodes registered twice outside the known variants: $unexpected", unexpected.isEmpty())
    }

    @Test
    fun `loading a packets file with a duplicate client opcode fails at startup`() {
        val file = File.createTempFile("packets-duplicate", ".yml")
        try {
            file.writeText(
                """
                out-packets:
                  - message: gg.rsmod.game.message.impl.LogoutFullMessage
                    type: FIXED
                    opcode: 51
                    length: 0
                in-packets:
                  - message: gg.rsmod.game.message.impl.OpLocTMessage
                    type: FIXED
                    opcode: 42
                    length: 15
                  - message: gg.rsmod.game.message.impl.WindowStatusMessage
                    type: FIXED
                    opcode: 42
                    length: 6
                """.trimIndent(),
            )
            try {
                MessageStructureSet().load(file)
                fail("a second in-packet at opcode 42 was accepted - the later entry would silently replace the first")
            } catch (e: IllegalStateException) {
                assertTrue(e.message!!, e.message!!.contains("42"))
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `OPLOCT 42 carries an inventory item and a spell through one decoder and one handler`() {
        val structures = MessageStructureSet().load(packetsFile)
        val decoders = MessageDecoderSet().apply { init(structures) }
        val handler = decoders.getHandler(OPLOCT)
        assertNotNull(handler)
        assertEquals("OPLOCT must reach the handler that splits item use from spell use", "OpLocTHandler", handler!!.javaClass.simpleName)

        // Use logs (1511, inventory slot 3) on a fire at 3222,3218; then Charge Earth Orb (192:64) on the Earth obelisk (loc 29415).
        val itemUse = decode(decoders, structures, componentHash = (679 shl 16), componentSlot = 3, invObj = 1511, x = 3222, z = 3218, loc = 2732)
        assertEquals(OpLocTMessage(679 shl 16, 3, 1511, 3222, 3218, 2732, 0), itemUse)
        val spell = decode(decoders, structures, componentHash = (192 shl 16) or 64, componentSlot = -1, invObj = -1, x = 3088, z = 9933, loc = 29415)
        assertEquals(OpLocTMessage((192 shl 16) or 64, -1, -1, 3088, 9933, 29415, 0), spell)
    }

    /** The 15 bytes exactly as MiniMenu writes ClientProt.OPLOCT (p1_alt2 ctrl, p2_alt1 z, p2_alt1 targetComponent,
     * p4_alt1 targetSlot, p2_alt3 targetInvObj, p2_alt1 x, p2_alt2 loc id). */
    private fun decode(
        decoders: MessageDecoderSet,
        structures: MessageStructureSet,
        componentHash: Int,
        componentSlot: Int,
        invObj: Int,
        x: Int,
        z: Int,
        loc: Int,
    ): Any {
        val out = ByteArrayOutputStream()
        out.write(-0) // p1_alt2(ctrl = 0)
        p2alt1(out, z)
        p2alt1(out, componentSlot)
        out.write(componentHash)
        out.write(componentHash shr 8)
        out.write(componentHash shr 16)
        out.write(componentHash shr 24)
        out.write(invObj + 128) // p2_alt3
        out.write(invObj shr 8)
        p2alt1(out, x)
        out.write(loc shr 8) // p2_alt2
        out.write(loc + 128)
        val bytes = out.toByteArray()
        assertEquals("ClientProt OPLOCT is 15 bytes", 15, bytes.size)
        val structure = structures.get(OPLOCT)!!
        val reader = GamePacketReader(GamePacket(OPLOCT, PacketType.FIXED, Unpooled.wrappedBuffer(bytes)))
        @Suppress("UNCHECKED_CAST")
        return (decoders.get(OPLOCT) as MessageDecoder<Message>).decode(OPLOCT, structure, reader)
    }

    private fun p2alt1(
        out: ByteArrayOutputStream,
        value: Int,
    ) {
        out.write(value)
        out.write(value shr 8)
    }

    private companion object {
        const val OPLOCT = 42
    }
}
