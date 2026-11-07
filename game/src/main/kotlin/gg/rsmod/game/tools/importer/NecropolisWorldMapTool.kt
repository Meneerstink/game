package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * N462 (owner 2026-11-07 15:3x: 'dat hele gebied correct integreren net zoals osrs worldmap minimap'): the world map draws the prebuilt
 * `main/area` snapshot of index 23, not the landscape (see [FeroxWorldMapTool]); the Necropolis imports (OsrsRegionImportTool
 * 'necropolis' + 'necropolis-west') rewrote the map squares below but not their world-map records, so the map still showed the old
 * rev-667 desert there. This rebuilds exactly those records from the current landscape with [FeroxWorldMapTool.encodeSquare] (which
 * reproduces untouched squares byte for byte). Squares the snapshot has no record for (OSRS-only sea squares) are reported, not added.
 * Adds the 'Necropolis' town label like [FeroxWorldMapTool] did for Ferox.
 *
 * Usage: `./gradlew :game:runNecropolisWorldMapTool --args="plan|apply"`.
 */
object NecropolisWorldMapTool {
    /** 51_41 13097, 51_42 13098, 52_41 13353, 52_42 13354, 52_43 13355, 53_41 13609, 53_42 13610, 53_43 13611. */
    val SQUARES = listOf(51 to 41, 51 to 42, 52 to 41, 52 to 42, 52 to 43, 53 to 41, 53 to 42, 53 to 43)

    /** The town label (OSRS world map 'Necropolis'): first free MapElementType (HOOFDLIJST sectie 3: mapelement 1148). */
    const val LABEL_ELEMENT = 1148
    const val LABEL_TEXT = "Necropolis"
    /** OSRS Wiki Necropolis infobox {{Map|name=Necropolis|x=3340|y=2724}}. */
    const val LABEL_X = 3340
    const val LABEL_Z = 2724

    /** Edgeville's town-label element 92 with the text replaced (same bytes as [FeroxWorldMapTool.labelElement]). */
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
            SQUARES.filter { (x, z) -> area.records.none { it.x == x && it.z == z } }.forEach { (x, z) -> println("square ${x}_$z: NO RECORD (not drawn, left out)") }
            val out = ByteArrayOutputStream(current.size + 4096)
            var copied = 0
            var changed = 0
            area.records.filter { r -> SQUARES.any { (x, z) -> r.x == x && r.z == z } }.sortedBy { it.start }.forEach { r ->
                check(r.square) { "square ${r.x}_${r.z} is stored as chunks" }
                out.write(current, copied, r.start - copied)
                val (tiles, locs) = FeroxWorldMapTool.landscape(library, keys, r.x, r.z)
                val rebuilt = FeroxWorldMapTool.encodeSquare(r.x, r.z, tiles, locs, area, def)
                val old = current.copyOfRange(r.start, r.end)
                if (!old.contentEquals(rebuilt)) changed++
                println("square ${r.x}_${r.z}: ${old.size} -> ${rebuilt.size} bytes${if (old.contentEquals(rebuilt)) " (unchanged)" else ""}")
                out.write(rebuilt)
                copied = r.end
            }
            out.write(current, copied, current.size - copied)
            // 07-11: 13098 did not exist in the rev-667 cache (no landscape, no world-map record) and is created by 'necropolis-west' -
            // a full-square record for every created square with a landscape is appended (records are read sequentially).
            var appended = 0
            SQUARES.filter { (x, z) -> area.records.none { it.x == x && it.z == z } }.forEach { (x, z) ->
                val land = runCatching { FeroxWorldMapTool.landscape(library, keys, x, z) }.getOrNull()
                if (land == null) { println("square ${x}_$z: no landscape either - left out"); return@forEach }
                out.write(FeroxWorldMapTool.encodeSquare(x, z, land.first, land.second, area, def))
                println("square ${x}_$z: APPENDED new record"); appended++; changed++
            }
            val updated = out.toByteArray()
            check(FeroxWorldMapTool.parse(updated).records.size == area.records.size + appended) { "record count changed" }
            if (changed > 0) {
                mutations += CacheMutation(FeroxWorldMapTool.WORLD_MAP_INDEX, mainGroup.id, areaFile.id, updated,
                    "N462 world map main/area: Necropolis squares rebuilt", CacheItemProbeTool.sha1(current))
            }
            val label = library.data(FeroxWorldMapTool.CONFIG_INDEX, FeroxWorldMapTool.MAP_ELEMENT_GROUP, LABEL_ELEMENT)
            check(label == null || label.contentEquals(labelElement())) { "element $LABEL_ELEMENT is taken" }
            if (label == null) mutations += CacheMutation(FeroxWorldMapTool.CONFIG_INDEX, FeroxWorldMapTool.MAP_ELEMENT_GROUP, LABEL_ELEMENT, labelElement(), "N462 Necropolis label element", null)
            val staticGroup = worldMap.archive("main_staticelements") ?: error("main_staticelements missing")
            val entry = MansionMapIconTool.staticEntry(LABEL_ELEMENT, LABEL_X, LABEL_Z, 0)
            val existing = staticGroup.fileIds().filter { f -> library.data(FeroxWorldMapTool.WORLD_MAP_INDEX, staticGroup.id, f)?.contentEquals(entry) == true }
            if (existing.isEmpty()) mutations += CacheMutation(FeroxWorldMapTool.WORLD_MAP_INDEX, staticGroup.id, (staticGroup.fileIds().maxOrNull() ?: -1) + 1, entry, "N462 Necropolis label", null)
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
