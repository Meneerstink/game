package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.RspsPaths
import java.io.File

/**
 * N609 13-11 (eigenaar 'er gaat nog veel mis grafisch'): TEST tool for choosing a textured 667 underlay by LOOK, not by colour alone
 * (round 2 picked by colour and got a checkerboard in Ba-Ba and cobblestones in the Apmeken puzzle). In one ToA map it gives the tiles of
 * one OSRS underlay a different candidate local underlay per z-band, so one screenshot shows every candidate next to the OSRS reference.
 * Afterwards ToaUnderlayColourTool (TEXTURED, which accepts these candidates as 'from') writes the chosen one.
 * Usage: ToaUnderlayTrialTool plan|apply <osrsSquare> <osrsUnderlay> <currentLocal>[,<currentLocal>...] <candidate>[,<candidate>...] (one candidate = revert/apply)
 */
object ToaUnderlayTrialTool {
    internal fun trial(bytes: ByteArray, source: Array<Array<Array<ModernTile>>>, osrs: Int, allowed: Set<Int>, candidates: List<Int>): Pair<ByteArray, Map<Int, Int>> {
        val map = Rev667TileCodec.decode(bytes)
        check(Rev667TileCodec.encode(map).contentEquals(bytes)) { "Tile roundtrip failed" }
        val counts = sortedMapOf<Int, Int>()
        for (p in 0..3) for (x in 0..63) for (z in 0..63) {
            if (source[p][x][z].underlayId - 1 != osrs) continue
            val tile = map.tiles[p][x][z]
            val current = (tile.underlayId and 255) - 1
            check(current in allowed) { "Unexpected underlay $current at $p/$x/$z for OSRS $osrs (allowed $allowed)" }
            val want = candidates[(x / 4) % candidates.size] // stripes 4 tiles wide (west->east), every candidate near the player
            if (current == want) continue
            tile.underlayId = want + 1
            counts.merge(want, 1, Int::plus)
        }
        return Rev667TileCodec.encode(map) to counts
    }

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 5 && args[0] in setOf("plan", "apply")) { "Usage: plan|apply <osrsSquare> <osrsUnderlay> <current1,current2,...> <c1,c2,...>" }
        val square = args[1].toInt(); val osrs = args[2].toInt(); val current = args[3].split(',').map { it.trim().toInt() }.toSet()
        val candidates = args[4].split(',').map { it.trim().toInt() }
        // N609 14-11: also the ToA lobby (13454) and the Necropolis squares - same OSRS->667 floor import.
        val name: String = ToaUpperTileFlagTool.targets("all")[square] ?: error("$square is not a ToA raid/lobby/Necropolis map")
        val paths = listOf(RspsPaths.data("cache"), RspsPaths.root("file-server/cache"))
        val mutations = mutableListOf<CacheMutation>()
        ModernCacheReader(File(RspsPaths.root("import-source/openrs2-2686/cache"))).use { reader ->
            val game = CacheLibrary(paths[0])
            val files = CacheLibrary(paths[1])
            try {
                val source = ModernRegion.decodeTiles(requireNotNull(reader.file(5, square, 0)))
                val archive = requireNotNull(game.index(5).archive(name)) { "Missing $name" }
                val other = requireNotNull(files.index(5).archive(name)) { "Fileserver missing $name" }
                val before = requireNotNull(archive.file(0)?.data)
                check(archive.id == other.id && before.contentEquals(other.file(0)?.data)) { "$name cache copies differ" }
                val (after, counts) = trial(before, source, osrs, candidates.toSet() + current, candidates)
                println("UNDERLAY_TRIAL square=$square target=$name osrs=$osrs stripes(x/4 % n, west->east)=$candidates written=$counts")
                if (!before.contentEquals(after)) mutations += CacheMutation(5, archive.id, 0, after,
                    "ToA $name: underlay trial OSRS $osrs -> $candidates (N609 test)", CacheItemProbeTool.sha1(before))
            } finally { game.close(); files.close() }
        }
        if (mutations.isEmpty()) { println("NOTHING_TO_DO"); return }
        val tx = CacheTransaction(paths, mutations)
        val plan = tx.preflight()
        check(tx.blockingErrors(plan).isEmpty()) { tx.blockingErrors(plan).joinToString("\n") }
        if (args[0] == "plan") { println("DRY_RUN ${tx.id}"); return }
        val result = tx.apply(plan)
        check(tx.verify().isEmpty()) { "Cache verification failed: ${tx.id}" }
        println("UNDERLAY_TRIAL transaction=${tx.id} applied=${result.applied} verified=true")
    }
}
