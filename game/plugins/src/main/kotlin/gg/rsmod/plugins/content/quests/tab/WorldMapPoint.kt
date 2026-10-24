package gg.rsmod.plugins.content.quests.tab

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.openFullscreenInterface
import gg.rsmod.plugins.api.ext.setVarc

/**
 * "Show on map" (Drop Viewer, the Deadman panel's breach and chest): the world map opened on [tile], the way the gameframe's map
 * button opens it on the player (world_map.plugin.kts: varc 622 = the packed tile the map centres on, 674 = the player's own marker).
 */
object WorldMapPoint {
    const val WORLD_MAP = 755

    fun open(player: Player, tile: Tile) {
        player.openFullscreenInterface(interfaceId = WORLD_MAP)
        fun pack(t: Tile) = (t.z and 0x3fff) + ((t.x and 0x3fff) shl 14) + ((t.height and 0x3) shl 28)
        // 622 = where the map looks, 674 = the "You are here" marker: that one stays on the player.
        player.setVarc(622, pack(tile))
        player.setVarc(674, pack(player.tile))
    }
}
