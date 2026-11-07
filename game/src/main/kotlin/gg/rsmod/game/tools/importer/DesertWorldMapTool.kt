package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * N462 (owner 2026-11-07 17:5x, world map next to the OSRS world map: 'zo hoort die eruit te zien in de woestijn de wereldkaart'):
 * around the Kharidian Desert the rev-667 world-map snapshot (index 23 main/area, see [FeroxWorldMapTool]) has NO record for the
 * squares OSRS added later (the sea east of Nardah and south of Menaphos, the Menaphos port, the Necropolis coast) - the map drew
 * black blocks there. This tool draws those squares from the OSRS map squares themselves, WORLD MAP ONLY (no landscape, no
 * collision, nothing walkable changes):
 *  - floors: OSRS underlay -> nearest-colour local underlay, OSRS overlay -> [FeroxImportTool.bestOverlay] (water 6 -> 6, like the
 *    region imports), OSRS "hidden" overlays left out;
 *  - walls (types 0, 2, 3, 9): drawn with a plain local wall, or a local door when the OSRS loc has an 'Open' option;
 *  - map scenes (trees, rocks ...): a local loc with the same picture ([OsrsMapIconTable.SCENE_TO_MSI]); no map icons (content that
 *    is not in our game gets no icon).
 * [OVERRIDE] squares have a record but show rev-667 filler where OSRS has other content (rebuilt the same way).
 *
 * Usage: java -cp ... gg.rsmod.game.tools.importer.DesertWorldMapTool plan|apply
 */
object DesertWorldMapTool {
    /** The desert box on the world map (squares). */
    val XS = 46..58
    val ZS = 38..48

    /** Squares with a rev-667 record that OSRS draws differently (filled in after the plan run). */
    val OVERRIDE = emptySet<Pair<Int, Int>>()

    /** The rev-667 sea overlay (local 111: rgb 60769a, texture 669) the snapshot's sea squares use. */
    const val SEA_OVERLAY = 111

    /** Untextured OSRS overlays the OSRS map draws as water (150 rgb 7296b7: the shallow sea round the Necropolis). */
    val SEA_PLAIN = setOf(150)

    /**
     * Squares already imported from OSRS (Necropolis, [NecropolisWorldMapTool.SQUARES]): their record keeps OUR landscape (real locs),
     * only the tiles OSRS draws as sea get the rev-667 sea overlay (the region import gave the OSRS sea a green rev-667 overlay).
     */
    val SEA_FIX = NecropolisWorldMapTool.SQUARES.toSet()

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val mutations = ArrayList<CacheMutation>()
        val modernKeys = ModernRegionProbeTool.loadKeys(File(FeroxImportTool.MODERN_KEYS))
        ModernCacheReader(File(FeroxImportTool.MODERN_CACHE)).use { modern ->
            val library = CacheLibrary(FeroxImportTool.GAME_CACHE)
            try {
                val def = FeroxWorldMapTool.objectDefs(library)
                val worldMap = library.index(FeroxWorldMapTool.WORLD_MAP_INDEX)
                val mainGroup = worldMap.archive("main") ?: error("world map group main missing")
                val areaFile = mainGroup.file("area") ?: error("main/area missing")
                val current = library.data(FeroxWorldMapTool.WORLD_MAP_INDEX, mainGroup.id, areaFile.id) ?: error("main/area unreadable")
                val area = FeroxWorldMapTool.parse(current)
                val have = area.records.map { it.x to it.z }.toSet()

                val modernUnderlays = modern.files(2, 1).mapValues { ModernFloorDefs.decodeUnderlay(it.value) }
                val modernOverlays = modern.files(2, 4).mapValues { ModernFloorDefs.decodeOverlay(it.value) }
                val modernLocDefs = modern.files(2, 6)
                val localUnderlays = FeroxImportTool.decodeLocalFloors(library, Rev667RegionProbeTool.UNDERLAY_GROUP) { Rev667FloorCodec.decodeUnderlay(it).rgb }
                val localOverlays = FeroxImportTool.decodeLocalFloors(library, Rev667RegionProbeTool.OVERLAY_GROUP) { Rev667FloorCodec.decodeOverlay(it) }
                val underlayMap = HashMap<Int, Int>()
                val overlayMap = HashMap<Int, Int>()
                fun mapUnderlay(v: Int): Int = underlayMap.getOrPut(v) {
                    val rgb = modernUnderlays[v - 1]?.rgb ?: return@getOrPut 0
                    localUnderlays.minByOrNull { Rev667FloorCodec.colourDistance(it.value, rgb) }!!.key + 1
                }
                fun mapOverlay(v: Int): Int = overlayMap.getOrPut(v) {
                    val d = modernOverlays[v - 1] ?: return@getOrPut 0
                    // OSRS sea/water (texture 1 = water, 130..149 = the newer sea textures) -> the rev-667 sea overlay of the
                    // snapshot's own sea squares (47_44, 48_45, 56_44: all overlay 111, rgb 60769a, texture 669)
                    if (d.texture == 1 || d.texture in 130..149 || v - 1 in SEA_PLAIN) return@getOrPut SEA_OVERLAY + 1
                    if (d.rgb == 0xFF00FF && d.texture == -1) return@getOrPut 0
                    val cands = if (d.texture == -1) localOverlays.filterValues { it.texture == -1 }.ifEmpty { localOverlays } else localOverlays
                    FeroxImportTool.bestOverlay(d, cands) + 1
                }

                // local stand-ins: plain wall / door from Sophanem (51_43), and one loc per map scene
                val (sophTiles, sophLocs) = FeroxWorldMapTool.landscape(library, Rev667RegionProbeTool.loadKeys(File(FeroxImportTool.XTEAS_FILE)), 51, 43)
                check(sophTiles.tiles.isNotEmpty())
                val walls = sophLocs.filter { it.type == 0 && it.plane == 0 }.groupingBy { it.id }.eachCount().entries.sortedByDescending { it.value }
                val plainWall = walls.first { e -> def(e.key)?.let { d -> d.options.all { it.isNullOrEmpty() || it == "null" } && d.mapScene == -1 && d.mapElement == -1 } == true }.key
                val doorWall = walls.firstOrNull { e -> def(e.key)?.options?.any { it == "Open" } == true }?.key ?: plainWall
                println("STAND-IN wall=$plainWall door=$doorWall")
                val sceneLoc = HashMap<Int, Int>()
                val wanted = OsrsMapIconTable.SCENE_TO_MSI.values.toSet()
                val locIndex = library.index(FeroxWorldMapTool.LOC_INDEX)
                for (group in locIndex.archiveIds()) {
                    for (file in 0 until 256) {
                        val id = (group shl 8) or file
                        val d = def(id) ?: continue
                        if (d.mapScene in wanted && d.mapElement == -1 && d.mapScene !in sceneLoc) sceneLoc[d.mapScene] = id
                    }
                    if (sceneLoc.size == wanted.size) break
                }
                println("STAND-IN scenes ${sceneLoc.toSortedMap()}")

                val modernDefCache = HashMap<Int, ModernObjectDef?>()
                fun modernDef(id: Int) = modernDefCache.getOrPut(id) { modernLocDefs[id]?.let { ModernObjectDef.decode(id, it) } }

                val out = ByteArrayOutputStream(current.size + 65536)
                var copied = 0
                var appended = 0
                var rebuilt = 0
                val records = ArrayList<ByteArray>()
                val preview = HashMap<Pair<Int, Int>, Pair<Rev667TileMap, List<Rev667Loc>>>()
                for (x in XS) for (z in ZS) {
                    val sq = x to z
                    if (sq in have && sq !in OVERRIDE && sq !in SEA_FIX) continue
                    val region = (x shl 8) or z
                    val osrs = runCatching { ModernRegion.load(modern, region, modernKeys[region]) }.getOrElse {
                        println("square ${x}_$z: not in OSRS either (${it.message?.take(60)})"); null
                    } ?: continue
                    val tiles = Rev667TileMap()
                    var land = 0
                    for (lx in 0 until 64) for (lz in 0 until 64) {
                        val src = osrs.tiles[0][lx][lz]
                        // bridges: OSRS plane 1 with flag 0x2 is drawn as ground level
                        val s = if ((osrs.tiles[1][lx][lz].settings and 2) != 0) osrs.tiles[1][lx][lz] else src
                        val dst = tiles.tiles[0][lx][lz]
                        dst.overlayShape = s.overlayPath
                        dst.overlayRotation = s.overlayRotation
                        dst.overlayId = if (s.overlayId != 0) mapOverlay(s.overlayId) else 0
                        dst.underlayId = if (s.underlayId != 0) mapUnderlay(s.underlayId) else 0
                        if (dst.overlayId != SEA_OVERLAY + 1 && (dst.overlayId != 0 || dst.underlayId != 0)) land++
                    }
                    if (sq in SEA_FIX) {
                        val (own, ownLocs) = FeroxWorldMapTool.landscape(library, Rev667RegionProbeTool.loadKeys(File(FeroxImportTool.XTEAS_FILE)), x, z)
                        var sea = 0
                        for (lx in 0 until 64) for (lz in 0 until 64) {
                            // the map colours of the OSRS square (same floor mapping as the new squares); the locs stay ours
                            val o = own.tiles[0][lx][lz]
                            val t = tiles.tiles[0][lx][lz]
                            // OSRS floor we cannot map (unknown def): keep our tile; OSRS with no floor at all (the pyramid) stays black like OSRS
                            val bridged = if ((osrs.tiles[1][lx][lz].settings and 2) != 0) osrs.tiles[1][lx][lz] else osrs.tiles[0][lx][lz]
                            if (t.overlayId == 0 && t.underlayId == 0 && (bridged.underlayId != 0 || bridged.overlayId != 0)) continue
                            o.overlayId = t.overlayId; o.overlayShape = t.overlayShape; o.overlayRotation = t.overlayRotation; o.underlayId = t.underlayId
                            if (t.overlayId == SEA_OVERLAY + 1) sea++
                        }
                        preview[sq] = own to ownLocs
                        records += FeroxWorldMapTool.encodeSquare(x, z, own, ownLocs, area, def)
                        println("square ${x}_$z (SEA_FIX): $sea sea tiles, own locs=${ownLocs.size}")
                        rebuilt++
                        continue
                    }
                    val locs = ArrayList<Rev667Loc>()
                    var noKey = modernKeys[region] == null
                    osrs.locs.filter { it.plane == 0 || (it.plane == 1 && (osrs.tiles[1][it.localX][it.localZ].settings and 2) != 0) }.forEach { l ->
                        val md = modernDef(l.id) ?: return@forEach
                        val id = when (l.type) {
                            0, 2, 3, 9 -> if (md.options.any { it == "Open" }) doorWall else plainWall
                            1 -> return@forEach
                            else -> OsrsMapIconTable.SCENE_TO_MSI[md.mapSceneId]?.let { sceneLoc[it] } ?: return@forEach
                        }
                        locs += Rev667Loc(id, l.localX, l.localZ, 0, l.type, l.rotation)
                    }
                    preview[sq] = tiles to locs
                    val bytes = FeroxWorldMapTool.encodeSquare(x, z, tiles, locs, area, def)
                    println("square ${x}_$z (${if (sq in have) "OVERRIDE" else "NEW"}): land tiles=$land locs=${locs.size}${if (noKey) " (no OSRS loc key)" else ""} -> ${bytes.size} bytes")
                    if (sq in have) {
                        val r = area.records.first { it.x == x && it.z == z }
                        check(r.square) { "square ${x}_$z is stored as chunks" }
                        records += bytes // placed below in order
                        rebuilt++
                    } else {
                        records += bytes
                        appended++
                    }
                }
                // overrides replace their record in place; new squares are appended
                val overrideBytes = HashMap<Pair<Int, Int>, ByteArray>()
                records.forEach { b -> val k = (b[1].toInt() and 0xFF) to (b[2].toInt() and 0xFF); if (k in have) overrideBytes[k] = b }
                area.records.filter { (it.x to it.z) in overrideBytes }.sortedBy { it.start }.forEach { r ->
                    out.write(current, copied, r.start - copied)
                    out.write(overrideBytes.getValue(r.x to r.z))
                    copied = r.end
                }
                out.write(current, copied, current.size - copied)
                records.filter { b -> ((b[1].toInt() and 0xFF) to (b[2].toInt() and 0xFF)) !in have }.forEach { out.write(it) }
                val updated = out.toByteArray()
                check(FeroxWorldMapTool.parse(updated).records.size == area.records.size + appended) { "record count" }
                println("FLOORS underlays=${underlayMap.size} overlays=${overlayMap.toSortedMap()}")
                overlayMap.toSortedMap().forEach { (v, l) ->
                    val m = modernOverlays[v - 1]
                    println("  OVERLAY osrs ${v - 1} rgb=${m?.rgb?.let { "%06x".format(it) }} tex=${m?.texture} -> local ${l - 1}")
                }
                underlayMap.toSortedMap().forEach { (v, l) ->
                    println("  UNDERLAY osrs ${v - 1} rgb=${modernUnderlays[v - 1]?.rgb?.let { "%06x".format(it) }} -> local ${l - 1} rgb=${localUnderlays[l - 1]?.let { "%06x".format(it) }}")
                }
                // preview PNG (4 px per tile): overlay colour, else underlay colour, walls white - rev-667 squares from their landscape
                val localKeys = Rev667RegionProbeTool.loadKeys(File(FeroxImportTool.XTEAS_FILE))
                val img = java.awt.image.BufferedImage(XS.count() * 64 * 2, ZS.count() * 64 * 2, java.awt.image.BufferedImage.TYPE_INT_RGB)
                for (x in XS) for (z in ZS) {
                    val sq = x to z
                    val src = preview[sq] ?: if (sq in have) runCatching { FeroxWorldMapTool.landscape(library, localKeys, x, z) }.getOrNull() else null
                    src ?: continue
                    for (lx in 0 until 64) for (lz in 0 until 64) {
                        val t = src.first.tiles[0][lx][lz]
                        val o = t.overlayId and 0xFF
                        val rgb = when {
                            o != 0 -> localOverlays[o - 1]?.rgb ?: 0xFF00FF
                            t.underlayId != 0 -> localUnderlays[t.underlayId - 1] ?: 0xFF00FF
                            else -> 0
                        }
                        val px = ((x - XS.first) * 64 + lx) * 2
                        val pz = ((ZS.last - z) * 64 + 63 - lz) * 2
                        for (a in 0..1) for (b in 0..1) img.setRGB(px + a, pz + b, rgb)
                    }
                    src.second.filter { it.plane == 0 && it.type in setOf(0, 2, 3, 9) }.forEach { l ->
                        val px = ((x - XS.first) * 64 + l.localX) * 2
                        val pz = ((ZS.last - z) * 64 + 63 - l.localZ) * 2
                        img.setRGB(px, pz, 0xFFFFFF)
                    }
                }
                javax.imageio.ImageIO.write(img, "png", File("C:/RSPS/work/claude-scripts/kc-out/desert-preview.png"))
                println("NEW=$appended OVERRIDE=$rebuilt main/area ${current.size} -> ${updated.size} bytes")
                if (appended + rebuilt > 0) {
                    mutations += CacheMutation(FeroxWorldMapTool.WORLD_MAP_INDEX, mainGroup.id, areaFile.id, updated,
                        "N462 world map main/area: OSRS desert squares (world map only)", CacheItemProbeTool.sha1(current))
                }
            } finally {
                library.close()
            }
        }
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
