package gg.rsmod.game.tools.importer

import java.io.File

/**
 * N609 14-11 15:4x (eigenaar 'vergelijk elke foto met osrs ... elke foto uitgebreid'; Nexus naast Kaoz 439 s: the gold god symbol on the
 * red Path-door cloth is missing). toa_locmodel_census.py: of 1595 ToA loc model pairs only the four Nexus path-door SYMBOL models
 * differ from current OSRS (cache 2686) - our copies are byte-equal to the OLDER OSRS cache 2499 the map import used; Jagex remodelled
 * them since (45361 320 -> 246 faces, 45365 146 -> 162, 45367 150 -> 140, 45372 142 -> 113). Re-imports the 2686 meshes into the SAME
 * local ids (the doors 65272-65275 keep pointing at them), both caches, through [CacheTransaction] with the current bytes pinned.
 *
 * Usage: `plan|apply` (kc_tool.sh ToaDoorSymbolModelTool plan).
 */
object ToaDoorSymbolModelTool {
    /** OSRS model (cache 2686) -> our local model id (asset map osrs_loc_model, OSRS_IMPORT_MASTER.yml). */
    val MODELS = linkedMapOf(45361 to 16068, 45365 to 16071, 45367 to 16072, 45372 to 16098)

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 1 && args[0] in setOf("plan", "apply")) { "Usage: plan|apply" }
        val mutations = ArrayList<CacheMutation>()
        ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE)).use { modern ->
            val library = com.displee.cache.CacheLibrary(FeroxImportTool.GAME_CACHE)
            try {
                MODELS.forEach { (osrs, local) ->
                    val raw = modern.file(FeroxImportTool.MODEL_INDEX, osrs, 0) ?: error("OSRS model $osrs missing")
                    val decoded = ModernModelDecoder.decode(raw)
                    check((decoded.faceTexture?.count { it.toInt() != -1 } ?: 0) == 0 && decoded.texSpaceCount == 0) { "model $osrs is textured" }
                    val converted = Rev667ModelEncoder.encode(decoded)
                    val back = Rev667ModelDecoder.decode(converted)
                    check(back.vertexCount == decoded.vertexCount && back.faceCount == decoded.faceCount) { "model $osrs lost geometry" }
                    val current = library.data(FeroxImportTool.MODEL_INDEX, local, 0)
                    val oldFaces = current?.let { Rev667ModelDecoder.decode(it, geometryOnly = true).faceCount }
                    println("PLAN model OSRS $osrs -> $local: OUD ${current?.size} bytes / $oldFaces vlakken -> NIEUW ${converted.size} bytes / ${back.faceCount} vlakken")
                    mutations += CacheMutation(FeroxImportTool.MODEL_INDEX, local, 0, converted, "N609 Nexus door symbol OSRS2686 $osrs -> $local",
                        expectedCurrentSha1 = current?.let { CacheItemProbeTool.sha1(it) })
                }
            } finally {
                library.close()
            }
        }
        val tx = CacheTransaction(listOf(FeroxImportTool.GAME_CACHE, FeroxImportTool.FILE_SERVER_CACHE), mutations)
        val preflight = tx.preflight()
        val blocking = tx.blockingErrors(preflight)
        println("PREFLIGHT transaction=${tx.id} mutations=${mutations.size} outcomes=${preflight.groupingBy { it.outcome }.eachCount()}")
        blocking.forEach { println("  BLOCKING: $it") }
        if (args[0] == "plan") { println("DRY_RUN"); return }
        check(blocking.isEmpty()) { "Preflight has blocking errors; refusing to apply." }
        val result = tx.apply(preflight)
        val verify = tx.verify()
        if (verify.isNotEmpty()) {
            verify.forEach { println("  VERIFY_FAILURE: $it") }
            println("ROLLED_BACK ${tx.rollback()}")
            return
        }
        println("APPLIED transaction=${tx.id} writes=${result.applied} skipped=${result.skipped}")
    }
}
