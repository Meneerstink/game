package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary

/**
 * N563 (owner 2026-11-04 14:5x: 'wereldkaart openen in Death's mansion / training area springt naar Lumbridge'; 20:4x answer:
 * 'GE met pijl'). Death's Office (square 49_95, DeathsOfficeMapImportTool) is on no world map (OSRS has no map for them either), so the client found no projection and fell back to the main map's origin
 * (Lumbridge, client WorldMap.getMap). This adds one source->display chunk per room to the RuneScape Surface map (`details` "main"):
 * the whole room square is drawn onto the Grand Exchange so the room's arrival tile lands on the entrance it is reached from - the
 * player arrow then shows "under the GE". No records or labels are added (the GE's own drawing stays); the chunks go at the END of
 * the list, so display->source lookups on the GE keep hitting the surface chunk first.
 *
 * Usage: `./gradlew :game:runHomeRoomsMapProjectionTool --args="show|plan|apply"`
 */
object HomeRoomsMapProjectionTool {
    class Room(val name: String, val squareX: Int, val squareZ: Int, val arrivalX: Int, val arrivalZ: Int, val entranceX: Int, val entranceZ: Int) {
        fun chunk(): WildernessDungeonsMapTool.Chunk {
            val dx = entranceX - arrivalX
            val dz = entranceZ - arrivalZ
            val sx = squareX * 64
            val sz = squareZ * 64
            return WildernessDungeonsMapTool.Chunk(0, sx, sz, sx + 63, sz + 63, sx + dx, sz + dz, sx + 63 + dx, sz + 63 + dz)
        }
    }

    /**
     * Death's Office: arrival DeathsOfficeIds.ARRIVAL 3171,6110 -> GE Death's Domain 62430 at 3169,3486 (home_decor.txt).
     * (The Skilling Basement needs nothing: it has its own world map, see [RETIRED].)
     */
    val ROOMS =
        listOf(
            Room("Death's Office", 49, 95, 3171, 6110, 3169, 3486),
        )

    /** Added by the first run and taken out again: the Skilling Basement has its own map ("Skilling Basement", details) already. */
    val RETIRED = listOf(Room("Skilling Basement", 46, 95, 2975, 6105, 3159, 3486))

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "show"
        require(mode in setOf("show", "plan", "apply")) { "Usage: show|plan|apply" }
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        val mutations = ArrayList<CacheMutation>()
        try {
            val group = library.index(WildernessDungeonsMapTool.INDEX).archive("details")!!
            val (file, bytes) =
                group.fileIds().map { it to library.data(WildernessDungeonsMapTool.INDEX, group.id, it)!! }
                    .first { (_, b) -> WildernessDungeonsMapTool.decodeDetails(b).file == "main" }
            val details = WildernessDungeonsMapTool.decodeDetails(bytes)
            check(details.encode().contentEquals(bytes)) { "main details re-encode differs" }
            println("MAIN details file=$file name='${details.name}' origin=${details.origin shr 14 and 0x3FFF},${details.origin and 0x3FFF} chunks=${details.chunks.size}")
            details.chunks.forEach { println("  CHUNK $it") }
            if (mode == "show") return
            val wanted = ROOMS.map { it.chunk() }
            val retired = RETIRED.map { it.chunk().toString() }
            val base = details.chunks.filterNot { it.toString() in retired }
            val missing = wanted.filter { w -> base.none { it.toString() == w.toString() } }
            base.forEach { c ->
                ROOMS.forEach { r -> check(!(c.sx1 <= r.squareX * 64 + 63 && r.squareX * 64 <= c.sx2 && c.sz1 <= r.squareZ * 64 + 63 && r.squareZ * 64 <= c.sz2) || wanted.any { it.toString() == c.toString() }) { "${r.name} source already projected by $c" } }
            }
            missing.forEach { println("PLAN add $it") }
            (details.chunks - base.toSet()).forEach { println("PLAN remove $it") }
            if (missing.isNotEmpty() || base.size != details.chunks.size) {
                val updated = WildernessDungeonsMapTool.Details(details.file, details.name, details.origin, details.colour, details.enabled, details.zoom, details.unknown, base + missing)
                mutations += CacheMutation(WildernessDungeonsMapTool.INDEX, group.id, file, updated.encode(), "N563 main map: Death's Office drawn at the GE", CacheItemProbeTool.sha1(bytes))
            }
        } finally {
            library.close()
        }
        if (mutations.isEmpty()) {
            println("NOTHING_TO_DO")
            return
        }
        val transaction = CacheTransaction(targets = OsrsItemImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        errors.forEach { println("  BLOCKING: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (mode == "plan") {
            println("DRY_RUN transaction=${transaction.id} mutations=${mutations.size}")
            return
        }
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        if (problems.isNotEmpty()) {
            problems.forEach { println("  VERIFY_FAILURE: $it") }
            println("ROLLED_BACK ${transaction.rollback()}")
            error("transaction ${transaction.id} failed verification and was rolled back")
        }
        println("APPLIED transaction=${applied.transactionId} writes=${applied.applied} skipped=${applied.skipped} VERIFY_OK")
    }
}
