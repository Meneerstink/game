package gg.rsmod.game.message.decoder

import gg.rsmod.game.message.MessageDecoder
import gg.rsmod.game.message.impl.OpLocTMessage

class OpLocTDecoder : MessageDecoder<OpLocTMessage>() {
    override fun decode(opcode: Int, opcodeIndex: Int, values: HashMap<String, Number>, stringValues: HashMap<String, String>): OpLocTMessage =
        OpLocTMessage(
            componentHash = values["component_hash"]!!.toInt(),
            componentSlot = values["component_slot"]!!.toInt(),
            item = values["verify"]!!.toInt(),
            x = values["x"]!!.toInt(),
            z = values["z"]!!.toInt(),
            id = values["id"]!!.toInt(),
            movementType = values["movement_type"]!!.toInt(),
        )
}