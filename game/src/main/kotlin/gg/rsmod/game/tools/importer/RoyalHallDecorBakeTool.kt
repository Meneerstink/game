package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.model.decor.HomeDecorBake
import gg.rsmod.game.model.decor.HomeDecorFile
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Bakes the Royal Hall's dressing (`data/cfg/home_decor.txt`) into region 12598's loc file `l49_54` of both caches (owner
 * 2026-10-09: the hall built up piece by piece after every login / teleport / stair climb; see [HomeDecorBake]).
 *
 * Loc-only: it starts from the CURRENT `l49_54` (RoyalHallMapTool rebuilds from the pristine journal copies and would also
 * rewrite `m49_54`), takes out the locs of the previous bake ([HomeDecorBake.MANIFEST]), then puts every `obj` line in -
 * carpet pieces as their forcedecor copies. Like `World.spawn`, a placement replaces the cache loc of the same client layer
 * on its tile (the cache decals under the carpet). The map group `m49_54` is never touched. After both caches verified, the
 * manifest is written; `home_decor_live.plugin.kts` then spawns only what the file has beyond it.
 *
 * Refuses: `del` lines (they hide cache locs; bake those by hand), placements outside the region, two placements on one
 * tile layer, a placement on a bridge tile (the loaders would drop it a level), a loc id missing from the cache, a
 * walk-through ground decoration without forcedecor (the low preset would not draw it).
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.RoyalHallDecorBakeTool plan|apply`
 */
object RoyalHallDecorBakeTool {
    private const val GAME_CACHE = "C:/RSPS/game/game/data/cache"
    private const val FILE_SERVER_CACHE = "C:/RSPS/file-server/cache"
    private const val XTEAS_FILE = "C:/RSPS/game/game/data/xteas/xteas.json"
    private const val DATA = "C:/RSPS/game/game"
    private const val LOC_INDEX = 16

    /** The cache decals the carpet covers (3163-3166 x 3483-3484): dirt 46541 x4, 46976 x3 (research 2026-10-09). */
    private val COVERED_DECALS = setOf(46541, 46976)
    private const val FIRST_BAKE_DECALS = 7

    /** Map-file markers RoyalHallMapTool once laid itself; when home_decor.txt places one, its old copy leaves (casino star, owner 2026-10-09). */
    private val MOVABLE_MARKERS = setOf(RoyalHallLocTool.CASINO_MARKER)

    private fun layer(type: Int) =
        when (type) {
            in 0..3 -> 0
            in 4..8 -> 1
            22 -> 3
            else -> 2
        }

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val decorPath = Paths.get(DATA).resolve(HomeDecorFile.PATH)
        val manifestPath = Paths.get(DATA).resolve(HomeDecorBake.MANIFEST)
        val design = HomeDecorFile.load(decorPath)
        require(design.removals.isEmpty()) { "home_decor.txt has ${design.removals.size} del lines; a bake cannot hide cache locs" }
        val placements = design.placements.map(HomeDecorBake::baked)
        val outside = placements.filterNot { HomeDecorBake.inRegion(it.tile) }
        require(outside.isEmpty()) { "outside region ${HomeDecorBake.REGION_ID}: $outside" }
        val clash = placements.groupBy { Triple(it.tile, it.tile.height, layer(it.type)) }.filterValues { it.size > 1 }
        require(clash.isEmpty()) { "two placements on one tile layer: ${clash.values}" }
        val oldManifest = HomeDecorBake.loadManifest(manifestPath)

        val rx = HomeDecorBake.REGION_ID shr 8
        val rz = HomeDecorBake.REGION_ID and 0xFF
        val mutations = ArrayList<CacheMutation>()
        val library = CacheLibrary(GAME_CACHE)
        var summary = ""
        try {
            placements.map { it.id }.distinct().forEach { id ->
                require(library.data(LOC_INDEX, id ushr 8, id and 0xFF) != null) { "loc $id is not in the cache (run RoyalHallLocTool apply first for the carpet copies)" }
            }
            val walkThroughDecor =
                placements.filter { it.type == 22 }.map { it.id }.distinct()
                    .filterNot { id -> RoyalHallLocTool.hasOpcode(id, library.data(LOC_INDEX, id ushr 8, id and 0xFF)!!, 73) }
            require(walkThroughDecor.isEmpty()) { "ground decorations without a forcedecor copy (low preset would drop them): $walkThroughDecor" }

            val mapArchive = library.index(Rev667RegionProbeTool.MAP_INDEX).archive("m${rx}_$rz") ?: error("m${rx}_$rz missing")
            val map = Rev667TileCodec.decode(mapArchive.file(0)?.data ?: error("m${rx}_$rz file 0 missing"))
            val bridged = placements.filter { map.tiles[1][it.tile.x - rx * 64][it.tile.z - rz * 64].flags and 0x2 != 0 }
            require(bridged.isEmpty()) { "on a bridge tile: $bridged" }

            val key = Rev667RegionProbeTool.loadKeys(File(XTEAS_FILE))[HomeDecorBake.REGION_ID] ?: error("No XTEA for region ${HomeDecorBake.REGION_ID}")
            val locName = "l${rx}_$rz"
            val locArchive = library.index(Rev667RegionProbeTool.MAP_INDEX).archive(locName, key) ?: error("$locName missing")
            val locBytes = locArchive.file(0)?.data ?: error("$locName file 0 missing")
            val current = Rev667LocCodec.decode(locBytes)
            check(Rev667LocCodec.encode(current).contentEquals(locBytes)) { "$locName loc round-trip failed" }

            fun loc(p: HomeDecorFile.Placement) = Rev667Loc(p.id, p.tile.x - rx * 64, p.tile.z - rz * 64, p.tile.height, p.type, p.rot)
            fun same(a: Rev667Loc, b: Rev667Loc) =
                a.id == b.id && a.localX == b.localX && a.localZ == b.localZ && a.plane == b.plane && a.type == b.type && a.rotation == b.rotation

            // The previous bake comes out first, so a rebake starts from the same map.
            val base = current.toMutableList()
            oldManifest.forEach { p ->
                val wanted = loc(p)
                val at = base.indexOfFirst { same(it, wanted) }
                check(at >= 0) { "manifest loc $p is not in $locName: the manifest and the cache differ" }
                base.removeAt(at)
            }
            val newLocs = placements.map(::loc)
            val taken = newLocs.map { Triple(it.localX to it.localZ, it.plane, layer(it.type)) }.toSet()
            val dropped = base.filter { Triple(it.localX to it.localZ, it.plane, layer(it.type)) in taken }
            dropped.forEach { println("REPLACE cache loc ${it.id} type ${it.type} at ${rx * 64 + it.localX},${rz * 64 + it.localZ},${it.plane}") }
            check(dropped.all { it.type == 22 && it.id in COVERED_DECALS }) { "a placement would replace a cache loc other than the covered decals: ${dropped.map { it.id }}" }
            check(oldManifest.isNotEmpty() || dropped.size == FIRST_BAKE_DECALS) { "first bake: expected $FIRST_BAKE_DECALS covered decals, found ${dropped.size}" }

            val placedMarkers = placements.map { it.id }.filter { it in MOVABLE_MARKERS }.toSet()
            val movedMarkers = base.filter { it.id in placedMarkers }
            movedMarkers.forEach { println("MOVE marker ${it.id} from ${rx * 64 + it.localX},${rz * 64 + it.localZ},${it.plane} (home_decor.txt places it)") }
            val baked = (base - dropped.toSet() - movedMarkers.toSet()) + newLocs
            val bytes = Rev667LocCodec.encode(baked)
            check(Rev667LocCodec.decode(bytes).size == baked.size) { "$locName encode/decode lost locs" }
            placements.groupingBy { it.id }.eachCount().entries.sortedByDescending { it.value }.forEach { (id, n) -> println("BAKE loc $id x$n") }
            summary = "locs ${current.size} -> ${baked.size} (old bake ${oldManifest.size} out, ${dropped.size} decals replaced, ${newLocs.size} decor in), ${locBytes.size} -> ${bytes.size} bytes; m${rx}_$rz untouched"
            println("ROYAL_HALL_DECOR_BAKE $summary")
            if (!bytes.contentEquals(locBytes)) {
                mutations +=
                    CacheMutation(
                        Rev667RegionProbeTool.MAP_INDEX,
                        locArchive.id,
                        0,
                        bytes,
                        "Royal Hall dressing baked into $locName: ${newLocs.size} home_decor.txt locs (carpet as forcedecor copies), ${dropped.size} covered decals replaced",
                        CacheItemProbeTool.sha1(locBytes),
                        xtea = key,
                    )
            }
        } finally {
            library.close()
        }
        check(mutations.all { it.indexId == Rev667RegionProbeTool.MAP_INDEX && it.xtea != null }) { "only the loc group may change" }

        val manifest = HomeDecorBake.manifestText(placements)
        if (mutations.isEmpty()) {
            println("NOTHING_TO_DO (the map already holds this design)")
            if (mode == "apply" && (!Files.exists(manifestPath) || Files.readString(manifestPath) != manifest)) {
                Files.writeString(manifestPath, manifest)
                println("MANIFEST written: $manifestPath (${placements.size} locs)")
            }
            return
        }
        val tx = CacheTransaction(listOf(GAME_CACHE, FILE_SERVER_CACHE), mutations)
        val preflight = tx.preflight()
        preflight.forEach { println("PREFLIGHT $it") }
        val blocking = tx.blockingErrors(preflight)
        blocking.forEach { println("BLOCKING: $it") }
        if (mode == "plan") {
            println("PLAN_ONLY transaction=${tx.id} mutations=${mutations.size} (nothing written; manifest would list ${placements.size} locs)")
            return
        }
        check(blocking.isEmpty()) { "Preflight has blocking errors; refusing to apply." }
        val result = tx.apply(preflight)
        println("APPLIED transaction=${tx.id} applied=${result.applied} skipped=${result.skipped}")
        val verify = tx.verify()
        verify.forEach { println("VERIFY_ERROR: $it") }
        check(verify.isEmpty()) { "Post-apply verification failed; see journal ${tx.id} for rollback." }
        println("VERIFY_OK both targets hold intended bytes (journal ${tx.journalDir})")
        Files.writeString(manifestPath, manifest)
        println("MANIFEST written: $manifestPath (${placements.size} locs)")
    }
}
