package gg.rsmod.game.message.decoder

import gg.rsmod.game.message.MessageDecoder
import gg.rsmod.game.message.impl.OpPlayerExtraMessage

/**
 * The rev-667 client writes every OPPLAYER packet the same way (MiniMenu: p1 ctrl-run, p2 player index). The yml entry
 * lists the opcodes in [OPTIONS] order, so the opcode index is the player-menu slot.
 */
class OpPlayerExtraDecoder : MessageDecoder<OpPlayerExtraMessage>() {
    override fun decode(opcode: Int, opcodeIndex: Int, values: HashMap<String, Number>, stringValues: HashMap<String, String>): OpPlayerExtraMessage =
        OpPlayerExtraMessage(OPTIONS[opcodeIndex], values["index"]!!.toInt())

    companion object {
        /** Slots for opcodes 14, 50, 82, 49, 62, 43, 19 (ClientProt OPPLAYER1, 5, 6, 7, 8, 9, 10). */
        val OPTIONS = intArrayOf(1, 5, 6, 7, 8, 9, 10)
    }
}