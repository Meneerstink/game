package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.def.ObjectDef
import io.netty.buffer.Unpooled
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * N228 (owner 2026-10-23: "ferox enclave wordt nog niet getoond op de worldmap die hebben we wel").
 *
 * The world map does not read the landscape: it draws the prebuilt `main/area` file of world-map index 23 (client
 * `WorldMap.method5080` / `decodeTile`), Jagex's snapshot of every map square. The Ferox import (tx-20260905-052852) rewrote
 * map squares 48_56 and 49_56 (FeroxImportTool.REGIONS 12344 / 12600) but not their world-map records, so the map still showed
 * the old Wilderness there. This tool rebuilds exactly those two records from the current landscape and adds the town label.
 *
 * **Record format** (one per map square, all level-0-only here): `0, squareX, squareZ`, then 64 x 64 tiles, x outer:
 *  - a tile without locs and without a shaped overlay is one byte - underlay `index << 2`, overlay `2 | index << 2` followed by
 *    its raw underlay byte - with index 63 = the id follows as a byte, 62 = nothing;
 *  - otherwise `1 | 8 (overlay) | 0x10 (locs)`, the raw underlay, overlay and `rotation << 6 | shape`, the loc count, and per
 *    loc its id (u16) and `rotation << 6 | type`.
 * The locs Jagex put in are the walls (types 0, 2, 3 and 9 - never the corner type 1) and every object with a map scene (op 102)
 * or a map element (op 107), in landscape order. [encodeSquare] reproduces untouched squares byte for byte (test: 48_55).
 *
 * **Label.** A new MapElementType [LABEL_ELEMENT] "Ferox Enclave" in the town-label style of Edgeville (element 92: size 1,
 * white, op 8, category 950) and a `main_staticelements` entry at [LABEL_X], [LABEL_Z].
 *
 * Usage: `./gradlew :game:runFeroxWorldMapTool --args="plan|apply"`.
 */
object FeroxWorldMapTool {
    const val WORLD_MAP_INDEX = 23
    const val CONFIG_INDEX = 2
    const val MAP_ELEMENT_GROUP = 36
    const val LOC_INDEX = 16

    /** Map squares of the Ferox import (FeroxImportTool.REGIONS 12344, 12600). */
    val SQUARES = listOf(48 to 56, 49 to 56)

    /** First free MapElementType after the hall quest icon (QuestMapIconTool.HALL_QUEST_ELEMENT 1116). */
    const val LABEL_ELEMENT = 1117
    const val LABEL_TEXT = "Ferox Enclave"
    const val LABEL_X = 3150
    const val LABEL_Z = 3634

    class Record(val square: Boolean, val x: Int, val z: Int, val start: Int, val end: Int)

    /** The area file's two floor lists and its records with their byte ranges. */
    class Area(val underlays: IntArray, val overlays: IntArray, val records: List<Record>)

    fun parse(area: ByteArray): Area {
        var p = 0
        fun g1() = area[p++].toInt() and 0xFF
        val underlays = IntArray(g1()) { g1() }
        val overlays = IntArray(g1()) { g1() }

        fun tile() {
            val data = g1()
            if (data and 1 == 0) {
                val k = (data shr 2) and 0x3F
                if (k != 62) {
                    if (k == 63) g1()
                    if (data and 2 != 0) g1()
                }
                return
            }
            val levels = ((data shr 1) and 3) + 1
            repeat(levels) {
                g1()
                if (data and 8 != 0) {
                    g1()
                    g1()
                }
                if (data and 0x10 != 0) repeat(g1()) { p += 3 }
            }
        }
        val records = ArrayList<Record>()
        while (p < area.size) {
            val start = p
            if (g1() == 0) {
                val x = g1()
                val z = g1()
                repeat(64 * 64) { tile() }
                records += Record(true, x, z, start, p)
            } else {
                val x = g1()
                val z = g1()
                p += 2
                repeat(64) { tile() }
                records += Record(false, x, z, start, p)
            }
        }
        check(p == area.size) { "area overran: $p of ${area.size}" }
        return Area(underlays, overlays, records)
    }

    /** Whether the world map draws [loc] (walls, and objects with a map scene or map element). */
    fun shown(
        loc: Rev667Loc,
        def: (Int) -> ObjectDef?,
    ): Boolean =
        when (loc.type) {
            0, 2, 3, 9 -> true
            1 -> false
            else -> def(loc.id)?.let { it.mapScene != -1 || it.mapElement != -1 } ?: false
        }

    /** One full-square record (level 0) of [tiles] and [locs], with [area]'s floor lists. */
    fun encodeSquare(
        squareX: Int,
        squareZ: Int,
        tiles: Rev667TileMap,
        locs: List<Rev667Loc>,
        area: Area,
        def: (Int) -> ObjectDef?,
    ): ByteArray {
        val shownLocs = locs.filter { it.plane == 0 && shown(it, def) }.groupBy { it.localX to it.localZ }
        val out = ByteArrayOutputStream()
        out.write(0)
        out.write(squareX)
        out.write(squareZ)
        val ground = tiles.tiles[0]
        for (x in 0 until 64) for (z in 0 until 64) {
            val t = ground[x][z]
            val underlay = t.underlayId and 0xFF
            val overlay = t.overlayId and 0xFF
            val here = shownLocs[x to z].orEmpty()
            val shaped = overlay != 0 && (t.overlayShape != 0 || t.overlayRotation != 0)
            if (here.isNotEmpty() || shaped) {
                check(here.size < 256) { "too many locs on $x,$z" }
                out.write(1 or (if (overlay != 0) 8 else 0) or (if (here.isNotEmpty()) 0x10 else 0))
                out.write(underlay)
                if (overlay != 0) {
                    out.write(overlay)
                    out.write((t.overlayRotation shl 6) or t.overlayShape)
                }
                if (here.isNotEmpty()) {
                    out.write(here.size)
                    here.forEach { loc ->
                        out.write(loc.id ushr 8)
                        out.write(loc.id and 0xFF)
                        out.write((loc.rotation shl 6) or loc.type)
                    }
                }
            } else if (overlay != 0) {
                val index = area.overlays.indexOf(overlay)
                if (index in 0 until 62) {
                    out.write(2 or (index shl 2))
                } else {
                    out.write(2 or (63 shl 2))
                    out.write(overlay)
                }
                out.write(underlay)
            } else if (underlay != 0) {
                val index = area.underlays.indexOf(underlay)
                if (index in 0 until 62) {
                    out.write(index shl 2)
                } else {
                    out.write(63 shl 2)
                    out.write(underlay)
                }
            } else {
                out.write(62 shl 2)
            }
        }
        return out.toByteArray()
    }

    /** The town label: Edgeville's element 92 with the text replaced. */
    fun labelElement(): ByteArray =
        ByteArrayOutputStream().use { out ->
            out.write(3)
            out.write(LABEL_TEXT.toByteArray(Charsets.ISO_8859_1))
            out.write(0)
            out.write(6)
            out.write(1)
            out.write(4)
            out.write(0xFF)
            out.write(0xFF)
            out.write(0xFF)
            out.write(8)
            out.write(1)
            out.write(19)
            out.write(950 ushr 8)
            out.write(950 and 0xFF)
            out.write(0)
            out.toByteArray()
        }

    /** Reads a square's tiles and locs from [library] with the region keys. */
    fun landscape(
        library: CacheLibrary,
        keys: Map<Int, IntArray>,
        squareX: Int,
        squareZ: Int,
    ): Pair<Rev667TileMap, List<Rev667Loc>> {
        val region = (squareX shl 8) or squareZ
        val maps = library.index(Rev667RegionProbeTool.MAP_INDEX)
        val tiles = Rev667TileCodec.decode(maps.archive("m${squareX}_$squareZ")!!.file(0)!!.data!!)
        val locs = Rev667LocCodec.decode(maps.archive("l${squareX}_$squareZ", keys[region])!!.file(0)!!.data!!)
        return tiles to locs
    }

    fun objectDefs(library: CacheLibrary): (Int) -> ObjectDef? {
        val cache = HashMap<Int, ObjectDef?>()
        return { id ->
            cache.getOrPut(id) {
                library.data(LOC_INDEX, id ushr 8, id and 0xFF)?.let { b -> ObjectDef(id).also { it.decode(Unpooled.wrappedBuffer(b)) } }
            }
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        val mutations = ArrayList<CacheMutation>()
        try {
            val keys = Rev667RegionProbeTool.loadKeys(File(FeroxImportTool.XTEAS_FILE))
            val def = objectDefs(library)
            val worldMap = library.index(WORLD_MAP_INDEX)
            val mainGroup = worldMap.archive("main") ?: error("world map group main missing")
            val areaFile = mainGroup.file("area") ?: error("main/area missing")
            val current = library.data(WORLD_MAP_INDEX, mainGroup.id, areaFile.id) ?: error("main/area unreadable")
            val area = parse(current)
            val out = ByteArrayOutputStream(current.size + 4096)
            var copied = 0
            var changed = 0
            area.records.filter { r -> SQUARES.any { (x, z) -> r.x == x && r.z == z } }.sortedBy { it.start }.forEach { r ->
                check(r.square) { "square ${r.x}_${r.z} is stored as chunks" }
                out.write(current, copied, r.start - copied)
                val (tiles, locs) = landscape(library, keys, r.x, r.z)
                val rebuilt = encodeSquare(r.x, r.z, tiles, locs, area, def)
                val old = current.copyOfRange(r.start, r.end)
                if (!old.contentEquals(rebuilt)) changed++
                println("square ${r.x}_${r.z}: ${old.size} -> ${rebuilt.size} bytes${if (old.contentEquals(rebuilt)) " (unchanged)" else ""}")
                out.write(rebuilt)
                copied = r.end
            }
            out.write(current, copied, current.size - copied)
            val updated = out.toByteArray()
            check(parse(updated).records.size == area.records.size) { "record count changed" }
            if (changed > 0) {
                mutations += CacheMutation(WORLD_MAP_INDEX, mainGroup.id, areaFile.id, updated, "N228 world map main/area: Ferox squares rebuilt", CacheItemProbeTool.sha1(current))
            }
            val label = library.data(CONFIG_INDEX, MAP_ELEMENT_GROUP, LABEL_ELEMENT)
            check(label == null || label.contentEquals(labelElement())) { "element $LABEL_ELEMENT is taken" }
            if (label == null) mutations += CacheMutation(CONFIG_INDEX, MAP_ELEMENT_GROUP, LABEL_ELEMENT, labelElement(), "N228 Ferox Enclave label element", null)
            val staticGroup = worldMap.archive("main_staticelements") ?: error("main_staticelements missing")
            val entry = MansionMapIconTool.staticEntry(LABEL_ELEMENT, LABEL_X, LABEL_Z, 0)
            val existing =
                staticGroup.fileIds().filter { f ->
                    library.data(WORLD_MAP_INDEX, staticGroup.id, f)?.let { it.size == 7 && it[4] == entry[4] && it[5] == entry[5] } == true
                }
            when {
                existing.any { library.data(WORLD_MAP_INDEX, staticGroup.id, it)!!.contentEquals(entry) } -> println("IN PLACE label")
                existing.isNotEmpty() -> mutations += CacheMutation(WORLD_MAP_INDEX, staticGroup.id, existing.first(), entry, "N228 Ferox label (moved)", CacheItemProbeTool.sha1(library.data(WORLD_MAP_INDEX, staticGroup.id, existing.first())!!))
                else -> mutations += CacheMutation(WORLD_MAP_INDEX, staticGroup.id, (staticGroup.fileIds().maxOrNull() ?: -1) + 1, entry, "N228 Ferox label", null)
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
