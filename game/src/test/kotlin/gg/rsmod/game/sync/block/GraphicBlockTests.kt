package gg.rsmod.game.sync.block

import gg.rsmod.net.packet.GamePacketBuilder
import gg.rsmod.util.ServerProperties
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * RCV-005 GFX root cause: the 667 client decodes four spot anims per entity per tick
 * (`PlayerExtendedInfoFlag`/`NpcExtendedInfoFlag` SPOTANIM0..3, `PlayerList`/`NPCList`
 * `setSpotAnim(0..3)`), the server had one slot, so a second graphic in a tick replaced the first.
 *
 * The readers below are copied from the client's `com.jagex.core.io.Packet` and the per-slot read
 * sequences from `PlayerList.processExtendedInfo` / `NPCList.processExtendedInfo`, so every slot is
 * proven against the decoder that actually runs, using the real `data/blocks.yml`.
 */
class GraphicBlockTests {
    private class ClientPacket(val data: ByteArray) {
        var pos = 0

        private fun u(i: Int) = data[i].toInt() and 0xFF

        fun g1() = u(pos++)

        fun g1Alt1() = (data[pos++] - 128) and 0xFF

        fun g1Alt2() = (-data[pos++]) and 0xFF

        fun g2(): Int {
            pos += 2
            return (u(pos - 2) shl 8) + u(pos - 1)
        }

        fun ig2(): Int {
            pos += 2
            return u(pos - 2) + (u(pos - 1) shl 8)
        }

        fun g2Alt2(): Int {
            pos += 2
            return (u(pos - 2) shl 8) + ((data[pos - 1] - 128) and 0xFF)
        }

        fun g2Alt3(): Int {
            pos += 2
            return ((data[pos - 2] - 128) and 0xFF) + (u(pos - 1) shl 8)
        }

        fun g4(): Int {
            pos += 4
            return (u(pos - 4) shl 24) + (u(pos - 3) shl 16) + (u(pos - 2) shl 8) + u(pos - 1)
        }

        fun g4Alt1(): Int {
            pos += 4
            return u(pos - 4) + (u(pos - 3) shl 8) + (u(pos - 2) shl 16) + (u(pos - 1) shl 24)
        }

        fun g4Alt3(): Int {
            pos += 4
            return (u(pos - 4) shl 16) + (u(pos - 3) shl 24) + u(pos - 2) + (u(pos - 1) shl 8)
        }
    }

    /** id, heightAndDelay, data byte - in the client's read order for that slot. */
    private val playerReaders: List<(ClientPacket) -> Triple<Int, Int, Int>> =
        listOf(
            { p -> Triple(p.g2(), p.g4Alt1(), p.g1Alt1()) },
            { p -> Triple(p.g2Alt3(), p.g4Alt3(), p.g1Alt2()) },
            { p -> Triple(p.g2Alt3(), p.g4Alt3(), p.g1Alt2()) },
            { p -> Triple(p.ig2(), p.g4Alt3(), p.g1Alt2()) },
        )

    private val npcReaders: List<(ClientPacket) -> Triple<Int, Int, Int>> =
        listOf(
            { p -> Triple(p.ig2(), p.g4Alt3(), p.g1Alt2()) },
            { p -> Triple(p.g2Alt2(), p.g4(), p.g1()) },
            { p -> Triple(p.g2(), p.g4(), p.g1Alt1()) },
            { p -> Triple(p.g2(), p.g4(), p.g1()) },
        )

    private fun load(section: String): UpdateBlockSet {
        val properties = ServerProperties().loadYaml(Paths.get("..", "data", "blocks.yml").toFile())
        return UpdateBlockSet().apply { load(properties.extract(section)) }
    }

    private fun assertSlotsDecode(
        entity: String,
        blocks: UpdateBlockSet,
        readers: List<(ClientPacket) -> Triple<Int, Int, Int>>,
    ) {
        GraphicBlock.SLOTS.forEachIndexed { index, type ->
            val slot =
                UpdateBlockBuffer.GraphicSlot().apply {
                    id = 2000 + index * 37
                    height = 92 + index
                    delay = 15 + index
                    rotation = index + 1
                }
            val builder = GamePacketBuilder()
            GraphicBlock.write(builder, blocks.updateBlocks[type]!!, slot)
            val buf = builder.byteBuf
            val bytes = ByteArray(buf.readableBytes())
            buf.getBytes(buf.readerIndex(), bytes)

            val packet = ClientPacket(bytes)
            val (id, heightAndDelay, data) = readers[index](packet)
            val where = "$entity SPOTANIM$index ($type)"
            assertEquals(bytes.size, packet.pos, "$where byte count")
            assertEquals(slot.id, id, "$where id")
            assertEquals(slot.delay, heightAndDelay and 0xFFFF, "$where delay")
            assertEquals(slot.height, heightAndDelay shr 16, "$where height")
            assertEquals(slot.rotation, data and 0x7, "$where rotation")
            assertEquals(0, data shr 3 and 0xF, "$where worn slot")
            assertEquals(0, data shr 7 and 0x1, "$where loop flag")
        }
    }

    @Test
    fun `every player spot-anim slot decodes in the 667 client`() {
        assertSlotsDecode("player", load("players"), playerReaders)
    }

    @Test
    fun `every npc spot-anim slot decodes in the 667 client`() {
        assertSlotsDecode("npc", load("npcs"), npcReaders)
    }

    @Test
    fun `slot mask bits are the client SPOTANIM flags`() {
        assertEquals(listOf(0x2, 0x100, 0x40000, 0x80000), GraphicBlock.SLOTS.map { load("players").updateBlocks[it]!!.bit })
        assertEquals(listOf(0x4, 0x1000, 0x100000, 0x20000), GraphicBlock.SLOTS.map { load("npcs").updateBlocks[it]!!.bit })
    }

    @Test
    fun `block order is the client read order`() {
        // Flag bits in the order PlayerList/NPCList.processExtendedInfo test them.
        val playerClient = listOf(0x8000, 0x40, 0x40000, 0x20000, 0x200, 0x2000, 0x80000, 0x100000, 0x4, 0x10000, 0x8, 0x4000, 0x400, 0x1, 0x10, 0x1000, 0x20, 0x2, 0x100)
        val npcClient = listOf(0x100000, 0x1, 0x20000, 0x40, 0x100, 0x40000, 0x20, 0x2, 0x8, 0x80000, 0x2000, 0x10000, 0x400, 0x10, 0x800, 0x4000, 0x1000, 0x4, 0x200)
        listOf("players" to playerClient, "npcs" to npcClient).forEach { (section, client) ->
            val blocks = load(section)
            val server = blocks.updateBlockOrder.map { blocks.updateBlocks[it]!!.bit }
            assertEquals(client.filter { it in server }, server, "$section update block order")
        }
    }

    @Test
    fun `graphics fill free slots, skip duplicates and overwrite the last when full`() {
        val buffer = UpdateBlockBuffer()
        val slots = (0 until 5).map { buffer.putGraphic(100 + it, 0, 0, 0) }
        assertEquals(listOf(0, 1, 2, 3, 3), slots)
        assertEquals(104, buffer.graphics[3].id)
        assertEquals(-1, buffer.putGraphic(100, 0, 0, 0), "identical graphic in the same tick")

        buffer.clean()
        assertEquals(0, buffer.putGraphic(7, 0, 0, 0), "a new tick starts at slot 0")
    }
}
