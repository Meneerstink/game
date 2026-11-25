package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/**
 * O10: restore the worn meshes of the native 4023 without replacing its inventory art/menu.
 * Sources: OSRS Wiki Monkey talisman (2015 worn-model addition), pinned OSRS cache 2686 item 4023.
 * Uses the existing conversion, census, allocator, batch planner and paired-cache transaction.
 * Default is read-only preflight; Claude may apply with --apply after reviewing the printed plan.
 */
object MonkeyTalismanWornTool {
    private const val ITEM = 4023
    private const val IDENTITY = "upstream_item:4023"

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.all { it == "--apply" }) { "Usage: MonkeyTalismanWornTool [--apply]" }
        val targets = OsrsItemImportTool.TARGETS
        val assetMap = File(OsrsItemImportTool.ASSET_MAP)
        val apply = "--apply" in args
        var durable = ImportBatchOrchestrator.readExistingMapping(assetMap)
        val recovered = ImportBatchOrchestrator.recoverUnfinalized(targets, durable.mappedItemSourceIdentities)
        if (apply && recovered.isNotEmpty()) {
            ImportBatchOrchestrator.finalizeDurableMapping(assetMap, recovered, durable)
            durable = ImportBatchOrchestrator.readExistingMapping(assetMap)
        }
        val mappedItem = durable.itemLocalIdBySourceIdentity[IDENTITY]
            ?: recovered.firstOrNull { it.sourceIdentity == IDENTITY }?.localId
        check(mappedItem == null || mappedItem == ITEM) { "OSRS 4023 already maps to $mappedItem; review before replacing the native item" }
        val existing = durable.copy(
            itemLocalIdBySourceIdentity = durable.itemLocalIdBySourceIdentity +
                recovered.associate { it.sourceIdentity to it.localId } + (IDENTITY to ITEM),
            modelLocalIdByMappingKey = durable.modelLocalIdByMappingKey + recovered.flatMap { it.models }.associate {
                ModelIdAllocator.mappingKey(it.sourceIdentity, it.role) to it.localId
            },
        )
        val before = targets.map { path ->
            val library = CacheLibrary(path)
            try { CacheItemProbeTool.itemData(library, ITEM) ?: error("Missing native item $ITEM in $path") }
            finally { library.close() }
        }
        check(before.drop(1).all { it.contentEquals(before.first()) }) { "Native item differs between caches" }
        val census = ModelNamespaceCensusTool.census(targets[0], targets[1], assetMap)
        check(census.physicalDivergentIds.isEmpty() && census.referencedDivergentIds.isEmpty()) { "Target model caches differ" }
        check(census.untracedReferenceTypes.isEmpty()) { "Untraced model references: ${census.untracedReferenceTypes}" }
        val dropped = mutableListOf<String>()
        ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE)).use { reader ->
            val def = ModernItemDefDecoder.decode(ITEM, reader.file(ModernCacheReader.INDEX_CONFIG, 10, ITEM)
                ?: error("Missing OSRS item $ITEM"))
            check(def.name == "Monkey talisman" && def.wearPos1 == 3)
            check(def.maleModel0 >= 0 && def.femaleModel0 >= 0)
            check(listOf(def.maleModel1, def.maleModel2, def.femaleModel1, def.femaleModel2).all { it == -1 }) {
                "Upstream worn-model structure changed; review the patch"
            }
            val upstream = mapOf(23 to def.maleModel0, 25 to def.femaleModel0)
            val models = upstream.values.distinct().map { model ->
                ImportBatchOrchestrator.ModelSource("upstream_model:$model", "model",
                    OsrsItemImportTool.decalLifted(OsrsModelConversion.convert(reader, model, dropped, wornSlot = 3)))
            }
            val source = ImportBatchOrchestrator.ItemSource(IDENTITY, def.name, models,
                CacheItemProbeTool.sha1(before.first())) { local ->
                ItemDefCodec.cloneWithOverrides(before.first(), emptyMap(),
                    shortOverrides = upstream.mapValues { (_, model) -> local.getValue("upstream_model:$model") })
            }
            val plan = ImportBatchOrchestrator.plan(targets, listOf(source), existing,
                ImportBatchOrchestrator.nextFreeItemId(targets), census.safeFreeRange.first, census.safeFreeRange.last,
                modelCandidates = OsrsItemImportTool.meshModelCandidates(census))
            println("NATIVE $ITEM: only opcodes 23/25 (worn male/female), source models=$upstream")
            plan.items.single().models.forEach { println("MODEL ${it.source.sourceIdentity} -> ${it.localId}") }
            val preflight = plan.transaction.preflight()
            preflight.forEach { println(it) }
            dropped.forEach { println("CONVERSION $it") }
            val errors = plan.transaction.blockingErrors(preflight)
            check(errors.isEmpty()) { errors.joinToString("\n") }
            if (!apply) {
                println("DRY-RUN OK: no cache or ledger changes")
                return
            }
            plan.transaction.apply(preflight)
            val problems = plan.transaction.verify().toMutableList()
            targets.forEach { path ->
                val library = CacheLibrary(path)
                try {
                    plan.items.single().models.forEach { model ->
                        runCatching { Rev667ModelDecoder.decode(ModelArchive.read(library, model.localId)
                            ?: error("Missing mesh ${model.localId}")) }.exceptionOrNull()?.let {
                            problems += "$path mesh ${model.localId}: ${it.message}"
                        }
                    }
                } finally { library.close() }
            }
            if (problems.isNotEmpty()) {
                plan.transaction.rollback()
                error(problems.joinToString("\n"))
            }
            ImportBatchOrchestrator.finalizeDurableMapping(assetMap, ImportBatchOrchestrator.toMappedItems(plan), durable)
            println("APPLIED ${plan.transaction.id}")
        }
    }
}
