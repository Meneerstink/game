package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary

/**
 * N633 (Claude 18-11): free a rev-667 overlay id for a new definition. All 255 one-byte overlay ids are in use (census
 * work/claude-scripts/grafisch/overlay_census.py: 0 unused over 2290 map squares), so a textured OSRS floor (Watson's house wood, OSRS
 * texture 51) has no slot. The least-used ids are used by a handful of tiles: 212 (rgb 41474a, texture 739) by ONE tile in square 13882.
 * This tool moves every tile that uses [from] onto [to] (an existing definition that looks the same: 211 = rgb 41474a, texture 737) in
 * every map square, so [from] is used by no tile and OsrsRegionImportTool's newOverlaySlots proof accepts it.
 *
 * Each changed tile file must round-trip byte for byte before the edit (otherwise the codec would also rewrite unrelated bytes), the
 * mutation is pinned on the current bytes, and nothing changes when no tile uses [from].
 *
 * Usage: `<from> <to> [--apply]`.
 */
object OverlaySlotFreeTool {
    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val (from, to) = args.filterNot { it.startsWith("--") }.map { it.toInt() }.also { require(it.size == 2) { "Usage: <from> <to> [--apply]" } }
        require(from in 0..254 && to in 0..254 && from != to) { "overlay ids must be 0..254 and differ" }
        val mutations = ArrayList<CacheMutation>()
        val library = CacheLibrary(FeroxImportTool.GAME_CACHE)
        try {
            val overlays = library.index(Rev667RegionProbeTool.CONFIG_INDEX).archive(Rev667RegionProbeTool.OVERLAY_GROUP)!!
            val a = Rev667FloorCodec.decodeOverlay(overlays.file(from)!!.data!!)
            val b = Rev667FloorCodec.decodeOverlay(overlays.file(to)!!.data!!)
            println("FROM overlay $from rgb=${Integer.toHexString(a.rgb)} tex=${a.texture} occludes=${a.occludes} -> TO $to rgb=${Integer.toHexString(b.rgb)} tex=${b.texture} occludes=${b.occludes}")
            check(a.rgb == b.rgb && a.occludes == b.occludes) { "target overlay $to does not share colour/occlusion with $from - pick a look-alike" }
            val names = HashMap<Int, String>()
            for (x in 0 until 256) for (z in 0 until 256) "m${x}_$z".let { names[it.hashCode()] = it }
            val maps = library.index(FeroxImportTool.MAP_INDEX)
            var scanned = 0
            maps.archives().forEach { archive ->
                val name = names[archive.hashName] ?: return@forEach
                val data = maps.archive(archive.id)?.file(0)?.data ?: return@forEach
                val tiles = Rev667TileCodec.decode(data)
                scanned++
                var changed = 0
                for (pl in 0 until 4) for (x in 0 until 64) for (z in 0 until 64) {
                    val t = tiles.tiles[pl][x][z]
                    // tile value = definition + 1; the codec reads it as a signed byte
                    if ((t.overlayId and 0xFF) == from + 1) {
                        t.overlayId = to + 1
                        changed++
                        println("TILE $name plane=$pl local=$x,$z overlay $from -> $to")
                    }
                }
                if (changed == 0) return@forEach
                check(Rev667TileCodec.encode(Rev667TileCodec.decode(data)).contentEquals(data)) { "$name does not round-trip - refusing to rewrite it" }
                mutations += CacheMutation(FeroxImportTool.MAP_INDEX, archive.id, 0, Rev667TileCodec.encode(tiles),
                    "N633 $name: $changed tile(s) overlay $from -> $to (free $from)", CacheItemProbeTool.sha1(data))
            }
            println("SCANNED $scanned tile files, ${mutations.size} to change")
        } finally {
            library.close()
        }
        if (mutations.isEmpty()) {
            println("NOTHING_TO_DO no tile uses overlay $from")
            return
        }
        val tx = CacheTransaction(listOf(FeroxImportTool.GAME_CACHE, FeroxImportTool.FILE_SERVER_CACHE), mutations)
        val preflight = tx.preflight()
        preflight.forEach { println("PREFLIGHT $it") }
        val blocking = tx.blockingErrors(preflight)
        blocking.forEach { println("BLOCKING: $it") }
        if (!apply) {
            println("PLAN_ONLY transaction=${tx.id} (nothing written)")
            return
        }
        check(blocking.isEmpty()) { "Preflight has blocking errors; refusing to apply." }
        val result = tx.apply(preflight)
        println("APPLIED transaction=${tx.id} applied=${result.applied} skipped=${result.skipped}")
        val verify = tx.verify()
        verify.forEach { println("VERIFY_ERROR: $it") }
        check(verify.isEmpty()) { "Post-apply verification failed; see journal ${tx.id} for rollback." }
        println("VERIFY_OK")
    }
}
