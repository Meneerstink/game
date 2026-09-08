package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.ByteArrayOutputStream

/**
 * Follow-up to the Ferox world import (tx-20260905-052852): makes the enclave barrier usable in
 * the rev-667 client.
 *
 * Cache evidence (ModelConvertTool verify667 / Rev667RegionProbeTool locs on the production cache):
 *  * the clickable modern 'Barrier' loc (39652/39653 -> local 62434/62435, type-0 wall) uses model
 *    40699 -> 2278, whose 10 faces are ALL alpha 254 / colour 0, i.e. a deliberately invisible
 *    click slab that the OSRS client still lets you pick;
 *  * the visible energy field is a separate, option-less loc 39656 -> local 62438 (models 2300 +
 *    2301) placed on exactly the four barrier tiles 3123,3628 / 3134,3617 / 3134,3639 / 3154,3634.
 * In the 2011 client the invisible slab cannot be targeted through the archway, so the visible
 * field gets the same "Pass-Through" option (LocType opcode 30) and the server binds it to the
 * identical handler. Nothing else in the definition changes; the slab keeps its option too.
 *
 * Usage: `./gradlew :game:runFeroxBarrierOptionTool --args="plan|apply"` (A7 CacheTransaction on
 * both production caches, journal under the default root, verify after apply).
 */
object FeroxBarrierOptionTool {
    const val LOC_INDEX = 16
    const val FIELD_LOC_ID = 62438
    const val OPTION = "Pass-Through"

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val group = FIELD_LOC_ID ushr 8
        val file = FIELD_LOC_ID and 0xFF

        val library = CacheLibrary(FeroxImportTool.GAME_CACHE)
        val current =
            try {
                library.data(LOC_INDEX, group, file) ?: error("loc $FIELD_LOC_ID absent from ${FeroxImportTool.GAME_CACHE}")
            } finally {
                library.close()
            }
        val currentSha1 = CacheItemProbeTool.sha1(current)
        val before = Rev667LocType.decode(FIELD_LOC_ID, current)
        println("CURRENT idx${LOC_INDEX}_grp${group}_file$file bytes=${current.size} sha1=$currentSha1 name='${before.name}' models=${before.allModels} options=${before.options.toList()}")
        check(before.options.all { it == null }) { "loc $FIELD_LOC_ID already carries options ${before.options.toList()}; refusing" }
        check(current.last() == 0.toByte()) { "loc $FIELD_LOC_ID does not end with the opcode-0 terminator" }

        val updated =
            ByteArrayOutputStream().use { out ->
                out.write(current, 0, current.size - 1)
                out.write(30)
                OPTION.forEach { out.write(it.code) }
                out.write(0)
                out.write(0)
                out.toByteArray()
            }
        val after = Rev667LocType.decode(FIELD_LOC_ID, updated)
        check(after.options[0] == OPTION && after.allModels == before.allModels && after.name == before.name) { "re-decode mismatch: ${after.options.toList()}" }
        println("INTENDED bytes=${updated.size} sha1=${CacheItemProbeTool.sha1(updated)} options=${after.options.toList()}")

        val mutation = CacheMutation(LOC_INDEX, group, file, updated, "ferox barrier field loc $FIELD_LOC_ID + option '$OPTION'", currentSha1)
        val tx = CacheTransaction(listOf(FeroxImportTool.GAME_CACHE, FeroxImportTool.FILE_SERVER_CACHE), listOf(mutation))
        val preflight = tx.preflight()
        preflight.forEach { println("PREFLIGHT $it") }
        val blocking = tx.blockingErrors(preflight)
        blocking.forEach { println("BLOCKING: $it") }
        if (mode == "plan") {
            println("PLAN_ONLY transaction=${tx.id} (nothing written)")
            return
        }
        check(blocking.isEmpty()) { "Preflight has blocking errors; refusing to apply." }
        val result = tx.apply(preflight)
        println("APPLIED transaction=${tx.id} applied=${result.applied} skipped=${result.skipped}")
        val verify = tx.verify()
        verify.forEach { println("VERIFY_ERROR: $it") }
        check(verify.isEmpty()) { "Post-apply verification failed; see journal ${tx.id} for rollback." }
        println("VERIFY_OK both targets hold intended bytes (journal ${tx.journalDir})")
    }
}
