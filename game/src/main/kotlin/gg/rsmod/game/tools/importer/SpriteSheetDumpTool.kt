package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Read-only: decodes a cache sprite sheet and writes every frame out as a PNG, so a frame's real
 * meaning can be identified by looking at it instead of guessed from a donor client's enum.
 *
 * The decoder is a direct port of this revision's own client
 * (`2011scape-client`'s `com.jagex.IndexedImage.load(byte[])`), field order and all:
 *
 *  - the last 2 bytes are the frame count;
 *  - a trailer at `len - count*8 - 7` holds scaleWidth, scaleHeight, paletteSize-1, then the
 *    per-frame offsetX / offsetY / width / height arrays;
 *  - the palette (3 bytes per entry, index 0 reserved for transparent) sits directly before that
 *    trailer at `len - count*8 - (paletteSize-1)*3 - 7`;
 *  - pixel data starts at offset 0, one frame after another, each preceded by a flags byte where
 *    bit 0 means column-major ("oblong") order and bit 1 means a separate alpha plane follows.
 *
 * A previous session's ad-hoc reader assumed the palette was at offset 0 and produced near-black
 * frames plus an out-of-range palette index; that is what this replaces.
 *
 * Usage: `./gradlew :game:runSpriteSheetDumpTool --args="<cachePath> <groupName> <outDir>"`
 */
object SpriteSheetDumpTool {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size >= 3) { "Usage: <cachePath> <groupName> <outDir>" }
        val library = CacheLibrary(args[0])
        try {
            val groupName = args[1]
            val outDir = File(args[2]).apply { mkdirs() }
            var located = false
            // A bare number is a sprite group id in the sprite index (8), e.g. the ids interface
            // components bake in their `sprite` field; a name is still looked up across indices.
            val numericId = groupName.toIntOrNull()
            for (indexId in (if (numericId != null) listOf(8) else (0..40).toList())) {
                val index =
                    try {
                        library.index(indexId)
                    } catch (e: Exception) {
                        continue
                    }
                val archive =
                    try {
                        if (numericId != null) index.archive(numericId) else index.archive(groupName)
                    } catch (e: Exception) {
                        null
                    } ?: continue
                val data = library.data(indexId, archive.id, 0) ?: continue
                located = true
                println("GROUP '$groupName' index=$indexId archive=${archive.id} bytes=${data.size}")
                dump(data, outDir, groupName)
                break
            }
            if (!located) println("GROUP '$groupName' NOT_FOUND")
        } finally {
            library.close()
        }
    }

    /** Minimal big-endian reader; the shared one in [InterfaceHookProbeTool] is private to it. */
    private class Reader(val data: ByteArray) {
        var pos = 0

        fun g1(): Int = data[pos++].toInt() and 0xFF

        fun g1b(): Byte = data[pos++]

        fun g2(): Int {
            pos += 2
            return ((data[pos - 2].toInt() and 0xFF) shl 8) or (data[pos - 1].toInt() and 0xFF)
        }

        fun g3(): Int {
            pos += 3
            return ((data[pos - 3].toInt() and 0xFF) shl 16) or
                ((data[pos - 2].toInt() and 0xFF) shl 8) or
                (data[pos - 1].toInt() and 0xFF)
        }
    }

    private fun dump(
        data: ByteArray,
        outDir: File,
        groupName: String,
    ) {
        val p = Reader(data)
        p.pos = data.size - 2
        val count = p.g2()

        val offX = IntArray(count)
        val offY = IntArray(count)
        val widths = IntArray(count)
        val heights = IntArray(count)

        p.pos = data.size - count * 8 - 7
        val scaleWidth = p.g2()
        val scaleHeight = p.g2()
        val paletteSize = (p.g1() and 0xFF) + 1
        for (i in 0 until count) offX[i] = p.g2()
        for (i in 0 until count) offY[i] = p.g2()
        for (i in 0 until count) widths[i] = p.g2()
        for (i in 0 until count) heights[i] = p.g2()

        p.pos = data.size - count * 8 - (paletteSize - 1) * 3 - 7
        val palette = IntArray(paletteSize)
        for (i in 1 until paletteSize) {
            palette[i] = p.g3()
            if (palette[i] == 0) palette[i] = 1
        }

        println("  frames=$count sheet=${scaleWidth}x$scaleHeight paletteSize=$paletteSize")

        p.pos = 0
        val frames = mutableListOf<BufferedImage>()
        for (i in 0 until count) {
            val w = widths[i]
            val h = heights[i]
            val size = w * h
            val raster = ByteArray(size)
            val flags = p.g1()
            val oblong = (flags and 0x1) != 0
            val alphaPlane = (flags and 0x2) != 0
            val alpha = if (alphaPlane) ByteArray(size) else null

            if (oblong) {
                for (x in 0 until w) for (y in 0 until h) raster[x + y * w] = p.g1b()
                if (alpha != null) for (x in 0 until w) for (y in 0 until h) alpha[x + y * w] = p.g1b()
            } else {
                for (pixel in 0 until size) raster[pixel] = p.g1b()
                if (alpha != null) for (pixel in 0 until size) alpha[pixel] = p.g1b()
            }

            val image = BufferedImage(maxOf(w, 1), maxOf(h, 1), BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val index = raster[x + y * w].toInt() and 0xFF
                    val rgb = if (index == 0) 0 else palette[index % palette.size]
                    val a = if (index == 0) 0 else (alpha?.get(x + y * w)?.toInt()?.and(0xFF) ?: 255)
                    image.setRGB(x, y, (a shl 24) or rgb)
                }
            }
            val file = File(outDir, "${groupName}_$i.png")
            ImageIO.write(image, "png", file)
            frames += image
            println("  frame $i ${w}x$h off=${offX[i]},${offY[i]} oblong=$oblong alpha=$alphaPlane -> ${file.name}")
        }
        writeContactSheet(frames, outDir, groupName)
    }

    /**
     * One scaled, gridded, index-labelled PNG of every frame. A 25x25 head icon is far too small to
     * identify by eye at native size, and identifying it by eye is the entire point of this tool.
     */
    private fun writeContactSheet(
        frames: List<BufferedImage>,
        outDir: File,
        groupName: String,
    ) {
        if (frames.isEmpty()) return
        val scale = System.getProperty("sheetScale")?.toIntOrNull() ?: 5
        val cell = 25 * scale
        val label = 16
        val columns = System.getProperty("sheetColumns")?.toIntOrNull() ?: 7
        val rows = (frames.size + columns - 1) / columns
        val sheet = BufferedImage(columns * cell, rows * (cell + label), BufferedImage.TYPE_INT_RGB)
        val g = sheet.createGraphics()
        g.color = java.awt.Color(0x20, 0x20, 0x28)
        g.fillRect(0, 0, sheet.width, sheet.height)
        frames.forEachIndexed { i, frame ->
            val cx = (i % columns) * cell
            val cy = (i / columns) * (cell + label)
            g.color = java.awt.Color(0x40, 0x40, 0x4C)
            g.drawRect(cx, cy, cell - 1, cell - 1)
            if (frame.width > 0 && frame.height > 0) {
                g.drawImage(frame, cx, cy, frame.width * scale, frame.height * scale, null)
            }
            g.color = java.awt.Color.WHITE
            g.drawString(i.toString(), cx + 4, cy + cell + label - 4)
        }
        g.dispose()
        val file = File(outDir, "${groupName}_sheet.png")
        ImageIO.write(sheet, "png", file)
        println("  contact sheet -> ${file.name}")
    }
}
