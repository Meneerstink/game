package gg.rsmod.game.message.encoder

import gg.rsmod.game.message.MessageEncoder
import gg.rsmod.game.message.impl.IfSetTextMessage

/**
 * @author Tom <rspsmods@gmail.com>
 */
class IfSetTextEncoder : MessageEncoder<IfSetTextMessage>() {
    override fun extract(
        message: IfSetTextMessage,
        key: String,
    ): Number =
        when (key) {
            "hash" -> (message.parent shl 16) or message.child
            else -> throw Exception("Unhandled value key.")
        }

    override fun extractBytes(
        message: IfSetTextMessage,
        key: String,
    ): ByteArray =
        when (key) {
            "text" -> {
                // N108 (2026-10-24): the client reads interface text as Cp1252 (Packet.gjstr); UTF-8 bytes turned a "·" into "Â·"
                // and cut the text short by one byte per such character.
                val bytes = message.text.toByteArray(CP1252)
                val data = ByteArray(bytes.size + 1)
                System.arraycopy(bytes, 0, data, 0, bytes.size)
                data[data.size - 1] = 0
                data
            }
            else -> throw Exception("Unhandled value key.")
        }
}

private val CP1252: java.nio.charset.Charset = java.nio.charset.Charset.forName("windows-1252")
