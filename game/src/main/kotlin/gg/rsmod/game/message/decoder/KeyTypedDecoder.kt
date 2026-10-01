package gg.rsmod.game.message.decoder

import gg.rsmod.game.message.MessageDecoder
import gg.rsmod.game.message.impl.KeyTypedMessage

/**
 * @author Alycia <https://github.com/alycii>
 */
class KeyTypedDecoder : MessageDecoder<KeyTypedMessage>() {
    /**
     * EVENT_KEYBOARD (client MainLogicManager): every key pressed since the last packet, each as p1 keycode + p2 delay (1/50 ms
     * units). Only the first byte was read, so a key batched behind another (Space then "1" in one client cycle) was dropped -
     * "left 2 of 3 bytes unread" in the owner session 2026-10-01.
     */
    override fun decode(
        opcode: Int,
        structure: gg.rsmod.game.message.MessageStructure,
        reader: gg.rsmod.net.packet.GamePacketReader,
    ): KeyTypedMessage {
        val keys = ArrayList<Int>()
        while (reader.readableBytes >= ENTRY_BYTES) {
            keys += reader.getUnsigned(gg.rsmod.net.packet.DataType.BYTE).toInt()
            reader.getUnsigned(gg.rsmod.net.packet.DataType.SHORT)
        }
        if (keys.isEmpty() && reader.readableBytes > 0) keys += reader.getUnsigned(gg.rsmod.net.packet.DataType.BYTE).toInt()
        return KeyTypedMessage(keys.firstOrNull() ?: -1, keys.drop(1))
    }

    override fun decode(
        opcode: Int,
        opcodeIndex: Int,
        values: HashMap<String, Number>,
        stringValues: HashMap<String, String>,
    ): KeyTypedMessage {
        val keycode = values["keycode"]!!.toInt()
        return KeyTypedMessage(keycode)
    }

    private companion object {
        const val ENTRY_BYTES = 3
    }
}
