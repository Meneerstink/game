package gg.rsmod.game.tools.importer

import com.displee.cache.compress.type.BZIP2Compressor
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.zip.GZIPInputStream

/**
 * Minimal, **read-only** JS5 reader for the pinned upstream modern OSRS cache
 * (`C:\RSPS\import-source\openrs2-2686\cache`).
 *
 * ## Why this exists
 *
 * The project's cache library, `com.displee:rs-cache-library:6.8`, cannot read this cache. Opening
 * it throws `ArrayIndexOutOfBoundsException` out of `ReferenceTable.read` for most indexes, and the
 * few indexes it does appear to load (2, 9, 17, 19) then fail with `NegativeArraySizeException`
 * when a group is actually unpacked - the library's reference-table parser predates the JS5 index
 * format this build uses, so its group/file tables are silently wrong.
 *
 * Rather than upgrade or patch the library the whole rev-667 server depends on, this class reads
 * the upstream cache directly and never writes to it. displee stays the only writer, and only ever
 * against the two rev-667 target caches.
 *
 * ## Format
 *
 * The disk store, container and group layouts are implemented from the OpenRS2 reference
 * implementation (`org.openrs2.cache.Js5Index.read`, `Js5Compression`, `Group.unpack`), which is
 * also the provider of the pinned snapshot. In particular the JS5 index field order is
 * groups -> [name hashes] -> checksums -> [uncompressed checksums] -> [whirlpool digests] ->
 * [lengths] -> versions -> group sizes -> file id deltas -> [file name hashes]; the two optional
 * blocks displee omits (uncompressed checksums and lengths) are exactly what desynchronises its
 * parse of this build.
 */
class ModernCacheReader(private val cacheDir: File) : Closeable {
    private val dat = RandomAccessFile(File(cacheDir, "main_file_cache.dat2"), "r")
    private val idxFiles = mutableMapOf<Int, RandomAccessFile>()
    private val indexCache = mutableMapOf<Int, Js5IndexData>()

    /** Every index id that has an `.idxN` file on disk, excluding the master index 255. */
    fun availableIndexes(): List<Int> =
        (cacheDir.listFiles() ?: emptyArray())
            .mapNotNull { file -> Regex("main_file_cache\\.idx(\\d+)").find(file.name)?.groupValues?.get(1)?.toInt() }
            .filter { it != MASTER_INDEX }
            .sorted()

    /** Parsed JS5 reference table for [indexId], read from the master index and cached. */
    fun index(indexId: Int): Js5IndexData =
        indexCache.getOrPut(indexId) {
            val container = readContainer(MASTER_INDEX, indexId) ?: error("No reference table for index $indexId.")
            Js5IndexData.read(decompress(container))
        }

    /** Every file of [groupId] in [indexId], keyed by file id, decompressed and unpacked. */
    fun files(
        indexId: Int,
        groupId: Int,
    ): Map<Int, ByteArray> {
        val group = index(indexId).groups[groupId] ?: error("Index $indexId has no group $groupId.")
        val container = readContainer(indexId, groupId) ?: error("Index $indexId group $groupId has no data.")
        return unpackGroup(decompress(container), group.fileIds)
    }

    /** A single file's bytes, or null when the group or file does not exist. */
    fun file(
        indexId: Int,
        groupId: Int,
        fileId: Int,
    ): ByteArray? = runCatching { files(indexId, groupId)[fileId] }.getOrNull()

    /**
     * Raw container (still compressed) bytes for one group, walked out of the sector chain in
     * `main_file_cache.dat2`. Returns null when the index entry is empty.
     */
    fun readContainer(
        indexId: Int,
        groupId: Int,
    ): ByteArray? {
        val idx = idxFiles.getOrPut(indexId) { RandomAccessFile(File(cacheDir, "main_file_cache.idx$indexId"), "r") }
        val entryPosition = groupId.toLong() * INDEX_ENTRY_SIZE
        if (entryPosition + INDEX_ENTRY_SIZE > idx.length()) return null

        val entry = ByteArray(INDEX_ENTRY_SIZE)
        idx.seek(entryPosition)
        idx.readFully(entry)
        val size = readMedium(entry, 0)
        var sector = readMedium(entry, 3)
        if (size <= 0 || sector <= 0) return null

        // Group ids above 0xFFFF use a 10-byte "extended" sector header instead of the 8-byte one,
        // which is why index 7 (models, >65536 groups in this build) cannot be read with the
        // narrow header alone.
        val extended = groupId > 0xFFFF
        val headerSize = if (extended) EXTENDED_HEADER_SIZE else HEADER_SIZE
        val payloadSize = SECTOR_SIZE - headerSize

        val out = ByteArray(size)
        var read = 0
        var chunk = 0
        val sectorBuffer = ByteArray(SECTOR_SIZE)
        while (read < size) {
            check(sector > 0) { "Sector chain for index $indexId group $groupId ended early." }
            dat.seek(sector.toLong() * SECTOR_SIZE)
            val available = minOf(SECTOR_SIZE, (dat.length() - dat.filePointer).toInt())
            dat.readFully(sectorBuffer, 0, available)

            var offset = 0
            val actualGroup: Int
            if (extended) {
                actualGroup = ByteBuffer.wrap(sectorBuffer, 0, 4).int
                offset = 4
            } else {
                actualGroup = ((sectorBuffer[0].toInt() and 0xFF) shl 8) or (sectorBuffer[1].toInt() and 0xFF)
                offset = 2
            }
            val actualChunk = ((sectorBuffer[offset].toInt() and 0xFF) shl 8) or (sectorBuffer[offset + 1].toInt() and 0xFF)
            val nextSector = readMedium(sectorBuffer, offset + 2)
            val actualIndex = sectorBuffer[offset + 5].toInt() and 0xFF

            check(actualGroup == groupId && actualChunk == chunk && actualIndex == indexId) {
                "Corrupt sector at $sector for index $indexId group $groupId: got group=$actualGroup " +
                    "chunk=$actualChunk index=$actualIndex, expected group=$groupId chunk=$chunk index=$indexId."
            }

            val copy = minOf(payloadSize, size - read)
            System.arraycopy(sectorBuffer, headerSize, out, read, copy)
            read += copy
            sector = nextSector
            chunk++
        }
        return out
    }

    /**
     * Decodes a JS5 container: compression type byte, compressed length, and for compressed types
     * an uncompressed length, followed by the payload and an optional trailing 2-byte version.
     */
    fun decompress(container: ByteArray): ByteArray {
        val type = container[0].toInt() and 0xFF
        val compressedLength = ByteBuffer.wrap(container, 1, 4).int
        return when (type) {
            COMPRESSION_NONE -> container.copyOfRange(5, 5 + compressedLength)
            COMPRESSION_BZIP2 -> {
                val uncompressedLength = ByteBuffer.wrap(container, 5, 4).int
                val out = ByteArray(uncompressedLength)
                // The RS bzip2 stream omits the four-byte "BZh1" header the format normally carries.
                BZIP2Compressor.decompress(out, uncompressedLength, container, compressedLength, 9)
                out
            }
            COMPRESSION_GZIP -> {
                val uncompressedLength = ByteBuffer.wrap(container, 5, 4).int
                val out = ByteArray(uncompressedLength)
                GZIPInputStream(ByteArrayInputStream(container, 9, compressedLength)).use { stream ->
                    var read = 0
                    while (read < uncompressedLength) {
                        val n = stream.read(out, read, uncompressedLength - read)
                        if (n < 0) break
                        read += n
                    }
                    check(read == uncompressedLength) {
                        "Gzip container decompressed to $read bytes, expected $uncompressedLength."
                    }
                }
                out
            }
            else -> error("Unsupported JS5 compression type $type (this reader handles none/bzip2/gzip only).")
        }
    }

    /**
     * Splits a decompressed group into its member files, per `org.openrs2.cache.Group.unpack`:
     * a single-file group is the payload verbatim; otherwise a trailing table of
     * `stripes * fileCount` deltas gives each file's per-stripe length.
     */
    fun unpackGroup(
        data: ByteArray,
        fileIds: List<Int>,
    ): Map<Int, ByteArray> {
        if (fileIds.size == 1) return mapOf(fileIds[0] to data)

        val stripes = data[data.size - 1].toInt() and 0xFF
        val trailerIndex = data.size - (stripes * fileIds.size * 4) - 1
        check(trailerIndex >= 0) { "Group trailer index $trailerIndex is before the start of the data." }

        val lengths = IntArray(fileIds.size)
        var cursor = trailerIndex
        repeat(stripes) {
            var previous = 0
            for (i in lengths.indices) {
                previous += ByteBuffer.wrap(data, cursor, 4).int
                cursor += 4
                lengths[i] += previous
            }
        }

        val out = fileIds.indices.associate { i -> fileIds[i] to ByteArray(lengths[i]) }
        val written = IntArray(fileIds.size)
        var dataIndex = 0
        cursor = trailerIndex
        repeat(stripes) {
            var previous = 0
            for (i in fileIds.indices) {
                previous += ByteBuffer.wrap(data, cursor, 4).int
                cursor += 4
                System.arraycopy(data, dataIndex, out.getValue(fileIds[i]), written[i], previous)
                written[i] += previous
                dataIndex += previous
            }
        }
        check(dataIndex == trailerIndex) { "Group unpack consumed $dataIndex bytes, expected $trailerIndex." }
        return out
    }

    override fun close() {
        dat.close()
        idxFiles.values.forEach { it.close() }
    }

    private fun readMedium(
        bytes: ByteArray,
        offset: Int,
    ): Int =
        ((bytes[offset].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            (bytes[offset + 2].toInt() and 0xFF)

    companion object {
        const val MASTER_INDEX = 255
        private const val SECTOR_SIZE = 520
        private const val HEADER_SIZE = 8
        private const val EXTENDED_HEADER_SIZE = 10
        private const val INDEX_ENTRY_SIZE = 6
        private const val COMPRESSION_NONE = 0
        private const val COMPRESSION_BZIP2 = 1
        private const val COMPRESSION_GZIP = 2

        /** Modern OSRS config index; item definitions live in its group [CONFIG_GROUP_ITEM]. */
        const val INDEX_CONFIG = 2

        /** Modern OSRS model index. */
        const val INDEX_MODEL = 7

        /** Modern OSRS texture index. */
        const val INDEX_TEXTURE = 9

        const val CONFIG_GROUP_ITEM = 10
        const val CONFIG_GROUP_SEQUENCE = 12
        const val CONFIG_GROUP_SPOTANIM = 13
    }
}

/** One parsed JS5 reference table. */
class Js5IndexData(
    val protocol: Int,
    val version: Int,
    val hasNames: Boolean,
    val hasDigests: Boolean,
    val hasLengths: Boolean,
    val hasUncompressedChecksums: Boolean,
    val groups: Map<Int, Js5GroupData>,
) {
    companion object {
        private const val FLAG_NAMES = 0x01
        private const val FLAG_DIGESTS = 0x02
        private const val FLAG_LENGTHS = 0x04
        private const val FLAG_UNCOMPRESSED_CHECKSUMS = 0x08
        private const val PROTOCOL_VERSIONED = 6
        private const val PROTOCOL_SMART = 7
        private const val WHIRLPOOL_BYTES = 64

        fun read(data: ByteArray): Js5IndexData {
            val buf = SimpleBuffer(data)
            val protocol = buf.u8()
            require(protocol in 5..PROTOCOL_SMART) { "Unsupported JS5 protocol number: $protocol" }
            val version = if (protocol >= PROTOCOL_VERSIONED) buf.i32() else 0
            val flags = buf.u8()
            val hasNames = flags and FLAG_NAMES != 0
            val hasDigests = flags and FLAG_DIGESTS != 0
            val hasLengths = flags and FLAG_LENGTHS != 0
            val hasUncompressedChecksums = flags and FLAG_UNCOMPRESSED_CHECKSUMS != 0

            val readSize: () -> Int = if (protocol >= PROTOCOL_SMART) buf::uIntSmart else buf::u16

            val size = readSize()
            val groupIds = IntArray(size)
            var previous = 0
            for (i in 0 until size) {
                previous += readSize()
                groupIds[i] = previous
            }

            val nameHashes = IntArray(size)
            if (hasNames) for (i in 0 until size) nameHashes[i] = buf.i32()
            for (i in 0 until size) buf.i32() // checksums
            if (hasUncompressedChecksums) for (i in 0 until size) buf.i32()
            if (hasDigests) for (i in 0 until size) buf.skip(WHIRLPOOL_BYTES)
            if (hasLengths) {
                for (i in 0 until size) {
                    buf.i32() // compressed length
                    buf.i32() // uncompressed length
                }
            }
            val versions = IntArray(size) { buf.i32() }
            val groupSizes = IntArray(size) { readSize() }

            val groups = LinkedHashMap<Int, Js5GroupData>(size)
            for (i in 0 until size) {
                val fileIds = ArrayList<Int>(groupSizes[i])
                var previousFile = 0
                repeat(groupSizes[i]) {
                    previousFile += readSize()
                    fileIds += previousFile
                }
                groups[groupIds[i]] = Js5GroupData(groupIds[i], nameHashes[i], versions[i], fileIds)
            }

            if (hasNames) {
                for (i in 0 until size) repeat(groupSizes[i]) { buf.i32() }
            }

            return Js5IndexData(protocol, version, hasNames, hasDigests, hasLengths, hasUncompressedChecksums, groups)
        }
    }
}

class Js5GroupData(
    val id: Int,
    val nameHash: Int,
    val version: Int,
    val fileIds: List<Int>,
)

/** Big-endian cursor over a byte array, with the JS5 "smart" variable-width int. */
private class SimpleBuffer(private val data: ByteArray) {
    private var position = 0

    fun u8(): Int = data[position++].toInt() and 0xFF

    fun u16(): Int = (u8() shl 8) or u8()

    fun i32(): Int = (u8() shl 24) or (u8() shl 16) or (u8() shl 8) or u8()

    fun skip(count: Int) {
        position += count
    }

    /** Two bytes when the high bit of the first byte is clear, otherwise four with that bit masked off. */
    fun uIntSmart(): Int = if ((data[position].toInt() and 0xFF) < 0x80) u16() else i32() and 0x7FFFFFFF
}
