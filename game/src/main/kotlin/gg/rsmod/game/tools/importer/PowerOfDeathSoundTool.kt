package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary

/**
 * N3 (Power of Death silent on the Staff of the dead family): the 667 Staff of light plays Power of Death with graphic 2319, whose
 * sequence 12805 carries the Jagex vorbis sound 7237 (radius 5, volume 75). The Staff of the dead / Toxic staff show the OSRS
 * graphic SOTD_SPECIAL_START instead (local spotanim 2982, sequence [TARGET_SEQ]), which has no sound. This puts the same 667
 * Power of Death sound on that sequence at the same moment after the cast, so both staves of the special sound
 * alike. ADAPTED: OSRS's own Power of Death sound is server-sent and not documented anywhere (OSRS Wiki pages, sound list).
 *
 * Usage: `[--apply]` (dry run without it). Both caches, through [CacheTransaction].
 */
object PowerOfDeathSoundTool {
    const val SOURCE_SEQ = 12805
    const val TARGET_SEQ = 15374
    const val SOUND = 7237
    const val RADIUS = 5
    const val VOLUME = 75

    /** [target] with the vorbis frame sound [SOUND] on [frame]: opcode 13, then 18 (vorbis) and 19 (volume), before the end byte. */
    fun withPowerOfDeathSound(
        target: ByteArray,
        frame: Int,
    ): ByteArray {
        val seq = OsrsPetImportTool.read667Seq(target)
        check(!seq.hasSounds) { "sequence already has frame sounds" }
        val sounds = OsrsPetImportTool.with667Sounds(target, seq, mapOf(frame to ((SOUND shl 8) or (1 shl 5) or RADIUS)))
        val out = java.io.ByteArrayOutputStream()
        out.write(sounds, 0, sounds.size - 1)
        out.write(18)
        out.write(19)
        out.write(frame)
        out.write(VOLUME)
        out.write(0)
        return out.toByteArray()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val targets = OsrsItemImportTool.TARGETS
        val library = CacheLibrary(targets[0])
        val mutation: CacheMutation
        try {
            val source = library.data(OsrsFxImportTool.INDEX_SEQ, SOURCE_SEQ ushr 7, SOURCE_SEQ and 0x7F) ?: error("seq $SOURCE_SEQ missing")
            val target = library.data(OsrsFxImportTool.INDEX_SEQ, TARGET_SEQ ushr 7, TARGET_SEQ and 0x7F) ?: error("seq $TARGET_SEQ missing")
            val sourceSeq = OsrsPetImportTool.read667Seq(source)
            val targetSeq = OsrsPetImportTool.read667Seq(target)
            // The Staff of light's sound sits on frame 2 of 12805 (34 cycles after the cast; both staves start the special with the same
            // player animation 12804): the frame of 15374 showing that same moment.
            val sourceTime = sourceSeq.durations.take(2).sum()
            val frame = OsrsPetImportTool.lentFrames(listOf(intArrayOf(sourceTime, SOUND, 1, RADIUS)), targetSeq.durations.sum(), targetSeq.durations).single().second
            val bytes = withPowerOfDeathSound(target, frame)
            OsrsFxImportTool.decode667SeqFrames(bytes.copyOfRange(0, bytes.size - 4) + byteArrayOf(0)) // 18 is a 667 opcode; 19 is checked by the client decoder
            mutation = CacheMutation(OsrsFxImportTool.INDEX_SEQ, TARGET_SEQ ushr 7, TARGET_SEQ and 0x7F, bytes, "N3 Power of Death sound $SOUND on seq $TARGET_SEQ frame $frame", expectedCurrentSha1 = CacheItemProbeTool.sha1(target))
            println("PLAN seq $TARGET_SEQ frame $frame/${targetSeq.durations.size} <- seq $SOURCE_SEQ frame 2/${sourceSeq.durations.size} sound $SOUND (vorbis) radius $RADIUS volume $VOLUME")
        } finally {
            library.close()
        }
        val transaction = CacheTransaction(targets = targets, mutations = listOf(mutation))
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKING: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if ("--apply" !in args) {
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
