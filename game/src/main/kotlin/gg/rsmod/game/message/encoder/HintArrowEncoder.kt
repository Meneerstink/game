package gg.rsmod.game.message.encoder

import gg.rsmod.game.message.MessageEncoder
import gg.rsmod.game.message.impl.HintArrowMessage

class HintArrowEncoder : MessageEncoder<HintArrowMessage>() {
    override fun extract(
        message: HintArrowMessage,
        key: String,
    ): Number =
        when (key) {
            "slot_type" -> (message.type and 0x1F) or (message.slot shl 5)
            "sprite" -> message.sprite
            "target" -> message.target
            "flash_rate" -> message.flashRate
            "padding" -> 0
            "model" -> if (message.model < 0) 65535 else message.model
            else -> throw Exception("Unhandled value key.")
        }

    override fun extractBytes(
        message: HintArrowMessage,
        key: String,
    ): ByteArray = throw Exception("Unhandled value key.")
}
