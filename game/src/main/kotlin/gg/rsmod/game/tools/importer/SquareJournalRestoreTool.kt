package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.RspsPaths
import java.io.File

/**
 * Puts one map square's tile file (`m<x>_<z>`) and loc file (`l<x>_<z>`) back to the bytes an earlier transaction journalled
 * (`import-journal/<tx>/..._idx5_grp<id>_file0.orig`), through a new [CacheTransaction] on both caches. The loc types and models the
 * earlier transaction created stay (unused). N569 (2026-11-05): the 'larran-ruins' patch turned the lava ground decoration of the
 * Ruins into flattened yellow/black triangles; the rev-667 Ruins use the same loc ids as OSRS, so the square goes back.
 *
 * Usage: `./gradlew :game:runSquareJournalRestoreTool --args="<tx id> <square> plan|apply"`
 */
object SquareJournalRestoreTool {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 3 && args[2] in setOf("plan", "apply")) { "Usage: <tx id> <square> plan|apply" }
        val (txId, squareArg, mode) = args
        val square = squareArg.toInt()
        val journal = File(RspsPaths.root("import-journal/$txId"))
        require(journal.isDirectory) { "no journal $journal" }
        val key = Rev667RegionProbeTool.loadKeys(File(FeroxImportTool.XTEAS_FILE))[square]
        val mutations = ArrayList<CacheMutation>()
        val library = CacheLibrary(FeroxImportTool.GAME_CACHE)
        try {
            val maps = library.index(FeroxImportTool.MAP_INDEX)
            val mapArchive = maps.archive(OsrsRegionImportTool.mapName(square)) ?: error("map missing")
            val locArchive = maps.archive(OsrsRegionImportTool.locName(square), key) ?: error("loc file missing or unreadable")
            listOf(Triple(mapArchive.id, mapArchive.file(0)!!.data!!, null as IntArray?), Triple(locArchive.id, locArchive.file(0)!!.data!!, key))
                .forEach { (group, current, xtea) ->
                    val orig = File(journal, "C_RSPS_game_game_data_cache__idx${FeroxImportTool.MAP_INDEX}_grp${group}_file0.orig")
                    require(orig.isFile) { "journal has no original for group $group" }
                    val bytes = orig.readBytes()
                    println("GROUP $group current=${current.size}b original=${bytes.size}b same=${bytes.contentEquals(current)}")
                    if (!bytes.contentEquals(current)) {
                        mutations += CacheMutation(FeroxImportTool.MAP_INDEX, group, 0, bytes, "square $square group $group back to $txId original",
                            CacheItemProbeTool.sha1(current), xtea = xtea)
                    }
                }
        } finally {
            library.close()
        }
        if (mutations.isEmpty()) return println("NOTHING_TO_DO")
        val tx = CacheTransaction(listOf(FeroxImportTool.GAME_CACHE, FeroxImportTool.FILE_SERVER_CACHE), mutations)
        val preflight = tx.preflight()
        preflight.forEach { println("PREFLIGHT $it") }
        val blocking = tx.blockingErrors(preflight)
        blocking.forEach { println("BLOCKING: $it") }
        if (mode == "plan") return println("PLAN_ONLY transaction=${tx.id} (nothing written)")
        check(blocking.isEmpty()) { "Preflight has blocking errors; refusing to apply." }
        val result = tx.apply(preflight)
        println("APPLIED transaction=${tx.id} applied=${result.applied} skipped=${result.skipped}")
        val verify = tx.verify()
        verify.forEach { println("VERIFY_ERROR: $it") }
        check(verify.isEmpty()) { "Post-apply verification failed; see journal ${tx.id} for rollback." }
        println("VERIFY_OK both targets hold intended bytes (journal ${tx.journalDir})")
    }
}
