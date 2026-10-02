package gg.rsmod.game.message

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ROOT_CAUSES #146: the fifth loc option (ClientProt OPLOC5, opcode 69) was an IgnoreMessage since the base project's "chore: ignore
 * packet 69" and OpLoc5Message was never registered, so 1274 locs' fifth option silently did nothing (the house spirit tree's Teleport,
 * agility Jump-to, Imbue-Staff, Pick-lock ...). Guards the whole class: no client action opcode is ignored, and every in-packet message
 * class of packets.yml has its decoder + handler registered.
 */
class ClientActionPacketsTests {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "data/packets.yml").isFile }
    private val yml = File(root, "data/packets.yml").readText()
    private val inPackets = yml.substring(yml.indexOf("\nin-packets:"))
    private val decoderSet = File(root, "game/src/main/kotlin/gg/rsmod/game/message/MessageDecoderSet.kt").readText()

    /** opcode -> message class, for single-opcode in-packets. */
    private val byOpcode: Map<Int, String> =
        inPackets.split("\n  - message: ").drop(1).mapNotNull { block ->
            val cls = block.lineSequence().first().substringBefore('#').trim()
            val op = Regex("""\n    opcode: (\d+)""").find(block)?.groupValues?.get(1)?.toInt() ?: return@mapNotNull null
            op to cls
        }.toMap()

    /**
     * Player actions of the rev-667 client (ClientProt.java): OPLOC1-6, OPNPC1-6, OPOBJ3/4/6 (the ground-item options this server
     * registers), OPPLAYER1/3/4/5/8/9 (single-opcode entries; the rest share a multi-opcode entry).
     */
    private val actions =
        mapOf(
            "OPLOC1" to 11, "OPLOC2" to 2, "OPLOC3" to 76, "OPLOC4" to 0, "OPLOC5" to 69, "OPLOC6" to 47,
            "OPNPC1" to 9, "OPNPC2" to 66, "OPNPC3" to 31, "OPNPC4" to 67, "OPNPC5" to 28, "OPNPC6" to 92,
            "OPOBJ3" to 24, "OPOBJ4" to 86, "OPOBJ6" to 27,
        )

    @Test
    fun `no client action opcode is an IgnoreMessage`() {
        actions.forEach { (name, op) ->
            val cls = byOpcode[op]
            assertTrue(cls != null && !cls.endsWith("IgnoreMessage"), "$name ($op) is ${cls ?: "missing"}")
        }
        assertEquals("gg.rsmod.game.message.impl.OpLoc5Message", byOpcode[69])
    }

    @Test
    fun `every in-packet message class is registered with its decoder and handler`() {
        val classes =
            inPackets.split("\n  - message: ").drop(1)
                .map { it.lineSequence().first().substringBefore('#').trim() }
                .filter { !it.endsWith("IgnoreMessage") }
                .map { it.substringAfterLast('.') }
                .toSet()
        // Registered by simple or fully qualified name (put(OpLoc5Message::class.java ... / put(gg.rsmod...FriendsChatKickMessage::class.java ...).
        val missing = classes.filter { !Regex("""put\((?:[\w.]+\.)?$it::class\.java""").containsMatchIn(decoderSet) }
        assertTrue(missing.isEmpty(), "registered in packets.yml but not in MessageDecoderSet: $missing")
    }
}
