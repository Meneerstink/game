package gg.rsmod.game.message.decoder

import gg.rsmod.game.message.MessageDecoder
import gg.rsmod.game.message.impl.OpLoc1Message

/**
 * @author Tom <rspsmods@gmail.com>
 */
class OpLoc1Decoder : MessageDecoder<OpLoc1Message>() {
    override fun decode(
        opcode: Int,
        opcodeIndex: Int,
        values: HashMap<String, Number>,
        stringValues: HashMap<String, String>,
    ): OpLoc1Message {
        val movementRaw = values["movement_type"]!!.toInt()
        val x = values["x"]!!.toInt()
        val idRaw = values["id"]!!.toInt()
        val z = values["z"]!!.toInt()
        val (movementType, id) = gg.rsmod.game.message.WideLocId.fromClient(movementRaw, idRaw) // N462 loc ids > 0xFFFF
        return OpLoc1Message(movementType, x, id, z)
    }
}
