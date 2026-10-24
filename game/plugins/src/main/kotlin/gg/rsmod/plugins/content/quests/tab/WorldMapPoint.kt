package gg.rsmod.plugins.content.quests.tab

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.openFullscreenInterface
import gg.rsmod.plugins.api.ext.setVarc

/**
 * "Show on map" (Drop Viewer, the Deadman panel's breach and chest): the world map opened on [tile], the way the gameframe's map
 * button opens it on the player (world_map.plugin.kts: varcs 622 / 674 = the packed tile the map centres on).
 */
object WorldMapPoint {
    const val WORLD_MAP = 755

    fun open(player: Player, tile: Tile) {
        player.openFullscreenInterface(interfaceId = WORLD_MAP)
        val packed = (tile.z and 0x3fff) + ((tile.x and 0x3fff) shl 14) + ((tile.height and 0x3) shl 28)
        player.setVarc(622, packed)
        player.setVarc(674, packed)
    }
}
