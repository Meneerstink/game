package gg.rsmod.game.message.impl

import gg.rsmod.game.message.Message

/**
 * ClientProt CLICKWORLDMAP (opcode 89): the tile the player clicked in the open world map, packed by the client as
 * `y | level << 28 | x << 14` ([WorldMapCloseDecoder][gg.rsmod.game.message.decoder.WorldMapCloseDecoder]). The defaults
 * (-1) only exist for code that builds the message without a click.
 */
data class WorldMapCloseMessage(
    val x: Int = -1,
    val z: Int = -1,
    val level: Int = -1,
) : Message
