package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/**
 * Read-only survey of any rev-667 target-cache index, used by the modern-content import pipeline
 * to plan collision-safe local ids (`RSPS_DECISIONS.md` 2026-09-02 STANDING OWNER AUTHORIZATION,
 * `RSPS_CURRENT_SPRINT.json` gate A6).
 *
 * [CacheItemProbeTool] answers the same questions for items only, where the server's
 * `ItemMetadataService` walks `0 until getCount(...)` and therefore makes id contiguity a hard boot
 * invariant. Other indexes - models above all - are addressed by id on demand and have no such
 * invariant, so allocation there only has to avoid collisions. This tool reports the facts needed
 * to tell those two cases apart per index instead of assuming either.
 *
 * Usage: `<cachePath>[;<cachePath>] <indexId> [groupId ...]`.
 */
object CacheIndexProbeTool {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size >= 2) { "Usage: <cachePath>[;<cachePath>] <indexId> [groupId ...]" }
        val indexId = args[1].toInt()
        val groups = args.drop(2).map { it.toInt() }
        args[0].split(";").filter { it.isNotBlank() }.forEach { path ->
            probe(path, indexId, groups)
        }
    }

    private fun probe(
        cachePath: String,
        indexId: Int,
        groups: List<Int>,
    ) {
        require(File(cachePath).isDirectory) { "Cache path '$cachePath' is not a directory." }
        val library = CacheLibrary(cachePath)
        try {
            val index = library.index(indexId)
            val ids = index.archiveIds().sorted()
            println("CACHE=$cachePath INDEX=$indexId")
            println("  GROUP_COUNT=${ids.size}")
            println("  MIN_GROUP_ID=${ids.firstOrNull()} MAX_GROUP_ID=${ids.lastOrNull()}")
            println("  CONTIGUOUS=${ids.isNotEmpty() && ids.last() - ids.first() + 1 == ids.size}")
            println("  NEXT_FREE_GROUP_ID=${(ids.lastOrNull() ?: -1) + 1}")
            val holes = ids.zipWithNext().filter { (a, b) -> b - a > 1 }
            println("  HOLE_COUNT=${holes.size}")
            holes.take(HOLES_REPORTED).forEach { (a, b) -> println("  HOLE=${a + 1}..${b - 1}") }
            if (holes.size > HOLES_REPORTED) println("  HOLES_TRUNCATED=${holes.size - HOLES_REPORTED}")

            groups.forEach { groupId ->
                val archive = index.archive(groupId)
                if (archive == null) {
                    println("  GROUP_$groupId=ABSENT")
                } else {
                    val files = archive.fileIds().sorted()
                    println("  GROUP_$groupId=PRESENT files=${files.size} fileIds=${files.take(FILE_IDS_REPORTED)}")
                    files.take(FILE_IDS_REPORTED).forEach { fileId ->
                        val data = archive.file(fileId)?.data
                        println(
                            "    FILE_$fileId bytes=${data?.size ?: 0} " +
                                "sha1=${data?.let { CacheItemProbeTool.sha1(it) } ?: "NONE"}",
                        )
                    }
                }
            }
        } finally {
            library.close()
        }
    }

    private const val HOLES_REPORTED = 10
    private const val FILE_IDS_REPORTED = 8
}
