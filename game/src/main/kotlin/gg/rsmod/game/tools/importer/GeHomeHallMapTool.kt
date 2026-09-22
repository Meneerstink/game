package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/**
 * Gives the Grand Exchange home service hall a real, cache-backed stone floor instead of covering its lawn with
 * 121 red POH rug objects. Overlay 188 is not guessed: it is the revision-667 Grand Exchange paving immediately
 * outside the east entrance (3164,3485,0). The same bounded transaction removes only the old lawn's plane-0 ground
 * decorations inside the 11 x 11 hall, so grass tufts cannot poke through the new paving.
 *
 * Usage: `./gradlew :game:runGeHomeHallMapTool --args="plan|apply"`.
 */
object GeHomeHallMapTool {
    private const val GAME_CACHE = "C:/RSPS/game/game/data/cache"
    private const val FILE_SERVER_CACHE = "C:/RSPS/file-server/cache"
    private const val XTEAS_FILE = "C:/RSPS/game/game/data/xteas/xteas.json"

    private const val MIN_X = 3152
    private const val MIN_Z = 3470
    private const val MAX_X = 3162
    private const val MAX_Z = 3480
    private const val REGION_ID = 12598
    private const val GE_PAVING_OVERLAY = 188
    private const val SOURCE_X = 3164
    private const val SOURCE_Z = 3485

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }

        val mutations = ArrayList<CacheMutation>()
        val library = CacheLibrary(GAME_CACHE)
        try {
            val keys = Rev667RegionProbeTool.loadKeys(File(XTEAS_FILE))
            val rx = REGION_ID shr 8
            val rz = REGION_ID and 0xFF
            val mapName = "m${rx}_$rz"
            val locName = "l${rx}_$rz"

            val mapArchive = library.index(Rev667RegionProbeTool.MAP_INDEX).archive(mapName) ?: error("$mapName missing")
            val mapBytes = mapArchive.file(0)?.data ?: error("$mapName file 0 missing")
            val tiles = Rev667TileCodec.decode(mapBytes)
            check(Rev667TileCodec.encode(tiles).contentEquals(mapBytes)) { "$mapName tile round-trip failed" }

            val source = tiles.tiles[0][SOURCE_X - rx * 64][SOURCE_Z - rz * 64]
            check(source.overlayId and 0xFF == GE_PAVING_OVERLAY && source.overlayShape == 0) {
                "Grand Exchange paving source $SOURCE_X,$SOURCE_Z changed: overlay=${source.overlayId and 0xFF} shape=${source.overlayShape}"
            }

            var floorChanges = 0
            for (x in MIN_X..MAX_X) for (z in MIN_Z..MAX_Z) {
                val tile = tiles.tiles[0][x - rx * 64][z - rz * 64]
                if (tile.overlayId and 0xFF != GE_PAVING_OVERLAY || tile.overlayShape != 0 || tile.overlayRotation != 0) {
                    floorChanges++
                    tile.overlayId = GE_PAVING_OVERLAY
                    tile.overlayShape = 0
                    tile.overlayRotation = 0
                }
            }
            val updatedMap = Rev667TileCodec.encode(tiles)
            if (!updatedMap.contentEquals(mapBytes)) {
                mutations +=
                    CacheMutation(
                        Rev667RegionProbeTool.MAP_INDEX,
                        mapArchive.id,
                        0,
                        updatedMap,
                        "GE home hall: $floorChanges tiles use adjacent GE paving overlay $GE_PAVING_OVERLAY",
                        CacheItemProbeTool.sha1(mapBytes),
                    )
            }

            val key = keys[REGION_ID] ?: error("No XTEA for region $REGION_ID")
            val locArchive = library.index(Rev667RegionProbeTool.MAP_INDEX).archive(locName, key) ?: error("$locName missing")
            val locBytes = locArchive.file(0)?.data ?: error("$locName file 0 missing")
            val locs = Rev667LocCodec.decode(locBytes)
            check(Rev667LocCodec.encode(locs).contentEquals(locBytes)) { "$locName loc round-trip failed" }
            val removed =
                locs.filter {
                    val x = rx * 64 + it.localX
                    val z = rz * 64 + it.localZ
                    it.plane == 0 && it.type == 22 && x in MIN_X..MAX_X && z in MIN_Z..MAX_Z
                }
            val kept = locs - removed.toSet()
            val updatedLocs = Rev667LocCodec.encode(kept)
            if (!updatedLocs.contentEquals(locBytes)) {
                mutations +=
                    CacheMutation(
                        Rev667RegionProbeTool.MAP_INDEX,
                        locArchive.id,
                        0,
                        updatedLocs,
                        "GE home hall: remove ${removed.size} lawn ground decorations beneath stone floor",
                        CacheItemProbeTool.sha1(locBytes),
                        xtea = key,
                    )
            }
            println("GE_HOME_HALL region=$REGION_ID floorChanges=$floorChanges groundDecorationsRemoved=${removed.size}")
        } finally {
            library.close()
        }

        if (mutations.isEmpty()) {
            println("NOTHING_TO_DO hall map and both production caches are already at the intended state")
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
}
