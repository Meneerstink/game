package gg.rsmod.game.message.encoder

import gg.rsmod.game.message.MessageEncoder
import gg.rsmod.game.message.impl.IfSetAngleMessage

class IfSetAngleEncoder : MessageEncoder<IfSetAngleMessage>() {
    override fun extract(
        message: IfSetAngleMessage,
        key: String,
    ): Number =
        when (key) {
            "hash" -> message.hash
            "zoom" -> message.zoom
            "xan" -> message.xan
            "yan" -> message.yan
            else -> throw Exception("Unhandled value key.")
        }

    override fun extractBytes(
        message: IfSetAngleMessage,
        key: String,
    ): ByteArray = throw Exception("Unhandled value key.")
}
