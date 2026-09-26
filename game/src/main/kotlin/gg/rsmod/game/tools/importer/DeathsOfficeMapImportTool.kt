package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

/**
 * Imports OSRS Death's Office (OSRS Wiki "Death's Office": map square 12633, office at 3168-3183 / 5721-5732) and the OSRS
 * "Death's Domain" entrance scenery into both rev-667 caches through [CacheTransaction], the same route as
 * [WatsonHouseImportTool]:
 *
 *  - OSRS square 12633 is taken in this cache by the Zarosian temple of Ancient Curses (probe 2026-09-26: loc 47120 "Altar of
 *    Zaros" at 3181,5705), so the whole OSRS square is written to the free square [TARGET] (m49_95 / l49_95, 384 tiles north
 *    of the OSRS one; every one of its eight neighbours is empty, so nothing else is ever drawn around the office, and it
 *    lies outside the instance allocator's area x >= 6400). The office keeps its OSRS x coordinates; z is OSRS z + [DZ].
 *  - Every OSRS loc type of the square is imported as a new loc type (no by-name reuse: generic names such as "Chest" or
 *    "Chair" would otherwise pick up unrelated rev-667 models), with its models converted by [OsrsModelConversion] and its
 *    idle animation mapped to the sequence the npc tool's `deaths-office` batch imported (asset map `seq:<id>`).
 *  - The entrance loc types (OSRS Wiki "Death's Domain (scenery)": Lumbridge 38426, Seers' Village 39546, Edgeville 39547;
 *    Ferox 39637 is already loc 62430, which the Grand Exchange entrance reuses) are imported as loc types; the server
 *    places them. Owner 2026-09-26: no Falador entrance (its OSRS crypt does not exist in the 667 map).
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.DeathsOfficeMapImportTool plan|apply`
 */
object DeathsOfficeMapImportTool {
    const val SOURCE_REGION = 12633
    const val TARGET = 12639
    const val MAP_NAME = "m49_95"
    const val LOC_NAME = "l49_95"

    /** Target square z minus source square z, in tiles ((95 - 89) * 64). */
    const val DZ = 384

    val ENTRANCES = listOf(38426, 39546, 39547)

    /** The Ferox Enclave "Death's domain" (OSRS 39637), imported by [FeroxImportTool] as loc 62430. */
    const val FEROX_ENTRANCE = 62430

    /** Config group of MapElementType (the client's `mapelement`, loc opcode 107). */
    const val MAP_ELEMENT_GROUP = 36

    /** The Death's Office map element this tool created (tx-20260926-012959): sprite [DeathsOfficeInterfaceImportTool.OFFICE_MAP_ICON]. */
    const val OFFICE_MAP_ELEMENT = 1108

    class Plan(
        val mutations: List<CacheMutation>,
        val locIdMap: Map<Int, Int>,
        val modelIdMap: Map<Int, Int>,
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
        val seqs = OsrsFxImportTool.existingFx(File(FeroxImportTool.ASSET_MAP)).filterKeys { it.startsWith("seq:") }
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

                // ---- loc types: every one imported new ----
                val modernRegion = ModernRegion.load(modern, SOURCE_REGION, keys[SOURCE_REGION] ?: error("No modern key for $SOURCE_REGION"))
                val modernLocDefs = modern.files(2, 6)
                val localTypes = Rev667RegionProbeTool.locTypes(library)
                var nextLocId = (localTypes.keys.maxOrNull() ?: -1) + 1
                val locIdMap = LinkedHashMap<Int, Int>()
                val neededModels = sortedSetOf<Int>()
                val importedDefs = LinkedHashMap<Int, ModernObjectDef>()
                (modernRegion.locs.map { it.id }.distinct().sorted() + ENTRANCES).forEach { id ->
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
                    mutations += CacheMutation(FeroxImportTool.MODEL_INDEX, localId, 0, OsrsModelConversion.convert(modern, modernId, dropped), "death's office model osrs=$modernId -> $localId")
                }
                // ---- the Death's Office map icon: a new MapElementType drawing OSRS sprite 2216, on every entrance ----
                val elementIds = library.index(Rev667RegionProbeTool.CONFIG_INDEX).archive(MAP_ELEMENT_GROUP)?.fileIds() ?: error("no mapelement group")
                val mapElement = (elementIds.maxOrNull() ?: -1) + 1
                check(mapElement == OFFICE_MAP_ELEMENT) { "next free map element is $mapElement, not $OFFICE_MAP_ELEMENT" }
                // MapElementType: opcode 1 = sprite (u16), 0 = end (client MapElementType.decode).
                val elementBytes = byteArrayOf(1, (DeathsOfficeInterfaceImportTool.OFFICE_MAP_ICON ushr 8).toByte(), DeathsOfficeInterfaceImportTool.OFFICE_MAP_ICON.toByte(), 0)
                mutations += CacheMutation(Rev667RegionProbeTool.CONFIG_INDEX, MAP_ELEMENT_GROUP, mapElement, elementBytes, "mapelement $mapElement: Death's Office icon (OSRS sprite 2216)")
                println("MAP_ELEMENT $mapElement sprite ${DeathsOfficeInterfaceImportTool.OFFICE_MAP_ICON}")
                val feroxGroup = FEROX_ENTRANCE ushr 8
                val feroxFile = FEROX_ENTRANCE and 0xFF
                val feroxBytes = library.data(FeroxImportTool.LOC_INDEX, feroxGroup, feroxFile) ?: error("loc $FEROX_ENTRANCE missing")
                val feroxBefore = Rev667LocType.decode(FEROX_ENTRANCE, feroxBytes)
                check(feroxBefore.mapElement == -1) { "loc $FEROX_ENTRANCE already has mapElement ${feroxBefore.mapElement}" }
                val feroxAfter = FeroxMinimapTool.patched(feroxBytes, null, mapElement)
                check(Rev667LocType.decode(FEROX_ENTRANCE, feroxAfter).mapElement == mapElement) { "Ferox entrance patch failed" }
                mutations += CacheMutation(FeroxImportTool.LOC_INDEX, feroxGroup, feroxFile, feroxAfter, "loc $FEROX_ENTRANCE '${feroxBefore.name}' mapelement $mapElement", CacheItemProbeTool.sha1(feroxBytes))

                importedDefs.forEach { (modernId, def) ->
                    val localId = locIdMap.getValue(modernId)
                    val encoded = Rev667LocType.encodeFromModern(def, { modelIdMap.getValue(it) }, dropped) { seqs["seq:$it"] }
                    val bytes = if (modernId in ENTRANCES) FeroxMinimapTool.patched(encoded, null, mapElement) else encoded
                    val decoded = Rev667LocType.decode(localId, bytes)
                    check(decoded.allModels == def.models.map { modelIdMap.getValue(it) }) { "loc $modernId encode/decode model mismatch" }
                    if (def.animationId != -1) check(decoded.animation == seqs["seq:${def.animationId}"]) { "loc $modernId animation ${def.animationId} not imported (run the npc tool's deaths-office batch first)" }
                    mutations += CacheMutation(FeroxImportTool.LOC_INDEX, localId ushr 8, localId and 0xFF, bytes, "death's office loc osrs=$modernId '${def.name}' -> $localId")
                    println("LOC osrs=$modernId '${def.name}' ops=${def.options.toList()} size=${def.sizeX}x${def.sizeY} anim=${def.animationId}->${decoded.animation} -> $localId")
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
                mutations += CacheMutation(FeroxImportTool.MAP_INDEX, mapGroup, 0, mapBytes, "death's office tiles $MAP_NAME", groupName = MAP_NAME)
                mutations += CacheMutation(FeroxImportTool.MAP_INDEX, locGroup, 0, locBytes, "death's office locs $LOC_NAME (unencrypted)", groupName = LOC_NAME)
                dropped.distinct().forEach { println("DROPPED $it") }
                println(
                    "PLAN square $SOURCE_REGION -> $TARGET groups $MAP_NAME=$mapGroup $LOC_NAME=$locGroup locs=${locs.size} types=${importedDefs.size} " +
                        "models=${modelIdMap.size} floors underlay=${underlayMap.size} overlay=${overlayMap.size} mutations=${mutations.size}",
                )
                return Plan(mutations, locIdMap, modelIdMap, dropped.distinct(), mapGroup, locGroup)
            } finally {
                library.close()
            }
        }
    }

    private fun zeroXteaKey() {
        val file = File(FeroxImportTool.XTEAS_FILE)
        val root = JsonParser().parse(file.readText()).asJsonArray
        if (root.none { it.asJsonObject.get("mapsquare").asInt == TARGET }) {
            root.add(JsonObject().apply {
                addProperty("mapsquare", TARGET)
                add("key", JsonArray().apply { repeat(4) { add(0) } })
            })
            file.writeText(GsonBuilder().setPrettyPrinting().create().toJson(root))
            println("XTEAS zero key added for $TARGET")
        }
    }

    private fun appendAssetMap(
        plan: Plan,
        transactionId: String,
    ) {
        val sb = StringBuilder()
        sb.appendLine("  - name: Death's Office world import (OSRS square $SOURCE_REGION -> $TARGET, z + $DZ) and Death's Domain entrances")
        sb.appendLine("    transaction_id: $transactionId")
        sb.appendLine("    source: OpenRS2 cache 2499, whole map square $SOURCE_REGION ($MAP_NAME -> group ${plan.mapGroup}, $LOC_NAME -> group ${plan.locGroup})")
        sb.appendLine("    loc_ids:")
        plan.locIdMap.forEach { (modern, local) -> sb.appendLine("      - { upstream_id: $modern, local_id: $local, mode: IMPORT }") }
        sb.appendLine("    models:")
        plan.modelIdMap.forEach { (modern, local) -> sb.appendLine("      - { upstream_id: $modern, local_id: $local, role: loc_model }") }
        File(FeroxImportTool.ASSET_MAP).appendText(sb.toString())
        println("ASSET_MAP appended ${plan.locIdMap.size} loc and ${plan.modelIdMap.size} model mappings")
    }
}
