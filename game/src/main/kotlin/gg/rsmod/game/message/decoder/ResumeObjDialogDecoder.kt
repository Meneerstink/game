package gg.rsmod.game.message.decoder

import gg.rsmod.game.message.MessageDecoder
import gg.rsmod.game.message.impl.ResumeObjDialogMessage

class ResumeObjDialogDecoder : MessageDecoder<ResumeObjDialogMessage>() {
    override fun decode(
        opcode: Int,
        opcodeIndex: Int,
        values: HashMap<String, Number>,
        stringValues: HashMap<String, String>,
    ): ResumeObjDialogMessage = ResumeObjDialogMessage(values["item"]!!.toInt())
}
