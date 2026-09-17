package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.ByteArrayOutputStream

/**
 * RCV-011 home (owner: Ferox Enclave on the minimap). The Ferox import (tx-20260905-052852) dropped the modern map
 * scene (OSRS op68) and map area (op82) of every imported loc, so the 667 client draws no minimap scenery or icons
 * there. This appends LocType op102 (msi) / op107 (mapelement) to exactly the imported Ferox locs whose OSRS value
 * has a pixel-proven 667 equivalent ([FeroxMapSpriteProbeTool], canvas-padded sprite compare + contact sheet):
 *
 *  * map scenes 4, 6, 7, 27, 46, 64, 70 -> msi with the same id (score <= 0.009, reused 667 trees/ladders already
 *    carry the same value, [FeroxMapDataProbeTool]);
 *  * map areas 5 -> 560 (bank), 12 -> 567 ("!", identical to 568/736; 567 is the one 667 locs use), 21 -> 575 (altar),
 *    33 -> 587 (pub), 40 -> 594, 58 -> 612 (transport arrow).
 *
 * SOURCE_BLOCKED (not patched): scene 22 is an empty OSRS frame; scenes 101/102 (red flowers) and areas 66 (ticked
 * scroll) and 652 (tombstone) have no 667 sprite.
 *
 * Usage: `./gradlew :game:runFeroxMinimapTool --args="plan|apply"` (CacheTransaction on both production caches,
 * journal under the default root, verify after apply).
 */
object FeroxMinimapTool {
    const val LOC_INDEX = 16

    /** OSRS map scene -> 667 msi, pixel-proven. */
    val SCENE_TO_MSI = mapOf(4 to 4, 6 to 6, 7 to 7, 27 to 27, 46 to 46, 64 to 64, 70 to 70)

    /** OSRS map area -> 667 mapelement, pixel-proven. */
    val AREA_TO_MAP_ELEMENT = mapOf(5 to 560, 12 to 567, 21 to 575, 33 to 587, 40 to 594, 58 to 612)

    /** SOURCE_BLOCKED: no 667 sprite shows the same picture (22 is an empty OSRS frame). */
    val BLOCKED_SCENES = setOf(22, 101, 102)
    val BLOCKED_AREAS = setOf(66, 652)

    /** local loc id -> msi, from the Ferox section of OSRS_IMPORT_MASTER.yml via [FeroxMapDataProbeTool]. */
    val MSI =
        mapOf(
            62366 to 4,
            62384 to 6, 62402 to 6, 62403 to 6, 62404 to 6, 62510 to 6, 62511 to 6, 62527 to 6, 62566 to 6, 62567 to 6, 62568 to 6,
            62431 to 7, 62432 to 7,
            62453 to 27, 62454 to 27, 62455 to 27,
            62416 to 46, 62471 to 46,
            62419 to 64, 62420 to 64,
            62434 to 70, 62435 to 70, 62559 to 70, 62560 to 70, 62561 to 70,
        )

    /** local loc id -> mapelement. */
    val MAP_ELEMENT = mapOf(62367 to 560, 62495 to 567, 62368 to 575, 62496 to 587, 62358 to 594, 62505 to 612)

    fun patched(
        current: ByteArray,
        msi: Int?,
        mapElement: Int?,
    ): ByteArray =
        ByteArrayOutputStream().use { out ->
            out.write(current, 0, current.size - 1)
            msi?.let {
                out.write(102)
                out.write(it ushr 8)
                out.write(it and 0xFF)
            }
            mapElement?.let {
                out.write(107)
                out.write(it ushr 8)
                out.write(it and 0xFF)
            }
            out.write(0)
            out.toByteArray()
        }

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val library = CacheLibrary(FeroxImportTool.GAME_CACHE)
        val mutations =
            try {
                (MSI.keys + MAP_ELEMENT.keys).sorted().map { id ->
                    val group = id ushr 8
                    val file = id and 0xFF
                    val current = library.data(LOC_INDEX, group, file) ?: error("loc $id absent from ${FeroxImportTool.GAME_CACHE}")
                    val before = Rev667LocType.decode(id, current)
                    check(before.msi == -1 && before.mapElement == -1) { "loc $id already has msi=${before.msi} mapElement=${before.mapElement}; refusing" }
                    check(current.last() == 0.toByte()) { "loc $id does not end with the opcode-0 terminator" }
                    val updated = patched(current, MSI[id], MAP_ELEMENT[id])
                    val after = Rev667LocType.decode(id, updated)
                    check(
                        after.msi == (MSI[id] ?: -1) && after.mapElement == (MAP_ELEMENT[id] ?: -1) &&
                            after.name == before.name && after.allModels == before.allModels && after.options.toList() == before.options.toList(),
                    ) { "re-decode mismatch for loc $id" }
                    println("LOC $id name='${before.name}' msi=${after.msi} mapElement=${after.mapElement} bytes ${current.size}->${updated.size}")
                    CacheMutation(LOC_INDEX, group, file, updated, "ferox minimap loc $id msi=${after.msi} mapelement=${after.mapElement}", CacheItemProbeTool.sha1(current))
                }
            } finally {
                library.close()
            }
        val tx = CacheTransaction(listOf(FeroxImportTool.GAME_CACHE, FeroxImportTool.FILE_SERVER_CACHE), mutations)
        val preflight = tx.preflight()
        preflight.forEach { println("PREFLIGHT $it") }
        val blocking = tx.blockingErrors(preflight)
        blocking.forEach { println("BLOCKING: $it") }
        if (mode == "plan") {
            println("PLAN_ONLY transaction=${tx.id} mutations=${mutations.size} (nothing written)")
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
