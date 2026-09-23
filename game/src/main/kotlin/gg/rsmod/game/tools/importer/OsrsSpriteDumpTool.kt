package gg.rsmod.game.tools.importer

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Read-only: writes every frame of an OSRS (openrs2 2686) sprite group as a lossless PNG, using the
 * same decoder the Ferox map probe and the loot-key interface import already rely on, so a frame's
 * meaning can be seen instead of guessed.
 *
 * Usage: `./gradlew :game:runOsrsSpriteDumpTool --args="<groupId> <outDir> [cachePath]"`
 */
object OsrsSpriteDumpTool {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size >= 2) { "Usage: <groupId> <outDir> [cachePath]" }
        val groupId = args[0].toInt()
        val outDir = File(args[1]).apply { mkdirs() }
        val cache = args.getOrNull(2) ?: OsrsItemImportTool.SOURCE_CACHE
        ModernCacheReader(File(cache)).use { source ->
            val data = source.file(8, groupId, 0) ?: error("OSRS sprite group $groupId not found in $cache")
            val frames = FeroxMapSpriteProbeTool.decodeSprites(data)
            println("GROUP $groupId frames=${frames.size} bytes=${data.size}")
            frames.forEachIndexed { index, frame ->
                val image = BufferedImage(maxOf(1, frame.w), maxOf(1, frame.h), BufferedImage.TYPE_INT_ARGB)
                for (y in 0 until frame.h) for (x in 0 until frame.w) image.setRGB(x, y, frame.argb[x + y * frame.w])
                val file = File(outDir, "${groupId}_$index.png")
                ImageIO.write(image, "png", file)
                println("  frame $index ${frame.w}x${frame.h} -> $file")
            }
        }
    }
}
