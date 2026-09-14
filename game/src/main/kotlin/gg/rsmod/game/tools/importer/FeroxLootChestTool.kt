package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/**
 * RCV-012 decision 3b: the Ferox Enclave Loot Chest.
 *
 * The Ferox world import placed OSRS loc 43486 at 3138,3626 plane 0 as local loc 62575. In the pinned 2499 cache 43486 is only a
 * multiloc (name "null", no models, varbit 13651 -> [43484 Loot Chest closed, 43485 Loot Chest opened, -1]); the world import drops
 * transform tables, so the placement has no model and no options in 667. This transaction imports the two real LocTypes (their
 * models converted by [OsrsModelConversion]) and re-points that one placement at the closed chest 43484, keeping its shape type and
 * rotation. ADAPTED: the per-player "opened" appearance is an OSRS varbit multiloc and is not reproduced (43485 is imported for a
 * later per-player transform, unplaced).
 *
 * Usage: `plan|apply`.
 */
object FeroxLootChestTool {
    const val PLACEMENT_X = 3138
    const val PLACEMENT_Z = 3626
    const val PLACEMENT_PLANE = 0
    const val WORLD_IMPORT_LOCAL_ID = 62575
    val CHEST_LOCS = listOf(43484, 43485)

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val mutations = ArrayList<CacheMutation>()
        val dropped = ArrayList<String>()
        val locIdMap = LinkedHashMap<Int, Int>()
        val modelIdMap = LinkedHashMap<Int, Int>()
        ModernCacheReader(File(FeroxImportTool.MODERN_CACHE)).use { modern ->
            val library = CacheLibrary(FeroxImportTool.GAME_CACHE)
            try {
                val localTypes = Rev667RegionProbeTool.locTypes(library)
                var nextLocId = (localTypes.keys.maxOrNull() ?: -1) + 1
                val census = ModelNamespaceCensusTool.census(FeroxImportTool.GAME_CACHE, FeroxImportTool.FILE_SERVER_CACHE, File(FeroxImportTool.ASSET_MAP))
                check(census.physicalDivergentIds.isEmpty() && census.referencedDivergentIds.isEmpty()) { "target caches diverge" }
                val free = census.provenFreeHoles.filter { it in 0..0xFFFF }.sorted().iterator()
                val modernLocDefs = modern.files(ModernCacheReader.INDEX_CONFIG, 6)
                CHEST_LOCS.forEach { modernId ->
                    val def = ModernObjectDef.decode(modernId, modernLocDefs[modernId] ?: error("modern loc $modernId missing"))
                    check(def.name == "Loot Chest" && def.options[0] == "Loot") { "modern loc $modernId is '${def.name}' ${def.options.toList()}" }
                    def.models.filter { it !in modelIdMap }.forEach { m ->
                        check(free.hasNext()) { "Out of proven-free model ids." }
                        val localModel = free.next()
                        modelIdMap[m] = localModel
                        mutations += CacheMutation(FeroxImportTool.MODEL_INDEX, localModel, 0, OsrsModelConversion.convert(modern, m, dropped), "loot chest model modern=$m -> local=$localModel")
                    }
                    val localId = nextLocId++
                    locIdMap[modernId] = localId
                    val bytes = Rev667LocType.encodeFromModern(def, { modelIdMap.getValue(it) }, dropped)
                    check(Rev667LocType.decode(localId, bytes).allModels == def.models.map { modelIdMap.getValue(it) }) { "loc $modernId encode/decode mismatch" }
                    mutations += CacheMutation(FeroxImportTool.LOC_INDEX, localId ushr 8, localId and 0xFF, bytes, "loot chest loc modern=$modernId -> local=$localId")
                    println("PLAN loc $modernId '${def.name}' options=${def.options.toList()} -> $localId models=${def.models}->${def.models.map { modelIdMap.getValue(it) }}")
                }

                val keys = Rev667RegionProbeTool.loadKeys(File(FeroxImportTool.XTEAS_FILE))
                val regionId = ((PLACEMENT_X shr 6) shl 8) or (PLACEMENT_Z shr 6)
                val rx = regionId shr 8
                val rz = regionId and 0xFF
                val locName = "l${rx}_$rz"
                val archive = library.index(FeroxImportTool.MAP_INDEX).archive(locName, keys[regionId]) ?: error("$locName missing")
                val bytes = archive.file(0)!!.data!!
                val locs = Rev667LocCodec.decode(bytes)
                check(Rev667LocCodec.encode(locs).contentEquals(bytes)) { "$locName round-trip failed" }
                val hit =
                    locs.filter {
                        it.id == WORLD_IMPORT_LOCAL_ID && it.localX == PLACEMENT_X - rx * 64 && it.localZ == PLACEMENT_Z - rz * 64 && it.plane == PLACEMENT_PLANE
                    }
                check(hit.size == 1) { "expected one placement of $WORLD_IMPORT_LOCAL_ID at $PLACEMENT_X,$PLACEMENT_Z, found ${hit.size}" }
                val old = hit.single()
                val replacement = Rev667Loc(locIdMap.getValue(43484), old.localX, old.localZ, old.plane, old.type, old.rotation)
                val newLocs = locs.map { if (it === old) replacement else it }
                val newBytes = Rev667LocCodec.encode(newLocs)
                check(Rev667LocCodec.decode(newBytes).size == locs.size)
                println("PLACEMENT $locName ${old.id} -> ${replacement.id} at $PLACEMENT_X,$PLACEMENT_Z,$PLACEMENT_PLANE type=${old.type} rot=${old.rotation}")
                mutations +=
                    CacheMutation(
                        FeroxImportTool.MAP_INDEX,
                        archive.id,
                        0,
                        newBytes,
                        "ferox loot chest placement in $locName",
                        CacheItemProbeTool.sha1(bytes),
                        xtea = keys[regionId],
                    )
            } finally {
                library.close()
            }
        }
        dropped.distinct().forEach { println("DROPPED $it") }
        val tx = CacheTransaction(listOf(FeroxImportTool.GAME_CACHE, FeroxImportTool.FILE_SERVER_CACHE), mutations)
        val preflight = tx.preflight()
        println("PREFLIGHT transaction=${tx.id} mutations=${mutations.size} outcomes=${preflight.groupingBy { it.outcome }.eachCount()}")
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
        println("VERIFY_OK both targets hold intended bytes")
        val block = StringBuilder()
        block.append("  - name: Ferox Enclave Loot Chest (RCV-012 decision 3b)\n")
        block.append("    transaction: ${tx.id}\n")
        block.append("    models:\n")
        modelIdMap.forEach { (m, l) -> block.append("      - { upstream_id: $m, local_id: $l, role: loc_model }\n") }
        block.append("    loc_ids:\n")
        locIdMap.forEach { (m, l) -> block.append("      - { upstream_id: $m, local_id: $l, mode: IMPORT }\n") }
        val assetMap = File(FeroxImportTool.ASSET_MAP)
        val text = assetMap.readText()
        assetMap.writeText(if (text.endsWith("\n")) text + block else "$text\n$block")
        println("ASSET_MAP appended loot chest entry")
    }
}
