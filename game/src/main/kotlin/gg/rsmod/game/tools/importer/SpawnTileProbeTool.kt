package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/**
 * N522 stap 3 breektest (read-only): every `spawn_npc` of the given spawn plugins on a tile a player could never stand on - the
 * tile is blocked (map flag 0x1), has no floor, or lies under a solid object (loc types 10/11 with blockwalk, footprint w x l
 * turned by the rotation; bridge flag 0x2 on the level above handled like the client). Same cache decoders as the world-map
 * tools (Rev667TileCodec / Rev667LocCodec / ObjectDef).
 *
 * Usage: `./gradlew :game:runSpawnTileProbeTool --args="<spawn plugin dir> [N522-only]"`
 */
object SpawnTileProbeTool {
    private val SPAWN = Regex("""spawn_npc\(npc = Npcs\.(\w+), x = (\d+), z = (\d+)(?:, height = (\d))?[^)]*\)(.*)""")

    @JvmStatic
    fun main(args: Array<String>) {
        val dir = File(args.getOrNull(0) ?: error("spawn plugin dir"))
        val onlyN522 = args.getOrNull(1) == "N522-only"
        val spawns =
            dir.listFiles { f -> f.name.startsWith("spawns_") }!!.flatMap { f ->
                f.readLines().mapNotNull { SPAWN.find(it) }.filter { !onlyN522 || "N522" in it.groupValues[5] }
                    .map { m -> listOf(m.groupValues[1], m.groupValues[2], m.groupValues[3], m.groupValues[4].ifEmpty { "0" }, f.name) }
            }
        println("SPAWNS ${spawns.size}")
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        try {
            val keys = Rev667RegionProbeTool.loadKeys(File(FeroxImportTool.XTEAS_FILE))
            val def = FeroxWorldMapTool.objectDefs(library)
            val squares = HashMap<Pair<Int, Int>, Pair<Rev667TileMap, List<Rev667Loc>>?>()
            var bad = 0
            spawns.forEach { (name, xs, zs, hs, file) ->
                val x = xs.toInt()
                val z = zs.toInt()
                val h = hs.toInt()
                val sq = x / 64 to z / 64
                val land = squares.getOrPut(sq) { runCatching { FeroxWorldMapTool.landscape(library, keys, sq.first, sq.second) }.getOrNull() }
                if (land == null) {
                    println("NO_MAP $name $x,$z,$h ($file)")
                    bad++
                    return@forEach
                }
                val (tiles, locs) = land
                fun problems(
                    lx: Int,
                    lz: Int,
                ): List<String> {
                    // the client moves a level down where the tile above carries the bridge flag
                    val level = if (h < 3 && tiles.tiles[1][lx][lz].flags and 2 != 0) h + 1 else h
                    val t = tiles.tiles[level][lx][lz]
                    val why = ArrayList<String>()
                    if (t.flags and 1 != 0) why += "blocked tile"
                    if (t.underlayId == 0 && t.overlayId == 0) why += "no floor"
                    locs.filter { it.plane == level && it.type in 10..11 }.forEach { l ->
                        val d = def(l.id) ?: return@forEach
                        if (!d.solid) return@forEach
                        val w = if (l.rotation and 1 == 1) d.length else d.width
                        val len = if (l.rotation and 1 == 1) d.width else d.length
                        if (lx in l.localX until l.localX + w && lz in l.localZ until l.localZ + len) why += "under ${l.id} '${d.name}'"
                    }
                    return why
                }
                val why = problems(x and 63, z and 63)
                if (why.isNotEmpty()) {
                    bad++
                    // nearest free tile in the same map square (ring by ring, Chebyshev distance)
                    val near =
                        (1..4).asSequence().flatMap { r -> (-r..r).asSequence().flatMap { dx -> (-r..r).asSequence().map { dz -> dx to dz } }.filter { (dx, dz) -> maxOf(kotlin.math.abs(dx), kotlin.math.abs(dz)) == r } }
                            .map { (dx, dz) -> (x + dx) to (z + dz) }
                            .firstOrNull { (nx, nz) -> nx / 64 == sq.first && nz / 64 == sq.second && problems(nx and 63, nz and 63).isEmpty() }
                    println("BAD $name $x,$z,$h ($file): ${why.joinToString()} -> nearest free ${near?.let { "${it.first},${it.second}" } ?: "NONE"}")
                }
            }
            println("SUMMARY spawns=${spawns.size} bad=$bad")
        } finally {
            library.close()
        }
    }
}
