package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/**
 * Owner 2026-09-19: "put the shrine of ralos in the grand exchange". Imports OSRS loc 52405 "Shrine of Ralos" (option "Bask";
 * OSRS Wiki "Shrine of Ralos": shrines "restore prayer points ... and upgrade Dizana's quiver using 150,000 sunfire splinters")
 * with its models from the pinned 2686 cache into both caches through [CacheTransaction], the same route as [FeroxLootChestTool].
 * The loc is not placed in the map: the server spawns it at the Grand Exchange (`grand_exchange_hub.plugin.kts`), like the
 * singing bowl. Its local id is printed and recorded in OSRS_IMPORT_MASTER.yml.
 *
 * Usage: `plan|apply`.
 */
object ShrineOfRalosImportTool {
    const val SHRINE_LOC = 52405

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val mutations = ArrayList<CacheMutation>()
        val dropped = ArrayList<String>()
        val modelIdMap = LinkedHashMap<Int, Int>()
        var localId = -1
        ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE)).use { modern ->
            val library = CacheLibrary(FeroxImportTool.GAME_CACHE)
            try {
                val localTypes = Rev667RegionProbeTool.locTypes(library)
                localId = (localTypes.keys.maxOrNull() ?: -1) + 1
                val census = ModelNamespaceCensusTool.census(FeroxImportTool.GAME_CACHE, FeroxImportTool.FILE_SERVER_CACHE, File(FeroxImportTool.ASSET_MAP))
                check(census.physicalDivergentIds.isEmpty() && census.referencedDivergentIds.isEmpty()) { "target caches diverge" }
                val free = census.provenFreeHoles.filter { it in 0..0xFFFF }.sorted().iterator()
                val def = ModernObjectDef.decode(SHRINE_LOC, modern.files(ModernCacheReader.INDEX_CONFIG, 6)[SHRINE_LOC] ?: error("loc $SHRINE_LOC missing"))
                check(def.name == "Shrine of Ralos" && def.options[0] == "Bask") { "loc $SHRINE_LOC is '${def.name}' ${def.options.toList()}" }
                def.models.filter { it !in modelIdMap }.forEach { m ->
                    check(free.hasNext()) { "Out of proven-free model ids." }
                    val localModel = free.next()
                    modelIdMap[m] = localModel
                    mutations += CacheMutation(FeroxImportTool.MODEL_INDEX, localModel, 0, OsrsModelConversion.convert(modern, m, dropped), "shrine of ralos model osrs=$m -> local=$localModel")
                }
                val bytes = Rev667LocType.encodeFromModern(def, { modelIdMap.getValue(it) }, dropped)
                check(Rev667LocType.decode(localId, bytes).allModels == def.models.map { modelIdMap.getValue(it) }) { "encode/decode mismatch" }
                mutations += CacheMutation(FeroxImportTool.LOC_INDEX, localId ushr 8, localId and 0xFF, bytes, "shrine of ralos loc osrs=$SHRINE_LOC -> local=$localId")
                println("PLAN loc $SHRINE_LOC '${def.name}' options=${def.options.toList()} size=${def.sizeX}x${def.sizeY} -> $localId models=${def.models}->${def.models.map { modelIdMap.getValue(it) }}")
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
        block.append("  - name: Shrine of Ralos at the Grand Exchange (owner 2026-09-19)\n")
        block.append("    transaction: ${tx.id}\n")
        block.append("    models:\n")
        modelIdMap.forEach { (m, l) -> block.append("      - { upstream_id: $m, local_id: $l, role: loc_model }\n") }
        block.append("    loc_ids:\n")
        block.append("      - { upstream_id: $SHRINE_LOC, local_id: $localId, mode: IMPORT }\n")
        val assetMap = File(FeroxImportTool.ASSET_MAP)
        val text = assetMap.readText()
        assetMap.writeText(if (text.endsWith("\n")) text + block else "$text\n$block")
        println("ASSET_MAP appended shrine entry")
    }
}
