package gg.rsmod.game.sync.segment

import com.displee.cache.CacheLibrary
import gg.rsmod.game.DevContext
import gg.rsmod.game.GameContext
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.net.packet.GamePacketBuilder
import io.mockk.mockk
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse

/**
 * N610 14-11: setTransmogId(-1) (an npc back to its own form, e.g. ToaHeadIcons.clear on the P3 Warden) was written as the
 * unsigned short 65535 and the 667 client (NPCList: NPCTypeList.list(ig2)) loaded a non-existent type - the npc went invisible.
 * The appearance block must carry the npc's own id, byte for byte the same as an explicit transform to that id.
 */
class NpcTransmogRevertTests {
    private val world =
        World(mockk<GameContext>(relaxed = true), mockk<DevContext>(relaxed = true)).also {
            it.filestore = CacheLibrary(Paths.get("..", "data", "cache").toFile().toString())
            it.definitions.loadAll(it.filestore)
            it.npcUpdateBlocks.load(gg.rsmod.util.ServerProperties().loadYaml(Paths.get("..", "data", "blocks.yml").toFile()).extract("npcs"))
        }

    private fun encoded(npc: Npc): ByteArray {
        val buf = GamePacketBuilder()
        NpcUpdateBlockSegment(npc, false).encode(buf)
        val b = buf.byteBuf
        return ByteArray(b.writerIndex()).also { b.getBytes(0, it) }
    }

    @Test
    fun `a transmog reset sends the npc's own id, never 65535`() {
        val npc = Npc(1, Tile(3200, 3200), world)
        npc.blockBuffer.clean()
        npc.setTransmogId(npc.id)
        val explicit = encoded(npc)
        npc.blockBuffer.clean()
        npc.setTransmogId(-1)
        val reset = encoded(npc)
        assertContentEquals(explicit, reset)
        assertFalse(reset.size >= 2 && reset.takeLast(2).all { it == 0xFF.toByte() }, "the reset still ends in 0xFFFF")
    }
}
