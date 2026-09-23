package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

/**
 * Owner answer Q10 (2026-09-14): import Watson's house (OSRS Wiki map 1645,3570 = map square 6455, "m25_55" / "l25_55") like the Ferox
 * import. The square does not exist in either rev-667 cache (probe 2026-09-14: index 5 named, 6831 groups, max id 6830, no m25_55 /
 * l25_55), so the WHOLE square is imported (no box to guess) as two NEW named groups through [CacheMutation.groupName]:
 *  - tiles: every tile of the OSRS square on all four planes (height, settings, overlay shape/rotation; floors mapped to the nearest rev-667
 *    floor definition exactly as [FeroxImportTool] does);
 *  - locs: every OSRS loc, REUSED where a rev-667 LocType with the same id, name and footprint exists, otherwise IMPORTED as a new LocType
 *    with converted models in census-proven free model holes;
 *  - the location group is written unencrypted and `data/xteas/xteas.json` gets a zero key for 6455 (the server returns zero keys for an
 *    unknown square anyway; the explicit entry keeps the missing-keys report clean).
 * FeroxImportTool itself is not changed (its import is live and proven); the floor / loc / model steps are the same calls.
 *
 * Usage: `./gradlew :game:runWatsonHouseImportTool --args="plan|apply"`
 */
object WatsonHouseImportTool {
    const val REGION = 6455
    const val MAP_NAME = "m25_55"
    const val LOC_NAME = "l25_55"

    class Plan(
        val mutations: List<CacheMutation>,
        val locIdMap: Map<Int, Int>,
        val modelIdMap: Map<Int, Int>,
        val reused: List<Int>,
        val dropped: List<String>,
        val mapGroup: Int,
        val locGroup: Int,
    )

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val plan = buildPlan()
        val tx = CacheTransaction(listOf(FeroxImportTool.GAME_CACHE, FeroxImportTool.FILE_SERVER_CACHE), plan.mutations)
        val preflight = tx.preflight()
        println("PREFLIGHT transaction=${tx.id} mutations=${plan.mutations.size} outcomes=${preflight.groupingBy { it.outcome }.eachCount()}")
        val blocking = tx.blockingErrors(preflight)
        blocking.forEach { println("BLOCKING: $it") }
        if (mode == "plan") {
            println("PLAN_ONLY transaction=${tx.id} (nothing written)")
            return
        }
        check(blocking.isEmpty()) { "Preflight has blocking errors; refusing to apply." }
        val result = tx.apply(preflight)
        println("APPLIED transaction=${tx.id} applied=${result.applied} skipped=${result.skipped}")
        val verify = tx.verify()
        verify.forEach { println("VERIFY_ERROR: $it") }
        check(verify.isEmpty()) { "Post-apply verification failed; see journal ${tx.id} for rollback." }
        println("VERIFY_OK both targets hold intended bytes and names")
        zeroXteaKey()
        appendAssetMap(plan, tx.id)
    }

    fun buildPlan(): Plan {
        val keys = ModernRegionProbeTool.loadKeys(File(FeroxImportTool.MODERN_KEYS))
        val mutations = ArrayList<CacheMutation>()
        val dropped = ArrayList<String>()
        ModernCacheReader(File(FeroxImportTool.MODERN_CACHE)).use { modern ->
            val library = CacheLibrary(FeroxImportTool.GAME_CACHE)
            try {
                val maps = library.index(FeroxImportTool.MAP_INDEX)
                check(maps.archive(MAP_NAME) == null && maps.archive(LOC_NAME) == null) { "$MAP_NAME / $LOC_NAME already exist; this tool only creates the square" }
                val nextGroup = (maps.archiveIds().maxOrNull() ?: -1) + 1
                val mapGroup = nextGroup
                val locGroup = nextGroup + 1

                // ---- floors (same mapping as FeroxImportTool) ----
                val modernUnderlays = modern.files(2, 1).mapValues { ModernFloorDefs.decodeUnderlay(it.value) }
                val modernOverlays = modern.files(2, 4).mapValues { ModernFloorDefs.decodeOverlay(it.value) }
                val localUnderlays = FeroxImportTool.decodeLocalFloors(library, Rev667RegionProbeTool.UNDERLAY_GROUP) { Rev667FloorCodec.decodeUnderlay(it).rgb }
                val localOverlays = FeroxImportTool.decodeLocalFloors(library, Rev667RegionProbeTool.OVERLAY_GROUP) { Rev667FloorCodec.decodeOverlay(it) }
                val underlayMap = HashMap<Int, Int>()
                val overlayMap = HashMap<Int, Int>()
                fun mapUnderlay(tileValue: Int): Int =
                    underlayMap.getOrPut(tileValue) {
                        val rgb = modernUnderlays[tileValue - 1]?.rgb ?: error("modern underlay ${tileValue - 1} missing")
                        localUnderlays.minByOrNull { Rev667FloorCodec.colourDistance(it.value, rgb) }!!.key + 1
                    }
                fun mapOverlay(tileValue: Int): Int =
                    overlayMap.getOrPut(tileValue) {
                        val def = modernOverlays[tileValue - 1] ?: error("modern overlay ${tileValue - 1} missing")
                        FeroxImportTool.bestOverlay(def, localOverlays) + 1
                    }

                // ---- loc types ----
                val modernRegion = ModernRegion.load(modern, REGION, keys[REGION] ?: error("No modern key for $REGION"))
                val modernLocDefs = modern.files(2, 6)
                val localTypes = Rev667RegionProbeTool.locTypes(library)
                var nextLocId = (localTypes.keys.maxOrNull() ?: -1) + 1
                val locIdMap = LinkedHashMap<Int, Int>()
                val reused = ArrayList<Int>()
                val neededModels = sortedSetOf<Int>()
                val importedDefs = LinkedHashMap<Int, ModernObjectDef>()
                modernRegion.locs.map { it.id }.distinct().sorted().forEach { id ->
                    val def = ModernObjectDef.decode(id, modernLocDefs[id] ?: error("modern loc $id missing"))
                    val local = localTypes[id]
                    if (local != null && local.name == def.name && local.sizeX == def.sizeX && local.sizeZ == def.sizeY && def.name != "null") {
                        locIdMap[id] = id
                        reused += id
                    } else {
                        locIdMap[id] = nextLocId++
                        importedDefs[id] = def
                        neededModels.addAll(def.models)
                    }
                }

                // ---- models ----
                val census = ModelNamespaceCensusTool.census(FeroxImportTool.GAME_CACHE, FeroxImportTool.FILE_SERVER_CACHE, File(FeroxImportTool.ASSET_MAP))
                check(census.physicalDivergentIds.isEmpty() && census.referencedDivergentIds.isEmpty()) { "target caches diverge" }
                val free = census.provenFreeHoles.iterator()
                val modelIdMap = LinkedHashMap<Int, Int>()
                neededModels.forEach { modernId ->
                    check(free.hasNext()) { "Out of proven-free model ids." }
                    val localId = free.next()
                    val raw = ModernModelDecoder.decode(modern.file(FeroxImportTool.MODEL_INDEX, modernId, 0) ?: error("modern model $modernId missing"))
                    val texFaces = raw.faceTexture?.count { it.toInt() != -1 } ?: 0
                    val decoded =
                        if (texFaces == 0 && raw.texSpaceCount == 0) {
                            raw
                        } else {
                            dropped += "model $modernId: $texFaces textured faces flattened to the modern textures' average colour"
                            stripTextures(raw) { tex ->
                                val t = modern.file(9, 0, tex) ?: error("modern texture $tex missing")
                                osrsTextureAverageHsl(t)
                            }
                        }
                    val converted = Rev667ModelEncoder.encode(decoded)
                    val roundTrip = Rev667ModelDecoder.decode(converted)
                    check(roundTrip.vertexCount == decoded.vertexCount && roundTrip.faceCount == decoded.faceCount) { "model $modernId conversion lost geometry" }
                    modelIdMap[modernId] = localId
                    mutations += CacheMutation(FeroxImportTool.MODEL_INDEX, localId, 0, converted, "watson model modern=$modernId -> local=$localId")
                }
                importedDefs.forEach { (modernId, def) ->
                    val localId = locIdMap.getValue(modernId)
                    val bytes = Rev667LocType.encodeFromModern(def, { modelIdMap.getValue(it) }, dropped)
                    check(Rev667LocType.decode(localId, bytes).allModels == def.models.map { modelIdMap.getValue(it) }) { "loc $modernId encode/decode model mismatch" }
                    mutations += CacheMutation(FeroxImportTool.LOC_INDEX, localId ushr 8, localId and 0xFF, bytes, "watson loc modern=$modernId '${def.name}' -> local=$localId")
                }

                // ---- the square ----
                val tiles = Rev667TileMap()
                for (plane in 0 until 4) for (lx in 0 until 64) for (lz in 0 until 64) {
                    val src = modernRegion.tiles[plane][lx][lz]
                    val dst = tiles.tiles[plane][lx][lz]
                    dst.height = src.height
                    dst.flags = src.settings
                    dst.overlayShape = src.overlayPath
                    dst.overlayRotation = src.overlayRotation
                    dst.overlayId = if (src.overlayId != 0) mapOverlay(src.overlayId) else 0
                    dst.underlayId = if (src.underlayId != 0) mapUnderlay(src.underlayId) else 0
                }
                val mapBytes = Rev667TileCodec.encode(tiles)
                val locs = modernRegion.locs.map { Rev667Loc(locIdMap.getValue(it.id), it.localX, it.localZ, it.plane, it.type, it.rotation) }
                val locBytes = Rev667LocCodec.encode(locs)
                check(Rev667LocCodec.decode(locBytes).size == locs.size) { "loc encode/decode count mismatch" }
                mutations += CacheMutation(FeroxImportTool.MAP_INDEX, mapGroup, 0, mapBytes, "watson tiles $MAP_NAME", groupName = MAP_NAME)
                mutations += CacheMutation(FeroxImportTool.MAP_INDEX, locGroup, 0, locBytes, "watson locs $LOC_NAME (unencrypted)", groupName = LOC_NAME)
                dropped.distinct().forEach { println("DROPPED $it") }
                println(
                    "PLAN square $REGION groups $MAP_NAME=$mapGroup $LOC_NAME=$locGroup locs=${locs.size} reused=${reused.size} " +
                        "imported=${importedDefs.size} models=${modelIdMap.size} floors underlay=${underlayMap.size} overlay=${overlayMap.size} mutations=${mutations.size}",
                )
                return Plan(mutations, locIdMap, modelIdMap, reused, dropped.distinct(), mapGroup, locGroup)
            } finally {
                library.close()
            }
        }
    }

    private fun zeroXteaKey() {
        val file = File(FeroxImportTool.XTEAS_FILE)
        val root = JsonParser().parse(file.readText()).asJsonArray
        if (root.none { it.asJsonObject.get("mapsquare").asInt == REGION }) {
            root.add(JsonObject().apply {
                addProperty("mapsquare", REGION)
                add("key", JsonArray().apply { repeat(4) { add(0) } })
            })
            file.writeText(GsonBuilder().setPrettyPrinting().create().toJson(root))
            println("XTEAS zero key added for $REGION")
        }
    }

    private fun appendAssetMap(
        plan: Plan,
        transactionId: String,
    ) {
        val sb = StringBuilder()
        sb.appendLine("  - name: Watson's house world import (owner answer Q10)")
        sb.appendLine("    transaction_id: $transactionId")
        sb.appendLine("    source: OpenRS2 cache 2499, whole map square $REGION ($MAP_NAME -> group ${plan.mapGroup}, $LOC_NAME -> group ${plan.locGroup})")
        sb.appendLine("    loc_ids:")
        plan.locIdMap.forEach { (modern, local) -> sb.appendLine("      - { upstream_id: $modern, local_id: $local, mode: ${if (modern == local) "REUSE_AS_IS" else "IMPORT"} }") }
        sb.appendLine("    models:")
        plan.modelIdMap.forEach { (modern, local) -> sb.appendLine("      - { upstream_id: $modern, local_id: $local, role: loc_model }") }
        File(FeroxImportTool.ASSET_MAP).appendText(sb.toString())
        println("ASSET_MAP appended ${plan.locIdMap.size} loc and ${plan.modelIdMap.size} model mappings")
    }
}
