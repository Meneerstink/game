package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.RspsPaths
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * N101: ONE verified dual-cache transaction for the chest's closed/open scenery and OSRS chest guardian 15564.
 * Ports OsrsLocImportTool + OsrsNpcImportTool's classic frames and encode667Npc. The pinned chest sequences are CLASSIC.
 * The guardian reuses the already imported Zemouregal models/movement/combat animations, with source equality checks.
 * Usage: probe | plan | apply. No server shutdown or live override is performed here.
 */
object DeadmanChestImportTool {
    private val locs = listOf(60246, 60248, 60250)
    private const val GUARDIAN = 15564
    private const val BREACH_GUARDIAN = 15237

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.singleOrNull() ?: error("Usage: probe | plan | apply")
        require(mode in setOf("probe", "plan", "apply"))
        val mutations = ArrayList<CacheMutation>()
        val records = linkedMapOf<String, Int>()
        val ledger = File(OsrsItemImportTool.ASSET_MAP)
        val ids = OsrsFxImportTool.existingFx(ledger)
        val marker = File(RspsPaths.root("team/cache-pending/N101-transaction.txt"))
        if (mode != "probe") {
            check(ids["osrs_loc_loc:60246"] == null && ids["osrs_loc_loc:60248"] == null && ids["npc:$GUARDIAN"] == null) {
                "Chest already has import ledger entries: use its existing journal/config; never allocate another set"
            }
            check(!marker.exists()) { "Chest import already prepared: ${marker.path}; inspect the recorded journal before any retry" }
        }
        var closed = -1
        var open = -1
        var guardian = -1
        var spawn = -1
        var spawnTicks = 0
        ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE)).use { reader ->
            val sourceLocs = reader.files(ModernCacheReader.INDEX_CONFIG, 6)
            val sourceNpcs = reader.files(ModernCacheReader.INDEX_CONFIG, OsrsNpcProbeTool.CONFIG_GROUP_NPC)
            val n = OsrsNpcProbeTool.decodeOsrs(GUARDIAN, sourceNpcs.getValue(GUARDIAN))
            val breach = OsrsNpcProbeTool.decodeOsrs(BREACH_GUARDIAN, sourceNpcs.getValue(BREACH_GUARDIAN))
            for (id in locs) {
                val def = ModernObjectDef.decode(id, sourceLocs.getValue(id))
                val skeletal = if (def.animationId >= 0) reader.file(2, ModernCacheReader.CONFIG_GROUP_SEQUENCE, def.animationId)?.let { SkeletalBakeTool.decodeSkeletalSeq(it) } else null
                println("SOURCE loc=$id name=${def.name} models=${def.models.toList()} animation=${def.animationId} skeletal=${skeletal != null} size=${def.sizeX}x${def.sizeY} options=${def.options.toList()} mapArea=${def.mapAreaId} mapScene=${def.mapSceneId}")
                check(def.unknownOpcodes.isEmpty()) { "Unrecognized source loc opcodes: ${def.unknownOpcodes}" }
                def.models.forEach { model ->
                    val dropped = ArrayList<String>()
                    val converted = OsrsModelConversion.convert(reader, model, dropped)
                    val back = Rev667ModelDecoder.decode(converted)
                    println("VALIDATE model=$model vertices=${back.vertexCount} faces=${back.faceCount} dropped=$dropped")
                }
                if (def.animationId >= 0) {
                    check(skeletal == null) { "Source is skeletal: separate bake/error review required" }
                    validateClassicSource(reader, def.animationId)
                }
            }
            println("SOURCE npc=$GUARDIAN name=${n.name} combat=${n.combat} models=${n.models.toList()} movement=${OsrsNpcProbeTool.movementSeqs(n)}")
            check(n.models.contentEquals(breach.models) && n.chatheads.contentEquals(breach.chatheads)) { "Guardian models differ from imported breach Zemouregal; import its missing assets first" }
            check(OsrsNpcProbeTool.movementSeqs(n) == OsrsNpcProbeTool.movementSeqs(breach)) { "Guardian rig differs from imported breach Zemouregal" }
            check(n.combat == 322) { "Unexpected source guardian combat level ${n.combat}" }
            if (mode == "probe") return

            val cache = CacheLibrary(OsrsItemImportTool.TARGETS[0])
            try {
                fun nextPaged(index: Int, shift: Int): Int {
                    val group = cache.index(index).archiveIds().maxOrNull() ?: return 0
                    return (group shl shift) + (cache.index(index).archive(group)?.fileIds()?.maxOrNull() ?: -1) + 1
                }
                var nextLoc = nextPaged(FeroxImportTool.LOC_INDEX, 8)
                var nextNpc = nextPaged(OsrsNpcImportTool.INDEX_NPC, 7)
                var nextSeq = nextPaged(OsrsFxImportTool.INDEX_SEQ, 7)
                var nextBase = (cache.index(OsrsFxImportTool.INDEX_BASES).archiveIds().maxOrNull() ?: -1) + 1
                var nextFrameset = (cache.index(OsrsFxImportTool.INDEX_FRAMES).archiveIds().maxOrNull() ?: -1) + 1
                var nextSynth = (cache.index(OsrsFxImportTool.INDEX_SYNTH).archiveIds().maxOrNull() ?: -1) + 1
                val allocated = ids.toMutableMap()
                val census = ModelNamespaceCensusTool.census(OsrsItemImportTool.TARGETS[0], OsrsItemImportTool.TARGETS[1], ledger)
                check(census.physicalDivergentIds.isEmpty() && census.referencedDivergentIds.isEmpty() && census.untracedReferenceTypes.isEmpty()) { "Model census is not safe for allocation" }
                val free = census.provenFreeHoles.iterator()
                fun put(index: Int, group: Int, file: Int, bytes: ByteArray, label: String) {
                    val previous = cache.data(index, group, file)
                    mutations += CacheMutation(index, group, file, bytes, label, expectedCurrentSha1 = previous?.let { CacheItemProbeTool.sha1(it) })
                }
                fun asset(kind: String, upstream: Int, allocate: () -> Int): Int =
                    allocated.getOrPut("deadman_chest_$kind:$upstream") { allocate().also { records["deadman_chest_$kind:$upstream"] = it } }

                // OsrsNpcImportTool.importSeq: preserve the object's ORIGINAL classic rig, never the player's merged rig.
                fun classicSeq(upstream: Int): Pair<Int, Int> {
                    val raw = reader.file(2, ModernCacheReader.CONFIG_GROUP_SEQUENCE, upstream) ?: error("Missing sequence $upstream")
                    val seq = OsrsFxImportTool.decodeOsrsSeq(raw)
                    val duration = ((seq.frameDurations.sum() + 29) / 30).coerceAtLeast(1)
                    val frameSets = (seq.frames.map { it ushr 16 } + (seq.secondaryFrames ?: IntArray(0)).map { it ushr 16 }).distinct()
                    val mapped = frameSets.associateWith { asset("frameset", it) { nextFrameset++ } }
                    frameSets.forEach { fs ->
                        reader.files(OsrsFxImportTool.INDEX_FRAMES, fs).forEach { (file, frame) ->
                            val baseId = ((frame[0].toInt() and 255) shl 8) or (frame[1].toInt() and 255)
                            val baseRaw = reader.file(OsrsFxImportTool.INDEX_BASES, baseId, 0) ?: error("Missing base $baseId")
                            val base = asset("base", baseId) { nextBase++ }
                            val converted = OsrsFxImportTool.convertFrame(frame, OsrsFxImportTool.baseTypes(baseRaw), base)
                            val base667 = OsrsFxImportTool.convertBase(baseRaw)
                            OsrsFxImportTool.check667Frame(converted, base667)
                            put(OsrsFxImportTool.INDEX_BASES, base, 0, base667, "chest classic base $baseId")
                            put(OsrsFxImportTool.INDEX_FRAMES, mapped.getValue(fs), file, converted, "chest classic frame $fs:$file")
                        }
                    }
                    seq.frames = seq.frames.map { (mapped.getValue(it ushr 16) shl 16) or (it and 65535) }.toIntArray()
                    seq.secondaryFrames = seq.secondaryFrames?.map { (mapped.getValue(it ushr 16) shl 16) or (it and 65535) }?.toIntArray()
                    val sounds = seq.sounds.values.map { it.first }.distinct().associateWith { source ->
                        val local = ids["synth:$source"] ?: asset("synth", source) { nextSynth++ }
                        put(OsrsFxImportTool.INDEX_SYNTH, local, 0, reader.file(OsrsFxImportTool.INDEX_SYNTH, source, 0) ?: error("Missing sound $source"), "chest classic sound $source")
                        local
                    }
                    seq.sounds.replaceAll { _, sound -> sounds.getValue(sound.first) to sound.second }
                    val local = asset("seq", upstream) { nextSeq++ }
                    val bytes = OsrsFxImportTool.encode667Seq(seq)
                    OsrsFxImportTool.decode667SeqFrames(bytes)
                    put(OsrsFxImportTool.INDEX_SEQ, local ushr 7, local and 127, bytes, "chest classic sequence $upstream")
                    return local to duration
                }
                for (id in locs) {
                    val def = ModernObjectDef.decode(id, sourceLocs.getValue(id))
                    check(def.models.size == 1) { "Unexpected chest model count ${def.models.size}" }
                    val model = def.models.single()
                    val localModel = asset("model", model) { free.next() }
                    val localLoc = ids["osrs_loc_loc:$id"] ?: nextLoc++.also { records["osrs_loc_loc:$id"] = it }
                    val dropped = ArrayList<String>()
                    var localSeq: Int? = null
                    if (def.animationId >= 0) {
                        val seqBytes = reader.file(2, ModernCacheReader.CONFIG_GROUP_SEQUENCE, def.animationId) ?: error("Missing chest animation ${def.animationId}")
                        check(SkeletalBakeTool.decodeSkeletalSeq(seqBytes) == null) { "Source changed to skeletal animation: requires a separately reviewed bake/error gate" }
                        val classic = classicSeq(def.animationId)
                        localSeq = classic.first
                        if (id == 60246) spawnTicks = classic.second
                        put(7, localModel, 0, OsrsModelConversion.convert(reader, model, dropped), "chest $id classic model $model")
                    } else {
                        put(7, localModel, 0, OsrsModelConversion.convert(reader, model, dropped), "chest $id model $model")
                    }
                    val bytes = Rev667LocType.encodeFromModern(def, { check(it == model); localModel }, dropped) { check(it == def.animationId); localSeq }
                    check(Rev667LocType.decode(localLoc, bytes).allModels == listOf(localModel))
                    put(FeroxImportTool.LOC_INDEX, localLoc ushr 8, localLoc and 0xFF, bytes, "Deadman's chest OSRS $id -> $localLoc")
                    println("PLAN loc=$id local=$localLoc model=$localModel sequence=$localSeq dropped=$dropped")
                    when (id) {
                        60246 -> spawn = localLoc
                        60248 -> { check(def.options.any { it.equals("Loot", true) }) { "Closed chest lacks source Loot option" }; closed = localLoc }
                        60250 -> open = localLoc
                    }
                }
                // Same source body and movement as the existing breach import, verified above; preserve the NEW variant's
                // source scale, options, colours and combat level through the existing faithful NPC encoder.
                fun importedModel(id: Int): Int = ids["npc_model:$id"]?.also { local ->
                    put(7, local, 0, ModelArchive.read(cache, local) ?: error("Missing guardian model $id"), "existing guardian model $id")
                } ?: error("Missing imported guardian model $id")
                val bas = ids["bas:$BREACH_GUARDIAN"] ?: error("Missing imported Zemouregal BAS")
                put(OsrsNpcImportTool.INDEX_CONFIG, OsrsNpcImportTool.GROUP_BAS, bas,
                    cache.data(OsrsNpcImportTool.INDEX_CONFIG, OsrsNpcImportTool.GROUP_BAS, bas) ?: error("Missing guardian BAS"), "existing guardian BAS $bas")
                guardian = ids["npc:$GUARDIAN"] ?: nextNpc++.also { records["npc:$GUARDIAN"] = it }
                val dropped = ArrayList<String>()
                val bytes = OsrsNpcImportTool.encode667Npc(n, n.models.map(::importedModel).toIntArray(), n.chatheads.map(::importedModel).toIntArray(), bas, dropped)
                put(OsrsNpcImportTool.INDEX_NPC, guardian ushr 7, guardian and 0x7F, bytes, "Chest guardian OSRS $GUARDIAN -> $guardian")
                println("PLAN guardian=$guardian combat=${n.combat} bas=$bas dropped=$dropped")
            } finally {
                cache.close()
            }
        }
        val tx = CacheTransaction(OsrsItemImportTool.TARGETS, mutations.distinctBy { it.describeLocation() })
        val preflight = tx.preflight()
        val errors = tx.blockingErrors(preflight)
        println("PREFLIGHT transaction=${tx.id} mutations=${mutations.size} outcomes=${preflight.groupingBy { it.outcome }.eachCount()}")
        check(errors.isEmpty()) { "Preflight blocked: $errors" }
        if (mode != "apply") return
        val block = buildString {
            records.forEach { (key, local) ->
                val (kind, upstream) = key.split(':')
                append("  - fx_kind: $kind\n    upstream_fx_id: $upstream\n    local_fx_id: $local\n    status: IMPORTED_BY_DEADMAN_CHEST_TOOL\n    transaction: ${tx.id}\n")
            }
        }
        val config = File(RspsPaths.root("game/game/data/cfg/deadman/chest-assets.json"))
        val originalLedger = ledger.readBytes()
        val afterLedger = originalLedger + "\n$block".toByteArray(Charsets.UTF_8)
        val afterConfig = "{\"closedLoc\":$closed,\"openLoc\":$open,\"spawnLoc\":$spawn,\"spawnTicks\":$spawnTicks,\"guardianNpc\":$guardian,\"transaction\":\"${tx.id}\"}\n".toByteArray(Charsets.UTF_8)
        // Durable metadata BEFORE any cache mutation. A crash leaves an explicit pending journal, never a blind re-import.
        tx.journalDir.mkdirs()
        File(tx.journalDir, "ledger-before.yml").writeBytes(originalLedger)
        File(tx.journalDir, "ledger-after.yml").writeBytes(afterLedger)
        File(tx.journalDir, "chest-assets.json").writeBytes(afterConfig)
        marker.writeText("PENDING transaction=${tx.id} journal=${tx.journalDir.path}\nMetadata must be recovered from this journal after verifying the cache; another apply is refused.\n")
        var verified = false
        try {
            val result = tx.apply(preflight)
            val failures = tx.verify()
            if (failures.isNotEmpty()) {
                tx.rollback()
                error("Verification failed; rolled back: $failures")
            }
            verified = true
            println("APPLIED transaction=${result.transactionId} writes=${result.applied} VERIFY_OK")
        } finally {
            if (verified) {
                check(ledger.readBytes().contentEquals(originalLedger)) { "Ledger changed during import; retain pending journal for reviewed metadata recovery" }
                atomicWrite(ledger, afterLedger)
                atomicWrite(config, afterConfig) // activation is LAST, after both caches and ledger are durable
                marker.writeText("COMPLETE transaction=${tx.id} journal=${tx.journalDir.path}\n")
                println("METADATA_OK assets=${config.path} ledgerBackup=${tx.journalDir.path}")
            }
        }
    }

    private fun atomicWrite(target: File, bytes: ByteArray) {
        val temp = File(target.parentFile, target.name + ".N101.tmp")
        temp.writeBytes(bytes)
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    /** Read-only format validation is available even while another lane owns the target caches. IDs here are test placeholders. */
    private fun validateClassicSource(reader: ModernCacheReader, id: Int) {
        val seq = OsrsFxImportTool.decodeOsrsSeq(reader.file(2, ModernCacheReader.CONFIG_GROUP_SEQUENCE, id) ?: error("Missing sequence $id"))
        var count = 0
        (seq.frames.map { it ushr 16 } + (seq.secondaryFrames ?: IntArray(0)).map { it ushr 16 }).distinct().forEach { fs ->
            reader.files(OsrsFxImportTool.INDEX_FRAMES, fs).forEach { (_, frame) ->
                val baseId = ((frame[0].toInt() and 255) shl 8) or (frame[1].toInt() and 255)
                val base = reader.file(OsrsFxImportTool.INDEX_BASES, baseId, 0) ?: error("Missing base $baseId")
                OsrsFxImportTool.check667Frame(OsrsFxImportTool.convertFrame(frame, OsrsFxImportTool.baseTypes(base), 0), OsrsFxImportTool.convertBase(base))
                count++
            }
        }
        OsrsFxImportTool.decode667SeqFrames(OsrsFxImportTool.encode667Seq(seq))
        println("VALIDATE classic=$id frames=$count ticks=${(seq.frameDurations.sum() + 29) / 30} sounds=${seq.sounds.size}")
    }
}
