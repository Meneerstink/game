package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/**
 * Read-only probe for the OSRS spotanim / sequence / frame / base / synth import (owner decision (e) 2026-09-14).
 *
 * Prints the id headroom of the rev-667 target cache (index 0 anims, 1 bases, 4 synth sounds, 20 sequences, 21 spotanims)
 * and dumps one spotanim that exists in both caches (default 369, Ice Barrage impact) through its sequence, first frame and
 * base, so the byte-level conversion rules are proven from real data instead of assumed.
 *
 * Usage: `java -cp "game/build/install/game/lib/(all jars)" gg.rsmod.game.tools.importer.OsrsFxProbeTool [spotanimId ...]`
 */
object OsrsFxProbeTool {
    private const val TARGET = "C:\\RSPS\\game\\game\\data\\cache"

    @JvmStatic
    fun main(args: Array<String>) {
        val spotanims = args.mapNotNull { it.toIntOrNull() }.ifEmpty { listOf(369) }
        val library = CacheLibrary(TARGET)
        val reader = ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE))
        try {
            listOf(0, 1, 4, 20, 21).forEach { index ->
                val ids = library.index(index).archiveIds()
                val max = ids.maxOrNull() ?: -1
                val lastFiles = if (max >= 0) library.index(index).archive(max)?.fileIds()?.toList() ?: emptyList() else emptyList()
                println("667 idx$index groups=${ids.size} maxGroup=$max lastGroupFiles=${lastFiles.size} maxFile=${lastFiles.maxOrNull()}")
            }
            listOf(0, 1, 4).forEach { index ->
                val groups = reader.index(index).groups.keys
                println("OSRS idx$index groups=${groups.size} maxGroup=${groups.maxOrNull()}")
            }
            listOf(ModernCacheReader.CONFIG_GROUP_SEQUENCE, ModernCacheReader.CONFIG_GROUP_SPOTANIM).forEach { group ->
                val files = reader.index(ModernCacheReader.INDEX_CONFIG).groups.getValue(group).fileIds
                println("OSRS config group $group files=${files.size} maxFile=${files.maxOrNull()}")
            }
            spotanims.forEach { id -> dump(library, reader, id) }
        } finally {
            reader.close()
            library.close()
        }
    }

    private fun dump(
        library: CacheLibrary,
        reader: ModernCacheReader,
        spotanimId: Int,
    ) {
        val local = library.data(21, spotanimId ushr 8, spotanimId and 0xFF)
        val osrs = reader.file(ModernCacheReader.INDEX_CONFIG, ModernCacheReader.CONFIG_GROUP_SPOTANIM, spotanimId)
        println("SPOTANIM $spotanimId 667=${hex(local)}")
        println("SPOTANIM $spotanimId OSRS=${hex(osrs)}")
        val localSeq = local?.let { seqOf(it) }
        val osrsSeq = osrs?.let { seqOf(it) }
        println("  seq 667=$localSeq OSRS=$osrsSeq")
        val localSeqBytes = localSeq?.let { library.data(20, it ushr 7, it and 0x7F) }
        val osrsSeqBytes = osrsSeq?.let { reader.file(ModernCacheReader.INDEX_CONFIG, ModernCacheReader.CONFIG_GROUP_SEQUENCE, it) }
        println("  SEQ 667=${hex(localSeqBytes)}")
        println("  SEQ OSRS=${hex(osrsSeqBytes)}")
        val localFrame = localSeqBytes?.let { firstFrame(it) }
        val osrsFrame = osrsSeqBytes?.let { firstFrame(it) }
        println("  first frame 667=${localFrame?.let { "${it ushr 16}:${it and 0xFFFF}" }} OSRS=${osrsFrame?.let { "${it ushr 16}:${it and 0xFFFF}" }}")
        val localFrameBytes = localFrame?.let { library.data(0, it ushr 16, it and 0xFFFF) }
        val osrsFrameBytes = osrsFrame?.let { reader.file(0, it ushr 16, it and 0xFFFF) }
        println("  FRAME 667=${hex(localFrameBytes)}")
        println("  FRAME OSRS=${hex(osrsFrameBytes)}")
        val localBase = localFrameBytes?.let { ((it[1].toInt() and 0xFF) shl 8) or (it[2].toInt() and 0xFF) }
        val osrsBase = osrsFrameBytes?.let { ((it[0].toInt() and 0xFF) shl 8) or (it[1].toInt() and 0xFF) }
        println("  base 667=$localBase OSRS=$osrsBase")
        val localBaseBytes = localBase?.let { library.data(1, it, 0) }
        val osrsBaseBytes = osrsBase?.let { reader.file(1, it, 0) }
        println("  BASE 667=${hex(localBaseBytes)}")
        println("  BASE OSRS=${hex(osrsBaseBytes)}")
        if (localFrameBytes != null && osrsFrameBytes != null && localBaseBytes != null && osrsBaseBytes != null && localBase == osrsBase) {
            val osrsValues = frameValues(osrsFrameBytes, 0, osrsBaseBytes[0].toInt() and 0xFF, osrsBaseBytes)
            val localValues = frameValues(localFrameBytes, 1, localBaseBytes[0].toInt() and 0xFF, localBaseBytes)
            println("  VALUES type:osrs->667 " + osrsValues.zip(localValues).joinToString(" ") { (o, l) -> "${o.first}:${o.second}->${l.second}" })
        }
    }

    /** (base type, value) per written translator axis, decoded exactly like both clients' frame readers. */
    private fun frameValues(
        frame: ByteArray,
        headerOffset: Int,
        baseCount: Int,
        base: ByteArray,
    ): List<Pair<Int, Int>> {
        val length = frame[headerOffset + 2].toInt() and 0xFF
        var flagPos = headerOffset + 3
        var dataPos = flagPos + length
        val out = mutableListOf<Pair<Int, Int>>()
        for (i in 0 until length) {
            val flags = frame[flagPos++].toInt() and 0xFF
            val type = if (i < baseCount) base[1 + i].toInt() and 0xFF else -1
            for (bit in 0..2) {
                if (flags and (1 shl bit) == 0) continue
                val first = frame[dataPos].toInt() and 0xFF
                val value =
                    if (first < 128) {
                        dataPos += 1
                        first - 64
                    } else {
                        val v = ((first shl 8) or (frame[dataPos + 1].toInt() and 0xFF)) - 49152
                        dataPos += 2
                        v
                    }
                out += type to value
            }
        }
        return out
    }

    /** Opcode 2 of a spotanim (the same stream layout in both revisions up to opcode 2). */
    private fun seqOf(data: ByteArray): Int? {
        var pos = 0
        while (pos < data.size) {
            when (data[pos++].toInt() and 0xFF) {
                0 -> return null
                1, 4, 5, 6 -> pos += 2
                2 -> return ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
                3 -> pos += 4
                7, 8 -> pos += 1
                else -> return null
            }
        }
        return null
    }

    /** Opcode 1 of a sequence: u16 count, count u16 durations, count u16 low frame ids, count u16 high parts. */
    private fun firstFrame(data: ByteArray): Int? {
        if (data.isEmpty() || data[0].toInt() != 1) return null
        val count = ((data[1].toInt() and 0xFF) shl 8) or (data[2].toInt() and 0xFF)
        val low = 3 + count * 2
        val high = low + count * 2
        val lo = ((data[low].toInt() and 0xFF) shl 8) or (data[low + 1].toInt() and 0xFF)
        val hi = ((data[high].toInt() and 0xFF) shl 8) or (data[high + 1].toInt() and 0xFF)
        return (hi shl 16) or lo
    }

    private fun hex(data: ByteArray?): String =
        data?.let { bytes -> "${bytes.size}b " + bytes.take(600).joinToString("") { "%02x".format(it) } } ?: "null"
}
