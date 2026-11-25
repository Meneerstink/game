package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * MAP-OSRS-IMPORTS deel 3 (Claude, nachtrun 25-11): the ToB import wrote Ver Sinhaza's landscape (region 14642 = square 57_50) but no
 * world-map record - rev 667 had no land there, so the world map showed a dark empty block east of Meiyerditch with 'You are here'
 * inside it (testclient 25-11 05:5x, screens\MAP-OSRS-IMPORTS\versinhaza-wm-s.png). Same method as [NecropolisWorldMapTool]: rebuild
 * the square from the current landscape with [FeroxWorldMapTool.encodeSquare] (append a record when the snapshot has none) and add the
 * town label. The ToB rooms (12611-12613, 13122-13125) are instance templates and need no world-map record.
 *
 * Usage: `bash work/claude-scripts/kc_tool.sh VerSinhazaWorldMapTool+NecropolisWorldMapTool plan|apply`.
 */
object VerSinhazaWorldMapTool {
    /** 57_50 = region 14642 (Ver Sinhaza). */
    val SQUARES = listOf(57 to 50)

    /** First free MapElementType after 1148 (Necropolis); 1149/1150 are taken (cache read 25-11). */
    const val LABEL_ELEMENT = 1151
    const val LABEL_TEXT = "Ver Sinhaza"
    /** OSRS Wiki Ver Sinhaza: {{Map|name=Ver Sinhaza|x=3663|y=3220}}. */
    const val LABEL_X = 3663
    const val LABEL_Z = 3220

    /** Same bytes as [NecropolisWorldMapTool.labelElement] with this text. */
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
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        val mutations = ArrayList<CacheMutation>()
        try {
            val keys = Rev667RegionProbeTool.loadKeys(File(FeroxImportTool.XTEAS_FILE))
            val def = FeroxWorldMapTool.objectDefs(library)
            val worldMap = library.index(FeroxWorldMapTool.WORLD_MAP_INDEX)
            val mainGroup = worldMap.archive("main") ?: error("world map group main missing")
            val areaFile = mainGroup.file("area") ?: error("main/area missing")
            val current = library.data(FeroxWorldMapTool.WORLD_MAP_INDEX, mainGroup.id, areaFile.id) ?: error("main/area unreadable")
            val area = FeroxWorldMapTool.parse(current)
            val out = ByteArrayOutputStream(current.size + 4096)
            var copied = 0
            var changed = 0
            area.records.filter { r -> SQUARES.any { (x, z) -> r.x == x && r.z == z } }.sortedBy { it.start }.forEach { r ->
                check(r.square) { "square ${r.x}_${r.z} is stored as chunks" }
                out.write(current, copied, r.start - copied)
                val (tiles, locs) = FeroxWorldMapTool.landscape(library, keys, r.x, r.z)
                println("square ${r.x}_${r.z}: ${NecropolisWorldMapTool.lowerFlaggedFloors(tiles)} tiles from flagged upper planes")
                val rebuilt = FeroxWorldMapTool.encodeSquare(r.x, r.z, tiles, locs, area, def)
                val old = current.copyOfRange(r.start, r.end)
                if (!old.contentEquals(rebuilt)) changed++
                println("square ${r.x}_${r.z}: ${old.size} -> ${rebuilt.size} bytes${if (old.contentEquals(rebuilt)) " (unchanged)" else ""}")
                out.write(rebuilt)
                copied = r.end
            }
            out.write(current, copied, current.size - copied)
            var appended = 0
            SQUARES.filter { (x, z) -> area.records.none { it.x == x && it.z == z } }.forEach { (x, z) ->
                val land = runCatching { FeroxWorldMapTool.landscape(library, keys, x, z) }.getOrNull()
                if (land == null) { println("square ${x}_$z: no landscape either - left out"); return@forEach }
                println("square ${x}_$z: ${NecropolisWorldMapTool.lowerFlaggedFloors(land.first)} tiles from flagged upper planes")
                out.write(FeroxWorldMapTool.encodeSquare(x, z, land.first, land.second, area, def))
                println("square ${x}_$z: APPENDED new record"); appended++; changed++
            }
            val updated = out.toByteArray()
            check(FeroxWorldMapTool.parse(updated).records.size == area.records.size + appended) { "record count changed" }
            if (changed > 0) {
                mutations += CacheMutation(FeroxWorldMapTool.WORLD_MAP_INDEX, mainGroup.id, areaFile.id, updated,
                    "MAP-OSRS-IMPORTS world map main/area: Ver Sinhaza square", CacheItemProbeTool.sha1(current))
            }
            val label = library.data(FeroxWorldMapTool.CONFIG_INDEX, FeroxWorldMapTool.MAP_ELEMENT_GROUP, LABEL_ELEMENT)
            check(label == null || label.contentEquals(labelElement())) { "element $LABEL_ELEMENT is taken" }
            if (label == null) mutations += CacheMutation(FeroxWorldMapTool.CONFIG_INDEX, FeroxWorldMapTool.MAP_ELEMENT_GROUP, LABEL_ELEMENT, labelElement(), "MAP-OSRS-IMPORTS Ver Sinhaza label element", null)
            val staticGroup = worldMap.archive("main_staticelements") ?: error("main_staticelements missing")
            val entry = MansionMapIconTool.staticEntry(LABEL_ELEMENT, LABEL_X, LABEL_Z, 0)
            val existing = staticGroup.fileIds().filter { f -> library.data(FeroxWorldMapTool.WORLD_MAP_INDEX, staticGroup.id, f)?.contentEquals(entry) == true }
            if (existing.isEmpty()) mutations += CacheMutation(FeroxWorldMapTool.WORLD_MAP_INDEX, staticGroup.id, (staticGroup.fileIds().maxOrNull() ?: -1) + 1, entry, "MAP-OSRS-IMPORTS Ver Sinhaza label", null)
            else println("IN PLACE label")
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
