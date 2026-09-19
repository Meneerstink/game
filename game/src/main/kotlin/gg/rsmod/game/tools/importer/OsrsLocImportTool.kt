package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/**
 * OSRS loc (scenery) type import into both rev-667 caches, for locs that are spawned at runtime rather than placed in a map square.
 * The model and LocType steps are the ones [FeroxImportTool] proved live: models go into census-proven free model holes with
 * textured faces flattened ([stripTextures]), the LocType is written by [Rev667LocType.encodeFromModern] after the next free loc id.
 * A loc animation is kept only when the OSRS sequence is a classic one already imported (asset map `seq:<id>`); skeletal ones are
 * dropped and reported, because rev 667 cannot represent them.
 *
 * Usage: `<batch> [--apply]` - without `--apply` only plan + preflight run.
 */
object OsrsLocImportTool {
    val BATCHES: Map<String, List<Int>> =
        mapOf(
            // Owner 2026-09-19 (exact OSRS Deadman breaches): OSRS Wiki "Breach (scenery)" id 49561 and "Boss Spawn" id 49563.
            "deadman-breach" to listOf(49561, 49563),
        )

    @JvmStatic
    fun main(args: Array<String>) {
        val batch = BATCHES[args.getOrNull(0)] ?: error("Usage: <${BATCHES.keys.joinToString("|")}> [--apply]")
        val apply = "--apply" in args
        val assetMap = File(OsrsItemImportTool.ASSET_MAP)
        val mutations = ArrayList<CacheMutation>()
        val dropped = ArrayList<String>()
        val records = ArrayList<String>()
        val importedSeqs = OsrsFxImportTool.existingFx(assetMap)

        ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE)).use { modern ->
            val library = CacheLibrary(FeroxImportTool.GAME_CACHE)
            try {
                val modernLocs = modern.files(ModernCacheReader.INDEX_CONFIG, 6)
                val localTypes = Rev667RegionProbeTool.locTypes(library)
                var nextLocId = (localTypes.keys.maxOrNull() ?: -1) + 1
                val census = ModelNamespaceCensusTool.census(FeroxImportTool.GAME_CACHE, FeroxImportTool.FILE_SERVER_CACHE, assetMap)
                val free = census.provenFreeHoles.iterator()
                val modelIdMap = LinkedHashMap<Int, Int>()

                batch.forEach { osrsId ->
                    val src = ModernObjectDef.decode(osrsId, modernLocs[osrsId] ?: error("OSRS loc $osrsId missing"))
                    val anim = src.animationId
                    val keepAnim = anim >= 0 && importedSeqs.containsKey("seq:$anim")
                    if (anim >= 0 && !keepAnim) dropped += "loc $osrsId: animation $anim (not an imported classic sequence)"
                    if (!keepAnim) src.animationId = -1
                    val def = src
                    def.models.forEach { modernId ->
                        if (modelIdMap.containsKey(modernId)) return@forEach
                        check(free.hasNext()) { "Out of proven-free model ids." }
                        val localId = free.next()
                        val raw = ModernModelDecoder.decode(modern.file(FeroxImportTool.MODEL_INDEX, modernId, 0) ?: error("model $modernId missing"))
                        val decoded =
                            if ((raw.faceTexture?.count { it.toInt() != -1 } ?: 0) == 0 && raw.texSpaceCount == 0) {
                                raw
                            } else {
                                dropped += "model $modernId: textured faces flattened to the textures' average colour"
                                stripTextures(raw) { tex ->
                                    val t = modern.file(9, 0, tex) ?: error("texture $tex missing")
                                    ((t[0].toInt() and 0xFF) shl 8) or (t[1].toInt() and 0xFF)
                                }
                            }
                        if (decoded.droppedAnimayaSkinning) dropped += "model $modernId: animaya skinning (no rev-667 equivalent)"
                        val converted = Rev667ModelEncoder.encode(decoded)
                        val back = Rev667ModelDecoder.decode(converted)
                        check(back.vertexCount == decoded.vertexCount && back.faceCount == decoded.faceCount) { "model $modernId lost geometry" }
                        modelIdMap[modernId] = localId
                        mutations += CacheMutation(FeroxImportTool.MODEL_INDEX, localId, 0, converted, "osrs loc model $modernId -> $localId")
                        records += "model|$modernId|$localId"
                    }
                    val localId = nextLocId++
                    val bytes = Rev667LocType.encodeFromModern(def, { modelIdMap.getValue(it) }, dropped)
                    check(Rev667LocType.decode(localId, bytes).allModels == def.models.map { modelIdMap.getValue(it) }) { "loc $osrsId model mismatch" }
                    mutations += CacheMutation(FeroxImportTool.LOC_INDEX, localId ushr 8, localId and 0xFF, bytes, "osrs loc $osrsId '${def.name}' -> $localId")
                    records += "loc|$osrsId|$localId"
                    println(
                        "PLAN loc $osrsId '${def.name}' -> $localId models=${def.models}->${def.models.map { modelIdMap[it] }} size=${def.sizeX}x${def.sizeY} " +
                            "interact=${def.interactType} anim=${if (keepAnim) anim else -1} options=${def.options.toList()}",
                    )
                }
            } finally {
                library.close()
            }
        }
        dropped.distinct().forEach { println("DROPPED $it") }
        val tx = CacheTransaction(listOf(FeroxImportTool.GAME_CACHE, FeroxImportTool.FILE_SERVER_CACHE), mutations)
        val preflight = tx.preflight()
        val blocking = tx.blockingErrors(preflight)
        println("PREFLIGHT transaction=${tx.id} mutations=${mutations.size} outcomes=${preflight.groupingBy { it.outcome }.eachCount()}")
        blocking.forEach { println("  BLOCKING: $it") }
        if (!apply) {
            println("DRY_RUN records=${records.size}")
            return
        }
        check(blocking.isEmpty()) { "Preflight has blocking errors; refusing to apply." }
        val result = tx.apply(preflight)
        val verify = tx.verify()
        if (verify.isNotEmpty()) {
            verify.forEach { println("  VERIFY_FAILURE: $it") }
            println("ROLLED_BACK ${tx.rollback()}")
            return
        }
        println("APPLIED transaction=${tx.id} writes=${result.applied} skipped=${result.skipped}")
        val block = StringBuilder()
        records.forEach { r ->
            val (kind, upstream, local) = r.split('|')
            block.append("  - fx_kind: osrs_loc_$kind\n    upstream_fx_id: $upstream\n    local_fx_id: $local\n    status: IMPORTED_BY_OSRS_LOC_TOOL\n    transaction: ${tx.id}\n")
        }
        val text = assetMap.readText()
        assetMap.writeText(if (text.endsWith("\n")) text + block else "$text\n$block")
        println("ASSET_MAP appended ${records.size} loc import entries")
    }
}
