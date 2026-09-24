package gg.rsmod.game.message.decoder

import gg.rsmod.game.message.MessageDecoder
import gg.rsmod.game.message.impl.ResumeHslDialogMessage

/** RESUME_P_HSLDIALOG: `p2 hsl` (ScriptRunner, this revision's client). */
class ResumeHslDialogDecoder : MessageDecoder<ResumeHslDialogMessage>() {
    override fun decode(
        opcode: Int,
        opcodeIndex: Int,
        values: HashMap<String, Number>,
        stringValues: HashMap<String, String>,
    ): ResumeHslDialogMessage = ResumeHslDialogMessage(values["hsl"]!!.toInt() and 0xFFFF)
}
