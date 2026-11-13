package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary

/**
 * N606 13-11 (Zebak's body hidden, only his tail shows in the water): per tile of OUR map square, planes 0 and 1: the explicit height
 * byte (or '-' = derived by the client), the settings flags (2 = bridge: plane 1 drawn on plane 0) and the overlay id. Read-only.
 * Usage: TileHeightProbeTool <worldX0> <worldX1> <worldZ0> <worldZ1>   (one 64x64 square)
 */
object TileHeightProbeTool {
    @JvmStatic
    fun main(args: Array<String>) {
        val (x0, x1, z0, z1) = args.map { it.toInt() }
        require(x0 shr 6 == x1 shr 6 && z0 shr 6 == z1 shr 6) { "one square only" }
        val lib = CacheLibrary(FeroxImportTool.GAME_CACHE)
        try {
            val data = Rev667RegionProbeTool.regionGroupData(lib, "m${x0 shr 6}_${z0 shr 6}", null) ?: error("no map m${x0 shr 6}_${z0 shr 6}")
            val map = Rev667TileCodec.decode(data)
            for (plane in 0..1) {
                println("PLANE $plane  cell = height/flags/overlay   (rows north -> south, columns x $x0..$x1)")
                for (z in z1 downTo z0) {
                    val row = (x0..x1).joinToString(" ") { x ->
                        val t = map.tiles[plane][x and 63][z and 63]
                        "%3s/%d/%-3d".format(if (t.height < 0) "-" else "${t.height}", t.flags, t.overlayId)
                    }
                    println("z$z $row")
                }
            }
        } finally {
            lib.close()
        }
    }
}
