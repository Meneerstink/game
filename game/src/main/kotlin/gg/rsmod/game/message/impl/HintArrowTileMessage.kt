package gg.rsmod.game.message.impl

import gg.rsmod.game.message.Message

/**
 * HINT_ARROW (server prot 81, 12 bytes) pointing at a tile: type 2 = the tile's centre (client `ServerConnectionReader`
 * reads level g1, x g2, z g2, height g1, draw distance g2, model g2 after the slot/type and sprite bytes).
 */
data class HintArrowTileMessage(
    val slot: Int,
    val x: Int,
    val z: Int,
    val level: Int,
    val height: Int = 0,
    val drawDistance: Int = 0,
    val sprite: Int = 0,
    val model: Int = -1,
) : Message {
    companion object {
        const val TYPE_TILE_CENTRE = 2
    }
}