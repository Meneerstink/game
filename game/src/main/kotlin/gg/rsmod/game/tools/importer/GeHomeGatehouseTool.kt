package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/**
 * Makes the Grand Exchange home's south gatehouse always visible (owner 2026-09-23: "de poort moet altijd zichtbaar
 * zijn"). Its roof, arched facade and animated banners (locs 47109/47110, model 19908) and the side pieces (47113/47114)
 * stand on level 1, which the client hides whenever the player walks under the roof or plays with "Remove roofs:
 * Always"; what remained were two roofless grey blocks with loose-paper models (47105/47106, level 0).
 *
 * This bounded transaction on both production caches:
 *  * moves every 47109/47110/47113/47114 placement from level 1 to level 0 and gives those loc types a vertical offset
 *    (opcode 71, value x4 in the client) equal to the level 1 - level 0 height of the gatehouse tiles (37 height steps
 *    of 32 = 1184, all gatehouse tiles), so they render exactly where they stood but are never hidden with the roofs;
 *  * makes the two side pieces non-blocking (opcode 17), so nothing new blocks the ground-floor lanes;
 *  * keeps the gatehouse body (lower walls and middle pillar, model 19867) of 47105/47106 but drops their loose-paper
 *    litter models (19504/19721, 98 and 116 tiny faces), so the ground under and around the gate is clean.
 * All six ids are placed only here (ObjectPlacementProbeTool, 8 hits, region 12598).
 *
 * Usage: `./gradlew :game:runGeHomeGatehouseTool --args="plan|apply"`.
 */
object GeHomeGatehouseTool {
    private const val GAME_CACHE = "C:/RSPS/game/game/data/cache"
    private const val FILE_SERVER_CACHE = "C:/RSPS/file-server/cache"
    private const val XTEAS_FILE = "C:/RSPS/game/game/data/xteas/xteas.json"
    private const val LOC_INDEX = 16
    private const val REGION_ID = 12598

    private val RAISED = listOf(47109, 47110, 47113, 47114)
    private val UNBLOCKED = setOf(47113, 47114)
    /** Gatehouse body loc -> the litter model dropped from its model list, and where the body stands. */
    private val TRIMMED = mapOf(47105 to 19504, 47106 to 19721)
    private val BODY_MODEL = 19867
    private val BODY_TILES = mapOf(47105 to (3159 to 3462), 47106 to (3159 to 3464))

    /** -1184 client units (level 1 is 37 * 32 above level 0 on every gatehouse tile) / 4. */
    private const val Y_OFFSET = -296

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        if (mode == "probe") return probe(args.drop(1).map { it.toInt() })
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply|probe <locId...>" }

        val mutations = ArrayList<CacheMutation>()
        val library = CacheLibrary(GAME_CACHE)
        try {
            RAISED.forEach { id ->
                val group = id ushr 8
                val file = id and 0xFF
                val current = library.data(LOC_INDEX, group, file) ?: error("loc $id missing")
                check(current.last() == 0.toByte()) { "loc $id does not end with the opcode-0 terminator" }
                val suffix = ArrayList<Byte>()
                suffix += 71.toByte()
                suffix += (Y_OFFSET shr 8).toByte()
                suffix += Y_OFFSET.toByte()
                if (id in UNBLOCKED) suffix += 17.toByte()
                val tail = suffix.toByteArray() + 0.toByte()
                if (current.size >= tail.size && current.copyOfRange(current.size - tail.size, current.size).contentEquals(tail)) {
                    println("LOC $id already raised")
                    return@forEach
                }
                val updated = current.copyOf(current.size - 1) + tail
                val before = Rev667LocType.decode(id, current)
                val after = Rev667LocType.decode(id, updated)
                check(after.allModels == before.allModels) { "loc $id re-decode changed its models" }
                mutations += CacheMutation(LOC_INDEX, group, file, updated, "GE gatehouse loc $id: y offset $Y_OFFSET" + if (id in UNBLOCKED) ", non-blocking" else "", CacheItemProbeTool.sha1(current))
            }

            TRIMMED.forEach { (id, litter) ->
                val group = id ushr 8
                val file = id and 0xFF
                val current = library.data(LOC_INDEX, group, file) ?: error("loc $id missing")
                val before = Rev667LocType.decode(id, current)
                if (before.allModels == listOf(BODY_MODEL)) return@forEach println("LOC $id already trimmed")
                check(before.allModels == listOf(BODY_MODEL, litter)) { "loc $id models ${before.allModels} unexpected" }
                // opcode 1: [count=1][shape=10][models=2][19867][litter] -> [models=1][19867]
                val pattern = byteArrayOf(2, (BODY_MODEL shr 8).toByte(), BODY_MODEL.toByte(), (litter shr 8).toByte(), litter.toByte())
                val at = (0..current.size - pattern.size).single { i -> (pattern.indices).all { current[i + it] == pattern[it] } }
                val updated = current.copyOfRange(0, at) + byteArrayOf(1, (BODY_MODEL shr 8).toByte(), BODY_MODEL.toByte()) + current.copyOfRange(at + pattern.size, current.size)
                check(Rev667LocType.decode(id, updated).allModels == listOf(BODY_MODEL)) { "loc $id trim re-decode failed" }
                mutations += CacheMutation(LOC_INDEX, group, file, updated, "GE gatehouse loc $id: drop litter model $litter", CacheItemProbeTool.sha1(current))
            }

            val key = Rev667RegionProbeTool.loadKeys(File(XTEAS_FILE))[REGION_ID] ?: error("No XTEA for region $REGION_ID")
            val rx = REGION_ID shr 8
            val rz = REGION_ID and 0xFF
            val locName = "l${rx}_$rz"
            val locArchive = library.index(Rev667RegionProbeTool.MAP_INDEX).archive(locName, key) ?: error("$locName missing")
            val locBytes = locArchive.file(0)?.data ?: error("$locName file 0 missing")
            val locs = Rev667LocCodec.decode(locBytes)
            check(Rev667LocCodec.encode(locs).contentEquals(locBytes)) { "$locName loc round-trip failed" }
            var lowered = 0
            var restored = 0
            val updatedLocs =
                locs.map { loc ->
                    if (loc.id in RAISED && loc.plane == 1) Rev667Loc(loc.id, loc.localX, loc.localZ, 0, loc.type, loc.rotation).also { lowered++ } else loc
                }.toMutableList()
            BODY_TILES.forEach { (id, tile) ->
                if (updatedLocs.none { it.id == id }) {
                    updatedLocs += Rev667Loc(id, tile.first - rx * 64, tile.second - rz * 64, 0, 10, 0)
                    restored++
                }
            }
            val encoded = Rev667LocCodec.encode(updatedLocs)
            if (!encoded.contentEquals(locBytes)) {
                mutations +=
                    CacheMutation(
                        Rev667RegionProbeTool.MAP_INDEX,
                        locArchive.id,
                        0,
                        encoded,
                        "GE gatehouse: $lowered roof/facade placements to level 0, $restored body placements restored",
                        CacheItemProbeTool.sha1(locBytes),
                        xtea = key,
                    )
            }
            println("GE_GATEHOUSE lowered=$lowered restored=$restored defMutations=${mutations.size - if (encoded.contentEquals(locBytes)) 0 else 1}")
        } finally {
            library.close()
        }

        if (mutations.isEmpty()) {
            println("NOTHING_TO_DO")
            return
        }
        val tx = CacheTransaction(listOf(GAME_CACHE, FILE_SERVER_CACHE), mutations)
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

    /** Prints each loc's raw definition bytes and, per model, its vertex/face counts, bounds and face colours. */
    private fun probe(ids: List<Int>) {
        val library = CacheLibrary(GAME_CACHE)
        try {
            ids.forEach { id ->
                val bytes = library.data(LOC_INDEX, id ushr 8, id and 0xFF) ?: error("loc $id missing")
                val def = Rev667LocType.decode(id, bytes)
                println("LOC $id bytes=${bytes.joinToString(" ") { "%02x".format(it) }} models=${def.modelsByShape}")
                def.allModels.distinct().forEach { modelId ->
                    val data = library.data(7, modelId, 0) ?: return@forEach println("  MODEL $modelId missing")
                    val newFormat = data[data.size - 1] == (-1).toByte() && data[data.size - 2] == (-1).toByte()
                    val h = data.size - if (newFormat) 23 else 18
                    val vertices = ((data[h].toInt() and 0xFF) shl 8) or (data[h + 1].toInt() and 0xFF)
                    val faces = ((data[h + 2].toInt() and 0xFF) shl 8) or (data[h + 3].toInt() and 0xFF)
                    println("  MODEL $modelId bytes=${data.size} newFormat=$newFormat vertices=$vertices faces=$faces")
                }
            }
        } finally {
            library.close()
        }
    }
}
