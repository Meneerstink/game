package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

/**
 * Imports whole OSRS map squares that are EMPTY in the rev-667 caches onto the same coordinates (N522, owner 2026-11-01: "alle osrs
 * wilderness bosses met hun locations ... ook de osrs rev cave"; "importeren waar mogelijk, geen custom dingen"). Same route as
 * [DeathsOfficeMapImportTool] (floors mapped to the nearest rev-667 floor, every OSRS loc type of the square imported as a NEW loc
 * type with its models converted by [OsrsModelConversion], loc group written unencrypted with a zero XTEA key), except that the
 * square keeps its OSRS position, so server code, spawns and transports use the OSRS Wiki / donor coordinates unchanged.
 *
 * A batch may also import loc types that live OUTSIDE the squares (e.g. the Scorpion Pit "Cavern" entrances on the rev-667 surface);
 * the server places those.
 *
 * Usage: `./gradlew :game:runOsrsRegionImportTool --args="<batch> [--apply]"`
 */
object OsrsRegionImportTool {
    class Batch(
        val squares: List<Int>,
        val extraLocTypes: List<Int> = emptyList(),
        val note: String,
    )

    val BATCHES: Map<String, Batch> =
        mapOf(
            // N522 bundle 2: OSRS Wiki "Scorpia" (cave in the Scorpion Pit, x 3233 y 10341 -> square 12961, empty in the 667 map) and
            // the three surface entrances "Cavern" 26762 (OSRS square 12861: 3231,3936 / 3231,3951 / 3241,3949); the exits "Crevice"
            // 26763 are inside 12961. Transports: Kronos-184 activities/wilderness/Scorpia.java.
            "scorpia-pit" to Batch(listOf(12961), listOf(26762), "Scorpion Pit cave (Scorpia)"),
        )

    class Plan(
        val mutations: List<CacheMutation>,
        val locIdMap: Map<Int, Int>,
        val modelIdMap: Map<Int, Int>,
        val dropped: List<String>,
        val squareGroups: Map<Int, Pair<Int, Int>>,
    )

    fun mapName(square: Int) = "m${square ushr 8}_${square and 0xFF}"

    fun locName(square: Int) = "l${square ushr 8}_${square and 0xFF}"

    @JvmStatic
    fun main(args: Array<String>) {
        val name = args.getOrNull(0) ?: error("Usage: <batch> [--apply]; batches: ${BATCHES.keys}")
        val batch = BATCHES[name] ?: error("Unknown batch $name; batches: ${BATCHES.keys}")
        val apply = "--apply" in args
        val plan = buildPlan(name, batch)
        val tx = CacheTransaction(listOf(FeroxImportTool.GAME_CACHE, FeroxImportTool.FILE_SERVER_CACHE), plan.mutations)
        val preflight = tx.preflight()
        println("PREFLIGHT transaction=${tx.id} mutations=${plan.mutations.size} outcomes=${preflight.groupingBy { it.outcome }.eachCount()}")
        val blocking = tx.blockingErrors(preflight)
        blocking.forEach { println("BLOCKING: $it") }
        if (!apply) {
            println("DRY_RUN transaction=${tx.id} (nothing written)")
            return
        }
        check(blocking.isEmpty()) { "Preflight has blocking errors; refusing to apply." }
        val result = tx.apply(preflight)
        println("APPLIED transaction=${tx.id} applied=${result.applied} skipped=${result.skipped}")
        val verify = tx.verify()
        verify.forEach { println("VERIFY_ERROR: $it") }
        check(verify.isEmpty()) { "Post-apply verification failed; see journal ${tx.id} for rollback." }
        println("VERIFY_OK both targets hold intended bytes and names")
        batch.squares.forEach { zeroXteaKey(it) }
        appendAssetMap(name, batch, plan, tx.id)
    }

    fun buildPlan(
        name: String,
        batch: Batch,
    ): Plan {
        val keys = ModernRegionProbeTool.loadKeys(File(FeroxImportTool.MODERN_KEYS))
        val seqs = OsrsFxImportTool.existingFx(File(FeroxImportTool.ASSET_MAP)).filterKeys { it.startsWith("seq:") }
        val mutations = ArrayList<CacheMutation>()
        val dropped = ArrayList<String>()
        ModernCacheReader(File(FeroxImportTool.MODERN_CACHE)).use { modern ->
            val library = CacheLibrary(FeroxImportTool.GAME_CACHE)
            try {
                val maps = library.index(FeroxImportTool.MAP_INDEX)
                batch.squares.forEach { square ->
                    check(maps.archive(mapName(square)) == null && maps.archive(locName(square)) == null) {
                        "${mapName(square)} / ${locName(square)} already exist; this tool only fills empty squares"
                    }
                }
                var nextGroup = (maps.archiveIds().maxOrNull() ?: -1) + 1

                // ---- floors (same mapping as FeroxImportTool / DeathsOfficeMapImportTool) ----
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

                // ---- loc types: every one imported new ----
                val regions = batch.squares.associateWith { ModernRegion.load(modern, it, keys[it] ?: error("No modern key for $it")) }
                val modernLocDefs = modern.files(2, 6)
                val localTypes = Rev667RegionProbeTool.locTypes(library)
                var nextLocId = (localTypes.keys.maxOrNull() ?: -1) + 1
                val locIdMap = LinkedHashMap<Int, Int>()
                val neededModels = sortedSetOf<Int>()
                val importedDefs = LinkedHashMap<Int, ModernObjectDef>()
                (regions.values.flatMap { r -> r.locs.map { it.id } }.distinct().sorted() + batch.extraLocTypes).forEach { id ->
                    if (id in locIdMap) return@forEach
                    val def = ModernObjectDef.decode(id, modernLocDefs[id] ?: error("modern loc $id missing"))
                    locIdMap[id] = nextLocId++
                    importedDefs[id] = def
                    neededModels.addAll(def.models)
                }

                // ---- models ----
                val census = ModelNamespaceCensusTool.census(FeroxImportTool.GAME_CACHE, FeroxImportTool.FILE_SERVER_CACHE, File(FeroxImportTool.ASSET_MAP))
                check(census.physicalDivergentIds.isEmpty() && census.referencedDivergentIds.isEmpty()) { "target caches diverge" }
                val free = census.provenFreeHoles.filter { it in 1..0xFFFF }.sorted().iterator()
                val modelIdMap = LinkedHashMap<Int, Int>()
                neededModels.forEach { modernId ->
                    check(free.hasNext()) { "Out of proven-free model ids." }
                    val localId = free.next()
                    modelIdMap[modernId] = localId
                    mutations += CacheMutation(FeroxImportTool.MODEL_INDEX, localId, 0, OsrsModelConversion.convert(modern, modernId, dropped), "$name model osrs=$modernId -> $localId")
                }

                importedDefs.forEach { (modernId, def) ->
                    val localId = locIdMap.getValue(modernId)
                    val bytes = Rev667LocType.encodeFromModern(def, { modelIdMap.getValue(it) }, dropped) { seqs["seq:$it"] }
                    val decoded = Rev667LocType.decode(localId, bytes)
                    check(decoded.allModels == def.models.map { modelIdMap.getValue(it) }) { "loc $modernId encode/decode model mismatch" }
                    if (def.animationId != -1) check(decoded.animation == seqs["seq:${def.animationId}"]) { "loc $modernId animation ${def.animationId} not imported" }
                    mutations += CacheMutation(FeroxImportTool.LOC_INDEX, localId ushr 8, localId and 0xFF, bytes, "$name loc osrs=$modernId '${def.name}' -> $localId")
                    println("LOC osrs=$modernId '${def.name}' ops=${def.options.toList()} size=${def.sizeX}x${def.sizeY} anim=${def.animationId}->${decoded.animation} -> $localId")
                }

                // ---- the squares, on their own OSRS coordinates ----
                val squareGroups = LinkedHashMap<Int, Pair<Int, Int>>()
                regions.forEach { (square, region) ->
                    val tiles = Rev667TileMap()
                    for (plane in 0 until 4) for (lx in 0 until 64) for (lz in 0 until 64) {
                        val src = region.tiles[plane][lx][lz]
                        val dst = tiles.tiles[plane][lx][lz]
                        dst.height = src.height
                        dst.flags = src.settings
                        dst.overlayShape = src.overlayPath
                        dst.overlayRotation = src.overlayRotation
                        dst.overlayId = if (src.overlayId != 0) mapOverlay(src.overlayId) else 0
                        dst.underlayId = if (src.underlayId != 0) mapUnderlay(src.underlayId) else 0
                    }
                    val mapGroup = nextGroup++
                    val locGroup = nextGroup++
                    val locs = region.locs.map { Rev667Loc(locIdMap.getValue(it.id), it.localX, it.localZ, it.plane, it.type, it.rotation) }
                    val locBytes = Rev667LocCodec.encode(locs)
                    check(Rev667LocCodec.decode(locBytes).size == locs.size) { "loc encode/decode count mismatch" }
                    mutations += CacheMutation(FeroxImportTool.MAP_INDEX, mapGroup, 0, Rev667TileCodec.encode(tiles), "$name tiles ${mapName(square)}", groupName = mapName(square))
                    mutations += CacheMutation(FeroxImportTool.MAP_INDEX, locGroup, 0, locBytes, "$name locs ${locName(square)} (unencrypted)", groupName = locName(square))
                    squareGroups[square] = mapGroup to locGroup
                    println("SQUARE $square ${mapName(square)}=$mapGroup ${locName(square)}=$locGroup locs=${locs.size}")
                }
                dropped.distinct().forEach { println("DROPPED $it") }
                println("PLAN $name squares=${batch.squares} types=${importedDefs.size} models=${modelIdMap.size} floors underlay=${underlayMap.size} overlay=${overlayMap.size} mutations=${mutations.size}")
                return Plan(mutations, locIdMap, modelIdMap, dropped.distinct(), squareGroups)
            } finally {
                library.close()
            }
        }
    }

    private fun zeroXteaKey(square: Int) {
        val file = File(FeroxImportTool.XTEAS_FILE)
        val root = JsonParser().parse(file.readText()).asJsonArray
        if (root.none { it.asJsonObject.get("mapsquare").asInt == square }) {
            root.add(JsonObject().apply {
                addProperty("mapsquare", square)
                add("key", JsonArray().apply { repeat(4) { add(0) } })
            })
            file.writeText(GsonBuilder().setPrettyPrinting().create().toJson(root))
            println("XTEAS zero key added for $square")
        }
    }

    private fun appendAssetMap(
        name: String,
        batch: Batch,
        plan: Plan,
        transactionId: String,
    ) {
        val sb = StringBuilder()
        sb.appendLine("  - name: OSRS region import '$name' - ${batch.note} (squares ${batch.squares}, same coordinates)")
        sb.appendLine("    transaction_id: $transactionId")
        sb.appendLine("    source: OpenRS2 cache 2499, map squares ${plan.squareGroups.entries.joinToString { "${it.key} -> groups ${it.value.first}/${it.value.second}" }}")
        sb.appendLine("    loc_ids:")
        plan.locIdMap.forEach { (modern, local) -> sb.appendLine("      - { upstream_id: $modern, local_id: $local, mode: IMPORT }") }
        sb.appendLine("    models:")
        plan.modelIdMap.forEach { (modern, local) -> sb.appendLine("      - { upstream_id: $modern, local_id: $local, role: loc_model }") }
        File(FeroxImportTool.ASSET_MAP).appendText(sb.toString())
        println("ASSET_MAP appended ${plan.locIdMap.size} loc and ${plan.modelIdMap.size} model mappings")
    }
}
