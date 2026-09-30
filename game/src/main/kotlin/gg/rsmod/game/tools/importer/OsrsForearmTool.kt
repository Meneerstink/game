package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/**
 * Gives every already imported OSRS item that hides the arms and leaves the lower forearm bare the 667 bare forearm
 * ([WornForearm]; owner 2026-09-30: forearms under Eclipse / Crystal / Masori / ... invisible, gloves floating). New imports
 * get the same from [OsrsItemImportTool] itself; this tool fixes what was imported before.
 *
 * One transaction on both caches: the forearm piece of each gender that any item needs, cut from the 667 default arms kit
 * into a census-proven free model id (recorded in the asset map as role [WornForearm.ROLE], `upstream_id` = the kit model,
 * so the importer reuses it), and on each affected item one more worn-mesh opcode (wear model 2 or 3 of that gender) with
 * that id; every other opcode is copied as is. The item's own worn meshes are read from the live cache, exactly as drawn.
 *
 * Usage: `[--apply]` - without it only plan + preflight run. A second run finds nothing to change.
 */
object OsrsForearmTool {
    private const val INDEX_ITEM = 19
    private const val INDEX_MODEL = 7

    data class Change(val local: Int, val name: String, val opcode: Int, val piece: WornForearm.Piece)

    /** Item opcode -> two-byte value, from [ItemDefCodec.describeOpcodes]. */
    fun shortOps(bytes: ByteArray): Map<Int, Int> =
        ItemDefCodec.describeOpcodes(bytes).mapNotNull { e ->
            val (op, value) = e.split('=', limit = 2)
            value.toIntOrNull()?.let { op.toInt() to it }
        }.toMap()

    /**
     * Every imported OSRS item that hides the arms, with the forearm opcode each gender still needs. [pieceIds]: the local
     * model id of each piece already in the cache (by kit model), so an item that already wears it counts as done.
     */
    fun plan(
        reader: ModernCacheReader,
        library: CacheLibrary,
        pieces: List<WornForearm.Piece>,
        pieceIds: Map<Int, Int>,
    ): List<Change> {
        val itemFiles = reader.files(ModernCacheReader.INDEX_CONFIG, ModernCacheReader.CONFIG_GROUP_ITEM)
        return OsrsFxImportTool.importedItems(File(OsrsItemImportTool.ASSET_MAP)).flatMap { (upstream, local) ->
            val def = itemFiles[upstream]?.let { ModernItemDefDecoder.decode(upstream, it) } ?: return@flatMap emptyList()
            if (!OsrsItemImportTool.removesArms(def)) return@flatMap emptyList()
            val ops = shortOps(CacheItemProbeTool.itemData(library, local) ?: error("item $local missing"))
            pieces.mapNotNull { piece ->
                val worn = piece.wornOps.filter { it in ops }
                if (worn.any { ops[it] == pieceIds[piece.kitModel] }) return@mapNotNull null
                val meshes =
                    worn.map { op ->
                        val model = ops.getValue(op)
                        Rev667ModelDecoder.decode(library.data(INDEX_MODEL, model, 0) ?: error("item $local worn model $model missing"), geometryOnly = true)
                    }
                WornForearm.opcodeFor(piece, worn.toSet(), meshes)?.let { Change(local, def.name, it, piece) }
            }
        }.sortedWith(compareBy({ it.local }, { it.opcode }))
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val assetMap = File(OsrsItemImportTool.ASSET_MAP)
        val reader = ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE))
        val mutations = ArrayList<CacheMutation>()
        val newModels = LinkedHashMap<Int, Int>()
        try {
            val existing = ImportBatchOrchestrator.readExistingMapping(assetMap)
            val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
            try {
                val pieces = WornForearm.pieces(library)
                val mapped = pieces.mapNotNull { p -> existing.modelLocalIdByMappingKey[ModelIdAllocator.mappingKey(p.sourceIdentity, WornForearm.ROLE)]?.let { p.kitModel to it } }.toMap()
                val changes = plan(reader, library, pieces, mapped)
                changes.forEach { println("PLAN item ${it.local} ${it.name}: + opcode ${it.opcode} = forearm of kit model ${it.piece.kitModel}") }
                if (changes.isEmpty()) {
                    println("NOTHING_TO_DO")
                    return
                }
                val census = ModelNamespaceCensusTool.census(OsrsItemImportTool.TARGETS[0], OsrsItemImportTool.TARGETS[1], assetMap)
                check(census.physicalDivergentIds.isEmpty() && census.referencedDivergentIds.isEmpty()) { "target caches diverge" }
                val candidates = OsrsItemImportTool.meshModelCandidates(census)
                val reserved = HashSet<Int>()
                val pieceIds =
                    changes.map { it.piece }.distinctBy { it.kitModel }.associate { piece ->
                        val id =
                            ModelIdAllocator.resolveFromCandidates(piece.sourceIdentity, WornForearm.ROLE, existing.modelLocalIdByMappingKey, candidates, reserved)
                        reserved += id
                        val current = library.data(INDEX_MODEL, id, 0)
                        when {
                            current == null -> {
                                mutations += CacheMutation(INDEX_MODEL, id, 0, piece.bytes, "rev-667 bare forearm (kit model ${piece.kitModel})")
                                newModels[piece.kitModel] = id
                            }
                            !current.contentEquals(piece.bytes) -> error("model $id is mapped to the forearm of kit model ${piece.kitModel} but holds other bytes")
                        }
                        println("FOREARM kit model ${piece.kitModel} (${if (piece.female) "female" else "male"}, kit forearm ends at y ${piece.kitForearmEnd}) -> local model $id")
                        piece.kitModel to id
                    }
                changes.groupBy { it.local }.forEach { (local, itemChanges) ->
                    val current = CacheItemProbeTool.itemData(library, local) ?: error("item $local missing")
                    val overrides = itemChanges.associate { it.opcode to pieceIds.getValue(it.piece.kitModel) }
                    val repaired = ItemDefCodec.cloneWithOverrides(current, emptyMap(), shortOverrides = overrides)
                    val before = ItemDefCodec.describeOpcodes(current).toSet()
                    val after = ItemDefCodec.describeOpcodes(repaired).toSet()
                    check(after - before == overrides.map { "${it.key}=${it.value}" }.toSet() && (before - after).isEmpty()) {
                        "item $local: re-encode changed more than the forearm opcodes"
                    }
                    mutations +=
                        CacheMutation(INDEX_ITEM, local ushr 8, local and 0xFF, repaired, "forearm item $local ${itemChanges.first().name}", expectedCurrentSha1 = CacheItemProbeTool.sha1(current))
                }
            } finally {
                library.close()
            }
        } finally {
            reader.close()
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
        if (newModels.isNotEmpty()) {
            val block = StringBuilder()
            block.append("  - name: rev-667 bare forearm (lower forearm under OSRS bodies that hide the arms kit, OsrsForearmTool / WornForearm)\n")
            block.append("    status: IMPORTED_BY_FOREARM_TOOL\n    transaction: ${transaction.id}\n    models:\n")
            newModels.forEach { (kit, local) -> block.append("      - role: ${WornForearm.ROLE}\n        upstream_id: $kit\n        local_id: $local\n") }
            val text = assetMap.readText()
            assetMap.writeText(if (text.endsWith("\n")) text + block else "$text\n$block")
        }
        println("APPLIED transaction=${applied.transactionId} writes=${applied.applied} skipped=${applied.skipped} VERIFY_OK")
    }
}
