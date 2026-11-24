package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.model.Tile

/** The existing 667 FFA arena bounds; z=5571 is not an eight-tile chunk boundary. */
object ClanWarsFfaCombat {
    private val arenas = listOf(2756..2878 to 5512..5630, 2948..3071 to 5512..5631)

    val multiChunks: Set<Int> = arenas.flatMap { (xs, zs) ->
        (xs.first..xs.last).flatMap { x ->
            (5571..zs.last).map { z -> Tile(x, z).chunkCoords.hashCode() }
        }
    }.toSet()

    /** Override the full registered chunk footprint, including its single-way border tiles. */
    fun classification(tile: Tile): Boolean? {
        if (tile.chunkCoords.hashCode() !in multiChunks) return null
        return tile.height == 0 && arenas.any { (xs, zs) -> tile.x in xs && tile.z in 5571..zs.last }
    }
}
