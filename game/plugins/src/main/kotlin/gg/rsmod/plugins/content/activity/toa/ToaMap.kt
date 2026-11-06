package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.instance.InstancedChunkSet
import gg.rsmod.game.model.instance.InstancedMap
import gg.rsmod.game.model.instance.InstancedMapAttribute
import gg.rsmod.game.model.instance.InstancedMapConfiguration

/** Source coordinates stay in OSRS space. Only the copy operation applies Claude's +576/+1536 cache translation. */
object ToaMap {
    // N462 card + 40-kaart-bron.tsv: raid bank (55..61,80..84), twelve raid squares + separate lobby, all four planes.
    const val SOURCE_BASE_X = 3520
    const val SOURCE_BASE_Z = 5120
    const val CACHE_OFFSET_X = 576
    const val CACHE_OFFSET_Z = 1536
    val REGIONS = listOf(14160, 14162, 14164, 14674, 14676, 15186, 15188, 15698, 15700, 15184, 15696, 14672)

    // 40-loc-placements.tsv: lobby Entry (3357,9112), nexus Exit (3549,5163); landing just inside each.
    val LOBBY = Tile(3359, 9114)
    val NEXUS = Tile(3551, 5161)
    fun inLobby(tile: Tile): Boolean = tile.height == 0 && tile.x in 3344..3375 && tile.z in 9104..9135

    fun chunks(): InstancedChunkSet {
        val builder = InstancedChunkSet.Builder()
        for (region in REGIONS) {
            val x = (region shr 8) * 64
            val z = (region and 255) * 64
            for (cx in 0 until 8) for (cz in 0 until 8) for (plane in 0..3) {
                builder.set((x - SOURCE_BASE_X) / 8 + cx, (z - SOURCE_BASE_Z) / 8 + cz, plane,
                    copy = Tile(x + CACHE_OFFSET_X + cx * 8, z + CACHE_OFFSET_Z + cz * 8, plane))
            }
        }
        return builder.build()
    }

    fun allocate(world: World): InstancedMap? = world.instanceAllocator.allocate(world, chunks(),
        InstancedMapConfiguration.Builder().setExitTile(LOBBY)
            .addAttribute(InstancedMapAttribute.MANAGED_LIFETIME).build())

    fun instance(map: InstancedMap, source: Tile): Tile =
        Tile(map.area.bottomLeftX + source.x - SOURCE_BASE_X, map.area.bottomLeftZ + source.z - SOURCE_BASE_Z, source.height)

    fun source(map: InstancedMap, tile: Tile): Tile? = map.sourceTile(tile)?.transform(-CACHE_OFFSET_X, -CACHE_OFFSET_Z)
}
