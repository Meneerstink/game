package gg.rsmod.game.message.impl

import gg.rsmod.game.message.Message

/**
 * T2: one piece of a bug-report screenshot from the 78 client (RspsBugShot.pump, ClientProt RSPS_BUGSHOT_CHUNK = opcode 94,
 * var-short): the PNG's [total] size, this piece's [offset] in it and its bytes. Handled by
 * [gg.rsmod.game.model.BugShotUploads] straight from the network thread (GameSystem.receiveMessage), never through the
 * per-cycle packet queue, so an upload never takes the place of a click.
 */
class BugShotChunkMessage(
    val total: Int,
    val offset: Int,
    val data: ByteArray,
) : Message {
    override fun toString(): String = "BugShotChunkMessage(total=$total, offset=$offset, bytes=${data.size})"
}
