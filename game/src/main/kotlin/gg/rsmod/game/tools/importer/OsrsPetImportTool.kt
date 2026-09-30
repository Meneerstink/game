package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/**
 * OSRS pets ([OsrsPetRoster]) into both rev-667 caches (owner 2026-09-30):
 *
 *  1. items: [OsrsItemImportTool] batch "pets" (every variant's pet item);
 *  2. classic followers: [OsrsNpcImportTool] batch "pets" (followers whose movement uses classic frames);
 *  3. skeletal followers: `skeletal` here - the follower's body models and its stand / walk sequences baked into classic 667 frames by
 *     [SkeletalBakeTool.bakeSet] (one shared base per npc), chatheads converted like any npc model, then a BAS and the npc type written
 *     with [OsrsNpcImportTool]'s own encoders;
 *  4. `table`: the server table [OsrsPetRoster.SERVER_TABLE] (local item id, local follower npc id per variant) from the asset map.
 *
 * Every write goes through [CacheTransaction] on both caches; ids are recorded in `OSRS_IMPORT_MASTER.yml`.
 *
 * Usage: `probe` | `skeletal [--apply]` | `table`.
 */
object OsrsPetImportTool {
    /** The follower npc of a roster row: the one of its infobox npc ids whose OSRS definition carries the follower flag (opcode 122). */
    fun followerNpc(
        row: OsrsPetRoster.Row,
        npcFiles: Map<Int, ByteArray>,
    ): OsrsNpcProbeTool.OsrsNpc? =
        row.osrsNpcs
            .mapNotNull { id -> npcFiles[id]?.let { OsrsNpcProbeTool.decodeOsrs(id, it) } }
            .firstOrNull { it.follower }

    private fun clusters(raw: ModelData): Int {
        val bones = raw.animayaBones ?: return 0
        val weights = raw.animayaWeights!!
        fun sig(v: Int) = bones[v].indices.sortedBy { bones[v][it] }.joinToString(",") { "${bones[v][it]}:${weights[v][it]}" }
        return (0 until raw.vertexCount).map { sig(it) }.distinct().size
    }

    /** Asset-map keys of the baked pieces: `upstream_fx_id` must be an int, so per-npc indexes are packed as `osrsNpc * 16 + index`. */
    private fun packed(
        osrsNpc: Int,
        index: Int,
    ): Int {
        require(index in 0..15)
        return osrsNpc * 16 + index
    }

    private fun skeletal(apply: Boolean) {
        val rows = OsrsPetRoster.load().filter { it.skeletal }
        val assetMap = File(OsrsItemImportTool.ASSET_MAP)
        val targets = OsrsItemImportTool.TARGETS
        val reader = ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE))
        val library = CacheLibrary(targets[0])
        val mutations = mutableListOf<CacheMutation>()
        val records = mutableListOf<String>()
        val dropped = mutableListOf<String>()
        val plans = mutableListOf<String>()
        try {
            fun next(index: Int): Int = (library.index(index).archiveIds().maxOrNull() ?: -1) + 1
            fun nextPaged(index: Int): Int {
                val group = library.index(index).archiveIds().maxOrNull() ?: return 0
                val maxFile = library.index(index).archive(group)?.fileIds()?.maxOrNull() ?: -1
                return (group shl 7) + maxFile + 1
            }
            var nextNpc = nextPaged(OsrsNpcImportTool.INDEX_NPC)
            var nextBas = (library.index(OsrsNpcImportTool.INDEX_CONFIG).archive(OsrsNpcImportTool.GROUP_BAS)?.fileIds()?.maxOrNull() ?: -1) + 1
            var nextSeq = nextPaged(OsrsFxImportTool.INDEX_SEQ)
            var nextFrameset = next(OsrsFxImportTool.INDEX_FRAMES)
            var nextBase = next(OsrsFxImportTool.INDEX_BASES)
            val census = ModelNamespaceCensusTool.census(targets[0], targets[1], assetMap)
            check(census.physicalDivergentIds.isEmpty() && census.referencedDivergentIds.isEmpty()) { "target caches diverge" }
            check(census.untracedReferenceTypes.isEmpty()) { "untraced model reference types: ${census.untracedReferenceTypes}" }
            val modelCandidates = OsrsItemImportTool.meshModelCandidates(census)
            val localIds = OsrsFxImportTool.existingFx(assetMap).toMutableMap()

            fun local(kind: String, upstream: Int, allocate: () -> Int): Int =
                localIds.getOrPut("$kind:$upstream") { allocate().also { records += "$kind|$upstream|$it" } }

            fun put(index: Int, group: Int, file: Int, bytes: ByteArray, label: String) {
                val current = library.data(index, group, file)?.let { CacheItemProbeTool.sha1(it) }
                mutations += CacheMutation(index, group, file, bytes, label, expectedCurrentSha1 = current?.takeIf { it != CacheItemProbeTool.sha1(bytes) })
            }

            val npcFiles = reader.files(ModernCacheReader.INDEX_CONFIG, OsrsNpcProbeTool.CONFIG_GROUP_NPC)
            for (row in rows) {
                val n = OsrsNpcProbeTool.decodeOsrs(row.osrsFollower, npcFiles[row.osrsFollower] ?: error("OSRS npc ${row.osrsFollower} missing"))
                check(n.follower) { "npc ${n.id} is not a follower" }
                val seqs = OsrsNpcProbeTool.movementSeqs(n).values.distinct()
                val localBase = local("pet_baked_base", n.id) { nextBase++ }
                val baked = SkeletalBakeTool.bakeSet(reader, n.models.toList(), seqs, localBase)
                put(OsrsFxImportTool.INDEX_BASES, localBase, 0, baked.base, "pet ${row.variant}: baked base for osrs npc ${n.id}")
                val localModels =
                    n.models.mapIndexed { i, m ->
                        local("pet_baked_model", packed(n.id, i)) { modelCandidates.removeAt(0) }
                            .also { put(ModelConvertTool.MODEL_INDEX, it, 0, baked.models[i], "pet ${row.variant}: osrs model $m baked for npc ${n.id}") }
                    }
                val localHeads =
                    n.chatheads.map { m ->
                        local("npc_model", m) { modelCandidates.removeAt(0) }
                            .also { put(ModelConvertTool.MODEL_INDEX, it, 0, OsrsModelConversion.convert(reader, m, dropped), "osrs npc model $m") }
                    }
                val seqMap =
                    seqs.mapIndexed { si, osrsSeq ->
                        val frameset = local("pet_baked_frameset", packed(n.id, si)) { nextFrameset++ }
                        baked.frames[si].forEachIndexed { fi, bytes -> put(OsrsFxImportTool.INDEX_FRAMES, frameset, fi, bytes, "pet ${row.variant}: baked frame $fi of osrs seq $osrsSeq") }
                        val seq = OsrsFxImportTool.Seq(frameDurations = baked.durations[si], frames = IntArray(baked.frames[si].size) { (frameset shl 16) or it })
                        val seqBytes = OsrsFxImportTool.encode667Seq(seq)
                        OsrsFxImportTool.decode667SeqFrames(seqBytes)
                        val localSeq = local("pet_baked_seq", packed(n.id, si)) { nextSeq++ }
                        put(OsrsFxImportTool.INDEX_SEQ, localSeq ushr 7, localSeq and 0x7F, seqBytes, "pet ${row.variant}: baked osrs seq $osrsSeq for npc ${n.id}")
                        osrsSeq to localSeq
                    }.toMap()
                val basId = local("bas", n.id) { nextBas++ }
                put(OsrsNpcImportTool.INDEX_CONFIG, OsrsNpcImportTool.GROUP_BAS, basId, OsrsNpcImportTool.encode667Bas(n) { seqMap.getValue(it) }, "bas for osrs npc ${n.id}")
                val localNpc = local("npc", n.id) { nextNpc++ }
                val npcBytes = OsrsNpcImportTool.encode667Npc(n, localModels.toIntArray(), localHeads.toIntArray(), basId, dropped)
                put(OsrsNpcImportTool.INDEX_NPC, localNpc ushr 7, localNpc and 0x7F, npcBytes, "osrs npc ${n.id} ${n.name}")
                plans +=
                    "PLAN pet '${row.variant}' npc ${n.id} -> $localNpc bas=$basId models=${n.models.toList()}->$localModels heads=$localHeads " +
                    "seqs=$seqMap vertices=${baked.vertices} clusters=${baked.signatureClusters}->${baked.clusters} " +
                    "error max=${"%.1f".format(baked.maxError)} mean=${"%.2f".format(baked.meanError)} far=${baked.farOff}/${baked.vertexFrames} (667 units)"
            }
        } finally {
            library.close()
            reader.close()
        }
        plans.forEach { println(it) }
        dropped.distinct().forEach { println("DROPPED $it") }
        val unique = mutations.distinctBy { it.describeLocation() }
        val transaction = CacheTransaction(targets = targets, mutations = unique)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} mutations=${unique.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKING: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY_RUN records=${records.size}")
            return
        }
        val result = transaction.apply(plan)
        val problems = transaction.verify()
        if (problems.isNotEmpty()) {
            problems.forEach { println("  VERIFY_FAILURE: $it") }
            println("ROLLED_BACK ${transaction.rollback()}")
            error("transaction ${transaction.id} failed verification and was rolled back")
        }
        println("APPLIED transaction=${result.transactionId} writes=${result.applied} skipped=${result.skipped}")
        if (records.isNotEmpty()) {
            val block = StringBuilder()
            records.forEach { r ->
                val (kind, upstream, localId) = r.split('|')
                block.append("  - fx_kind: $kind\n    upstream_fx_id: $upstream\n    local_fx_id: $localId\n    status: IMPORTED_BY_OSRS_PET_TOOL\n    transaction: ${transaction.id}\n")
            }
            val text = assetMap.readText()
            assetMap.writeText(if (text.endsWith("\n")) text + block else "$text\n$block")
            println("ASSET_MAP appended ${records.size} pet import entries")
        }
    }

    /** Writes [OsrsPetRoster.SERVER_TABLE]; every roster row must have both its item and its follower npc imported. */
    private fun table() {
        val assetMap = File(OsrsItemImportTool.ASSET_MAP)
        val items = OsrsFxImportTool.importedItems(assetMap)
        val fx = OsrsFxImportTool.existingFx(assetMap)
        val rows = OsrsPetRoster.load()
        val missing = rows.filter { items[it.osrsItem] == null || fx["npc:${it.osrsFollower}"] == null }
        check(missing.isEmpty()) { "not imported yet: ${missing.map { "${it.variant} item=${it.osrsItem}:${items[it.osrsItem]} npc=${it.osrsFollower}:${fx["npc:${it.osrsFollower}"]}" }}" }
        val out = StringBuilder()
        out.append("# Generated by OsrsPetImportTool table from OSRS_IMPORT_MASTER.yml - do not edit; the source is osrs_pets.tsv.\n")
        out.append("# pet\tvariant\titem\tnpc\tosrs_item\tosrs_npc\n")
        rows.forEach { r -> out.append("${r.pet}\t${r.variant}\t${items.getValue(r.osrsItem)}\t${fx.getValue("npc:${r.osrsFollower}")}\t${r.osrsItem}\t${r.osrsFollower}\n") }
        File(OsrsPetRoster.SERVER_TABLE).writeText(out.toString())
        println("TABLE_WRITTEN ${OsrsPetRoster.SERVER_TABLE} rows=${rows.size}")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val rows = OsrsPetRoster.load()
        when (args.firstOrNull()) {
            "skeletal" -> skeletal("--apply" in args)
            "table" -> table()
            "probe" ->
                ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE)).use { reader ->
                    val npcFiles = reader.files(ModernCacheReader.INDEX_CONFIG, OsrsNpcProbeTool.CONFIG_GROUP_NPC)
                    val itemFiles = reader.files(ModernCacheReader.INDEX_CONFIG, ModernCacheReader.CONFIG_GROUP_ITEM)
                    val seqFiles = reader.files(ModernCacheReader.INDEX_CONFIG, ModernCacheReader.CONFIG_GROUP_SEQUENCE)
                    var skeletalRows = 0
                    var refused = 0
                    for (row in rows) {
                        val item = itemFiles[row.osrsItem]?.let { ModernItemDefDecoder.decode(row.osrsItem, it) }
                        val npc = followerNpc(row, npcFiles)
                        if (item == null || npc == null) {
                            refused++
                            println("PET '${row.pet}' / '${row.variant}' item=${row.osrsItem}${if (item == null) " ABSENT" else ""} npcs=${row.osrsNpcs} FOLLOWER_ABSENT=${npc == null}")
                            continue
                        }
                        val seqs = OsrsNpcProbeTool.movementSeqs(npc).values.distinct()
                        val skeletal = seqs.filter { s -> seqFiles[s]?.let { SkeletalBakeTool.decodeSkeletalSeq(it) } != null }
                        val models =
                            npc.models.map { m ->
                                val raw = ModernModelDecoder.decode(reader.file(7, m, 0) ?: error("model $m missing"), flattenTextures = true)
                                "$m:v${raw.vertexCount}${if (raw.animayaBones != null) ":skin${clusters(raw)}" else ""}"
                            }
                        if (skeletal.isNotEmpty()) skeletalRows++
                        println(
                            "PET '${row.pet}' / '${row.variant}' item ${row.osrsItem} '${item.name}' iops=${item.inventoryOptions.toList()} " +
                                "npc ${npc.id} '${npc.name}' size=${npc.size} ops=${npc.ops.toList()} models=$models heads=${npc.chatheads.toList()} " +
                                "seqs=$seqs skeletal=$skeletal scale=${npc.widthScale}x${npc.heightScale}",
                        )
                    }
                    println("SUMMARY rows=${rows.size} refused=$refused skeletal_rows=$skeletalRows")
                }
            else -> error("Usage: probe | skeletal [--apply] | table")
        }
    }
}
