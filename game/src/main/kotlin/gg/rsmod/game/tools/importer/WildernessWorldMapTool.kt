package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * N529/N531/N522 G5 (owner 2026-11-02: 'nieuwe revcave entrance staat niet op de worldmap de oude staat er nog').
 *
 * The world map draws the prebuilt `main/area` snapshot of index 23, not the landscape ([FeroxWorldMapTool] explains the record
 * format). Every N522 surface import (Revenant Caves caverns + trapdoors, level-26 crevice, Web Chasm, lair entrances, OSRS
 * ground around them, Edgeville jail, volcano removal) rewrote map squares but not their snapshot records, so the world map
 * still shows the old 667 Wilderness there. This rebuilds the level-0 record of every square in [SQUARES] from the current
 * landscape with [FeroxWorldMapTool.encodeSquare] (which reproduces untouched squares byte for byte) - squares we never changed
 * come out identical and are left alone; `plan` lists which squares differ so each one can be checked against the import list.
 *
 * Usage: `./gradlew :game:runWildernessWorldMapTool --args="plan|apply"`
 */
object WildernessWorldMapTool {
    /** The Wilderness surface (x 2944-3455, z 3520-3967) plus the Edgeville jail square 48_54 (Krystilia). */
    val SQUARES: List<Pair<Int, Int>> = (46..53).flatMap { x -> (55..61).map { z -> x to z } } + listOf(48 to 54)

    /** A loc the SERVER places (spawn_obj) - not in the landscape, so the snapshot only gets it through this list. */
    class ServerLoc(val id: Int, val x: Int, val z: Int, val rot: Int)

    /**
     * Mirror of the plugin spawn lists (checked by WildernessWorldMapLocsTests in :game:plugins): RevenantCaves.SURFACE,
     * WildernessLairs.SURFACE and ScorpiaPit.CAVERNS. Each carries OSRS map scene 7 (the cave mouth) since tx-20261104-185727.
     */
    val SERVER_LOCS =
        listOf(
            ServerLoc(62993, 3073, 3654, 0), ServerLoc(62994, 3124, 3831, 3), ServerLoc(62996, 3067, 3740, 0),
            ServerLoc(62995, 3103, 3655, 3), ServerLoc(62995, 3122, 3805, 1),
            ServerLoc(63103, 3321, 3794, 1), ServerLoc(63182, 3183, 3744, 1),
            ServerLoc(63557, 3291, 3849, 3), ServerLoc(63558, 3221, 3787, 1), ServerLoc(63559, 3115, 3676, 0),
            ServerLoc(63104, 3282, 3774, 0), ServerLoc(63104, 3284, 3807, 0), ServerLoc(63104, 3320, 3830, 0), ServerLoc(63104, 3260, 3832, 0),
            ServerLoc(62957, 3231, 3936, 2), ServerLoc(62957, 3231, 3951, 0), ServerLoc(62957, 3241, 3949, 0),
        )

    /** The landscape locs of square [sx]_[sz] plus the [SERVER_LOCS] on it (type 10, plane 0). */
    fun withServerLocs(sx: Int, sz: Int, locs: List<Rev667Loc>): List<Rev667Loc> =
        locs + SERVER_LOCS.filter { it.x shr 6 == sx && it.z shr 6 == sz }.map { Rev667Loc(it.id, it.x and 63, it.z and 63, 0, 10, it.rot) }

    /** The byte slice of each of the 4096 tiles of a full-square record (same walk as [FeroxWorldMapTool.parse]). */
    fun tiles(record: ByteArray): List<ByteArray> {
        var p = 3
        fun g1() = record[p++].toInt() and 0xFF
        return List(64 * 64) {
            val start = p
            val data = g1()
            if (data and 1 == 0) {
                val k = (data shr 2) and 0x3F
                if (k != 62) {
                    if (k == 63) g1()
                    if (data and 2 != 0) g1()
                }
            } else {
                repeat(((data shr 1) and 3) + 1) {
                    g1()
                    if (data and 8 != 0) {
                        g1()
                        g1()
                    }
                    if (data and 0x10 != 0) repeat(g1()) { p += 3 }
                }
            }
            record.copyOfRange(start, p)
        }
    }

    fun describe(tile: ByteArray): String {
        val d = tile[0].toInt() and 0xFF
        if (d and 1 == 0) return "flat ${tile.joinToString(",") { (it.toInt() and 0xFF).toString() }}"
        var p = 2
        val sb = StringBuilder("u=${tile[1].toInt() and 0xFF}")
        if (d and 8 != 0) {
            sb.append(" o=${tile[p].toInt() and 0xFF}/${tile[p + 1].toInt() and 0xFF}")
            p += 2
        }
        if (d and 0x10 != 0) {
            val n = tile[p++].toInt() and 0xFF
            sb.append(" locs=")
            repeat(n) {
                val id = ((tile[p].toInt() and 0xFF) shl 8) or (tile[p + 1].toInt() and 0xFF)
                sb.append("$id/t${tile[p + 2].toInt() and 0x3F} ")
                p += 3
            }
        }
        return sb.toString()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        if (mode == "tiles") {
            // tiles x1 z1 x2 z2: the snapshot's locs on those tiles (read-only)
            val (x1, z1, x2, z2) = args.drop(1).map { it.toInt() }
            val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
            try {
                val def = FeroxWorldMapTool.objectDefs(library)
                val mainGroup = library.index(FeroxWorldMapTool.WORLD_MAP_INDEX).archive("main")!!
                val current = library.data(FeroxWorldMapTool.WORLD_MAP_INDEX, mainGroup.id, mainGroup.file("area")!!.id)!!
                val area = FeroxWorldMapTool.parse(current)
                for (x in x1..x2) for (z in z1..z2) {
                    val r = area.records.firstOrNull { it.square && it.x == x / 64 && it.z == z / 64 } ?: continue
                    val t = tiles(current.copyOfRange(r.start, r.end))[(x and 63) * 64 + (z and 63)]
                    val d = describe(t)
                    if ("locs=" in d) {
                        val ids = Regex("""(\d+)/t""").findAll(d).map { it.groupValues[1].toInt() }
                        println("TILE $x,$z $d :: ${ids.joinToString { id -> def(id)?.let { "$id '${it.name}' msi=${it.mapScene} el=${it.mapElement}" } ?: "$id ?" }}")
                    }
                }
            } finally {
                library.close()
            }
            return
        }
        if (mode == "diff") {
            val (sx, sz) = args[1].split("_").map { it.toInt() }
            val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
            try {
                val keys = Rev667RegionProbeTool.loadKeys(File(FeroxImportTool.XTEAS_FILE))
                val def = FeroxWorldMapTool.objectDefs(library)
                val mainGroup = library.index(FeroxWorldMapTool.WORLD_MAP_INDEX).archive("main")!!
                val current = library.data(FeroxWorldMapTool.WORLD_MAP_INDEX, mainGroup.id, mainGroup.file("area")!!.id)!!
                val area = FeroxWorldMapTool.parse(current)
                val r = area.records.first { it.x == sx && it.z == sz }
                val (t, l) = FeroxWorldMapTool.landscape(library, keys, sx, sz)
                val a = tiles(current.copyOfRange(r.start, r.end))
                val b = tiles(FeroxWorldMapTool.encodeSquare(sx, sz, t, withServerLocs(sx, sz, l), area, def))
                var n = 0
                for (i in a.indices) if (!a[i].contentEquals(b[i])) {
                    if (n++ < 60) println("TILE ${sx * 64 + i / 64},${sz * 64 + i % 64} old[${describe(a[i])}] new[${describe(b[i])}]")
                }
                println("DIFF_TILES $n")
            } finally {
                library.close()
            }
            return
        }
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply|diff x_z" }
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
            val out = ByteArrayOutputStream(current.size + 65536)
            var copied = 0
            val changed = ArrayList<String>()
            val wanted = SQUARES.toSet()
            val records = area.records.filter { (it.x to it.z) in wanted }.sortedBy { it.start }
            val missing = wanted - records.map { it.x to it.z }.toSet()
            if (missing.isNotEmpty()) println("NO_RECORD (left out) ${missing.sortedWith(compareBy({ it.first }, { it.second }))}")
            records.forEach { r ->
                out.write(current, copied, r.start - copied)
                val old = current.copyOfRange(r.start, r.end)
                val landscape = runCatching { FeroxWorldMapTool.landscape(library, keys, r.x, r.z) }.getOrNull()
                if (!r.square || landscape == null) {
                    println("SKIP ${r.x}_${r.z}: ${if (!r.square) "stored as chunks" else "landscape unreadable"}")
                    out.write(old)
                } else {
                    val rebuilt = FeroxWorldMapTool.encodeSquare(r.x, r.z, landscape.first, withServerLocs(r.x, r.z, landscape.second), area, def)
                    if (old.contentEquals(rebuilt)) {
                        println("SAME ${r.x}_${r.z} (region ${(r.x shl 8) or r.z})")
                    } else {
                        changed += "${r.x}_${r.z}"
                        println("CHANGED ${r.x}_${r.z} (region ${(r.x shl 8) or r.z}) ${old.size} -> ${rebuilt.size} bytes")
                    }
                    out.write(rebuilt)
                }
                copied = r.end
            }
            out.write(current, copied, current.size - copied)
            val updated = out.toByteArray()
            check(FeroxWorldMapTool.parse(updated).records.size == area.records.size) { "record count changed" }
            println("SUMMARY squares=${records.size} changed=${changed.size} $changed")
            if (changed.isNotEmpty()) {
                mutations += CacheMutation(FeroxWorldMapTool.WORLD_MAP_INDEX, mainGroup.id, areaFile.id, updated, "N529 world map main/area: wilderness squares $changed rebuilt", CacheItemProbeTool.sha1(current))
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
