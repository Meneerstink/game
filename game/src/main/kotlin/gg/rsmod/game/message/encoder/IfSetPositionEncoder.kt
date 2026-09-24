package gg.rsmod.game.message.encoder

import gg.rsmod.game.message.MessageEncoder
import gg.rsmod.game.message.impl.IfSetPositionMessage

class IfSetPositionEncoder : MessageEncoder<IfSetPositionMessage>() {
    override fun extract(
        message: IfSetPositionMessage,
        key: String,
    ): Number =
        when (key) {
            "hash" -> message.hash
            "pos_x" -> message.x
            "pos_y" -> message.y
            else -> throw Exception("Unhandled value key.")
        }

    override fun extractBytes(
        message: IfSetPositionMessage,
        key: String,
    ): ByteArray = throw Exception("Unhandled value key.")
}
