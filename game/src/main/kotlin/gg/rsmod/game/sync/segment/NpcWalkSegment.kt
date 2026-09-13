package gg.rsmod.game.sync.segment

import gg.rsmod.game.sync.SynchronizationSegment
import gg.rsmod.net.packet.GamePacketBuilder

/**
 * @author Tom <rspsmods@gmail.com>
 */
class NpcWalkSegment(
    private val walkDirection: Int,
    private val runDirection: Int,
    private val decodeUpdateBlocks: Boolean,
) : SynchronizationSegment {
    override fun encode(buf: GamePacketBuilder) {
        // RCV-012 B3/B4: running npcs (a familiar following a running owner) moved two tiles on the server but were sent
        // as a one-tile walk, so the client copy fell behind and cut through scenery. Revision-667 layout, identical in
        // the client (NPCList: NpcUpdate.RUN_OR_CRAWL, gbit(1) == 1 -> two 3-bit directions) and Novite
        // LocalNPCUpdate: type 2, run flag 1, walk direction, run direction, extended-info flag.
        if (runDirection == -1) {
            buf.putBits(2, 1)
            buf.putBits(3, walkDirection)
        } else {
            buf.putBits(2, 2)
            buf.putBits(1, 1)
            buf.putBits(3, walkDirection)
            buf.putBits(3, runDirection)
        }
        buf.putBits(1, if (decodeUpdateBlocks) 1 else 0)
    }
}
