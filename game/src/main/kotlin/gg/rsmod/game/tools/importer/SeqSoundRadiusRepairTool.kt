package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/**
 * Breach audit 2026-09-27, cause A1: every OSRS sequence imported before [OsrsFxImportTool.Seq.soundRadius] existed carries its
 * frame sounds with radius 0. The 667 client plays a radius-0 frame sound only when the local player is the one animating
 * (`Static431.method5827`), so the sounds of every imported npc animation - and of every imported player animation for the
 * players watching - were silent. This tool re-reads each imported sequence's OSRS source (the asset map `seq` / `playerseq`
 * entries), and writes the OSRS "location" radius into the low 5 bits of each packed frame sound of the local sequence. Nothing
 * else in the sequence changes; a sequence whose local sound ids do not line up frame for frame with the OSRS ones is skipped
 * and reported rather than guessed.
 *
 * Usage: `SeqSoundRadiusRepairTool [--apply] [--report]` (dry run by default; both caches through [CacheTransaction]).
 */
object SeqSoundRadiusRepairTool {
    class Patch(val localSeq: Int, val osrsSeq: Int, val bytes: ByteArray, val radii: Map<Int, Int>, val currentSha1: String)

    /** Offset of opcode 13's payload in a rev-667 sequence, or -1 when it has none (walk as `SeqType.decode`). */
    fun soundSectionOffset(bytes: ByteArray): Int {
        val c = OsrsFxImportTool.Cursor(bytes)
        var count = 0
        while (true) {
            when (val op = c.u8()) {
                0 -> return -1
                1 -> {
                    count = c.u16()
                    repeat(count * 3) { c.u16() }
                }
                2 -> c.u16()
                3 -> repeat(c.u8()) { c.u8() }
                5, 8, 9, 10, 11 -> c.u8()
                6, 7 -> c.u16()
                12 -> repeat(c.u8() * 2) { c.u16() }
                13 -> return c.pos
                4, 14, 15, 16, 18 -> Unit
                else -> error("opcode $op before the sound section")
            }
        }
    }

    /**
     * Rewrites the radius bits of [local] (a 667 sequence) from [radius] (frame -> OSRS location). Returns null when the
     * sequence has no sound section or nothing changes.
     */
    fun patch(
        local: ByteArray,
        radius: Map<Int, Int>,
    ): ByteArray? {
        val start = soundSectionOffset(local)
        if (start < 0) return null
        val out = local.copyOf()
        val c = OsrsFxImportTool.Cursor(out, start)
        val frames = c.u16()
        var changed = false
        for (frame in 0 until frames) {
            val options = c.u8()
            if (options == 0) continue
            val at = c.pos
            val packed = (c.u8() shl 16) or c.u16()
            repeat(options - 1) { c.u16() }
            val wanted = OsrsFxImportTool.packedRadius(radius[frame] ?: continue)
            val updated = (packed and 0x1F.inv()) or wanted
            if (updated != packed) {
                out[at] = (updated ushr 16).toByte()
                out[at + 1] = (updated ushr 8).toByte()
                out[at + 2] = updated.toByte()
                changed = true
            }
        }
        return if (changed) out else null
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val report = "--report" in args
        val imported = OsrsFxImportTool.existingFx(File(OsrsItemImportTool.ASSET_MAP)).filterKeys { it.startsWith("seq:") || it.startsWith("playerseq:") }
        val reader = ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE))
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        val patches = ArrayList<Patch>()
        val radiusHistogram = sortedMapOf<Int, Int>()
        var withSounds = 0
        try {
            for ((key, localSeq) in imported.entries.sortedBy { it.value }) {
                val osrsSeq = key.substringAfter(':').toInt()
                val osrsBytes = reader.file(ModernCacheReader.INDEX_CONFIG, ModernCacheReader.CONFIG_GROUP_SEQUENCE, osrsSeq) ?: continue
                val seq = runCatching { OsrsFxImportTool.decodeOsrsSeq(osrsBytes) }.getOrNull() ?: continue
                if (seq.sounds.isEmpty()) continue
                withSounds++
                seq.soundRadius.values.forEach { radiusHistogram[it] = (radiusHistogram[it] ?: 0) + 1 }
                val local = library.data(OsrsFxImportTool.INDEX_SEQ, localSeq ushr 7, localSeq and 0x7F) ?: continue
                val patched = patch(local, seq.soundRadius) ?: continue
                patches += Patch(localSeq, osrsSeq, patched, seq.soundRadius, CacheItemProbeTool.sha1(local))
                if (report) println("PATCH seq $localSeq (osrs $osrsSeq) radius by frame ${seq.soundRadius}")
            }
        } finally {
            library.close()
            reader.close()
        }
        println("SEQ_SOUND_RADIUS imported=${imported.size} with_sounds=$withSounds to_patch=${patches.size} osrs_radius_histogram=$radiusHistogram")
        if (patches.isEmpty()) return
        val mutations =
            patches.map { p ->
                CacheMutation(OsrsFxImportTool.INDEX_SEQ, p.localSeq ushr 7, p.localSeq and 0x7F, p.bytes, "seq ${p.localSeq} frame sound radius (osrs ${p.osrsSeq})", expectedCurrentSha1 = p.currentSha1)
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
