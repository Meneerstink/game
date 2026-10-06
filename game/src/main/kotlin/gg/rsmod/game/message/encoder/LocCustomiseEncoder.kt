package gg.rsmod.game.message.encoder

import gg.rsmod.game.message.MessageEncoder
import gg.rsmod.game.message.impl.LocCustomiseMessage
import java.io.ByteArrayOutputStream

class LocCustomiseEncoder : MessageEncoder<LocCustomiseMessage>() {
    override fun extract(
        message: LocCustomiseMessage,
        key: String,
    ): Number =
        when (key) {
            "settings" -> message.settings
            "flags" -> message.flags
            "tile" -> message.tile
            "id" -> message.id
            else -> throw Exception("Unhandled value key.")
        }

    override fun extractBytes(
        message: LocCustomiseMessage,
        key: String,
    ): ByteArray {
        if (key != "payload") throw Exception("Unhandled value key.")
        val fit = message.fit?.normalised() ?: return ByteArray(0)
        return ByteArrayOutputStream().apply {
            listOf(fit.dx, fit.dy, fit.dz, fit.scale, fit.yaw).forEach { v ->
                write((v shr 8) and 0xFF)
                write(v and 0xFF)
            }
        }.toByteArray()
    }
}
