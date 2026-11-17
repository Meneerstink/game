package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.RspsPaths
import java.io.File

/**
 * GRAFISCH 16-11 (eigenaar 19:5x, CoX-kamercontrole): in ALLE CoX-kamers ligt op de gewone grotvloer (OSRS tegel-underlay 103 = def 102
 * rgb 111e1a, GEEN overlay; OSRS mengt hem met de buren tot de donkere looppaden uit de video) bij ons de zwarte void-overlay (tegelwaarde 52)
 * - 'cox-map-floors' voidUnderlayOverlay (102 to 51), een noodgreep tegen de te lichte 667-textuur 154 (COX.txt 16-11 00:5x). ~4000 tegels.
 * Deze tool haalt die zwarte overlay weg op precies die tegels (OSRS-bron = underlay 103 zonder overlay, ons = overlay 52) en zet de underlay
 * op een gekozen 667-underlay. Zoals ToA (ToaUnderlayColourTool: 14160 u103 -> 172 na strepenproef) wordt de keuze eerst met een strepenproef
 * naast de OSRS-video gemaakt: 'trial c1,c2,..' zet de kandidaten in banen van 4 tegels (x/4 mod n). Alleen CoX-kaartvakken.
 * Usage: plan|apply <underlay-tegelwaarde> | plan|apply trial <c1,c2,...>
 */
object CoxVoidFloorTool {
    const val SOURCE_UNDERLAY = 103
    const val VOID_OVERLAY = 52

    internal fun rewrite(bytes: ByteArray, source: Array<Array<Array<ModernTile>>>, pick: (Int, Int) -> Int,
                         sourceUnderlay: Int = SOURCE_UNDERLAY): Pair<ByteArray, IntArray> {
        val map = Rev667TileCodec.decode(bytes)
        check(Rev667TileCodec.encode(map).contentEquals(bytes)) { "Tile roundtrip failed" }
        val counts = IntArray(4)
        for (p in 0..3) for (x in 0..63) for (z in 0..63) {
            val src = source[p][x][z]
            if (src.underlayId != sourceUnderlay || src.overlayId != 0) continue
            val tile = map.tiles[p][x][z]
            if ((tile.overlayId and 255) != VOID_OVERLAY) continue
            tile.overlayId = 0
            tile.overlayShape = 0
            tile.overlayRotation = 0
            tile.underlayId = pick(x, z)
            counts[p]++
        }
        return Rev667TileCodec.encode(map) to counts
    }

    /** plan|apply remap <osrs=ons,...> over alle CoX-kaartvakken. */
    private fun remapMain(mode: String, spec: String) {
        require(mode in setOf("plan", "apply"))
        val pairs = spec.split(",").map { p -> p.split("=").let { it[0].trim().toInt() to it[1].trim().toInt() } }
        pairs.forEach { require(it.second in 1..174) }
        val paths = listOf(RspsPaths.data("cache"), RspsPaths.root("file-server/cache"))
        val mutations = mutableListOf<CacheMutation>()
        ModernCacheReader(File(RspsPaths.root("import-source/openrs2-2686/cache"))).use { reader ->
            val game = CacheLibrary(paths[0]); val files = CacheLibrary(paths[1])
            try {
                for ((_, dst) in pairs) for (cache in listOf(game, files)) requireNotNull(cache.data(2, 1, dst - 1)) { "underlay def ${dst - 1} missing" }
                for ((square, name) in ToaUpperTileFlagTool.targets("cox")) {
                    val source = ModernRegion.decodeTiles(reader.file(5, square, 0) ?: continue)
                    val archive = requireNotNull(game.index(5).archive(name)); val other = requireNotNull(files.index(5).archive(name))
                    val before = requireNotNull(archive.file(0)?.data)
                    check(archive.id == other.id && before.contentEquals(other.file(0)?.data)) { "$name cache copies differ" }
                    var cur = before; val tot = IntArray(4)
                    for ((s, d) in pairs) { val (b, c) = remap(cur, source, s, d); cur = b; for (i in 0..3) tot[i] += c[i] }
                    println("COX_REMAP source=$square target=$name rewritten=${tot.toList()} $spec")
                    if (!before.contentEquals(cur)) mutations += CacheMutation(5, archive.id, 0, cur, "CoX $name: underlay remap $spec", CacheItemProbeTool.sha1(before))
                }
            } finally { game.close(); files.close() }
        }
        if (mutations.isEmpty()) { println("NOTHING_TO_DO"); return }
        val tx = CacheTransaction(paths, mutations)
        val plan = tx.preflight()
        check(tx.blockingErrors(plan).isEmpty()) { tx.blockingErrors(plan).joinToString("\n") }
        if (mode == "plan") { println("DRY_RUN ${tx.id} maps=${mutations.size}"); return }
        val result = tx.apply(plan)
        check(tx.verify().isEmpty()) { "Cache verification failed: ${tx.id}" }
        println("COX_REMAP transaction=${tx.id} applied=${result.applied} skipped=${result.skipped} verified=true")
    }

    /** 'remap': alleen de UNDERLAY van tegels met OSRS-bron-underlay [sourceUnderlay] (elke overlay) -> [dst]; overlay blijft. 16-11 22:3x
     *  Ice demon: OSRS u16 828b9f (blauwgrijs) was bij ons u33 7f9a86 (groengrijs + textuur 66) = groene ijsvloer. */
    internal fun remap(bytes: ByteArray, source: Array<Array<Array<ModernTile>>>, sourceUnderlay: Int, dst: Int): Pair<ByteArray, IntArray> {
        val map = Rev667TileCodec.decode(bytes)
        check(Rev667TileCodec.encode(map).contentEquals(bytes)) { "Tile roundtrip failed" }
        val counts = IntArray(4)
        for (p in 0..3) for (x in 0..63) for (z in 0..63) {
            if (source[p][x][z].underlayId != sourceUnderlay) continue
            val tile = map.tiles[p][x][z]
            if (tile.underlayId == dst) continue
            tile.underlayId = dst
            counts[p]++
        }
        return Rev667TileCodec.encode(map) to counts
    }

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size == 3 && args[1] == "remap") return remapMain(args[0], args[2])
        require(args.size in 2..3 && args[0] in setOf("plan", "apply")) { "Usage: plan|apply <underlay> | trial <c1,..> | map <osrs=ons,...>" }
        // 'map 103=3,72=142': per OSRS-bron-underlay (tegelwaarde) een eigen doel (16-11 22:3x: ook u72 030303 = zwarte strepen in de paden).
        val mapping: List<Pair<Int, Int>> = if (args[1] == "map") args[2].split(",").map { p -> p.split("=").let { it[0].trim().toInt() to it[1].trim().toInt() } }
            else emptyList()
        val candidates = when (args[1]) { "trial" -> args[2].split(",").map { it.trim().toInt() }; "map" -> mapping.map { it.second }; else -> listOf(args[1].toInt()) }
        candidates.forEach { require(it in 1..174) { "underlay tile value $it not encodable" } }
        val pick: (Int, Int) -> Int = { x, _ -> candidates[(x / 4) % candidates.size] }
        val paths = listOf(RspsPaths.data("cache"), RspsPaths.root("file-server/cache"))
        val mutations = mutableListOf<CacheMutation>()
        ModernCacheReader(File(RspsPaths.root("import-source/openrs2-2686/cache"))).use { reader ->
            val game = CacheLibrary(paths[0])
            val files = CacheLibrary(paths[1])
            try {
                for (c in candidates) for (cache in listOf(game, files))
                    requireNotNull(cache.data(2, 1, c - 1)) { "underlay def ${c - 1} (tile value $c) missing" }
                for ((square, name) in ToaUpperTileFlagTool.targets("cox")) {
                    val sourceBytes = reader.file(5, square, 0) ?: continue
                    val source = ModernRegion.decodeTiles(sourceBytes)
                    val archive = requireNotNull(game.index(5).archive(name)) { "Missing $name" }
                    val other = requireNotNull(files.index(5).archive(name)) { "Fileserver missing $name" }
                    val before = requireNotNull(archive.file(0)?.data)
                    check(archive.id == other.id && before.contentEquals(other.file(0)?.data)) { "$name cache copies differ" }
                    val (after, counts) = if (mapping.isEmpty()) rewrite(before, source, pick) else {
                        var cur = before; val tot = IntArray(4)
                        for ((src, dst) in mapping) { val (b, c) = rewrite(cur, source, { _, _ -> dst }, src); cur = b; for (i in 0..3) tot[i] += c[i] }
                        cur to tot
                    }
                    println("COX_VOID_FLOOR source=$square target=$name rewritten=${counts.toList()} underlay=${candidates}")
                    if (!before.contentEquals(after)) mutations += CacheMutation(5, archive.id, 0, after,
                        "CoX $name: void overlay off OSRS underlay-103 floor -> underlay $candidates", CacheItemProbeTool.sha1(before))
                }
            } finally { game.close(); files.close() }
        }
        if (mutations.isEmpty()) { println("NOTHING_TO_DO"); return }
        val tx = CacheTransaction(paths, mutations)
        val plan = tx.preflight()
        check(tx.blockingErrors(plan).isEmpty()) { tx.blockingErrors(plan).joinToString("\n") }
        if (args[0] == "plan") { println("DRY_RUN ${tx.id} maps=${mutations.size}"); return }
        val result = tx.apply(plan)
        check(tx.verify().isEmpty()) { "Cache verification failed: ${tx.id}" }
        println("COX_VOID_FLOOR transaction=${tx.id} applied=${result.applied} skipped=${result.skipped} verified=true")
    }
}
