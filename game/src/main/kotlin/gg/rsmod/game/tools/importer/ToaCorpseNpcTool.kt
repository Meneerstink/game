package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.RspsPaths
import java.io.File

/**
 * N606 (13-11 nachtrun): the ToA boss corpses. OSRS cache2686 has an npc per corpse, without options, whose stand sequence is the
 * one-tick dead pose of the boss rig: 11722 toa_kephri_boss_dead (stand 9583 npc_kephri_dead), 11733 toa_zebak_dead (stand 9636
 * npc_zebak01_dead) and 11734 toa_zebak_tail_dead (stand 9637 npc_zebak02_dead). Same models and rig as the live boss (11719 / 11730 /
 * 11731), so nothing is baked again: the corpse npc reuses the live boss's baked models and its already imported dead sequence
 * (data/cfg/toa/ids.tsv 'baked for npc <base>'); only a BAS (stand = the dead pose) and the npc type are new.
 *
 * Every write goes through [CacheTransaction] on both caches; ids are appended to the asset map (kinds npc / bas, like the importers).
 * Prints the ids.tsv rows and the sidecar job line (SkeletalSidecarExportTool cache-file) for each corpse.
 *
 * Usage: `plan` | `apply`
 */
object ToaCorpseNpcTool {
    /** OSRS corpse npc -> the OSRS live npc whose bake it reuses. */
    val CORPSES = listOf(11722 to 11719, 11733 to 11730, 11734 to 11731)

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = args.firstOrNull() == "apply"
        val assetMap = File(OsrsItemImportTool.ASSET_MAP)
        val targets = OsrsItemImportTool.TARGETS
        val existing = OsrsFxImportTool.existingFx(assetMap)
        val ids = File(RspsPaths.data("cfg/toa/ids.tsv").toString()).readLines().map { it.split('\t') }
        fun bakedSeq(osrsSeq: Int, osrsBase: Int): Int? =
            ids.firstOrNull { it.size >= 4 && it[0] == "seq" && it[1] == "$osrsSeq" && it[3] == "baked for npc $osrsBase" }?.get(2)?.toInt()
        val reader = ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE))
        val library = CacheLibrary(targets[0])
        val mutations = mutableListOf<CacheMutation>()
        val records = mutableListOf<String>()
        val rows = mutableListOf<String>()
        val jobs = mutableListOf<String>()
        val dropped = mutableListOf<String>()
        try {
            val npcFiles = reader.files(ModernCacheReader.INDEX_CONFIG, OsrsNpcProbeTool.CONFIG_GROUP_NPC)
            val group = library.index(OsrsNpcImportTool.INDEX_NPC).archiveIds().maxOrNull() ?: error("no npc index")
            var nextNpc = (group shl 7) + (library.index(OsrsNpcImportTool.INDEX_NPC).archive(group)?.fileIds()?.maxOrNull() ?: -1) + 1
            var nextBas = (library.index(OsrsNpcImportTool.INDEX_CONFIG).archive(OsrsNpcImportTool.GROUP_BAS)?.fileIds()?.maxOrNull() ?: -1) + 1
            fun put(index: Int, g: Int, file: Int, bytes: ByteArray, label: String) {
                val current = library.data(index, g, file)?.let { CacheItemProbeTool.sha1(it) }
                mutations += CacheMutation(index, g, file, bytes, label, expectedCurrentSha1 = current?.takeIf { it != CacheItemProbeTool.sha1(bytes) })
            }
            for ((corpse, base) in CORPSES) {
                val n = OsrsNpcProbeTool.decodeOsrs(corpse, npcFiles[corpse] ?: error("OSRS npc $corpse missing"))
                val b = OsrsNpcProbeTool.decodeOsrs(base, npcFiles[base] ?: error("OSRS npc $base missing"))
                check(n.models.toList() == b.models.toList()) { "corpse $corpse models ${n.models.toList()} != base $base ${b.models.toList()}" }
                val localModels = n.models.indices.map { i -> existing["npc_baked_model:${base * 16 + i}"] ?: error("no baked model $i of npc $base") }
                val localHeads = n.chatheads.map { m -> existing["npc_model:$m"] ?: error("chathead model $m of $corpse not imported") }
                val seqs = OsrsNpcProbeTool.movementSeqs(n).values.filter { it >= 0 }.distinct()
                val seqMap = seqs.associateWith { s -> bakedSeq(s, base) ?: error("seq $s not baked for npc $base (ids.tsv)") }
                val basId = existing["bas:$corpse"] ?: nextBas++.also { records += "bas|$corpse|$it" }
                val localNpc = existing["npc:$corpse"] ?: nextNpc++.also { records += "npc|$corpse|$it" }
                put(OsrsNpcImportTool.INDEX_CONFIG, OsrsNpcImportTool.GROUP_BAS, basId, OsrsNpcImportTool.encode667Bas(n) { seqMap.getValue(it) }, "bas for osrs corpse npc $corpse")
                put(OsrsNpcImportTool.INDEX_NPC, localNpc ushr 7, localNpc and 0x7F, OsrsNpcImportTool.encode667Npc(n, localModels.toIntArray(), localHeads.toIntArray(), basId, dropped), "osrs corpse npc $corpse ${n.name}")
                println("PLAN corpse npc $corpse '${n.name}' -> $localNpc bas=$basId stand=${n.stand}->${seqMap[n.stand]} models=${n.models.toList()}->$localModels seqs=$seqMap")
                rows += "npc\t$corpse\t$localNpc\tcorpse bas=$basId (reuses bake of $base)"
                jobs += "$localNpc ${n.models.joinToString(",")} " + seqMap.entries.joinToString(" ") { "${it.value}=${it.key}" }
            }
        } finally {
            library.close()
            reader.close()
        }
        dropped.distinct().forEach { println("DROPPED $it") }
        val transaction = CacheTransaction(targets = targets, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} mutations=${mutations.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKING: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        plan.filter { "${it.outcome}" == "REPLACE" }.forEach { println("  REPLACE $it") }
        rows.forEach { println("ROW $it") }
        jobs.forEach { println("JOB $it") }
        if (!apply) {
            println("DRY_RUN records=${records.size}")
            return
        }
        val result = transaction.apply(plan)
        val problems = transaction.verify()
        problems.forEach { println("  VERIFY_FAILURE: $it") }
        check(problems.isEmpty()) { "verify failed" }
        println("APPLIED transaction=${result.transactionId} writes=${result.applied} skipped=${result.skipped}")
        if (records.isNotEmpty()) {
            val block = StringBuilder()
            records.forEach { r ->
                val (kind, upstream, localId) = r.split('|')
                block.append("  - fx_kind: $kind\n    upstream_fx_id: $upstream\n    local_fx_id: $localId\n    status: IMPORTED_BY_TOA_CORPSE_TOOL\n    transaction: ${transaction.id}\n")
            }
            val text = assetMap.readText()
            assetMap.writeText(if (text.endsWith("\n")) text + block else "$text\n$block")
            println("ASSET_MAP appended ${records.size} corpse entries")
        }
    }
}
