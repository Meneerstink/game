package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/**
 * Read-only: which textures an item's models use, in the pinned OSRS source cache or in a rev-667 cache.
 *
 * Usage: `osrs <itemId> [itemId ...]` or `r667 <cachePath> <modelId> [modelId ...]`.
 * For every model it prints the textured-face count per texture id and the texture-space mapping types, so an OSRS texture can be
 * matched to the rev-667 texture a comparable 667 model uses (owner 2026-09-23: "Infernal cape is still not correct like osrs").
 */
object ItemTextureProbeTool {
    @JvmStatic
    fun main(args: Array<String>) {
        when (args.firstOrNull()) {
            "osrs" ->
                ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE)).use { reader ->
                    val itemFiles = reader.files(ModernCacheReader.INDEX_CONFIG, ModernCacheReader.CONFIG_GROUP_ITEM)
                    args.drop(1).map { it.toInt() }.forEach { id ->
                        val def = ModernItemDefDecoder.decode(id, itemFiles[id] ?: error("upstream item $id missing"))
                        println("OSRS_ITEM_$id ${def.modelDependencies()}")
                        def.modelDependencies().values.filter { it >= 0 }.distinct().forEach { m ->
                            val bytes = reader.file(ModernCacheReader.INDEX_MODEL, m, 0) ?: return@forEach println("  model $m missing")
                            val data = runCatching { ModernModelDecoder.decode(bytes) }.getOrElse { ModernModelDecoder.decode(bytes, flattenTextures = true) }
                            println("  model $m ${summary(data)}")
                        }
                    }
                }
            "r667" -> {
                val library = CacheLibrary(args[1])
                args.drop(2).map { it.toInt() }.forEach { m ->
                    val bytes = library.data(ModelConvertTool.MODEL_INDEX, m) ?: return@forEach println("MODEL_$m absent")
                    println("MODEL_$m ${summary(Rev667ModelDecoder.decode(bytes))}")
                }
                library.close()
            }
            "texosrs" ->
                ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE)).use { reader ->
                    args.drop(1).map { it.toInt() }.forEach { t ->
                        val bytes = reader.file(ModernCacheReader.INDEX_TEXTURE, 0, t)
                        println("OSRS_TEXTURE_$t ${bytes?.joinToString(" ") { "%02x".format(it) }}")
                    }
                }
            "tex667" -> {
                val library = CacheLibrary(args[1])
                val materials = library.data(26, 0, 0)!!
                println("MATERIALS count=${((materials[0].toInt() and 0xFF) shl 8) or (materials[1].toInt() and 0xFF)} bytes=${materials.size}")
                args.drop(2).map { it.toInt() }.forEach { t ->
                    val bytes = library.data(9, t)
                    println("TEXTURE667_$t ${bytes?.joinToString(" ") { "%02x".format(it) }}")
                }
                library.close()
            }
            "seq667" -> {
                // Per sequence: the base of its frames and, per transform type, how many frame groups use it and the value range.
                val library = CacheLibrary(args[1])
                args.drop(2).map { it.toInt() }.forEach { seq ->
                    val bytes = library.data(OsrsFxImportTool.INDEX_SEQ, seq ushr 7, seq and 0x7F) ?: return@forEach println("SEQ_$seq absent")
                    val frames = OsrsFxImportTool.decode667SeqFrames(bytes)
                    val stats = sortedMapOf<Int, MutableList<Int>>()
                    var baseId = -1
                    var baseTypes = IntArray(0)
                    frames.forEach { f ->
                        val frame = library.data(OsrsFxImportTool.INDEX_FRAMES, f ushr 16, f and 0xFFFF) ?: return@forEach println("  frame $f absent")
                        val b = ((frame[1].toInt() and 0xFF) shl 8) or (frame[2].toInt() and 0xFF)
                        if (b != baseId) {
                            baseId = b
                            val base = library.data(OsrsFxImportTool.INDEX_BASES, b) ?: return@forEach println("  base $b absent")
                            baseTypes = IntArray(base[0].toInt() and 0xFF) { base[1 + it].toInt() and 0xFF }
                        }
                        var p = 3
                        val length = frame[p++].toInt() and 0xFF
                        val flags = IntArray(length) { frame[p++].toInt() and 0xFF }
                        for (i in 0 until length) {
                            for (bit in 0..2) {
                                if (flags[i] and (1 shl bit) == 0) continue
                                val v0 = frame[p].toInt() and 0xFF
                                val v = if (v0 < 128) { p += 1; v0 - 64 } else { val s = ((frame[p].toInt() and 0xFF) shl 8) or (frame[p + 1].toInt() and 0xFF); p += 2; s - 49152 }
                                stats.getOrPut(baseTypes.getOrElse(i) { -1 }) { mutableListOf() } += v
                            }
                        }
                    }
                    println("SEQ_$seq frames=${frames.size} base=$baseId types=${baseTypes.toList()} " + stats.entries.joinToString { (t, v) -> "type$t n=${v.size} ${v.minOrNull()}..${v.maxOrNull()}" })
                }
                library.close()
            }
            else -> error("Usage: osrs <itemId>... | r667 <cachePath> <modelId>... | texosrs <texId>... | tex667 <cachePath> <texId>... | seq667 <cachePath> <seqId>...")
        }
    }

    private fun summary(m: ModelData): String {
        val tex = m.faceTexture?.map { it.toInt() }?.filter { it != -1 }?.groupingBy { it }?.eachCount()?.toSortedMap()
        val spaces = m.texMappingType?.map { it.toInt() }?.groupingBy { it }?.eachCount()
        return "faces=${m.faceCount} textures=$tex texSpaces=${m.texSpaceCount} mappingTypes=$spaces colours=${m.faceColour.map { it.toInt() and 0xFFFF }.distinct().take(8)}"
    }
}
