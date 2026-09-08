package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.ArchiveType
import java.security.MessageDigest

/**
 * Read-only inspector for the item index of one or more real caches - the "verify current state"
 * half of the import pipeline's PREPARE/VERIFY lifecycle (`RSPS_DECISIONS.md` 2026-09-02
 * "STANDING OWNER AUTHORIZATION").
 *
 * Never writes. Answers the two questions every safe allocation and every post-write verification
 * needs, straight from the cache bytes rather than from `Items.kt`/`items.yml`/a manifest that may
 * lag behind reality:
 *
 *  1. what is the real highest contiguous item id (`maxContiguousId`), i.e. the last id before the
 *     first hole - [gg.rsmod.game.service.game.ItemMetadataService] and
 *     [gg.rsmod.game.fs.DefinitionSet] require zero gaps, so the only safe new id is that + 1;
 *  2. for a specific id, does archive data exist, what does it decode its name to, and what is the
 *     SHA-1 of its raw bytes (so two caches can be compared for byte equality without dumping
 *     either).
 *
 * Usage: `./gradlew :game:runCacheItemProbeTool --args="<cachePath>[;<cachePath>...] [id ...]"`
 *
 * Output is one `KEY=VALUE` line per fact, greppable and diffable between cache paths.
 */
object CacheItemProbeTool {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.isNotEmpty()) { "Usage: <cachePath>[;<cachePath>...] [itemId ...]" }
        val cachePaths = args[0].split(';').filter { it.isNotBlank() }
        val probeIds = args.drop(1).map { it.toInt() }

        cachePaths.forEach { path -> probe(path, probeIds) }
    }

    private fun probe(
        cachePath: String,
        probeIds: List<Int>,
    ) {
        println("CACHE=$cachePath")
        val library = CacheLibrary(cachePath)
        try {
            val allIds = allItemIds(library)
            val maxContiguous = maxContiguousItemId(allIds)
            val orphans = allIds.filter { it > maxContiguous }
            println("  ITEM_INDEX_ID_COUNT=${allIds.size}")
            println("  MAX_CONTIGUOUS_ITEM_ID=$maxContiguous")
            println("  NEXT_SAFE_ITEM_ID=${maxContiguous + 1}")
            // Kept for compatibility with tooling/docs that already grep this key: the first orphan,
            // or -1 when none exist. Unlike the previous implementation this is no longer bounded to
            // a fixed scan window - it is derived from ORPHAN_ITEM_ID_COUNT below, which enumerates
            // the complete real item index.
            println("  FIRST_ID_WITH_DATA_ABOVE_MAX=${orphans.firstOrNull() ?: -1}")
            println("  ORPHAN_ITEM_ID_COUNT=${orphans.size}")
            if (orphans.isNotEmpty()) {
                println("  ORPHAN_ITEM_IDS=${orphans.take(ORPHANS_REPORTED)}${if (orphans.size > ORPHANS_REPORTED) " (+${orphans.size - ORPHANS_REPORTED} more)" else ""}")
            }
            probeIds.forEach { id ->
                val bytes = itemData(library, id)
                if (bytes == null) {
                    println("  ITEM_$id=ABSENT")
                } else {
                    println("  ITEM_$id=PRESENT bytes=${bytes.size} sha1=${sha1(bytes)} name=${ItemDefCodec.readName(bytes)}")
                    if (System.getProperty("probe.hex") == "true") {
                        println("  ITEM_${id}_HEX=${bytes.joinToString("") { "%02x".format(it) }}")
                    }
                    if (System.getProperty("probe.opcodes") == "true") {
                        println("  ITEM_${id}_OPCODES=${ItemDefCodec.describeOpcodes(bytes).joinToString(" ")}")
                    }
                }
            }
        } finally {
            library.close()
        }
    }

    /** Raw decompressed archive bytes for [id], or null when this id has no data at all. */
    fun itemData(
        library: CacheLibrary,
        id: Int,
    ): ByteArray? = library.data(ArchiveType.ITEM.id, id ushr 8, id and 0xFF)

    /**
     * The complete, exact set of item ids that have archive data in [library], read directly from
     * the item index's real archive/file structure rather than by scanning a guessed id range.
     *
     * The item index (see [CacheMutation.item]) packs 256 items per archive: group = `id ushr 8`,
     * file = `id and 0xFF`. `index.archiveIds()` reports every group the index actually contains and
     * `archive.fileIds()` reports every file within it, so combining them enumerates every id with
     * data anywhere in the index - no assumption that ids are contiguous, no assumption that item
     * ids and model ids allocate the same way, and no upper bound on how far above the contiguous
     * block a hand-allocated or corrupted id could sit.
     */
    fun allItemIds(library: CacheLibrary): List<Int> {
        val index = library.index(ArchiveType.ITEM.id)
        val ids = mutableListOf<Int>()
        index.archiveIds().forEach { groupId ->
            val archive = index.archive(groupId) ?: return@forEach
            archive.fileIds().forEach { fileId -> ids += (groupId shl 8) or fileId }
        }
        return ids.sorted()
    }

    /**
     * The highest item id such that every id in `0..result` has archive data, derived from the
     * complete real id set in [allIds] (see [allItemIds]) rather than a fresh per-id cache read.
     * Mirrors the contiguity assumption [gg.rsmod.game.service.game.ItemMetadataService]'s
     * `0 until getCount(...)` load loop makes: the first hole from 0 is where that loop would stop.
     */
    fun maxContiguousItemId(allIds: List<Int>): Int {
        val present = allIds.toHashSet()
        var id = 0
        while (present.contains(id)) {
            id++
        }
        return id - 1
    }

    /** Convenience overload for callers that only have a [CacheLibrary], not a pre-enumerated id list. */
    fun maxContiguousItemId(library: CacheLibrary): Int = maxContiguousItemId(allItemIds(library))

    fun sha1(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

    private const val ORPHANS_REPORTED = 20
}
