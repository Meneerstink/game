package gg.rsmod.game.message.encoder

import gg.rsmod.game.message.MessageEncoder
import gg.rsmod.game.message.impl.HintArrowTileMessage

class HintArrowTileEncoder : MessageEncoder<HintArrowTileMessage>() {
    override fun extract(
        message: HintArrowTileMessage,
        key: String,
    ): Number =
        when (key) {
            "slot_type" -> (HintArrowTileMessage.TYPE_TILE_CENTRE and 0x1F) or (message.slot shl 5)
            "sprite" -> message.sprite
            "level" -> message.level
            "x" -> message.x
            "z" -> message.z
            "height" -> message.height
            "draw_distance" -> message.drawDistance
            "model" -> if (message.model < 0) 65535 else message.model
            else -> throw Exception("Unhandled value key.")
        }

    override fun extractBytes(
        message: HintArrowTileMessage,
        key: String,
    ): ByteArray = throw Exception("Unhandled value key.")
}