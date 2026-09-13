package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File
import java.awt.image.BufferedImage
import java.io.PrintStream
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * RCV-011 home (owner: Ferox Enclave on the minimap). Read-only proof of which 667 minimap ids show the same picture as
 * the OSRS values the Ferox import dropped, by comparing decoded sprite pixels - no numeric id is carried across.
 *
 * - OSRS map scene N = frame N of sprite group "mapscene" (RuneLite `MapImageDumper.loadSprites`); OSRS map area A =
 *   sprite `spriteId` frame 0 of AreaDefinition A (config group 35, RuneLite `AreaLoader`).
 * - 667 msi M = `MSIType` (config group 34) op1 image, sprite frame 0; 667 mapelement E = `MapElementType` (group 36) op1
 *   sprite, frame 0 (client `MSIType.decode` / `MapElementType.decode`).
 * Both caches use the same legacy sprite container (see [SpriteSheetDumpTool]).
 *
 * Usage: `./gradlew :game:runFeroxMapSpriteProbeTool --args="[outFile]"`
 */
object FeroxMapSpriteProbeTool {
    val FEROX_MAPSCENES = listOf(4, 6, 7, 22, 27, 46, 64, 70, 101, 102)
    val FEROX_AREAS = listOf(5, 12, 21, 33, 40, 58, 66, 652)

    class Frame(val w: Int, val h: Int, val argb: IntArray) {
        /** 0 = identical; opacity-mask mismatches count fully, opaque pixels by normalised RGB distance. */
        fun diff(other: Frame): Double {
            if (w != other.w || h != other.h || argb.isEmpty()) return 1.0
            var score = 0.0
            for (i in argb.indices) {
                val a = argb[i]
                val b = other.argb[i]
                val opaqueA = a ushr 24 != 0
                if (opaqueA != (b ushr 24 != 0)) {
                    score += 1.0
                } else if (opaqueA) {
                    val d = abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)) + abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)) + abs((a and 0xFF) - (b and 0xFF))
                    score += d / 765.0
                }
            }
            return score / argb.size
        }
    }

    private const val SCALE = 4

    /** One row per target: source sprite then the nearest local candidates, each scaled [SCALE]x on a grey cell. */
    fun writeSheet(
        file: File,
        rows: List<List<Frame?>>,
    ) {
        val cell = 16 * SCALE + 4
        val cols = rows.maxOf { it.size }
        val image = BufferedImage(cols * cell, rows.size * cell, BufferedImage.TYPE_INT_ARGB)
        rows.forEachIndexed { r, frames ->
            frames.forEachIndexed { c, frame ->
                for (y in 0 until cell - 4) for (x in 0 until cell - 4) image.setRGB(c * cell + x, r * cell + y, if (c == 0) 0xFF404060.toInt() else 0xFF505050.toInt())
                frame ?: return@forEachIndexed
                for (y in 0 until frame.h) for (x in 0 until frame.w) {
                    val px = frame.argb[x + y * frame.w]
                    if (px ushr 24 == 0) continue
                    for (dy in 0 until SCALE) for (dx in 0 until SCALE) {
                        val ix = c * cell + x * SCALE + dx
                        val iy = r * cell + y * SCALE + dy
                        if (ix < (c + 1) * cell && iy < (r + 1) * cell) image.setRGB(ix, iy, px or (0xFF shl 24))
                    }
                }
            }
        }
        ImageIO.write(image, "png", file)
    }

    private class Buf(val d: ByteArray) {
        var p = 0

        fun u8(): Int = d[p++].toInt() and 0xFF

        fun i8(): Int = d[p++].toInt()

        fun u16(): Int = (u8() shl 8) or u8()

        fun s16(): Int = u16().toShort().toInt()

        fun u24(): Int = (u8() shl 16) or (u8() shl 8) or u8()

        fun i32(): Int = (u8() shl 24) or (u8() shl 16) or (u8() shl 8) or u8()

        fun str(): String {
            val sb = StringBuilder()
            while (true) {
                val c = u8()
                if (c == 0) return sb.toString()
                sb.append(c.toChar())
            }
        }

        /** RuneLite `InputStream.readBigSmart2`. */
        fun bigSmart2(): Int =
            if (d[p] < 0) {
                i32() and 0x7FFFFFFF
            } else {
                u16().let { if (it == 32767) -1 else it }
            }
    }

    /** Port of [SpriteSheetDumpTool]'s decoder, returning ARGB frames. */
    fun decodeSprites(data: ByteArray): List<Frame> {
        val p = Buf(data)
        p.p = data.size - 2
        val count = p.u16()
        val widths = IntArray(count)
        val heights = IntArray(count)
        p.p = data.size - count * 8 - 7
        val maxWidth = p.u16()
        val maxHeight = p.u16()
        val paletteSize = p.u8() + 1
        val offsetX = IntArray(count) { p.u16() }
        val offsetY = IntArray(count) { p.u16() }
        for (i in 0 until count) widths[i] = p.u16()
        for (i in 0 until count) heights[i] = p.u16()
        p.p = data.size - count * 8 - (paletteSize - 1) * 3 - 7
        val palette = IntArray(paletteSize)
        for (i in 1 until paletteSize) {
            palette[i] = p.u24()
            if (palette[i] == 0) palette[i] = 1
        }
        p.p = 0
        return (0 until count).map { i ->
            val w = widths[i]
            val h = heights[i]
            val size = w * h
            val raster = IntArray(size)
            val flags = p.u8()
            val alpha = if (flags and 0x2 != 0) IntArray(size) else null
            if (flags and 0x1 != 0) {
                for (x in 0 until w) for (y in 0 until h) raster[x + y * w] = p.u8()
                if (alpha != null) for (x in 0 until w) for (y in 0 until h) alpha[x + y * w] = p.u8()
            } else {
                for (px in 0 until size) raster[px] = p.u8()
                if (alpha != null) for (px in 0 until size) alpha[px] = p.u8()
            }
            val argb = IntArray(size) { px ->
                val index = raster[px]
                if (index == 0) 0 else ((alpha?.get(px) ?: 255) shl 24) or palette[index % palette.size]
            }
            // Frames are stored trimmed; place them on the full canvas so trimmed and untrimmed copies compare equal.
            val canvas = IntArray(maxWidth * maxHeight)
            for (y in 0 until h) for (x in 0 until w) {
                val cx = x + offsetX[i]
                val cy = y + offsetY[i]
                if (cx < maxWidth && cy < maxHeight) canvas[cx + cy * maxWidth] = argb[x + y * w]
            }
            Frame(maxWidth, maxHeight, canvas)
        }
    }

    fun nameHash(name: String): Int {
        var h = 0
        name.lowercase().forEach { h = h * 31 + it.code }
        return h
    }

    /** RuneLite `AreaLoader` - returns sprite id and name. */
    fun osrsArea(data: ByteArray): Pair<Int, String?> {
        val b = Buf(data)
        var sprite = -1
        var name: String? = null
        while (true) {
            when (val op = b.u8()) {
                0 -> return sprite to name
                1 -> sprite = b.bigSmart2()
                2 -> b.bigSmart2()
                3 -> name = b.str()
                4, 5 -> b.u24()
                6, 7, 8, 28 -> b.u8()
                in 10..14 -> b.str()
                15 -> {
                    val n = b.u8()
                    repeat(n * 2) { b.s16() }
                    b.i32()
                    val colours = b.u8()
                    repeat(colours) { b.i32() }
                    repeat(n) { b.i8() }
                }
                17 -> b.str()
                19 -> b.u16()
                21, 22 -> b.i32()
                23 -> repeat(3) { b.u8() }
                24 -> repeat(2) { b.s16() }
                25 -> b.bigSmart2()
                29, 30 -> b.p += 1
                else -> error("unknown OSRS area opcode $op")
            }
        }
    }

    /** Client `MSIType.decode`. */
    fun msiImage(data: ByteArray): Int {
        val b = Buf(data)
        var image = -1
        while (true) {
            when (b.u8()) {
                0 -> return image
                1 -> image = b.u16()
                2 -> b.u24()
                3 -> {}
                4 -> image = -1
            }
        }
    }

    /** Client `MapElementType.decode`. */
    fun mapElementSprite(data: ByteArray): Int {
        val b = Buf(data)
        var sprite = -1
        while (true) {
            when (val op = b.u8()) {
                0 -> return sprite
                1 -> sprite = b.u16()
                2, 18, 19 -> b.u16()
                3, in 10..14, 17 -> b.str()
                4, 5 -> b.u24()
                6, 7, 8 -> b.u8()
                9, 20 -> {
                    b.u16()
                    b.u16()
                    b.i32()
                    b.i32()
                }
                15 -> {
                    val n = b.u8()
                    repeat(n * 2) { b.s16() }
                    b.i32()
                    val colours = b.u8()
                    repeat(colours) { b.i32() }
                    repeat(n) { b.i8() }
                }
                16 -> {}
                21, 22 -> b.i32()
                23 -> repeat(3) { b.u8() }
                24 -> repeat(2) { b.s16() }
                249 -> {
                    val n = b.u8()
                    repeat(n) {
                        val string = b.u8() == 1
                        b.u24()
                        if (string) b.str() else b.i32()
                    }
                }
                else -> error("unknown 667 mapelement opcode $op")
            }
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val out = args.getOrNull(0)?.let { PrintStream(File(it)) } ?: System.out
        val library = CacheLibrary(FeroxImportTool.GAME_CACHE)
        try {
            ModernCacheReader(File(FeroxImportTool.MODERN_CACHE)).use { modern ->
                val localSprites = HashMap<Int, Frame?>()
                fun localFrame(id: Int): Frame? =
                    localSprites.getOrPut(id) { runCatching { library.data(8, id, 0)?.let { decodeSprites(it).firstOrNull() } }.getOrNull() }

                val msi = library.index(2).archive(34)!!.fileIds().associateWith { runCatching { msiImage(library.data(2, 34, it)!!) }.getOrDefault(-1) }
                val elements = library.index(2).archive(36)!!.fileIds().associateWith { runCatching { mapElementSprite(library.data(2, 36, it)!!) }.getOrDefault(-1) }
                out.println("LOCAL msi=${msi.size} mapelements=${elements.size}")

                val sheets = HashMap<String, MutableList<List<Frame?>>>()

                fun report(label: String, frame: Frame?, candidates: Map<Int, Int>, sheet: String) {
                    if (frame == null) {
                        out.println("$label NO_SOURCE_SPRITE")
                        return
                    }
                    val scored = candidates.mapNotNull { (id, sprite) -> localFrame(sprite)?.let { id to it.diff(frame) } }.sortedBy { it.second }
                    val exact = scored.filter { it.second == 0.0 }.map { it.first }
                    val nearest = scored.take(6)
                    out.println("$label size=${frame.w}x${frame.h} EXACT=$exact NEAREST=${nearest.map { "${it.first}:${"%.3f".format(Locale.ROOT, it.second)}" }}")
                    sheets.getOrPut(sheet) { mutableListOf() } += listOf(frame) + nearest.map { localFrame(candidates.getValue(it.first)) }
                }

                val sceneGroup = modern.index(8).groups.values.first { it.nameHash == nameHash("mapscene") }
                val osrsScenes = decodeSprites(modern.files(8, sceneGroup.id)[0]!!)
                out.println("OSRS mapscene group=${sceneGroup.id} frames=${osrsScenes.size}")
                FEROX_MAPSCENES.forEach { n -> report("MAPSCENE osrs=$n ->msi", osrsScenes.getOrNull(n), msi, "mapscenes") }

                val areas = modern.files(2, 35)
                FEROX_AREAS.forEach { a ->
                    val (sprite, name) = areas[a]?.let { osrsArea(it) } ?: (-1 to null)
                    val frame = if (sprite >= 0) runCatching { decodeSprites(modern.files(8, sprite)[0]!!).firstOrNull() }.getOrNull() else null
                    report("AREA osrs=$a name='$name' sprite=$sprite ->mapelement", frame, elements, "areas")
                }
                if (out !== System.out) {
                    val dir = File(args[0]).parentFile
                    sheets.forEach { (name, rows) -> writeSheet(File(dir, "ferox_$name.png"), rows) }
                }
            }
        } finally {
            library.close()
            if (out !== System.out) out.close()
        }
    }
}
