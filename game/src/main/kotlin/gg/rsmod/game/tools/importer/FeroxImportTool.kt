package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

/**
 * Cross-revision world import of the Ferox Enclave footprint from the pinned modern OSRS cache
 * (OpenRS2 2499, build 236) into the two rev-667 production caches, through the A7
 * [CacheTransaction] path.
 *
 * What it does, per map square touched by the footprint box:
 *  1. decodes the modern tiles/locs (ModernRegion) and the current rev-667 tiles/locs
 *     (Rev667TileCodec / Rev667LocCodec, both proven byte-identical on real regions);
 *  2. replaces every tile inside the box on all four planes (height, flags, overlay shape/rotation
 *     verbatim; underlay/overlay ids remapped to the nearest-colour rev-667 floor definition);
 *  3. removes every rev-667 loc inside the box and adds the modern ones, each modern loc id being
 *     either REUSED (a rev-667 LocType with the same id, name and footprint already exists) or
 *     IMPORTED as a new LocType (ids continue contiguously above the current maximum) whose models
 *     are converted with the A5 codec and allocated from the census-proven free holes of archive 7;
 *  4. rewrites the loc group through the transaction with the region's existing XTEA key, so the
 *     server's `data/xteas/xteas.json` and the client keys stay unchanged.
 *
 * `plan` never writes a cache: it prints the classification, the preflight and the drop list.
 * `apply` runs the transaction against both targets, verifies, then updates xteas + mapping files.
 *
 * Usage: `./gradlew :game:runFeroxImportTool --args="plan|apply"`
 */
object FeroxImportTool {
    const val MODERN_CACHE = "C:/RSPS/import-source/openrs2-2499/cache"
    const val MODERN_KEYS = "C:/RSPS/import-source/openrs2-2499-keys.json"
    const val GAME_CACHE = "C:/RSPS/game/game/data/cache"
    const val FILE_SERVER_CACHE = "C:/RSPS/file-server/cache"
    const val XTEAS_FILE = "C:/RSPS/game/game/data/xteas/xteas.json"
    const val ASSET_MAP = "C:/RSPS/RSPS_IMPORT_ASSET_MAP.yml"
    const val OUT_DIR = "C:/RSPS/import-source/ferox"
    val REGIONS = listOf(12344, 12600)

    /** Footprint (inclusive absolute tiles): Ferox walls span x 3123..3187, z 3603..3646 on planes 0-3. */
    const val MIN_X = 3122
    const val MIN_Z = 3602
    const val MAX_X = 3188
    const val MAX_Z = 3647

    const val MODEL_INDEX = 7
    const val MAP_INDEX = 5
    const val LOC_INDEX = 16

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        File(OUT_DIR).mkdirs()
        val report = StringBuilder()
        fun log(s: String) {
            println(s)
            report.appendLine(s)
        }

        val plan = buildPlan(::log)
        val tx = CacheTransaction(listOf(GAME_CACHE, FILE_SERVER_CACHE), plan.mutations)
        val preflight = tx.preflight()
        val outcomes = preflight.groupBy { it.outcome }.mapValues { it.value.size }
        log("PREFLIGHT outcomes=$outcomes")
        preflight.filter { it.outcome == MutationOutcome.CONFLICT || it.outcome == MutationOutcome.MISSING_FOR_REMOVE }
            .forEach { log("  $it") }
        val blocking = tx.blockingErrors(preflight)
        blocking.forEach { log("BLOCKING: $it") }
        File(OUT_DIR, "ferox_plan_${tx.id}.txt").writeText(report.toString() + preflight.joinToString("\n"))
        if (mode == "plan") {
            log("PLAN_ONLY transaction=${tx.id} mutations=${plan.mutations.size} (nothing written)")
            return
        }
        check(blocking.isEmpty()) { "Preflight has blocking errors; refusing to apply." }
        val result = tx.apply(preflight)
        log("APPLIED transaction=${tx.id} applied=${result.applied} skipped=${result.skipped}")
        val verify = tx.verify()
        verify.forEach { log("VERIFY_ERROR: $it") }
        check(verify.isEmpty()) { "Post-apply verification failed; see journal ${tx.id} for rollback." }
        log("VERIFY_OK both targets hold intended bytes")
        appendAssetMap(plan, tx.id, ::log)
        File(OUT_DIR, "ferox_apply_${tx.id}.txt").writeText(report.toString())
    }

    class Plan(
        val mutations: List<CacheMutation>,
        val locIdMap: Map<Int, Int>,
        val modelIdMap: Map<Int, Int>,
        val reusedLocs: List<Int>,
        val dropped: List<String>,
    )

    fun buildPlan(log: (String) -> Unit): Plan {
        val keys = ModernRegionProbeTool.loadKeys(File(MODERN_KEYS))
        val inBox = { x: Int, z: Int -> x in MIN_X..MAX_X && z in MIN_Z..MAX_Z }
        val mutations = ArrayList<CacheMutation>()
        val dropped = ArrayList<String>()

        ModernCacheReader(File(MODERN_CACHE)).use { modern ->
            val library = CacheLibrary(GAME_CACHE)
            try {
                // ---- floors ----
                val modernUnderlays = modern.files(2, 1).mapValues { ModernFloorDefs.decodeUnderlay(it.value) }
                val modernOverlays = modern.files(2, 4).mapValues { ModernFloorDefs.decodeOverlay(it.value) }
                val localUnderlays = decodeLocalFloors(library, Rev667RegionProbeTool.UNDERLAY_GROUP) { Rev667FloorCodec.decodeUnderlay(it).rgb }
                val localOverlays = decodeLocalFloors(library, Rev667RegionProbeTool.OVERLAY_GROUP) { Rev667FloorCodec.decodeOverlay(it) }
                val underlayMap = HashMap<Int, Int>()
                val overlayMap = HashMap<Int, Int>()
                fun mapUnderlay(tileValue: Int): Int =
                    underlayMap.getOrPut(tileValue) {
                        val rgb = modernUnderlays[tileValue - 1]?.rgb ?: error("modern underlay ${tileValue - 1} missing")
                        val best = localUnderlays.minByOrNull { Rev667FloorCodec.colourDistance(it.value, rgb) }!!
                        log("FLOOR underlay modern=${tileValue - 1} rgb=${"%06x".format(rgb)} -> local=${best.key} rgb=${"%06x".format(best.value)}")
                        best.key + 1
                    }
                fun mapOverlay(tileValue: Int): Int =
                    overlayMap.getOrPut(tileValue) {
                        val def = modernOverlays[tileValue - 1] ?: error("modern overlay ${tileValue - 1} missing")
                        val best = bestOverlay(def, localOverlays)
                        log(
                            "FLOOR overlay modern=${tileValue - 1} rgb=${"%06x".format(def.rgb)} tex=${def.texture} -> local=$best " +
                                "rgb=${"%06x".format(localOverlays.getValue(best).rgb)} secondary=${def.secondaryRgb} blend=${localOverlays.getValue(best).blendRgb}",
                        )
                        best + 1
                    }

                // ---- loc types ----
                val modernLocDefs = modern.files(2, 6)
                val localTypes = Rev667RegionProbeTool.locTypes(library)
                var nextLocId = (localTypes.keys.maxOrNull() ?: -1) + 1
                val locIdMap = LinkedHashMap<Int, Int>()
                val reused = ArrayList<Int>()
                val modelIdMap = LinkedHashMap<Int, Int>()
                val neededModernModels = sortedSetOf<Int>()
                val importedDefs = LinkedHashMap<Int, ModernObjectDef>()

                val regionData = REGIONS.map { regionId ->
                    val modernRegion = ModernRegion.load(modern, regionId, keys[regionId] ?: error("No modern key for $regionId"))
                    val rx = regionId shr 8
                    val rz = regionId and 0xFF
                    val boxLocs = modernRegion.locs.filter { inBox(rx * 64 + it.localX, rz * 64 + it.localZ) }
                    boxLocs.map { it.id }.distinct().sorted().forEach { id ->
                        if (locIdMap.containsKey(id)) return@forEach
                        val def = ModernObjectDef.decode(id, modernLocDefs[id] ?: error("modern loc $id missing"))
                        val local = localTypes[id]
                        val sameFootprint = local != null && local.name == def.name && local.sizeX == def.sizeX && local.sizeZ == def.sizeY && def.name != "null"
                        if (sameFootprint) {
                            locIdMap[id] = id
                            reused.add(id)
                            log("LOC REUSE_AS_IS id=$id '${def.name}' (rev-667 has same name/footprint; models ${local!!.allModels})")
                        } else {
                            locIdMap[id] = nextLocId++
                            importedDefs[id] = def
                            neededModernModels.addAll(def.models)
                            log("LOC IMPORT modern=$id '${def.name}' -> local=${locIdMap[id]} models=${def.models} types=${def.modelTypes} anim=${def.animationId}")
                        }
                    }
                    Triple(regionId, modernRegion, boxLocs)
                }

                // ---- model allocation from census-proven free holes ----
                val census = ModelNamespaceCensusTool.census(GAME_CACHE, FILE_SERVER_CACHE, File(ASSET_MAP))
                log("CENSUS proven_free_holes=${census.provenFreeHoles.size} safe_free=${census.safeFreeCount} referenced_but_missing=${census.referencedButMissing.size}")
                val free = census.provenFreeHoles.iterator()
                neededModernModels.forEach { modernId ->
                    check(free.hasNext()) { "Out of proven-free model ids." }
                    val localId = free.next()
                    val bytes = modern.file(MODEL_INDEX, modernId, 0) ?: error("modern model $modernId missing")
                    val raw = ModernModelDecoder.decode(bytes)
                    val texFaces = raw.faceTexture?.count { it.toInt() != -1 } ?: 0
                    val decoded =
                        if (texFaces == 0 && raw.texSpaceCount == 0) {
                            raw
                        } else {
                            // Modern texture ids index a different texture table; a rev-667 client crashes on an
                            // out-of-range id, so textured faces are flattened to the texture's own average HSL
                            // colour (the first u16 of the modern texture definition, archive 9 group 0).
                            val used = raw.faceTexture!!.filter { it.toInt() != -1 }.distinct()
                            dropped.add("model $modernId: $texFaces textured faces flattened to average colour of modern textures $used")
                            stripTextures(raw) { textureId ->
                                val def = modern.file(9, 0, textureId) ?: error("modern texture $textureId missing")
                                ((def[0].toInt() and 0xFF) shl 8) or (def[1].toInt() and 0xFF)
                            }
                        }
                    val converted = Rev667ModelEncoder.encode(decoded)
                    val roundTrip = Rev667ModelDecoder.decode(converted)
                    check(roundTrip.vertexCount == decoded.vertexCount && roundTrip.faceCount == decoded.faceCount) { "model $modernId conversion lost geometry" }
                    modelIdMap[modernId] = localId
                    mutations += CacheMutation(MODEL_INDEX, localId, 0, converted, "ferox model modern=$modernId -> local=$localId (${decoded.vertexCount}v/${decoded.faceCount}f)")
                }
                log("MODELS imported=${modelIdMap.size} (first=${modelIdMap.values.minOrNull()} last=${modelIdMap.values.maxOrNull()})")

                // ---- loc type definitions ----
                importedDefs.forEach { (modernId, def) ->
                    val localId = locIdMap.getValue(modernId)
                    val bytes = Rev667LocType.encodeFromModern(def, { modelIdMap.getValue(it) }, dropped)
                    val check = Rev667LocType.decode(localId, bytes)
                    check(check.allModels == def.models.map { modelIdMap.getValue(it) }) { "loc $modernId encode/decode model mismatch" }
                    mutations += CacheMutation(LOC_INDEX, localId ushr 8, localId and 0xFF, bytes, "ferox loc modern=$modernId '${def.name}' -> local=$localId")
                }

                // ---- regions ----
                regionData.forEach { (regionId, modernRegion, boxLocs) ->
                    val rx = regionId shr 8
                    val rz = regionId and 0xFF
                    val mapName = "m${rx}_$rz"
                    val locName = "l${rx}_$rz"
                    val localKeys = Rev667RegionProbeTool.loadKeys(File(XTEAS_FILE))
                    val mapArchive = library.index(MAP_INDEX).archive(mapName) ?: error("local $mapName missing")
                    val locArchive = library.index(MAP_INDEX).archive(locName, localKeys[regionId]) ?: error("local $locName missing")
                    val mapBytes = mapArchive.file(0)!!.data!!
                    val locBytes = locArchive.file(0)!!.data!!
                    val tiles = Rev667TileCodec.decode(mapBytes)
                    check(Rev667TileCodec.encode(tiles).contentEquals(mapBytes)) { "$mapName tile round-trip failed" }
                    val locs = Rev667LocCodec.decode(locBytes)
                    check(Rev667LocCodec.encode(locs).contentEquals(locBytes)) { "$locName loc round-trip failed" }

                    var replacedTiles = 0
                    for (plane in 0 until 4) for (lx in 0 until 64) for (lz in 0 until 64) {
                        if (!inBox(rx * 64 + lx, rz * 64 + lz)) continue
                        val src = modernRegion.tiles[plane][lx][lz]
                        val dst = tiles.tiles[plane][lx][lz]
                        dst.height = src.height
                        dst.flags = src.settings
                        dst.overlayShape = src.overlayPath
                        dst.overlayRotation = src.overlayRotation
                        dst.overlayId = if (src.overlayId != 0) mapOverlay(src.overlayId) else 0
                        dst.underlayId = if (src.underlayId != 0) mapUnderlay(src.underlayId) else 0
                        replacedTiles++
                    }
                    val removed = locs.filter { inBox(rx * 64 + it.localX, rz * 64 + it.localZ) }
                    val kept = locs - removed.toSet()
                    val added = boxLocs.map { Rev667Loc(locIdMap.getValue(it.id), it.localX, it.localZ, it.plane, it.type, it.rotation) }
                    val newLocs = kept + added
                    val newLocBytes = Rev667LocCodec.encode(newLocs)
                    check(Rev667LocCodec.decode(newLocBytes).size == newLocs.size)
                    val newMapBytes = Rev667TileCodec.encode(tiles)
                    log("REGION $regionId tilesReplaced=$replacedTiles locsRemoved=${removed.size} locsKept=${kept.size} locsAdded=${added.size} map ${mapBytes.size}->${newMapBytes.size}b locs ${locBytes.size}->${newLocBytes.size}b")
                    mutations += CacheMutation(MAP_INDEX, mapArchive.id, 0, newMapBytes, "ferox tiles $mapName", CacheItemProbeTool.sha1(mapBytes))
                    mutations += CacheMutation(MAP_INDEX, locArchive.id, 0, newLocBytes, "ferox locs $locName (re-encrypted with existing key)", CacheItemProbeTool.sha1(locBytes), xtea = localKeys[regionId])
                }
                dropped.forEach { log("DROPPED $it") }
                log("PLAN locs reused=${reused.size} imported=${importedDefs.size} models=${modelIdMap.size} mutations=${mutations.size}")
                return Plan(mutations, locIdMap, modelIdMap, reused, dropped)
            } finally {
                library.close()
            }
        }
    }

    fun <T> decodeLocalFloors(
        library: CacheLibrary,
        group: Int,
        decode: (ByteArray) -> T,
    ): Map<Int, T> {
        val archive = library.index(Rev667RegionProbeTool.CONFIG_INDEX).archive(group) ?: error("No floor group $group")
        val result = HashMap<Int, T>()
        for (id in archive.fileIds()) {
            val data = archive.file(id)?.data ?: continue
            result[id] = decode(data)
        }
        return result
    }

    private const val FLAG_PENALTY = 3L * 255 * 255 + 1
    private const val MINIMAP_PENALTY = 2 * FLAG_PENALTY

    /**
     * How differently local overlay [local] shows modern overlay [modern]. The minimap draws the op7 colour first
     * (client `Static718.blendColour`; OSRS secondary colour), so losing or gaining it costs most; then the op5
     * no-occlude flag; then colour distances. Textures are ignored: the two caches number textures differently.
     * The original main-colour-only match sent every ff00ff overlay to local 41, blanking Ferox paths on the minimap.
     */
    fun overlayScore(
        modern: ModernOverlayDef,
        local: Rev667OverlayDef,
    ): Long {
        val minimap =
            when {
                modern.secondaryRgb == -1 && local.blendRgb == -1 -> 0L
                modern.secondaryRgb == -1 || local.blendRgb == -1 -> MINIMAP_PENALTY
                else -> Rev667FloorCodec.colourDistance(modern.secondaryRgb, local.blendRgb).toLong()
            }
        val flag = if (modern.hideUnderlay != local.occludes) FLAG_PENALTY else 0L
        return minimap + flag + Rev667FloorCodec.colourDistance(modern.rgb, local.rgb)
    }

    /** Lowest [overlayScore], ties to the lowest local id. */
    fun bestOverlay(
        modern: ModernOverlayDef,
        locals: Map<Int, Rev667OverlayDef>,
    ): Int = locals.entries.minWithOrNull(compareBy({ overlayScore(modern, it.value) }, { it.key }))!!.key

    private fun zeroXteaKeys(
        regions: List<Int>,
        log: (String) -> Unit,
    ) {
        val file = File(XTEAS_FILE)
        val root = JsonParser().parse(file.readText()).asJsonArray
        val seen = HashSet<Int>()
        root.forEach { element ->
            val obj = element.asJsonObject
            val mapsquare = obj.get("mapsquare").asInt
            if (mapsquare in regions) {
                obj.add("key", JsonArray().apply { repeat(4) { add(0) } })
                seen.add(mapsquare)
            }
        }
        regions.filter { it !in seen }.forEach { region ->
            root.add(JsonObject().apply {
                addProperty("mapsquare", region)
                add("key", JsonArray().apply { repeat(4) { add(0) } })
            })
        }
        file.writeText(GsonBuilder().setPrettyPrinting().create().toJson(root))
        log("XTEAS zeroed keys for $regions in $XTEAS_FILE")
    }

    private fun appendAssetMap(
        plan: Plan,
        transactionId: String,
        log: (String) -> Unit,
    ) {
        val sb = StringBuilder()
        sb.appendLine("  - name: Ferox Enclave world import")
        sb.appendLine("    transaction_id: $transactionId")
        sb.appendLine("    source: OpenRS2 cache 2499 (oldschool build 236, 2026-03-18), regions 12344/12600, box x$MIN_X..$MAX_X z$MIN_Z..$MAX_Z")
        sb.appendLine("    loc_ids:")
        plan.locIdMap.forEach { (modern, local) -> sb.appendLine("      - { upstream_id: $modern, local_id: $local, mode: ${if (modern == local) "REUSE_AS_IS" else "IMPORT"} }") }
        sb.appendLine("    models:")
        plan.modelIdMap.forEach { (modern, local) ->
            sb.appendLine("      - role: ferox_loc_model")
            sb.appendLine("        upstream_id: $modern")
            sb.appendLine("        local_id: $local")
        }
        File(ASSET_MAP).appendText(sb.toString())
        log("ASSET_MAP appended ${plan.modelIdMap.size} model mappings and ${plan.locIdMap.size} loc mappings")
    }
}

/** Copy of [source] with every textured face flattened to `averageHsl(textureId)` and no texture spaces. */
fun stripTextures(
    source: ModelData,
    averageHsl: (Int) -> Int,
): ModelData {
    val out = ModelData(source.vertexCount, source.faceCount, 0)
    source.vertexX.copyInto(out.vertexX)
    source.vertexY.copyInto(out.vertexY)
    source.vertexZ.copyInto(out.vertexZ)
    source.faceA.copyInto(out.faceA)
    source.faceB.copyInto(out.faceB)
    source.faceC.copyInto(out.faceC)
    source.faceColour.copyInto(out.faceColour)
    source.faceTexture?.forEachIndexed { i, tex -> if (tex.toInt() != -1) out.faceColour[i] = averageHsl(tex.toInt() and 0xFFFF).toShort() }
    out.shadingType = source.shadingType?.copyOf()
    out.facePriority = source.facePriority?.copyOf()
    out.globalPriority = source.globalPriority
    out.faceAlpha = source.faceAlpha?.copyOf()
    out.faceLabel = source.faceLabel?.copyOf()
    out.vertexLabel = source.vertexLabel?.copyOf()
    out.droppedAnimayaSkinning = source.droppedAnimayaSkinning
    out.droppedFaceZOffsets = source.droppedFaceZOffsets
    return out
}
