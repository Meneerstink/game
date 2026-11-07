package gg.rsmod.game.message.decoder

import gg.rsmod.game.message.MessageDecoder
import gg.rsmod.game.message.impl.OpLoc2Message

/**
 * @author Tom <rspsmods@gmail.com>
 */
class OpLoc2Decoder : MessageDecoder<OpLoc2Message>() {
    override fun decode(
        opcode: Int,
        opcodeIndex: Int,
        values: HashMap<String, Number>,
        stringValues: HashMap<String, String>,
    ): OpLoc2Message {
        val idRaw = values["id"]!!.toInt()
        val x = values["x"]!!.toInt()
        val z = values["z"]!!.toInt()
        val movementRaw = values["movement_type"]!!.toInt()
        val (movementType, id) = gg.rsmod.game.message.WideLocId.fromClient(movementRaw, idRaw) // N462 loc ids > 0xFFFF
        return OpLoc2Message(id, x, z, movementType)
    }
}
