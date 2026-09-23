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

    override val entityType: EntityType = EntityType.AREA_SOUND
}
