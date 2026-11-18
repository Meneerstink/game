package gg.rsmod.game.tools.importer

import gg.rsmod.game.RspsPaths
import java.io.File

/**
 * Adds ONE loc to an existing (possibly XTEA-encrypted) map square's loc file, in both caches, nothing else touched - generalised from
 * [AfkBasementMapTool.hallEntrance]. First use (N618, 16-11): the OSRS raid icon RAID_ICON 46201 sits once at 3355,2714 plane 1 in OSRS
 * square 13354 (ModernRegionProbeTool on openrs2-2499), our Necropolis import dropped it; re-importing the Necropolis would undo approved
 * floor/light work (N601/N607), so only this loc is added.
 *
 * Usage: `<locId> <x> <z> <plane> <type> <rotation> [--apply]` (world tiles). Already present (same id, tile, type, rotation) = nothing to do.
 * The loc's tile must not hold another loc of the same type (refused).
 */
object MapLocAddTool {
    private val GAME_CACHE = RspsPaths.data("cache")
    private val FILE_SERVER_CACHE = RspsPaths.root("file-server/cache")
    private val XTEAS_FILE = RspsPaths.data("xteas/xteas.json")

    @JvmStatic
    fun main(args: Array<String>) {
        val n = args.filterNot { it.startsWith("--") }.map { it.toInt() }
        require(n.size == 6) { "Usage: <locId> <x> <z> <plane> <type> <rotation> [--apply]" }
        val (id, x, z, plane, type, rot) = n
        val apply = "--apply" in args
        val sx = x shr 6
        val sz = z shr 6
        val regionId = (sx shl 8) or sz
        val key = Rev667RegionProbeTool.loadKeys(File(XTEAS_FILE))[regionId]
        val name = "l${sx}_$sz"
        val library = com.displee.cache.CacheLibrary(GAME_CACHE.toString())
        val mutation =
            try {
                val archive = library.index(Rev667RegionProbeTool.MAP_INDEX).archive(name, key) ?: error("$name missing")
                val bytes = archive.file(0)!!.data!!
                val locs = Rev667LocCodec.decode(bytes)
                check(Rev667LocCodec.encode(locs).contentEquals(bytes)) { "$name round-trip failed" }
                val wanted = Rev667Loc(id, x - sx * 64, z - sz * 64, plane, type, rot)
                if (locs.any { it.id == id && it.localX == wanted.localX && it.localZ == wanted.localZ && it.plane == plane && it.type == type && it.rotation == rot }) {
                    println("NOTHING_TO_DO $name already holds loc $id at $x,$z,$plane")
                    return
                }
                val clash = locs.filter { it.localX == wanted.localX && it.localZ == wanted.localZ && it.plane == plane && it.type == type }
                check(clash.isEmpty()) { "tile $x,$z,$plane already holds type $type locs ${clash.map { it.id }}" }
                val merged = (locs + wanted).sortedWith(compareBy({ it.id }, { it.packedPosition }, { it.type }))
                val newBytes = Rev667LocCodec.encode(merged)
                check(Rev667LocCodec.decode(newBytes).size == merged.size)
                println("PLAN $name (region $regionId, key=${key != null}) locs ${locs.size} -> ${merged.size}: + loc $id at $x,$z,$plane type $type rot $rot")
                CacheMutation(Rev667RegionProbeTool.MAP_INDEX, archive.id, 0, newBytes, "MapLocAddTool loc $id in $name", CacheItemProbeTool.sha1(bytes), xtea = key)
            } finally {
                library.close()
            }
        val tx = CacheTransaction(listOf(GAME_CACHE, FILE_SERVER_CACHE), listOf(mutation))
        val preflight = tx.preflight()
        println(preflight.joinToString("\n"))
        val errors = tx.blockingErrors(preflight)
        check(errors.isEmpty()) { errors.joinToString("\n") }
        if (!apply) {
            println("PLAN_ONLY transaction=${tx.id}")
            return
        }
        val result = tx.apply(preflight)
        val problems = tx.verify()
        check(problems.isEmpty()) { problems.joinToString("\n") }
        println("APPLIED transaction=${result.transactionId} applied=${result.applied} skipped=${result.skipped} VERIFY_OK")
    }

    private operator fun <T> List<T>.component6(): T = this[5]
}
