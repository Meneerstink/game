package gg.rsmod.game.message.decoder

import gg.rsmod.game.message.MessageDecoder
import gg.rsmod.game.message.impl.OpPlayer9Message

/** OPPLAYER9: `p1 ctrl-run, p2 index` (MiniMenu.doAction in this revision's client). */
class OpPlayer9Decoder : MessageDecoder<OpPlayer9Message>() {
    override fun decode(
        opcode: Int,
        opcodeIndex: Int,
        values: HashMap<String, Number>,
        stringValues: HashMap<String, String>,
    ): OpPlayer9Message = OpPlayer9Message(values["index"]!!.toInt())
}
