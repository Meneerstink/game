package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/**
 * Grand Exchange home map/minimap icon cleanup (owner 2026-09-22: "Remove unrelated map/minimap icons such as
 * Runecrafting, Herblore and other leftover activity icons. Keep ONLY the icons that belong there: Grand Exchange,
 * Banker / Bank. Check both minimap and world map data").
 *
 * **Where the icons come from.** The client draws a minimap icon from (a) the world map's `main_staticelements` file,
 * (b) the `mapelement` of every LocType in the loaded scene and (c) the `mapElement` of minimap-visible NPCs. The world
 * map draws the same loc-based elements from the same landscape. A probe of all three (2026-09-22, read-only decoders
 * of the client's NPCType / LocType / MapElementType) found: no GE-area staticelement except the "Grand Exchange" text
 * label (element 284), no GE NPC with an icon except the casino croupier (the owner's own 2026-09-20 request, kept),
 * and twelve invisible Jagex map-marker locs (type 22, model 1105) in region 12598. Four are the bank ($, element 560)
 * and four the Grand Exchange icon (637). The other ten are removed here:
 *
 *  * 638-641, 643 - the GE trading-area icons (herbs, logs, weapons, runes, food);
 *  * 595 x2 - water source; 612 - transport (the GE spirit tree); 1093 - shortcut; 225 - music.
 *
 * Removing the placement removes the icon from both the minimap and the world map, which both read it from this
 * landscape. The LocTypes stay in the cache; only these placements go. Mechanism as [FeroxPortalCleanupTool]:
 * byte-exact [Rev667LocCodec] round-trip, one [CacheTransaction] on both production caches.
 *
 * Usage: `./gradlew :game:runGeMapIconCleanupTool --args="plan|apply"`.
 */
object GeMapIconCleanupTool {
    class Removal(val locId: Int, val x: Int, val z: Int, val plane: Int, val label: String)

    val REMOVALS =
        listOf(
            Removal(27991, 3156, 3501, 1, "GE trading icon 638 (herbs)"),
            Removal(27992, 3174, 3482, 1, "GE trading icon 639 (logs)"),
            Removal(27993, 3165, 3481, 0, "GE trading icon 640 (weapons)"),
            Removal(27994, 3174, 3501, 1, "GE trading icon 641 (sprite 1365)"),
            Removal(27996, 3185, 3492, 0, "GE trading icon 643 (sprite 1367)"),
            Removal(2771, 3190, 3470, 0, "water source icon 595"),
            Removal(2771, 3164, 3491, 1, "water source icon 595"),
            Removal(7389, 3187, 3511, 0, "transport icon 612"),
            Removal(39510, 3141, 3515, 0, "shortcut icon 1093"),
            Removal(43522, 3145, 3472, 0, "music icon 225"),
        )

    /** Marker locs that must survive (bank 560 and Grand Exchange 637), checked after the edit. */
    val KEPT = setOf(2738, 27990)

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val mutations = ArrayList<CacheMutation>()
        val library = CacheLibrary(FeroxImportTool.GAME_CACHE)
        try {
            val keys = Rev667RegionProbeTool.loadKeys(File(FeroxImportTool.XTEAS_FILE))
            REMOVALS.groupBy { ((it.x shr 6) shl 8) or (it.z shr 6) }.forEach { (regionId, removals) ->
                val rx = regionId shr 8
                val rz = regionId and 0xFF
                val locName = "l${rx}_$rz"
                val archive =
                    library.index(FeroxImportTool.MAP_INDEX).archive(locName, keys[regionId]) ?: error("$locName missing")
                val bytes = archive.file(0)!!.data!!
                val locs = Rev667LocCodec.decode(bytes)
                check(Rev667LocCodec.encode(locs).contentEquals(bytes)) { "$locName round-trip failed" }
                val toRemove =
                    removals.map { r ->
                        val hit =
                            locs.filter {
                                it.id == r.locId && it.localX == r.x - rx * 64 && it.localZ == r.z - rz * 64 && it.plane == r.plane
                            }
                        check(hit.size == 1) {
                            "${r.label}: expected exactly one placement of ${r.locId} at ${r.x},${r.z},${r.plane}, found ${hit.size}"
                        }
                        check(hit[0].type == 22) { "${r.label}: not a map-marker (type ${hit[0].type})" }
                        println("REMOVE ${r.label} loc=${r.locId} at ${r.x},${r.z},${r.plane} region=$regionId")
                        hit[0]
                    }
                val kept = locs - toRemove.toSet()
                println("KEPT markers: " + kept.filter { it.id in KEPT }.joinToString { "${it.id}@${rx * 64 + it.localX},${rz * 64 + it.localZ},${it.plane}" })
                val newBytes = Rev667LocCodec.encode(kept)
                check(Rev667LocCodec.decode(newBytes).size == kept.size)
                println("REGION $regionId $locName locs ${locs.size} -> ${kept.size} bytes ${bytes.size} -> ${newBytes.size}")
                mutations +=
                    CacheMutation(
                        FeroxImportTool.MAP_INDEX,
                        archive.id,
                        0,
                        newBytes,
                        "GE map icon cleanup: remove ${removals.size} non-bank/non-GE marker locs from $locName",
                        CacheItemProbeTool.sha1(bytes),
                        xtea = keys[regionId],
                    )
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
