package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary

/** Bounded, transactional floor design for the GE Home entrance, avenue, and casino. */
object GeHomeArrivalMapTool {
    private const val GAME_CACHE = "C:/RSPS/game/game/data/cache"
    private const val FILE_SERVER_CACHE = "C:/RSPS/file-server/cache"
    private const val REGION_ID = 12598
    // Exact overlay/underlay pair sampled from existing grey GE paving at 3164,3487.
    private const val GE_PAVING = 188
    private const val GE_UNDERLAY = 160
    private const val DARK_STONE = 217

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val library = CacheLibrary(GAME_CACHE)
        val mutations = ArrayList<CacheMutation>()
        try {
            val overlays = library.index(2).archive(4) ?: error("Floor overlays missing")
            listOf(GE_PAVING, DARK_STONE).forEach { id ->
                val bytes = overlays.file(id)?.data ?: error("Floor overlay $id missing")
                val floor = Rev667FloorCodec.decodeOverlay(bytes)
                println("FLOOR id=$id rgb=${"%06x".format(floor.rgb)} texture=${floor.texture}")
                if (id == GE_PAVING) check(floor.rgb == 0x60769A && floor.texture == 669) {
                    "GE paving material changed in this cache"
                }
            }
            val rx = REGION_ID shr 8
            val rz = REGION_ID and 0xFF
            val archive = library.index(Rev667RegionProbeTool.MAP_INDEX).archive("m${rx}_$rz")
                ?: error("Home map archive missing")
            val original = archive.file(0)?.data ?: error("Home map data missing")
            val map = Rev667TileCodec.decode(original)
            check(Rev667TileCodec.encode(map).contentEquals(original)) { "Home map round-trip failed" }
            var changed = 0
            for (x in 3157..3173) for (z in 3457..3515) {
                val overlay = desiredOverlay(x, z) ?: continue
                val tile = map.tiles[0][x - rx * 64][z - rz * 64]
                val underlay = if (overlay == GE_PAVING) GE_UNDERLAY else tile.underlayId
                if ((tile.overlayId and 0xFF) == overlay && tile.overlayShape == 0 &&
                    tile.overlayRotation == 0 && tile.underlayId == underlay) continue
                tile.overlayId = overlay
                tile.overlayShape = 0
                tile.overlayRotation = 0
                tile.underlayId = underlay
                changed++
            }
            val updated = Rev667TileCodec.encode(map)
            if (!updated.contentEquals(original)) {
                mutations += CacheMutation(
                    Rev667RegionProbeTool.MAP_INDEX,
                    archive.id,
                    0,
                    updated,
                    "GE Home ceremonial arrival and gaming floor: $changed tiles",
                    CacheItemProbeTool.sha1(original),
                )
            }
            println("GE_HOME_ARRIVAL floorChanges=$changed")
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
        blocking.forEach { println("BLOCKING $it") }
        if (mode == "plan") {
            println("PLAN_ONLY transaction=${tx.id} changes=${mutations.size}")
            return
        }
        check(blocking.isEmpty()) { "Cache preflight failed" }
        val result = tx.apply(preflight)
        println("APPLIED transaction=${tx.id} applied=${result.applied} skipped=${result.skipped}")
        val errors = tx.verify()
        errors.forEach { println("VERIFY_ERROR $it") }
        check(errors.isEmpty()) { "Cache verification failed; journal ${tx.id}" }
        println("VERIFY_OK both production caches")
    }

    /** The safe border and two open gate lanes stay unchanged in collision and function. */
    internal fun desiredOverlay(x: Int, z: Int): Int? =
        when {
            // No dark-stone apron outside the gate any more (owner 2026-09-23); GeHomeFloorRestoreTool put the ground back.
            z == 3467 && x in 3162..3167 -> GE_PAVING
            z in 3468..3469 && x in 3162..3167 -> GE_PAVING
            z in 3470..3483 && x in 3163..3168 -> GE_PAVING
            z in 3508..3514 && x in 3158..3172 -> GE_PAVING
            else -> null
        }
}
