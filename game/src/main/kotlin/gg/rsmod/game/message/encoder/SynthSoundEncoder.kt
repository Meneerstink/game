package gg.rsmod.game.message.encoder

import gg.rsmod.game.message.MessageEncoder
import gg.rsmod.game.message.impl.SynthSoundMessage

/**
 * @author Tom <rspsmods@gmail.com>
 */
class SynthSoundEncoder : MessageEncoder<SynthSoundMessage>() {
    override fun extract(
        message: SynthSoundMessage,
        key: String,
    ): Number =
        when (key) {
            "sound" -> message.sound
            "loops" -> message.loops
            "delay" -> message.delay
            "volume" -> message.volume
            "rate" -> message.rate
            else -> throw Exception("Unhandled value key.")
        }

    override fun extractBytes(
        message: SynthSoundMessage,
        key: String,
    ): ByteArray = throw Exception("Unhandled value key.")
}
