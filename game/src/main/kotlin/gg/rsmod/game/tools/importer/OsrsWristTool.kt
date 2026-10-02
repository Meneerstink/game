package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/**
 * Fits the lower-forearm cuff of every already imported OSRS hands-slot item (gloves, gauntlets, vambraces, bracelets) onto
 * the 667 forearm ([OsrsWristFit]; owner 2026-10-02 "the osrs armour still invisible at the hands"). New imports get the same
 * from [OsrsItemImportTool].
 *
 * Scope: the male and female worn meshes of every imported item whose OSRS wear slot is the hands; each is fitted against the
 * bare forearm of its own gender ([WornForearm.pieces]), a mesh worn by both genders against the male one. A mesh is replaced
 * only when the cache still holds exactly the plain conversion the importer wrote before the fit; one that already holds the
 * fitted mesh is done, any other content is reported and left alone. One transaction on both caches; a second run finds
 * nothing to change. Usage: `[--apply]`.
 */
object OsrsWristTool {
    private const val INDEX_MODEL = 7

    data class Target(val local: Int, val upstream: Int, val female: Boolean, val item: String)

    fun targets(
        reader: ModernCacheReader,
        existing: ImportBatchOrchestrator.ExistingMapping,
    ): List<Target> {
        val itemFiles = reader.files(ModernCacheReader.INDEX_CONFIG, ModernCacheReader.CONFIG_GROUP_ITEM)
        return OsrsFxImportTool.importedItems(File(OsrsItemImportTool.ASSET_MAP)).flatMap { (upstream, _) ->
            val def = itemFiles[upstream]?.let { ModernItemDefDecoder.decode(upstream, it) } ?: return@flatMap emptyList()
            if (def.wearPos1 != OsrsWristFit.HANDS_SLOT) return@flatMap emptyList()
            OsrsItemImportTool.wornModels(def).map { model ->
                val local =
                    existing.modelLocalIdByMappingKey[ModelIdAllocator.mappingKey("upstream_model:$model", "model")]
                        ?: error("${def.name}: hands mesh $model has no local model in the asset map")
                Target(local, model, model !in setOf(def.maleModel0, def.maleModel1, def.maleModel2), def.name)
            }
        }.groupBy { it.local }.map { (_, same) -> same.firstOrNull { !it.female } ?: same.first() }.sortedBy { it.local }
    }

    /** [current] is the [plain] conversion with only its forearm (cuff) vertices moved: an earlier version of the fit. */
    fun isEarlierFit(
        current: ModelData,
        plain: ModelData,
    ): Boolean {
        if (current.vertexCount != plain.vertexCount || current.faceCount != plain.faceCount) return false
        if (!current.faceA.contentEquals(plain.faceA) || !current.faceB.contentEquals(plain.faceB) || !current.faceC.contentEquals(plain.faceC)) return false
        val labels = plain.vertexLabel ?: return false
        return (0 until plain.vertexCount).all { v ->
            current.vertexY[v] == plain.vertexY[v] &&
                (labels[v] in WornForearm.FOREARM_LABELS || (current.vertexX[v] == plain.vertexX[v] && current.vertexZ[v] == plain.vertexZ[v]))
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val dropped = mutableListOf<String>()
        val mutations = ArrayList<CacheMutation>()
        val foreign = ArrayList<String>()
        var done = 0
        val reader = ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE))
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        try {
            val pieces = WornForearm.pieces(library).associateBy { it.female }
            val targets = targets(reader, ImportBatchOrchestrator.readExistingMapping(File(OsrsItemImportTool.ASSET_MAP)))
            println("HANDS MESHES ${targets.size}")
            targets.forEach { t ->
                val current = library.data(INDEX_MODEL, t.local, 0) ?: error("model ${t.local} missing")
                val plain = OsrsModelConversion.convert(reader, t.upstream, dropped, OsrsWristFit.HANDS_SLOT)
                val fitted = OsrsWristFit.fitBytes(plain, pieces.getValue(t.female))
                if (current.contentEquals(fitted)) {
                    done++
                    return@forEach
                }
                if (!current.contentEquals(plain) && !isEarlierFit(Rev667ModelDecoder.decode(current), Rev667ModelDecoder.decode(plain))) {
                    foreign += "model ${t.local} (${t.item}, upstream ${t.upstream}) holds neither the plain nor a fitted conversion"
                    return@forEach
                }
                val scales = OsrsWristFit.fit(Rev667ModelDecoder.decode(plain), Rev667ModelDecoder.decode(pieces.getValue(t.female).bytes, geometryOnly = true))
                println("PLAN model ${t.local} ${t.item} ${if (t.female) "female" else "male"}: widen ${scales.entries.joinToString { "label ${it.key} x${"%.2f".format(it.value)}" }}")
                mutations += CacheMutation(INDEX_MODEL, t.local, 0, fitted, "osrs wrist fit model ${t.local} (${t.item})", CacheItemProbeTool.sha1(current))
            }
        } finally {
            library.close()
            reader.close()
        }
        println("SUMMARY fit=${mutations.size} already=$done foreign=${foreign.size}")
        foreign.forEach { println("FOREIGN $it") }
        if (mutations.isEmpty()) {
            println("NOTHING_TO_DO")
            return
        }
        val transaction = CacheTransaction(targets = OsrsItemImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} mutations=${mutations.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKING: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY_RUN")
            return
        }
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
