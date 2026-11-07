package gg.rsmod.game.tools.importer

import java.io.File

/**
 * N462 07-11 (Kephri antechamber black screen): print the OSRS tile settings + whether a floor exists, per plane, for a window of one
 * modern square. Read-only. Usage: TileFlagsProbeTool <osrsSquare> <x0> <x1> <z0> <z1> (local 0..63).
 */
object TileFlagsProbeTool {
    @JvmStatic
    fun main(args: Array<String>) {
        val sq = args[0].toInt()
        val (x0, x1, z0, z1) = args.drop(1).map { it.toInt() }
        val keys = ModernRegionProbeTool.loadKeys(File(FeroxImportTool.MODERN_KEYS))
        ModernCacheReader(File(FeroxImportTool.MODERN_CACHE)).use { reader ->
            val region = ModernRegion.load(reader, sq, keys[sq])
            for (plane in 0 until 4) {
                println("plane $plane  (settings hex / '.'=no floor, '#'=floor)")
                for (z in z1 downTo z0) {
                    val row = (x0..x1).joinToString(" ") { x ->
                        val t = region.tiles[plane][x][z]
                        val floor = if (t.underlayId > 0 || t.overlayId != 0) "#" else "."
                        "%2x%s".format(t.settings, floor)
                    }
                    println("  z$z $row")
                }
            }
        }
    }
}
