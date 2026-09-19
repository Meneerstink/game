package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * OSRS npc type import into both rev-667 caches (RCV-012 decision 3: Ferox Enclave npcs).
 *
 * One OSRS npc brings its body and chathead models (index 7, converted like item models by [OsrsModelConversion]), a 667 BASType
 * (config group 32) holding its movement sequences, and a 667 NPCType (index 18, group = id ushr 7). OSRS fields map onto 667 opcodes
 * only where the meaning is proven:
 *
 * - NPCType 1/2/12/30-34/40/60/93/95/97/98/99/100/103/107 carry the same values in both clients (RuneLite `NpcLoader`,
 *   2011scape-client `NPCType.decode`); 101 contrast is divided by 5 exactly like item contrast ([OsrsItemImportTool.rev667Contrast]).
 * - Movement: OSRS keeps stand/walk/turn sequences on the npc, 667 on a BASType. The mapping is proven by the Man pair: 667 npc 1
 *   uses BAS 4 = ready 808, walk 819, opcode 40 = 820, 41 = 821, 42 = 822; OSRS Man 3106 = stand 808, walk 819, walk180 820,
 *   walkLeft 821, walkRight 822. So walk180 -> 40, walkLeft -> 41 (walkFollowTurnCcw), walkRight -> 42; the run (7/8/9) and crawl
 *   (3/4/5) triples follow the same 180/left/right order in both clients.
 * - Sequence ids 808/819/820/821/822 are used by the 667 Man BAS for exactly the roles OSRS gives them, so those ids are reused; every
 *   other sequence is imported with its framesets, bases and synth sounds through the [OsrsFxImportTool] conversion (shared asset-map
 *   keys, so a sequence is never imported twice).
 * - Dropped (reported): OSRS stats, category, head icons, params, varbit transforms, follower flags, height/footprint, sub-ops and
 *   retextures (textures are flattened into model colours).
 *
 * Usage: `<batch> [--apply]` - without `--apply` only plan + preflight run.
 */
object OsrsNpcImportTool {
    val TARGETS = OsrsItemImportTool.TARGETS
    const val INDEX_NPC = 18
    const val INDEX_CONFIG = 2
    const val GROUP_BAS = 32
    const val MODEL_ID_LIMIT = 65535

    /** Sequence ids proven to mean the same movement role in both caches (667 BAS 4 vs OSRS Man 3106). */
    val SHARED_MOVEMENT_SEQS = setOf(808, 819, 820, 821, 822)

    val BATCHES: Map<String, List<Int>> =
        mapOf(
            // OSRS Wiki "Ferox Enclave" personalities, ids from each npc's infobox.
            "ferox" to
                listOf(
                    10377, 10378, 10379, 10380, 10381, 10382, 10383, 10384, 10385, 10386, 10387, 10388, 10389, 10390, 10392,
                    10370, 6590, 7456, 8721, 7316, 7317, 10371, 10372, 10373,
                ),
            // Owner answer Q10: The Mimic encounter (RuneLite gameval NpcID TRAIL_MIMIC_NONCOMBAT 7979, TRAIL_MIMIC_COMBAT 8633,
            // TRAIL_MIMIC_SPAWN_MELEE / RANGER / MAGE 8635-8637) and Watson (TRAIL_WATSON_PRE_TALK 7303, OSRS Wiki infobox 7303,7304).
            "mimic" to listOf(7979, 8633, 8635, 8636, 8637, 7303),
            // Owner 2026-09-17 ("gebruik exact de deadmanmode guard van osrs"): OSRS Wiki "Guard (Deadman Mode)" Varrock
            // variants 6582 (slash) and 11203 (ranged), and "Wizguard" 14792. Combat stats/anims are server-side (city_guards.plugin.kts).
            // Owner 2026-09-19 (100 % OSRS): every per-city melee/ranged variant from the same infobox (ids 6574-6583, 6698-6702,
            // 11199-11209), imported with their OSRS names and "Attack" op. Re-running restores 6582/11203/14792 to the OSRS definition.
            "deadman-guard" to
                listOf(
                    6582, 11203, 14792,
                    6574, 11199, 6575, 6576, 6579, 11200, 6580, 11201, 6581, 11202, 6583, 11204,
                    6698, 11205, 6699, 11206, 6700, 11207, 6701, 11208, 6702, 11209,
                ),
        )

    /**
     * Batches whose entries are renamed clones of an OSRS npc: the same models, BAS and options, only the cache name changed.
     * skully-family (owner 2026-09-17): Skully 10382 (already imported as 14382) cloned as Skully Jr / Sr / Max / Bob.
     * Each clone is keyed `npc:<osrsId>#<name>` in the asset map so it is never confused with the original import.
     */
    val CLONE_BATCHES: Map<String, List<Pair<Int, String>>> =
        mapOf(
            "skully-family" to listOf(10382 to "Skully Jr", 10382 to "Skully Sr", 10382 to "Skully Max", 10382 to "Skully Bob"),
        )

    /**
     * Sequences a batch needs that no npc definition references (attacks, death), imported with the same conversion as the movement sets.
     * mimic: RuneLite gameval AnimationID MIMIC_MELEE 8308, MIMIC_CHARGE_RANGED 8309 (candy attack), MIMIC_DEATH 8310.
     */
    val EXTRA_SEQS: Map<String, List<Int>> = mapOf("mimic" to listOf(8308, 8309, 8310))

    private fun ByteArrayOutputStream.u8(v: Int) = write(v and 0xFF)

    private fun ByteArrayOutputStream.u16(v: Int) {
        write(v ushr 8 and 0xFF)
        write(v and 0xFF)
    }

    private fun ByteArrayOutputStream.str(v: String) {
        write(v.toByteArray(Charsets.ISO_8859_1))
        write(0)
    }

    /** 667 BASType opcodes for an OSRS npc's movement, sequence ids already local. */
    fun encode667Bas(
        n: OsrsNpcProbeTool.OsrsNpc,
        local: (Int) -> Int,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        fun seq(v: Int) = if (v < 0) 65535 else local(v)
        fun opt(code: Int, v: Int) {
            if (v >= 0) {
                out.u8(code)
                out.u16(local(v))
            }
        }
        out.u8(1)
        out.u16(seq(n.stand))
        out.u16(seq(n.walk))
        opt(2, n.crawl)
        opt(3, n.crawl180)
        opt(4, n.crawlLeft)
        opt(5, n.crawlRight)
        opt(6, n.run)
        opt(7, n.run180)
        opt(8, n.runLeft)
        opt(9, n.runRight)
        opt(40, n.walk180)
        opt(41, n.walkLeft)
        opt(42, n.walkRight)
        out.u8(0)
        return out.toByteArray()
    }

    /** 667 NPCType stream for [n]; model ids and the BAS id already local. */
    fun encode667Npc(
        n: OsrsNpcProbeTool.OsrsNpc,
        models: IntArray,
        chatheads: IntArray,
        basId: Int,
        dropped: MutableList<String>,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.u8(1)
        out.u8(models.size)
        models.forEach { check(it in 0..MODEL_ID_LIMIT) { "model $it above the unsigned-short limit" }; out.u16(it) }
        out.u8(2)
        out.str(n.name)
        if (n.size != 1) {
            out.u8(12)
            out.u8(n.size)
        }
        n.ops.forEachIndexed { i, op ->
            if (op != null) {
                out.u8(30 + i)
                out.str(op)
            }
        }
        if (n.recolorFind.isNotEmpty()) {
            out.u8(40)
            out.u8(n.recolorFind.size)
            for (i in n.recolorFind.indices) {
                out.u16(n.recolorFind[i])
                out.u16(n.recolorReplace[i])
            }
        }
        if (chatheads.isNotEmpty()) {
            out.u8(60)
            out.u8(chatheads.size)
            chatheads.forEach { out.u16(it) }
        }
        if (!n.minimap) out.u8(93)
        if (n.combat > 0) {
            out.u8(95)
            out.u16(n.combat)
        }
        if (n.widthScale != 128) {
            out.u8(97)
            out.u16(n.widthScale)
        }
        if (n.heightScale != 128) {
            out.u8(98)
            out.u16(n.heightScale)
        }
        when (n.renderPriority) {
            1 -> out.u8(99)
            2 -> dropped += "npc ${n.id}: render priority 2 (no 667 equivalent)"
        }
        if (n.ambient != 0) {
            out.u8(100)
            out.u8(n.ambient)
        }
        if (n.contrast != 0) {
            out.u8(101)
            out.u8(OsrsItemImportTool.rev667Contrast(n.contrast))
        }
        if (n.rotationSpeed != 32) {
            out.u8(103)
            out.u16(n.rotationSpeed)
        }
        if (!n.interactable) out.u8(107)
        out.u8(127)
        out.u16(basId)
        out.u8(0)
        if (n.retextures > 0) dropped += "npc ${n.id}: ${n.retextures} retextures (textures flattened into model colours)"
        if (n.multiNpc) dropped += "npc ${n.id}: varbit/varp transforms (OSRS config ids)"
        if (!n.rotationFlag) dropped += "npc ${n.id}: rotation flag (opcode 109 meaning differs in 667)"
        if (n.follower) dropped += "npc ${n.id}: follower flag"
        if (n.height >= 0 || n.footprint >= 0) dropped += "npc ${n.id}: height/footprint"
        n.dropped.forEach { dropped += "npc ${n.id}: $it" }
        return out.toByteArray()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val batchName = args.firstOrNull { !it.startsWith("--") } ?: error("Usage: <batch> [--apply]")
        val entries: List<Pair<Int, String?>> =
            BATCHES[batchName]?.map { it to null }
                ?: CLONE_BATCHES[batchName]?.map { (id, name) -> id to name }
                ?: error("Unknown batch '$batchName' (known: ${BATCHES.keys + CLONE_BATCHES.keys})")
        val apply = "--apply" in args
        val assetMap = File(OsrsItemImportTool.ASSET_MAP)
        val reader = ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE))
        val library = CacheLibrary(TARGETS[0])
        val mutations = mutableListOf<CacheMutation>()
        val records = mutableListOf<String>()
        val dropped = mutableListOf<String>()
        val plans = mutableListOf<String>()
        try {
            fun next(index: Int): Int = (library.index(index).archiveIds().maxOrNull() ?: -1) + 1
            fun nextPaged(index: Int, shift: Int): Int {
                val group = library.index(index).archiveIds().maxOrNull() ?: return 0
                val maxFile = library.index(index).archive(group)?.fileIds()?.maxOrNull() ?: -1
                return (group shl shift) + maxFile + 1
            }
            var nextNpc = nextPaged(INDEX_NPC, 7)
            var nextBas = (library.index(INDEX_CONFIG).archive(GROUP_BAS)?.fileIds()?.maxOrNull() ?: -1) + 1
            var nextSeq = nextPaged(OsrsFxImportTool.INDEX_SEQ, 7)
            var nextFrameset = next(OsrsFxImportTool.INDEX_FRAMES)
            var nextBase = next(OsrsFxImportTool.INDEX_BASES)
            var nextSynth = next(OsrsFxImportTool.INDEX_SYNTH)

            val census = ModelNamespaceCensusTool.census(TARGETS[0], TARGETS[1], assetMap)
            check(census.physicalDivergentIds.isEmpty() && census.referencedDivergentIds.isEmpty()) { "target caches diverge" }
            check(census.untracedReferenceTypes.isEmpty()) { "untraced model reference types: ${census.untracedReferenceTypes}" }
            val modelCandidates =
                (census.provenFreeHoles.filter { it > OsrsItemImportTool.SPOTANIM_MODEL_LIMIT && it <= MODEL_ID_LIMIT }.sorted() +
                    census.safeFreeRange.filter { it <= MODEL_ID_LIMIT }).toMutableList()
            println("CENSUS npc_model_candidates=${modelCandidates.size} next npc=$nextNpc bas=$nextBas seq=$nextSeq frameset=$nextFrameset base=$nextBase synth=$nextSynth")

            val localIds = OsrsFxImportTool.existingFx(assetMap).toMutableMap()
            fun local(kind: String, upstream: Int, allocate: () -> Int): Int =
                localIds.getOrPut("$kind:$upstream") { allocate().also { records += "$kind|$upstream|$it" } }

            fun put(index: Int, group: Int, file: Int, bytes: ByteArray, label: String) {
                val current = library.data(index, group, file)?.let { CacheItemProbeTool.sha1(it) }
                mutations += CacheMutation(index, group, file, bytes, label, expectedCurrentSha1 = current?.takeIf { it != CacheItemProbeTool.sha1(bytes) })
            }

            fun importSeq(seqId: Int): Int {
                if (seqId in SHARED_MOVEMENT_SEQS) return seqId
                localIds["seq:$seqId"]?.let { return it }
                val seqBytes = reader.file(ModernCacheReader.INDEX_CONFIG, ModernCacheReader.CONFIG_GROUP_SEQUENCE, seqId) ?: error("OSRS sequence $seqId missing")
                val seq = OsrsFxImportTool.decodeOsrsSeq(seqBytes)
                dropped += seq.dropped.map { "seq $seqId: $it" }
                val framesets = (seq.frames.map { it ushr 16 } + (seq.secondaryFrames?.map { it ushr 16 } ?: emptyList())).distinct()
                val framesetMap = framesets.associateWith { fs -> local("frameset", fs) { nextFrameset++ } }
                framesets.forEach { fs ->
                    reader.files(OsrsFxImportTool.INDEX_FRAMES, fs).forEach { (file, frameBytes) ->
                        val osrsBase = ((frameBytes[0].toInt() and 0xFF) shl 8) or (frameBytes[1].toInt() and 0xFF)
                        val baseBytes = reader.file(OsrsFxImportTool.INDEX_BASES, osrsBase, 0) ?: error("OSRS base $osrsBase missing")
                        val localBase = local("base", osrsBase) { nextBase++ }
                        val converted = OsrsFxImportTool.convertFrame(frameBytes, OsrsFxImportTool.baseTypes(baseBytes), localBase)
                        val base667 = OsrsFxImportTool.convertBase(baseBytes)
                        OsrsFxImportTool.check667Frame(converted, base667)
                        put(OsrsFxImportTool.INDEX_BASES, localBase, 0, base667, "osrs base $osrsBase")
                        put(OsrsFxImportTool.INDEX_FRAMES, framesetMap.getValue(fs), file, converted, "osrs frame $fs:$file")
                    }
                }
                seq.frames = IntArray(seq.frames.size) { (framesetMap.getValue(seq.frames[it] ushr 16) shl 16) or (seq.frames[it] and 0xFFFF) }
                seq.secondaryFrames = seq.secondaryFrames?.let { s -> IntArray(s.size) { (framesetMap.getValue(s[it] ushr 16) shl 16) or (s[it] and 0xFFFF) } }
                val soundMap =
                    seq.sounds.values.map { it.first }.distinct().associateWith { id ->
                        val synth = reader.file(OsrsFxImportTool.INDEX_SYNTH, id, 0) ?: error("OSRS synth $id missing")
                        local("synth", id) { nextSynth++ }.also { localId -> put(OsrsFxImportTool.INDEX_SYNTH, localId, 0, synth, "osrs synth $id") }
                    }
                seq.sounds.replaceAll { _, sound -> soundMap.getValue(sound.first) to sound.second }
                val localSeq = local("seq", seqId) { nextSeq++ }
                val seq667 = OsrsFxImportTool.encode667Seq(seq)
                OsrsFxImportTool.decode667SeqFrames(seq667)
                put(OsrsFxImportTool.INDEX_SEQ, localSeq ushr 7, localSeq and 0x7F, seq667, "osrs seq $seqId")
                return localSeq
            }

            val npcFiles = reader.files(ModernCacheReader.INDEX_CONFIG, OsrsNpcProbeTool.CONFIG_GROUP_NPC)
            val basBySet = mutableMapOf<String, Int>()
            for ((npcId, nameOverride) in entries) {
                val n = OsrsNpcProbeTool.decodeOsrs(npcId, npcFiles[npcId] ?: error("OSRS npc $npcId missing"))
                if (nameOverride != null) n.name = nameOverride
                if (OsrsNpcProbeTool.movementSeqs(n).isEmpty() && n.size == 1) {
                    // The OSRS definition carries no stand/walk sequences at all (e.g. the Deadman guards 6582/11203):
                    // the client then shows the model in its rest pose. 667 needs a BAS, so the humanoid default set
                    // proven by the Man pair (667 BAS 4 = OSRS Man 3106: 808/819/820/821/822) is applied - the same
                    // ids the OSRS Wizguard 14792 declares explicitly.
                    n.stand = 808
                    n.walk = 819
                    n.walk180 = 820
                    n.walkLeft = 821
                    n.walkRight = 822
                    dropped += "npc $npcId: no OSRS movement sequences; humanoid default set 808/819/820/821/822 applied"
                }
                val localModels = n.models.map { m -> local("npc_model", m) { modelCandidates.removeAt(0) }.also { put(ModelConvertTool.MODEL_INDEX, it, 0, OsrsModelConversion.convert(reader, m, dropped), "osrs npc model $m") } }
                val localHeads = n.chatheads.map { m -> local("npc_model", m) { modelCandidates.removeAt(0) }.also { put(ModelConvertTool.MODEL_INDEX, it, 0, OsrsModelConversion.convert(reader, m, dropped), "osrs npc model $m") } }
                val seqMap = OsrsNpcProbeTool.movementSeqs(n).values.distinct().associateWith { importSeq(it) }
                val movementKey = OsrsNpcProbeTool.movementSeqs(n).toString()
                val basBytes = encode667Bas(n) { seqMap.getValue(it) }
                val basId =
                    basBySet.getOrPut(movementKey) {
                        local("bas", npcId) { nextBas++ }.also { put(INDEX_CONFIG, GROUP_BAS, it, basBytes, "bas for osrs npc $npcId") }
                    }
                val localNpc =
                    if (nameOverride == null) {
                        local("npc", npcId) { nextNpc++ }
                    } else {
                        localIds.getOrPut("npc:$npcId#$nameOverride") { (nextNpc++).also { records += "npc_clone|$npcId#$nameOverride|$it" } }
                    }
                val npcBytes = encode667Npc(n, localModels.toIntArray(), localHeads.toIntArray(), basId, dropped)
                put(INDEX_NPC, localNpc ushr 7, localNpc and 0x7F, npcBytes, "osrs npc $npcId ${n.name}")
                plans += "PLAN npc $npcId '${n.name}' -> $localNpc bas=$basId models=${n.models.toList()}->$localModels heads=${n.chatheads.toList()}->$localHeads seqs=$seqMap ops=${n.ops.toList()}"
            }
            EXTRA_SEQS[batchName]?.forEach { seqId -> plans += "PLAN seq $seqId -> ${importSeq(seqId)}" }
        } finally {
            library.close()
            reader.close()
        }
        plans.forEach { println(it) }
        dropped.distinct().forEach { println("DROPPED $it") }
        val unique = mutations.distinctBy { it.describeLocation() }
        val transaction = CacheTransaction(targets = TARGETS, mutations = unique)
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
                if (kind == "npc_clone") {
                    // A renamed clone: keep the numeric upstream id (existingFx needs an int) and record the name separately.
                    val (osrsId, name) = upstream.split('#', limit = 2)
                    block.append("  - fx_kind: npc_clone\n    upstream_fx_id: $osrsId\n    clone_name: \"$name\"\n    local_fx_id: $localId\n    status: IMPORTED_BY_OSRS_NPC_TOOL\n    transaction: ${transaction.id}\n")
                } else {
                    block.append("  - fx_kind: $kind\n    upstream_fx_id: $upstream\n    local_fx_id: $localId\n    status: IMPORTED_BY_OSRS_NPC_TOOL\n    transaction: ${transaction.id}\n")
                }
            }
            val text = assetMap.readText()
            assetMap.writeText(if (text.endsWith("\n")) text + block else "$text\n$block")
            println("ASSET_MAP appended ${records.size} npc import entries")
        }
    }
}
