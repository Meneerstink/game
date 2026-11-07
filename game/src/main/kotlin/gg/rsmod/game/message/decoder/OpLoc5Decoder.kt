package gg.rsmod.game.message.decoder

import gg.rsmod.game.message.MessageDecoder
import gg.rsmod.game.message.impl.OpLoc5Message

/**
 * @author Tom <rspsmods@gmail.com>
 */
class OpLoc5Decoder : MessageDecoder<OpLoc5Message>() {
    override fun decode(
        opcode: Int,
        opcodeIndex: Int,
        values: HashMap<String, Number>,
        stringValues: HashMap<String, String>,
    ): OpLoc5Message {
        val idRaw = values["id"]!!.toInt()
        val x = values["x"]!!.toInt()
        val z = values["z"]!!.toInt()
        val movementRaw = values["movement_type"]!!.toInt()
        val (movementType, id) = gg.rsmod.game.message.WideLocId.fromClient(movementRaw, idRaw) // N462 loc ids > 0xFFFF
        return OpLoc5Message(id, x, z, movementType)
    }
}
