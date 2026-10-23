package gg.rsmod.game.message.decoder

import gg.rsmod.game.message.MessageDecoder
import gg.rsmod.game.message.impl.ChatSetModeMessage

class ChatSetModeDecoder : MessageDecoder<ChatSetModeMessage>() {
    override fun decode(
        opcode: Int,
        opcodeIndex: Int,
        values: HashMap<String, Number>,
        stringValues: HashMap<String, String>,
    ): ChatSetModeMessage = ChatSetModeMessage(values["mode"]!!.toInt() and 0xFF)
}
