package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary

/**
 * Read-only diagnostic: finds which top-level cache index holds a named sprite group (by trying
 * `index.archive(name)` on every index 0..40 - the real client resolves sprite groups the same
 * way, `js5.getgroupid(name)`, which hashes the name with the JVM's own `String.hashCode()`
 * algorithm, `ReferenceTable.toHash()`'s default in this cache library version) and reports how
 * many sprite frames (files) that group's archive actually contains.
 *
 * Usage: `./gradlew :game:runSpriteGroupProbeTool --args="<cachePath> <groupName> [groupName ...]"`
 */
object SpriteGroupProbeTool {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size >= 2) { "Usage: <cachePath> <groupName> [groupName ...]" }
        val library = CacheLibrary(args[0])
        try {
            args.drop(1).forEach { name ->
                var found = false
                for (indexId in 0..40) {
                    val index =
                        try {
                            library.index(indexId)
                        } catch (e: Exception) {
                            continue
                        }
                    val archive =
                        try {
                            index.archive(name)
                        } catch (e: Exception) {
                            null
                        } ?: continue
                    found = true
                    println("GROUP '$name' -> index=$indexId archiveId=${archive.id} files=${archive.files.size} fileIds=${archive.files.keys.sorted()}")
                    val data = library.data(indexId, archive.id, 0)
                    if (data != null && data.size >= 2) {
                        val spriteCount = ((data[data.size - 2].toInt() and 0xFF) shl 8) or (data[data.size - 1].toInt() and 0xFF)
                        println("  raw file0 bytes=${data.size} trailing-u16 (classic sprite-sheet frame count if this really is that format)=$spriteCount")
                        if (data.size >= 7 + spriteCount * 8) {
                            val widthPos = data.size - 7 - spriteCount * 8
                            fun u16(off: Int) = ((data[off].toInt() and 0xFF) shl 8) or (data[off + 1].toInt() and 0xFF)
                            fun u8(off: Int) = data[off].toInt() and 0xFF
                            val width = u16(widthPos)
                            val height = u16(widthPos + 2)
                            val paletteLength = u8(widthPos + 4)
                            println("  container width=$width height=$height paletteLength=$paletteLength (+1 for index 0 = transparent)")

                            val offsetXPos = widthPos + 5
                            val offsetYPos = offsetXPos + spriteCount * 2
                            val spriteWPos = offsetYPos + spriteCount * 2
                            val spriteHPos = spriteWPos + spriteCount * 2
                            val paletteStart = 0
                            val palette = IntArray(paletteLength + 1)
                            for (i in 0 until paletteLength) {
                                val p = paletteStart + i * 3
                                val r = u8(p)
                                val g = u8(p + 1)
                                val b = u8(p + 2)
                                palette[i + 1] = (r shl 16) or (g shl 8) or b
                            }
                            var pixelCursor = paletteStart + paletteLength * 3
                            for (frame in 0 until spriteCount) {
                                val fw = u16(spriteWPos + frame * 2)
                                val fh = u16(spriteHPos + frame * 2)
                                val pixelCount = fw * fh
                                if (pixelCursor + pixelCount > data.size) {
                                    println("  frame=$frame w=$fw h=$fh -- OUT OF BOUNDS, decode assumption wrong, stopping")
                                    break
                                }
                                var rSum = 0L
                                var gSum = 0L
                                var bSum = 0L
                                var opaque = 0
                                for (p in 0 until pixelCount) {
                                    val idx = u8(pixelCursor + p)
                                    if (idx != 0 && idx < palette.size) {
                                        val rgb = palette[idx]
                                        rSum += (rgb shr 16) and 0xFF
                                        gSum += (rgb shr 8) and 0xFF
                                        bSum += rgb and 0xFF
                                        opaque++
                                    }
                                }
                                pixelCursor += pixelCount
                                val avg =
                                    if (opaque > 0) {
                                        "avgRGB=(${rSum / opaque},${gSum / opaque},${bSum / opaque})"
                                    } else {
                                        "fully transparent"
                                    }
                                println("  frame=$frame w=$fw h=$fh opaquePixels=$opaque/$pixelCount $avg")
                            }
                        }
                    }
                }
                if (!found) {
                    println("GROUP '$name' -> NOT FOUND in indices 0..40")
                }
            }
        } finally {
            library.close()
        }
    }
}
