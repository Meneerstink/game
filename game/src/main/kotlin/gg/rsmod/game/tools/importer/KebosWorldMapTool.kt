package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * TOA-1911 punt 1 (owner 19-11 17:3x 'wereldkaart bij fairy ring BLS: je staat in Lumbridge'; 19:4x V1 'ja het volledig gebied'): the
 * rev-667 'RuneScape Surface' map has no projection for Kebos (its chunks start at display x 2048), so WorldMap.getMap found nothing for
 * the BLS fairy ring / Chambers of Xeric entrance and fell back to the origin 3232,3232 (Lumbridge).
 *
 * This draws ALL Kebos squares our landscape has ([SQUARES] = 19_54, 19_55, 20_54, 20_55: Mount Quidamortem, the CoX entrance, BLS) in
 * the empty south-west corner of the main map: one source->display chunk ([chunk]; display 2048..2367 x 2496..2751 is covered by no
 * other chunk - checked in [main]) + records at the DISPLAY squares rebuilt from the landscape ([FeroxWorldMapTool.encodeSquare], with
 * the 0x8-flagged upper floors like [NecropolisWorldMapTool.lowerFlaggedFloors]) + the OSRS world-map label 'Mount Quidamortem' at its
 * source tile (static elements are projected source -> display by the client, like the player arrow).
 *
 * Usage: java -cp ... gg.rsmod.game.tools.importer.KebosWorldMapTool show|plan|apply
 */
object KebosWorldMapTool {
    val SQUARES = listOf(19 to 54, 19 to 55, 20 to 54, 20 to 55)
    const val SRC_X1 = 19 * 64
    const val SRC_Z1 = 54 * 64
    const val SRC_X2 = 21 * 64 - 1
    const val SRC_Z2 = 56 * 64 - 1
    /** Display corner inside the free block 2048..2367 x 2496..2751 (one square of sea west of it, like OSRS's coast). */
    const val DISP_X1 = 2112
    const val DISP_Z1 = 2560
    /** The rest of the free block (display squares) gets open sea - without a record the map drew black/dark red there. */
    val SEA_SQUARES = (32..36).flatMap { x -> (39..42).map { z -> x to z } }

    /** A full-square record whose 4096 tiles are one and the same tile = open sea; its body copied under a new x/z header. */
    fun uniformBody(rec: ByteArray): ByteArray? {
        val body = rec.copyOfRange(3, rec.size)
        for (k in 2..3) {
            if (body.size != 4096 * k) continue
            val first = body.copyOfRange(0, k)
            if ((0 until 4096).all { i -> (0 until k).all { j -> body[i * k + j] == first[j] } }) return body
        }
        return null
    }

    const val LABEL_TEXT = "Mount Quidamortem"
    /** OSRS Wiki Mount Quidamortem infobox {{Map|x=1255|y=3561}} (the mountain top above the Chambers of Xeric entrance). */
    const val LABEL_X = 1255
    const val LABEL_Z = 3561

    fun chunk() = WildernessDungeonsMapTool.Chunk(0, SRC_X1, SRC_Z1, SRC_X2, SRC_Z2, DISP_X1, DISP_Z1, DISP_X1 + SRC_X2 - SRC_X1, DISP_Z1 + SRC_Z2 - SRC_Z1)

    fun displaySquare(sq: Pair<Int, Int>) = (sq.first * 64 - SRC_X1 + DISP_X1) / 64 to (sq.second * 64 - SRC_Z1 + DISP_Z1) / 64

    /** [NecropolisWorldMapTool.labelElement] bytes with this label text. */
    fun labelElement(): ByteArray =
        ByteArrayOutputStream().use { out ->
            out.write(3)
            out.write(LABEL_TEXT.toByteArray(Charsets.ISO_8859_1))
            out.write(0)
            out.write(6); out.write(1)
            out.write(4); out.write(0xFF); out.write(0xFF); out.write(0xFF)
            out.write(8); out.write(1)
            out.write(19); out.write(950 ushr 8); out.write(950 and 0xFF)
            out.write(0)
            out.toByteArray()
        }

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "show"
        require(mode in setOf("show", "plan", "apply")) { "Usage: show|plan|apply" }
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        val mutations = ArrayList<CacheMutation>()
        try {
            val index = library.index(WildernessDungeonsMapTool.INDEX)
            // 1. projection
            val detailsGroup = index.archive("details")!!
            val (detailsFile, detailsBytes) = detailsGroup.fileIds().map { it to library.data(WildernessDungeonsMapTool.INDEX, detailsGroup.id, it)!! }
                .first { (_, b) -> WildernessDungeonsMapTool.decodeDetails(b).file == "main" }
            val details = WildernessDungeonsMapTool.decodeDetails(detailsBytes)
            check(details.encode().contentEquals(detailsBytes)) { "main details re-encode differs" }
            val want = chunk()
            println("CHUNK $want")
            val have = details.chunks.any { it.toString() == want.toString() }
            if (!have) {
                details.chunks.forEach { c ->
                    val dispOverlap = c.dx1 <= want.dx2 && want.dx1 <= c.dx2 && c.dz1 <= want.dz2 && want.dz1 <= c.dz2
                    val srcOverlap = c.sx1 <= want.sx2 && want.sx1 <= c.sx2 && c.sz1 <= want.sz2 && want.sz1 <= c.sz2
                    check(!dispOverlap && !srcOverlap) { "overlaps $c" }
                }
                val updated = WildernessDungeonsMapTool.Details(details.file, details.name, details.origin, details.colour, details.enabled, details.zoom, details.unknown, details.chunks + want)
                mutations += CacheMutation(WildernessDungeonsMapTool.INDEX, detailsGroup.id, detailsFile, updated.encode(), "TOA-1911 main map: Kebos/CoX projection", CacheItemProbeTool.sha1(detailsBytes))
            } else println("IN PLACE chunk")
            // 2. records at the display squares
            val keys = Rev667RegionProbeTool.loadKeys(File(FeroxImportTool.XTEAS_FILE))
            val def = FeroxWorldMapTool.objectDefs(library)
            val mainGroup = index.archive("main") ?: error("world map group main missing")
            val areaFile = mainGroup.file("area") ?: error("main/area missing")
            val current = library.data(FeroxWorldMapTool.WORLD_MAP_INDEX, mainGroup.id, areaFile.id) ?: error("main/area unreadable")
            val area = FeroxWorldMapTool.parse(current)
            val out = ByteArrayOutputStream(current.size + 40000)
            var copied = 0
            var changed = 0
            val targets = SQUARES.associateBy { displaySquare(it) }
            area.records.filter { r -> (r.x to r.z) in targets }.sortedBy { it.start }.forEach { r ->
                check(r.square) { "display ${r.x}_${r.z} is stored as chunks" }
                val src = targets.getValue(r.x to r.z)
                out.write(current, copied, r.start - copied)
                val (tiles, locs) = FeroxWorldMapTool.landscape(library, keys, src.first, src.second)
                NecropolisWorldMapTool.lowerFlaggedFloors(tiles)
                val rebuilt = FeroxWorldMapTool.encodeSquare(r.x, r.z, tiles, locs, area, def)
                if (!current.copyOfRange(r.start, r.end).contentEquals(rebuilt)) changed++
                println("display ${r.x}_${r.z} <- ${src.first}_${src.second}: rebuilt ${rebuilt.size} bytes")
                out.write(rebuilt)
                copied = r.end
            }
            out.write(current, copied, current.size - copied)
            // sea template: the uniform record nearest to the free block
            val sea = area.records.filter { it.square }.sortedBy { kotlin.math.abs(it.x - 34) + kotlin.math.abs(it.z - 41) }
                .firstNotNullOfOrNull { r -> uniformBody(current.copyOfRange(r.start, r.end))?.let { r to it } }
                ?: error("no uniform sea record found")
            println("SEA template display ${sea.first.x}_${sea.first.z} (${sea.second.size} bytes)")
            var appended = 0
            targets.filterKeys { (x, z) -> area.records.none { it.x == x && it.z == z } }.forEach { (disp, src) ->
                val (tiles, locs) = FeroxWorldMapTool.landscape(library, keys, src.first, src.second)
                val lowered = NecropolisWorldMapTool.lowerFlaggedFloors(tiles)
                val rec = FeroxWorldMapTool.encodeSquare(disp.first, disp.second, tiles, locs, area, def)
                out.write(rec)
                println("display ${disp.first}_${disp.second} <- ${src.first}_${src.second}: APPENDED ${rec.size} bytes ($lowered upper floors)")
                appended++; changed++
            }
            val kebos = targets.keys
            SEA_SQUARES.filter { it !in kebos && area.records.none { r -> r.x == it.first && r.z == it.second } }.forEach { (x, z) ->
                out.write(0); out.write(x); out.write(z); out.write(sea.second)
                appended++; changed++
            }
            println("SEA appended ${SEA_SQUARES.count { it !in kebos && area.records.none { r -> r.x == it.first && r.z == it.second } }} squares")
            val updated = out.toByteArray()
            check(FeroxWorldMapTool.parse(updated).records.size == area.records.size + appended) { "record count changed" }
            if (changed > 0) mutations += CacheMutation(FeroxWorldMapTool.WORLD_MAP_INDEX, mainGroup.id, areaFile.id, updated,
                "TOA-1911 world map main/area: Kebos/CoX squares", CacheItemProbeTool.sha1(current))
            // 3. label
            val elements = library.index(FeroxWorldMapTool.CONFIG_INDEX).archive(FeroxWorldMapTool.MAP_ELEMENT_GROUP)!!
            val label = labelElement()
            val element = elements.fileIds().firstOrNull { library.data(FeroxWorldMapTool.CONFIG_INDEX, FeroxWorldMapTool.MAP_ELEMENT_GROUP, it)?.contentEquals(label) == true }
                ?: ((elements.fileIds().maxOrNull() ?: -1) + 1).also { id ->
                    println("CENSUS mapelement $id (first free) = '$LABEL_TEXT'")
                    mutations += CacheMutation(FeroxWorldMapTool.CONFIG_INDEX, FeroxWorldMapTool.MAP_ELEMENT_GROUP, id, label, "TOA-1911 Mount Quidamortem label element", null)
                }
            val staticGroup = index.archive("main_staticelements") ?: error("main_staticelements missing")
            val entry = MansionMapIconTool.staticEntry(element, LABEL_X, LABEL_Z, 0)
            if (staticGroup.fileIds().none { f -> library.data(FeroxWorldMapTool.WORLD_MAP_INDEX, staticGroup.id, f)?.contentEquals(entry) == true })
                mutations += CacheMutation(FeroxWorldMapTool.WORLD_MAP_INDEX, staticGroup.id, (staticGroup.fileIds().maxOrNull() ?: -1) + 1, entry, "TOA-1911 Mount Quidamortem label", null)
            else println("IN PLACE label")
        } finally {
            library.close()
        }
        if (mode == "show") { mutations.forEach { println("WOULD ${it.describeLocation()}") }; return }
        if (mutations.isEmpty()) { println("NOTHING_TO_DO"); return }
        val transaction = CacheTransaction(targets = OsrsItemImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        errors.forEach { println("  BLOCKING: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (mode == "plan") { println("DRY_RUN transaction=${transaction.id} mutations=${mutations.size}"); return }
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
