package gg.rsmod.game.tools.importer

import java.io.File

/**
 * N606 12-11 (owner: Zebak arena 'heeft niet echt een afscheiding met de rest van de room'): print the OSRS overlay/underlay ids
 * (+shape) per tile for a window of one modern square, per plane. Read-only.
 * Usage: TileFloorProbeTool <osrsSquare> <x0> <x1> <z0> <z1> [plane]   (local 0..63). Cell = settings:overlay/shape:underlay.
 */
object TileFloorProbeTool {
    @JvmStatic
    fun main(args: Array<String>) {
        if (args[0] == "defs") { // TileFloorProbeTool defs u<id> o<id> ... (zero-based OSRS config ids)
            ModernCacheReader(File(FeroxImportTool.MODERN_CACHE)).use { reader ->
                val under = reader.files(2, 1); val over = reader.files(2, 4)
                args.drop(1).forEach { a ->
                    val id = a.drop(1).toInt()
                    if (a[0] == 'u') println("underlay $id rgb=%06x".format(under[id]?.let { ModernFloorDefs.decodeUnderlay(it).rgb } ?: -1))
                    else println("overlay $id " + (over[id]?.let { ModernFloorDefs.decodeOverlay(it) }?.let { "rgb=%06x tex=%d hide=%s".format(it.rgb, it.texture, it.hideUnderlay) } ?: "-"))
                }
            }
            return
        }
        if (args[0] == "pairs") { // TileFloorProbeTool pairs: per ToA raid square, OSRS overlay/underlay -> what OUR map holds on the same tiles
            val lib = com.displee.cache.CacheLibrary(gg.rsmod.game.RspsPaths.data("cache"))
            val localU = FeroxImportTool.decodeLocalFloors(lib, Rev667RegionProbeTool.UNDERLAY_GROUP) { Rev667FloorCodec.decodeUnderlay(it) }
            val localO = FeroxImportTool.decodeLocalFloors(lib, Rev667RegionProbeTool.OVERLAY_GROUP) { Rev667FloorCodec.decodeOverlay(it) }
            ModernCacheReader(File(gg.rsmod.game.RspsPaths.root("import-source/openrs2-2686/cache"))).use { reader ->
                val under = reader.files(2, 1); val over = reader.files(2, 4)
                for ((square, name) in ToaUpperTileFlagTool.targets(args.getOrNull(1) ?: "raid")) { // N609 14-11: optional scope lobby|necropolis|all
                    val raw = requireNotNull(reader.file(5, square, 0))
                    val src = ModernRegion.decodeTiles(raw)
                    val map = Rev667TileCodec.decode(requireNotNull(lib.index(5).archive(name)?.file(0)?.data))
                    val pairs = HashMap<String, Int>()
                    for (p in 0..3) for (x in 0..63) for (z in 0..63) {
                        val s = src[p][x][z]; val t = map.tiles[p][x][z]
                        if (s.overlayId > 0) pairs.merge("o ${s.overlayId - 1} -> ${(t.overlayId and 255) - 1}", 1, Int::plus)
                        if (s.underlayId > 0) pairs.merge("u ${s.underlayId - 1} -> ${(t.underlayId and 255) - 1}", 1, Int::plus)
                    }
                    pairs.forEach { (k, n) ->
                        val (kind, a, _, b) = k.split(" ")
                        val desc = if (kind == "o") {
                            val o = over[a.toInt()]?.let { ModernFloorDefs.decodeOverlay(it) }; val l = localO[b.toInt()]
                            "osrs rgb=%06x tex=%d hide=%s | ons rgb=%06x tex=%d".format(o?.rgb ?: -1, o?.texture ?: -2, o?.hideUnderlay, l?.rgb ?: -1, l?.texture ?: -2)
                        } else {
                            val u = under[a.toInt()]?.let { ModernFloorDefs.decodeUnderlay(it) }; val l = localU[b.toInt()]
                            "osrs rgb=%06x | ons rgb=%06x tex=%d".format(u?.rgb ?: -1, l?.rgb ?: -1, l?.texture ?: -2)
                        }
                        println("sq $square $kind $a -> $b tiles $n $desc")
                    }
                }
            }
            lib.close(); return
        }
        if (args[0] == "census") { // TileFloorProbeTool census <sq> [sq...]: underlay id -> tiles (all planes) + OSRS rgb
            val keys = ModernRegionProbeTool.loadKeys(File(FeroxImportTool.MODERN_KEYS))
            ModernCacheReader(File(FeroxImportTool.MODERN_CACHE)).use { reader ->
                val under = reader.files(2, 1)
                args.drop(1).map { it.toInt() }.forEach { sq ->
                    val region = ModernRegion.load(reader, sq, keys[sq])
                    val counts = sortedMapOf<Int, Int>()
                    val ocounts = sortedMapOf<Int, Int>()
                    for (p in 0 until 4) for (x in 0 until 64) for (z in 0 until 64) {
                        val t = region.tiles[p][x][z]
                        if (t.underlayId > 0) counts.merge(t.underlayId - 1, 1, Int::plus)
                        if (t.overlayId > 0) ocounts.merge(t.overlayId - 1, 1, Int::plus)
                    }
                    counts.forEach { (id, n) -> println("sq $sq underlay $id tiles $n rgb=%06x".format(under[id]?.let { ModernFloorDefs.decodeUnderlay(it).rgb } ?: -1)) }
                    val over = reader.files(2, 4)
                    ocounts.forEach { (id, n) -> val d = over[id]?.let { ModernFloorDefs.decodeOverlay(it) }
                        println("sq $sq overlay $id tiles $n rgb=%06x tex=%d hide=%s".format(d?.rgb ?: -1, d?.texture ?: -2, d?.hideUnderlay)) }
                }
            }
            return
        }
        val sq = args[0].toInt()
        val (x0, x1, z0, z1) = args.drop(1).take(4).map { it.toInt() }
        val planes = args.getOrNull(5)?.toInt()?.let { listOf(it) } ?: (0 until 4).toList()
        val keys = ModernRegionProbeTool.loadKeys(File(FeroxImportTool.MODERN_KEYS))
        ModernCacheReader(File(FeroxImportTool.MODERN_CACHE)).use { reader ->
            val region = ModernRegion.load(reader, sq, keys[sq])
            for (plane in planes) {
                println("plane $plane  (settings:overlay/shape:underlay; overlay/underlay as stored, 0 = none)")
                for (z in z1 downTo z0) {
                    val row = (x0..x1).joinToString(" ") { x ->
                        val t = region.tiles[plane][x][z]
                        "%x:%d/%d:%d".format(t.settings, t.overlayId, t.overlayPath, t.underlayId)
                    }
                    println("  z$z $row")
                }
            }
        }
    }
}
