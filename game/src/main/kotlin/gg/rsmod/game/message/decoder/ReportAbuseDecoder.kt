package gg.rsmod.game.message.decoder

import gg.rsmod.game.message.MessageDecoder
import gg.rsmod.game.message.impl.ReportAbuseMessage

class ReportAbuseDecoder : MessageDecoder<ReportAbuseMessage>() {
    override fun decode(
        opcode: Int,
        opcodeIndex: Int,
        values: HashMap<String, Number>,
        stringValues: HashMap<String, String>,
    ): ReportAbuseMessage =
        ReportAbuseMessage(
            name = stringValues["name"] ?: "",
            rule = values["rule"]?.toInt() ?: 0,
            mute = (values["mute"]?.toInt() ?: 0) == 1,
            comment = stringValues["comment"] ?: "",
        )
}
