package gg.rsmod.game.model.instance

import gg.rsmod.game.model.Area
import gg.rsmod.game.model.PlayerUID
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.region.Chunk
import java.util.*

/**
 * A dynamically constructed map area made up of one or more [InstancedChunk]s.
 *
 * @param area
 * The total area that this map covers in the game world.
 *
 * @param chunks
 * The copied chunks that make up this instanced map.
 *
 * @param exitTile
 * The [Tile] that players will be taken to under certain circumstances, such as
 * logging out of the game while in the instanced map area.
 *
 * @param owner
 * The [PlayerUID] of the 'owner'. This value can be null, unless [attr] contains
 * an attribute which requires an owner.
 *
 * @param attr
 * A set of [InstancedMapAttribute]s that our map has.
 *
 * @author Tom <rspsmods@gmail.com>
 */
class InstancedMap internal constructor(
    val area: Area,
    val chunks: InstancedChunkSet,
    val exitTile: Tile,
    val owner: PlayerUID?,
    val attr: EnumSet<InstancedMapAttribute>,
) {
    /**
     * Get the [InstancedChunk.packed] values in relation to the [relative] tile.
     *
     * The values are represented as:
     * `
     * zzzzzzzzzzzzz-xxxxxxxxxxxxx-hhhhhhhhhhhhh
     * zzzzzzzzzzzzz-xxxxxxxxxxxxx-hhhhhhhhhhhhh
     * zzzzzzzzzzzzz-xxxxxxxxxxxxx-hhhhhhhhhhhhh
     * zzzzzzzzzzzzz-xxxxxxxxxxxxx-hhhhhhhhhhhhh
     * `
     */
    fun getCoordinates(relative: Tile): IntArray {
        val heights = Tile.TOTAL_HEIGHT_LEVELS
        val bounds = Chunk.CHUNKS_PER_REGION

        val coordinates = IntArray(heights * bounds * bounds)

        val startX = relative.x - 48
        val startZ = relative.z - 48

        var index = 0
        for (height in 0 until heights) {
            for (x in 0 until bounds) {
                for (z in 0 until bounds) {
                    val absolute = Tile(startX + (x shl 3), startZ + (z shl 3))
                    val chunkX = (absolute.x - area.bottomLeftX) shr 3
                    val chunkZ = (absolute.z - area.bottomLeftZ) shr 3

                    val coord = InstancedChunkSet.getCoordinates(chunkX, chunkZ, height)
                    val chunk = chunks.values[coord]

                    coordinates[index++] = chunk?.packed ?: -1
                }
            }
        }

        return coordinates
    }

    /**
     * The real-world tile an instance [tile] copies (the packed source chunk of [InstancedChunkSet.Builder.set]), or null
     * when its chunk copies nothing. Unrotated chunks map tile for tile; a rotated chunk maps to its source chunk's corner
     * (enough for area rules, which are drawn on whole map squares). Used so an instanced copy of a real place follows that
     * place's rules (2026-10-08: the new-player intro's private copy of the Edgeville / Grand Exchange border).
     */
    fun sourceTile(tile: Tile): Tile? {
        if (!area.contains(tile)) return null
        val chunkX = (tile.x - area.bottomLeftX) shr 3
        val chunkZ = (tile.z - area.bottomLeftZ) shr 3
        val chunk = chunks.values[InstancedChunkSet.getCoordinates(chunkX, chunkZ, tile.height)] ?: return null
        val packed = chunk.packed
        val height = (packed shr 24) and 0x3
        val sourceX = ((packed shr 14) and 0x3FF) shl 3
        val sourceZ = ((packed shr 3) and 0x7FF) shl 3
        return if (chunk.rot == 0) Tile(sourceX + (tile.x and 7), sourceZ + (tile.z and 7), height) else Tile(sourceX, sourceZ, height)
    }
}
