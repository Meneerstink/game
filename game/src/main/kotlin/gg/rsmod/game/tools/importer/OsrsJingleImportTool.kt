package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/**
 * N462 ToA jingles (ChatGPT-B research 07-11, N462.txt "JINGLES"): OSRS Wiki Test of Nobility (Tombs of Amascut)?oldid=15353884 = cache id
 * 295 (a challenge room cleared), Tomb Cleared (Tombs of Amascut)?oldid=15353885 = cache id 296 (a boss defeated). Our index 11 holds OTHER
 * jingles at 295/296, so they go to the next free groups 488/489 (index 11 holds 0..487). Song bytes copied unchanged (played on the 667
 * instrument patches, like every imported OSRS track). Usage: plan|apply
 */
object OsrsJingleImportTool {
    const val JINGLE_INDEX = 11
    /** OSRS jingle -> local group. */
    val JINGLES: Map<Int, Int> = linkedMapOf(295 to 488, 296 to 489)

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.firstOrNull() ?: error("Usage: plan|apply")
        val songs = ModernCacheReader(File(OsrsMusicImportTool.SOURCE_CACHE)).use { reader ->
            JINGLES.keys.associateWith { reader.file(JINGLE_INDEX, it, 0) ?: error("OSRS jingle $it missing") }
        }
        val library = CacheLibrary(FeroxImportTool.GAME_CACHE)
        try {
            JINGLES.forEach { (osrs, local) ->
                // A re-run finds the jingle already there (NO_OP); any other content in the target group is refused.
                val existing = if (library.index(JINGLE_INDEX).archive(local) == null) null else library.data(JINGLE_INDEX, local, 0)
                check(existing == null || existing.contentEquals(songs.getValue(osrs))) { "index $JINGLE_INDEX group $local holds a different jingle" }
            }
        } finally {
            library.close()
        }
        val mutations = JINGLES.map { (osrs, local) ->
            val bytes = songs.getValue(osrs)
            println("JINGLE osrs=$osrs bytes=${bytes.size} sha1=${CacheItemProbeTool.sha1(bytes)} -> index $JINGLE_INDEX group $local")
            CacheMutation(JINGLE_INDEX, local, 0, bytes, "osrs jingle $osrs -> group $local")
        }
        val tx = CacheTransaction(listOf(FeroxImportTool.GAME_CACHE, FeroxImportTool.FILE_SERVER_CACHE), mutations)
        val preflight = tx.preflight()
        println("PREFLIGHT transaction=${tx.id} mutations=${mutations.size} outcomes=${preflight.groupingBy { it.outcome }.eachCount()}")
        val blocking = tx.blockingErrors(preflight)
        blocking.forEach { println("BLOCKING: $it") }
        if (mode != "apply") {
            println("PLAN_ONLY transaction=${tx.id} (nothing written)")
            return
        }
        check(blocking.isEmpty()) { "Preflight has blocking errors; refusing to apply." }
        val result = tx.apply(preflight)
        println("APPLIED transaction=${tx.id} applied=${result.applied} skipped=${result.skipped}")
        val verify = tx.verify()
        verify.forEach { println("VERIFY_ERROR: $it") }
        check(verify.isEmpty()) { "Post-apply verification failed; see journal ${tx.id} for rollback." }
        println("VERIFY_OK both targets hold intended bytes")
    }
}
