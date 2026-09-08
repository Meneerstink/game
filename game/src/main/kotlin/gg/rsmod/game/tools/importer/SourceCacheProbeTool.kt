package gg.rsmod.game.tools.importer

import java.io.File

/**
 * Read-only inspector for the **pinned upstream modern OSRS cache**
 * (`C:\RSPS\import-source\openrs2-2686\cache`, see that directory's `PROVENANCE.txt`), as opposed
 * to [CacheItemProbeTool] which inspects this project's two rev-667 target caches.
 *
 * Reads through [ModernCacheReader] rather than `com.displee:rs-cache-library`, which cannot parse
 * this build's reference tables - see that class's doc for the evidence.
 *
 * The two cache generations do not lay item definitions out the same way, which is why a separate
 * probe exists rather than reusing the 667 one:
 *
 *  - rev-667 (this project): index [gg.rsmod.game.fs.ArchiveType.ITEM] = 19, group `id ushr 8`,
 *    file `id and 0xFF`.
 *  - modern OSRS: index 2 (configs), group 10 (item), file `id` - one flat group per config type.
 *
 * Verbs:
 *
 *  - `indexes <cachePath>` - every index on disk with its protocol, flags and group count.
 *  - `groupinfo <cachePath> <index> <group>` - file count and id range of one group.
 *  - `find <cachePath> <index> <group> <asciiNeedle>` - every file in the group whose raw bytes
 *    contain the needle, with size and sha1. Locates an upstream item by name without needing a
 *    modern opcode table first.
 *  - `dump <cachePath> <index> <group> <file> [outFile]` - size/sha1/hex of one file, optionally
 *    written to disk for the staging step of an import.
 *  - `container <cachePath> <index> <group> [outFile]` - the whole decompressed group.
 */
object SourceCacheProbeTool {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size >= 2) { USAGE }
        val verb = args[0]
        val cachePath = args[1]

        ModernCacheReader(File(cachePath)).use { reader ->
            when (verb) {
                "indexes" -> {
                    println("CACHE=$cachePath")
                    reader.availableIndexes().forEach { indexId ->
                        val result = runCatching { reader.index(indexId) }
                        result.fold(
                            onSuccess = { index ->
                                println(
                                    "  INDEX=$indexId protocol=${index.protocol} version=${index.version} " +
                                        "groups=${index.groups.size} names=${index.hasNames} digests=${index.hasDigests} " +
                                        "lengths=${index.hasLengths} uncompressedChecksums=${index.hasUncompressedChecksums}",
                                )
                            },
                            onFailure = { println("  INDEX=$indexId UNREADABLE: ${it.javaClass.simpleName}: ${it.message}") },
                        )
                    }
                }
                "groupinfo" -> {
                    require(args.size >= 4) { USAGE }
                    val group = reader.index(args[2].toInt()).groups[args[3].toInt()]
                        ?: error("No group ${args[3]} in index ${args[2]}.")
                    println(
                        "CACHE=$cachePath INDEX=${args[2]} GROUP=${group.id} files=${group.fileIds.size} " +
                            "minFile=${group.fileIds.minOrNull()} maxFile=${group.fileIds.maxOrNull()} version=${group.version}",
                    )
                }
                "find" -> {
                    require(args.size >= 5) { USAGE }
                    val indexId = args[2].toInt()
                    val groupId = args[3].toInt()
                    val needleBytes = args[4].toByteArray(Charsets.ISO_8859_1)
                    var hits = 0
                    reader.files(indexId, groupId).forEach { (fileId, bytes) ->
                        if (contains(bytes, needleBytes)) {
                            hits++
                            println("  HIT file=$fileId bytes=${bytes.size} sha1=${CacheItemProbeTool.sha1(bytes)}")
                        }
                    }
                    println("FIND index=$indexId group=$groupId needle='${args[4]}' hits=$hits")
                }
                "dump" -> {
                    require(args.size >= 5) { USAGE }
                    val indexId = args[2].toInt()
                    val groupId = args[3].toInt()
                    val fileId = args[4].toInt()
                    val bytes =
                        reader.files(indexId, groupId)[fileId]
                            ?: error("No file $fileId in index $indexId group $groupId.")
                    println("DUMP index=$indexId group=$groupId file=$fileId bytes=${bytes.size} sha1=${CacheItemProbeTool.sha1(bytes)}")
                    println("HEX=${bytes.joinToString("") { "%02x".format(it) }}")
                    writeIfRequested(args.getOrNull(5), bytes)
                }
                "container" -> {
                    require(args.size >= 4) { USAGE }
                    val indexId = args[2].toInt()
                    val groupId = args[3].toInt()
                    val container = reader.readContainer(indexId, groupId) ?: error("No data for index $indexId group $groupId.")
                    val bytes = reader.decompress(container)
                    println("CONTAINER index=$indexId group=$groupId compressed=${container.size} decompressed=${bytes.size} sha1=${CacheItemProbeTool.sha1(bytes)}")
                    writeIfRequested(args.getOrNull(4), bytes)
                }
                "itemdef" -> {
                    require(args.size >= 3) { USAGE }
                    val itemId = args[2].toInt()
                    val bytes =
                        reader.files(ModernCacheReader.INDEX_CONFIG, ModernCacheReader.CONFIG_GROUP_ITEM)[itemId]
                            ?: error("Upstream cache has no item definition $itemId.")
                    println("ITEMDEF upstream_id=$itemId bytes=${bytes.size} sha1=${CacheItemProbeTool.sha1(bytes)}")
                    println(ModernItemDefDecoder.decode(itemId, bytes).describe())
                }
                else -> error("Unknown verb '$verb'.\n$USAGE")
            }
        }
    }

    private fun writeIfRequested(
        path: String?,
        bytes: ByteArray,
    ) {
        if (path == null) return
        File(path).parentFile?.mkdirs()
        File(path).writeBytes(bytes)
        println("WROTE=$path")
    }

    private fun contains(
        haystack: ByteArray,
        needle: ByteArray,
    ): Boolean {
        if (needle.isEmpty() || needle.size > haystack.size) return false
        outer@ for (start in 0..haystack.size - needle.size) {
            for (offset in needle.indices) {
                if (haystack[start + offset] != needle[offset]) continue@outer
            }
            return true
        }
        return false
    }

    private const val USAGE =
        "Usage:\n" +
            "  indexes <cachePath>\n" +
            "  groupinfo <cachePath> <index> <group>\n" +
            "  find <cachePath> <index> <group> <asciiNeedle>\n" +
            "  dump <cachePath> <index> <group> <file> [outFile]\n" +
            "  container <cachePath> <index> <group> [outFile]\n" +
            "  itemdef <cachePath> <upstreamItemId>"
}
