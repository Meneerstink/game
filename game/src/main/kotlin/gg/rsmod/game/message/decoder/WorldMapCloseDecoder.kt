package gg.rsmod.game.message.decoder

import gg.rsmod.game.message.MessageDecoder
import gg.rsmod.game.message.impl.WorldMapCloseMessage

/**
 * ClientProt CLICKWORLDMAP (89, 4 bytes): one INVERSED_MIDDLE int (packets.yml `coord`), the clicked tile packed as
 * `y | level << 28 | x << 14` (client MainLogicManager / InterfaceManager world-map click). The decoder read nothing before, so
 * every click left its 4 bytes unread (trace PROBLEM 2026-10-04).
 */
class WorldMapCloseDecoder : MessageDecoder<WorldMapCloseMessage>() {
    override fun decode(
        opcode: Int,
        opcodeIndex: Int,
        values: HashMap<String, Number>,
        stringValues: HashMap<String, String>,
    ): WorldMapCloseMessage {
        val coord = values["coord"]?.toInt() ?: return WorldMapCloseMessage()
        return WorldMapCloseMessage(x = (coord shr 14) and 0x3FFF, z = coord and 0x3FFF, level = (coord ushr 28) and 0x3)
    }
}
