package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/**
 * Breach audit 2026-09-27 (C4): the BAS of every npc imported by [OsrsNpcImportTool] before [OsrsNpcImportTool.encode667Bas]
 * wrote opcodes 38/39 lacks the OSRS idle rotations, although their sequences were imported. This adds them to the existing BAS
 * (asset map `bas:<osrs npc>` entries), nothing else. Usage: `BasIdleTurnRepairTool [--apply]`, both caches, transactional.
 */
object BasIdleTurnRepairTool {
    /** Opcodes of a BAS written by [OsrsNpcImportTool.encode667Bas] (1 = 4 bytes, the rest 2), or null if it has others. */
    fun opcodes(bytes: ByteArray): Set<Int>? {
        val ops = HashSet<Int>()
        var p = 0
        while (p < bytes.size) {
            val op = bytes[p++].toInt() and 0xFF
            if (op == 0) return ops
            ops += op
            p += when (op) {
                1 -> 4
                in 2..9, in 38..42 -> 2
                else -> return null
            }
        }
        return null
    }

    fun withIdleTurns(
        bytes: ByteArray,
        left: Int,
        right: Int,
    ): ByteArray {
        val ops = opcodes(bytes) ?: return bytes
        val extra = java.io.ByteArrayOutputStream()
        if (left >= 0 && 38 !in ops) extra.write(byteArrayOf(38, (left ushr 8).toByte(), left.toByte()))
        if (right >= 0 && 39 !in ops) extra.write(byteArrayOf(39, (right ushr 8).toByte(), right.toByte()))
        if (extra.size() == 0) return bytes
        return bytes.copyOf(bytes.size - 1) + extra.toByteArray() + byteArrayOf(0)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val ids = OsrsFxImportTool.existingFx(File(OsrsItemImportTool.ASSET_MAP))
        val reader = ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE))
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        val mutations = ArrayList<CacheMutation>()
        try {
            val npcFiles = reader.files(ModernCacheReader.INDEX_CONFIG, OsrsNpcProbeTool.CONFIG_GROUP_NPC)
            val seen = HashSet<Int>()
            for ((key, basId) in ids) {
                if (!key.startsWith("bas:") || !seen.add(basId)) continue
                val npcId = key.substringAfter(':').toInt()
                val n = OsrsNpcProbeTool.decodeOsrs(npcId, npcFiles[npcId] ?: continue)
                fun localSeq(s: Int): Int = if (s < 0) -1 else if (s in OsrsNpcImportTool.SHARED_MOVEMENT_SEQS) s else ids["seq:$s"] ?: -1
                val left = localSeq(n.idleTurnLeft)
                val right = localSeq(n.idleTurnRight)
                if (left < 0 && right < 0) continue
                val current = library.data(OsrsNpcImportTool.INDEX_CONFIG, OsrsNpcImportTool.GROUP_BAS, basId) ?: continue
                val patched = withIdleTurns(current, left, right)
                if (patched.contentEquals(current)) continue
                println("PATCH bas $basId (osrs npc $npcId ${n.name}) idle turn left=$left right=$right")
                mutations += CacheMutation(OsrsNpcImportTool.INDEX_CONFIG, OsrsNpcImportTool.GROUP_BAS, basId, patched, "bas $basId idle turns (osrs npc $npcId)", expectedCurrentSha1 = CacheItemProbeTool.sha1(current))
            }
        } finally {
            library.close()
            reader.close()
        }
        println("BAS_IDLE_TURN to_patch=${mutations.size}")
        if (mutations.isEmpty()) return
        val transaction = CacheTransaction(targets = OsrsItemImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} mutations=${mutations.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKING: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) return println("DRY_RUN")
        val result = transaction.apply(plan)
        val problems = transaction.verify()
        if (problems.isNotEmpty()) {
            problems.forEach { println("  VERIFY_FAILURE: $it") }
            println("ROLLED_BACK ${transaction.rollback()}")
            error("transaction ${transaction.id} failed verification and was rolled back")
        }
        println("APPLIED transaction=${result.transactionId} writes=${result.applied} skipped=${result.skipped}")
    }
}
