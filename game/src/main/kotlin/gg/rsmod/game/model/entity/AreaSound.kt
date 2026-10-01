package gg.rsmod.game.model.entity

import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile

/**
 * A sound that can be spawned in a tile.
 *
 * @author Tom <rspsmods@gmail.com>
 */
class AreaSound private constructor(
    val id: Int,
    val radius: Int,
    val volume: Int,
    val delay: Int,
    val playbackVolume: Int,
) : Entity() {
    constructor(tile: Tile, id: Int, radius: Int, volume: Int, delay: Int = 0, playbackVolume: Int = 255) : this(id, radius, volume, delay, playbackVolume) {
        check(radius <= 0xf) { "Radius can not exceed 15 tiles." }
        this.tile = tile
    }

    /**
     * A player who must not receive this sound because his client already plays it itself - e.g. a radius-0 cache frame
     * sound on his own attack sequence, which only the local player hears. Everyone else nearby still does.
     */
    var excluded: Player? = null

    override val entityType: EntityType = EntityType.AREA_SOUND
}
