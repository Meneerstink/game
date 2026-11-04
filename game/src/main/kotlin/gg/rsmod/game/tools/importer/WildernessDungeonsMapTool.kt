package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * N562 (owner 2026-11-04: 'klik op Revenant Caves (dungeon) toont nog de OUDE 667-revcave, niet de OSRS-versie').
 *
 * The world map's "Wilderness Dungeons" map is three things in index 23 (client WorldMapArea.decode / WorldMap.method5080 /
 * MapElementList.load): `details` file [DETAILS_FILE] (name, origin and the source->display chunk projections), the prebuilt
 * `wilderness_dungeons/area` snapshot (records at DISPLAY squares) and `wilderness_dungeons_staticelements` (labels at SOURCE tiles).
 * The old 667 Forinthry revenant dungeon ([OLD_SQUARES] = RetiredRevenantDungeon.REGIONS 12190/12445/12446, gone since the OSRS
 * import) still has its projections, snapshot records and labels; the OSRS Revenant Caves ([NEW_SQUARES], OsrsRegionImportTool
 * 'rev-caves') have none, so the map shows the old cave and opening the map inside the new one falls back to Lumbridge (N563).
 *
 * This removes exactly the old dungeon's projections, records and labels (KBD, Lava Maze, deep Wilderness, Corp etc. stay) and adds
 * the new caves ([DISPLAY_SHIFT_X]), records rebuilt from the landscape with [FeroxWorldMapTool.encodeSquare].
 *
 * Usage: `./gradlew :game:runWildernessDungeonsMapTool --args="show|plan|apply"`
 */
object WildernessDungeonsMapTool {
    const val INDEX = 23
    const val DETAILS_FILE = 6
    const val AREA_GROUP_NAME = "wilderness_dungeons"

    /** The retired 667 Forinthry revenant dungeon (RetiredRevenantDungeon.REGIONS). */
    val OLD_SQUARES = setOf(47 to 158, 48 to 157, 48 to 158)

    /** The OSRS Revenant Caves (OsrsRegionImportTool 'rev-caves'; 12704/12960 have no OSRS map). */
    val NEW_SQUARES = listOf(49 to 157, 49 to 158, 49 to 159, 50 to 157, 50 to 158, 50 to 159)

    /**
     * The new caves are drawn one map square east of their real place: their own spot 3136-3151 x 10208-10239 is where this map
     * already draws the King Black Dragon lair (source 2248-2295 x 4672-4719). 3200-3327 x 10048-10239 overlaps no other projection
     * (checked in [main]); the player arrow and every icon are projected source -> display by the client, so nothing else changes.
     */
    const val DISPLAY_SHIFT_X = 64

    class Chunk(val level: Int, val sx1: Int, val sz1: Int, val sx2: Int, val sz2: Int, val dx1: Int, val dz1: Int, val dx2: Int, val dz2: Int) {
        fun encode(out: ByteArrayOutputStream) {
            out.write(level)
            listOf(sx1, sz1, sx2, sz2, dx1, dz1, dx2, dz2).forEach {
                out.write(it ushr 8)
                out.write(it and 0xFF)
            }
        }

        override fun toString() = "L$level src $sx1..$sx2 x $sz1..$sz2 -> disp $dx1..$dx2 x $dz1..$dz2"

        fun sourceSquares(): Set<Pair<Int, Int>> = (sx1 / 64..sx2 / 64).flatMap { x -> (sz1 / 64..sz2 / 64).map { z -> x to z } }.toSet()
    }

    class Details(val file: String, val name: String, val origin: Int, val colour: Int, val enabled: Int, val zoom: Int, val unknown: Int, val chunks: List<Chunk>) {
        fun encode(): ByteArray =
            ByteArrayOutputStream().use { out ->
                fun str(s: String) {
                    out.write(s.toByteArray(Charsets.ISO_8859_1))
                    out.write(0)
                }
                fun g4(v: Int) = listOf(24, 16, 8, 0).forEach { out.write((v ushr it) and 0xFF) }
                str(file)
                str(name)
                g4(origin)
                g4(colour)
                out.write(enabled)
                out.write(zoom)
                out.write(unknown)
                out.write(chunks.size)
                chunks.forEach { it.encode(out) }
                out.toByteArray()
            }
    }

    /** Client WorldMapArea.decode (gjstr = 0-terminated string). */
    fun decodeDetails(b: ByteArray): Details {
        var p = 0
        fun g1() = b[p++].toInt() and 0xFF
        fun g2() = (g1() shl 8) or g1()
        fun g4() = (g2() shl 16) or g2()
        fun gjstr(): String {
            val start = p
            while (b[p] != 0.toByte()) p++
            return String(b, start, p++ - start, Charsets.ISO_8859_1)
        }
        val file = gjstr()
        val name = gjstr()
        val origin = g4()
        val colour = g4()
        val enabled = g1()
        val zoom = g1()
        val unknown = g1()
        val chunks = List(g1()) { Chunk(g1(), g2(), g2(), g2(), g2(), g2(), g2(), g2(), g2()) }
        check(p == b.size) { "details trailing bytes ${b.size - p}" }
        return Details(file, name, origin, colour, enabled, zoom, unknown, chunks)
    }

    class StaticEntry(val file: Int, val level: Int, val x: Int, val z: Int, val element: Int)

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "show"
        require(mode in setOf("show", "plan", "apply")) { "Usage: show|plan|apply" }
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        val mutations = ArrayList<CacheMutation>()
        try {
            val index = library.index(INDEX)
            val detailsGroup = index.archive("details")!!
            val detailsBytes = library.data(INDEX, detailsGroup.id, DETAILS_FILE)!!
            val details = decodeDetails(detailsBytes)
            check(details.encode().contentEquals(detailsBytes)) { "details re-encode differs" }
            println("DETAILS file='${details.file}' name='${details.name}' origin=${details.origin shr 14 and 0x3FFF},${details.origin and 0x3FFF} chunks=${details.chunks.size}")
            details.chunks.forEach { println("  CHUNK $it old=${it.sourceSquares().any { s -> s in OLD_SQUARES }}") }

            val areaGroup = index.archive(AREA_GROUP_NAME)!!
            val areaFile = areaGroup.file("area")!!
            val areaBytes = library.data(INDEX, areaGroup.id, areaFile.id)!!
            val area = FeroxWorldMapTool.parse(areaBytes)
            println("AREA group=${areaGroup.id} records=${area.records.size}: ${area.records.map { "${if (it.square) "S" else "c"}${it.x}_${it.z}" }}")

            val staticGroup = index.archive("${AREA_GROUP_NAME}_staticelements")!!
            val statics =
                staticGroup.fileIds().map { f ->
                    val d = library.data(INDEX, staticGroup.id, f)!!
                    val coord = ((d[0].toInt() and 0xFF) shl 24) or ((d[1].toInt() and 0xFF) shl 16) or ((d[2].toInt() and 0xFF) shl 8) or (d[3].toInt() and 0xFF)
                    StaticEntry(f, coord ushr 28 and 3, coord shr 14 and 0x3FFF, coord and 0x3FFF, ((d[4].toInt() and 0xFF) shl 8) or (d[5].toInt() and 0xFF))
                }
            statics.forEach { s -> println("  STATIC file=${s.file} element=${s.element} at ${s.x},${s.z},${s.level} old=${(s.x / 64 to s.z / 64) in OLD_SQUARES}") }
            if (mode == "show") return

            // 1. details: drop the old projections, add the new caves (display = source; refuse an overlap with another display rect).
            val kept = details.chunks.filterNot { it.sourceSquares().all { s -> s in OLD_SQUARES } }
            check(details.chunks.filter { c -> c.sourceSquares().any { it in OLD_SQUARES } }.all { c -> c.sourceSquares().all { it in OLD_SQUARES } }) { "a projection mixes old and other squares" }
            val added = NEW_SQUARES.map { (x, z) -> Chunk(0, x * 64, z * 64, x * 64 + 63, z * 64 + 63, x * 64 + DISPLAY_SHIFT_X, z * 64, x * 64 + 63 + DISPLAY_SHIFT_X, z * 64 + 63) }
            added.forEach { n ->
                kept.firstOrNull { k -> n.dx1 <= k.dx2 && k.dx1 <= n.dx2 && n.dz1 <= k.dz2 && k.dz1 <= n.dz2 }?.let { error("new $n overlaps display of $it") }
            }
            val newDetails = Details(details.file, details.name, details.origin, details.colour, details.enabled, details.zoom, details.unknown, kept + added)
            println("PLAN details chunks ${details.chunks.size} -> ${newDetails.chunks.size} (removed ${details.chunks.size - kept.size}, added ${added.size})")
            mutations += CacheMutation(INDEX, detailsGroup.id, DETAILS_FILE, newDetails.encode(), "N562 wilderness_dungeons details: Forinthry out, OSRS Revenant Caves in", CacheItemProbeTool.sha1(detailsBytes))

            // 2. area snapshot: old records out (display squares of the removed projections), new caves rebuilt from the landscape.
            val removedDisplay = details.chunks.filter { it !in kept }.flatMap { c -> (c.dx1 / 64..c.dx2 / 64).flatMap { x -> (c.dz1 / 64..c.dz2 / 64).map { z -> x to z } } }.toSet()
            val newDisplay = NEW_SQUARES.map { (x, z) -> x + DISPLAY_SHIFT_X / 64 to z }.toSet()
            val keys = Rev667RegionProbeTool.loadKeys(File(FeroxImportTool.XTEAS_FILE))
            val def = FeroxWorldMapTool.objectDefs(library)
            val out = ByteArrayOutputStream(areaBytes.size + 65536)
            // header: the two floor lists, unchanged
            out.write(areaBytes, 0, area.records.firstOrNull()?.start ?: areaBytes.size)
            var removedRecords = 0
            area.records.forEach { r ->
                check((r.x to r.z) !in newDisplay) { "display square ${r.x}_${r.z} of the new caves already has a record" }
                if ((r.x to r.z) in removedDisplay) {
                    removedRecords++
                } else {
                    out.write(areaBytes, r.start, r.end - r.start)
                }
            }
            NEW_SQUARES.forEach { (x, z) ->
                val (tiles, locs) = FeroxWorldMapTool.landscape(library, keys, x, z)
                // the record sits at the DISPLAY square (client draws record square * 64 - area origin), the tiles come from the source
                out.write(FeroxWorldMapTool.encodeSquare(x + DISPLAY_SHIFT_X / 64, z, tiles, locs, area, def))
            }
            val newArea = out.toByteArray()
            val reparsed = FeroxWorldMapTool.parse(newArea)
            check(reparsed.records.size == area.records.size - removedRecords + NEW_SQUARES.size) { "record count" }
            println("PLAN area records -$removedRecords +${NEW_SQUARES.size} (removed display squares $removedDisplay) bytes ${areaBytes.size} -> ${newArea.size}")
            mutations += CacheMutation(INDEX, areaGroup.id, areaFile.id, newArea, "N562 wilderness_dungeons area: Forinthry records out, OSRS Revenant Caves in", CacheItemProbeTool.sha1(areaBytes))

            // 3. labels of the old dungeon (source tiles inside the old squares) removed.
            statics.filter { (it.x / 64 to it.z / 64) in OLD_SQUARES }.forEach { s ->
                println("PLAN remove label file=${s.file} element=${s.element} at ${s.x},${s.z}")
                mutations += CacheMutation(INDEX, staticGroup.id, s.file, null, "N562 remove old Forinthry label ${s.element}", CacheItemProbeTool.sha1(library.data(INDEX, staticGroup.id, s.file)!!))
            }
        } finally {
            library.close()
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
