package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/**
 * RCV-011 home minimap floors. The Ferox import (tx-20260905-052852) matched overlays by main colour only, so modern
 * overlays 54/60/99/160 (ff00ff with a secondary minimap colour) all became local 41, which has no minimap colour and
 * leaves the paths blank. This re-derives every Ferox tile overlay from the modern tiles through the corrected
 * [FeroxImportTool.bestOverlay] and rewrites only the overlay ids that differ (height, shape, rotation, underlay,
 * flags untouched). Map (m) archives are not XTEA-encrypted.
 *
 * Usage: `./gradlew :game:runFeroxFloorRepairTool --args="plan|apply"` (CacheTransaction on both production caches).
 */
object FeroxFloorRepairTool {
    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val keys = ModernRegionProbeTool.loadKeys(File(FeroxImportTool.MODERN_KEYS))
        val mutations = ArrayList<CacheMutation>()
        ModernCacheReader(File(FeroxImportTool.MODERN_CACHE)).use { modern ->
            val library = CacheLibrary(FeroxImportTool.GAME_CACHE)
            try {
                val modernOverlays = modern.files(2, 4).mapValues { ModernFloorDefs.decodeOverlay(it.value) }
                val localOverlays = FeroxImportTool.decodeLocalFloors(library, Rev667RegionProbeTool.OVERLAY_GROUP) { Rev667FloorCodec.decodeOverlay(it) }
                FeroxImportTool.REGIONS.forEach { regionId ->
                    val modernRegion = ModernRegion.load(modern, regionId, keys[regionId] ?: error("No modern key for $regionId"))
                    val rx = regionId shr 8
                    val rz = regionId and 0xFF
                    val mapName = "m${rx}_$rz"
                    val archive = library.index(5).archive(mapName) ?: error("local $mapName missing")
                    val bytes = archive.file(0)!!.data!!
                    val tiles = Rev667TileCodec.decode(bytes)
                    check(Rev667TileCodec.encode(tiles).contentEquals(bytes)) { "$mapName tile round-trip failed" }
                    val changes = sortedMapOf<String, Int>()
                    for (plane in 0 until 4) for (lx in 0 until 64) for (lz in 0 until 64) {
                        val x = rx * 64 + lx
                        val z = rz * 64 + lz
                        if (x !in FeroxImportTool.MIN_X..FeroxImportTool.MAX_X || z !in FeroxImportTool.MIN_Z..FeroxImportTool.MAX_Z) continue
                        val src = modernRegion.tiles[plane][lx][lz]
                        if (src.overlayId == 0) continue
                        val dst = tiles.tiles[plane][lx][lz]
                        val wanted = FeroxImportTool.bestOverlay(modernOverlays.getValue(src.overlayId - 1), localOverlays) + 1
                        // The codec (like client Class306 `g1b`) reads the id byte signed; compare the stored byte.
                        if (dst.overlayId and 0xFF != wanted and 0xFF) {
                            changes.merge("modern=${src.overlayId - 1} local ${(dst.overlayId and 0xFF) - 1}->${wanted - 1}", 1, Int::plus)
                            dst.overlayId = wanted
                        }
                    }
                    changes.forEach { (change, count) -> println("REGION $regionId $change tiles=$count") }
                    if (changes.isNotEmpty()) {
                        val updated = Rev667TileCodec.encode(tiles)
                        mutations += CacheMutation(5, archive.id, 0, updated, "ferox floor overlay repair $mapName $changes", CacheItemProbeTool.sha1(bytes))
                    }
                }
            } finally {
                library.close()
            }
        }
        if (mutations.isEmpty()) {
            println("NOTHING_TO_DO every Ferox tile overlay already matches")
            return
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
