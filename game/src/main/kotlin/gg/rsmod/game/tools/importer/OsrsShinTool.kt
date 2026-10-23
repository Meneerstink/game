package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/**
 * Fits the shin of every already imported OSRS legs mesh onto the 667 shin ([OsrsBodyFrame.fitShin]; owner 2026-09-30: all
 * OSRS legs leave the ankle bare above the 667 feet). New imports get the fit from [OsrsItemImportTool] itself.
 *
 * Scope: the male and female worn meshes of every imported item whose OSRS wear slot is the legs. A mesh is replaced only
 * when the cache still holds exactly what the importer wrote before the fit (the OSRS mesh converted with
 * [OsrsBodyFrame.shiftToRev667]); one that already holds the fitted mesh is done, and any other content is reported and left
 * alone. One transaction on both caches; a second run finds nothing to change.
 *
 * Usage: `[--apply] [--replace=<local model,...>]` - without --apply only plan + preflight run.
 */
object OsrsShinTool {
    private const val INDEX_MODEL = 7

    data class Target(val local: Int, val upstream: Int, val female: Boolean, val item: String)

    /** Every legs worn mesh of the imported OSRS items, once per local model. */
    fun targets(
        reader: ModernCacheReader,
        existing: ImportBatchOrchestrator.ExistingMapping,
    ): List<Target> {
        val itemFiles = reader.files(ModernCacheReader.INDEX_CONFIG, ModernCacheReader.CONFIG_GROUP_ITEM)
        return OsrsFxImportTool.importedItems(File(OsrsItemImportTool.ASSET_MAP)).flatMap { (upstream, _) ->
            val def = itemFiles[upstream]?.let { ModernItemDefDecoder.decode(upstream, it) } ?: return@flatMap emptyList()
            if (def.wearPos1 != OsrsBodyFrame.LEGS_SLOT) return@flatMap emptyList()
            OsrsItemImportTool.wornModels(def).map { model ->
                val local =
                    existing.modelLocalIdByMappingKey[ModelIdAllocator.mappingKey("upstream_model:$model", "model")]
                        ?: error("${def.name}: legs mesh $model has no local model in the asset map")
                Target(local, model, OsrsItemImportTool.wornFemale(def, model), def.name)
            }
        }.distinctBy { it.local }.sortedBy { it.local }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val forced = args.firstOrNull { it.startsWith("--replace=") }?.substringAfter('=')?.split(',')?.map { it.trim().toInt() }?.toSet().orEmpty()
        val dropped = mutableListOf<String>()
        val mutations = ArrayList<CacheMutation>()
        val foreign = ArrayList<String>()
        var done = 0
        val reader = ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE))
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        try {
            val targets = targets(reader, ImportBatchOrchestrator.readExistingMapping(File(OsrsItemImportTool.ASSET_MAP)))
            println("LEGS MESHES ${targets.size}")
            targets.forEach { t ->
                val current = library.data(INDEX_MODEL, t.local, 0) ?: error("model ${t.local} missing")
                val plain = OsrsModelConversion.convert(reader, t.upstream, dropped, OsrsBodyFrame.LEGS_SLOT, t.female)
                // The end state of a mesh that needs the A5 decal lift is the importer's (N12): shin fit, then the lift.
                val fitted = OsrsItemImportTool.decalLifted(plain)
                if (current.contentEquals(plain) || current.contentEquals(fitted)) {
                    done++
                    return@forEach
                }
                val unfitted =
                    Rev667ModelEncoder.encode(
                        OsrsBodyFrame.shiftToRev667(Rev667ModelDecoder.decode(OsrsModelConversion.convert(reader, t.upstream, dropped)), OsrsBodyFrame.LEGS_SLOT),
                    )
                // T5: --replace=<local,...> names meshes whose decal lift (OsrsDecalLiftTool, 2026-10-11) sits on the shin fit of before the
                // A3 change of 2026-10-12, so they match no conversion of today; they get today's fit with the lift.
                if (!current.contentEquals(unfitted) && t.local !in forced) {
                    foreign += "model ${t.local} (${t.item}, upstream ${t.upstream}) holds neither the old nor the fitted conversion"
                    return@forEach
                }
                val before = Rev667ModelDecoder.decode(current)
                val after = Rev667ModelDecoder.decode(fitted)
                val shin = (0 until after.vertexCount).filter { after.vertexLabel!![it] in OsrsBodyFrame.SHIN_LABELS }
                println(
                    "PLAN model ${t.local} ${t.item} ${if (t.female) "female" else "male"}: shin bottom " +
                        "${shin.maxOfOrNull { before.vertexY[it] }} -> ${shin.maxOfOrNull { after.vertexY[it] }}",
                )
                mutations += CacheMutation(INDEX_MODEL, t.local, 0, fitted, "osrs shin fit model ${t.local} (${t.item})", CacheItemProbeTool.sha1(current))
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
