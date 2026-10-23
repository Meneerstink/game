package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.def.ObjectDef
import io.netty.buffer.Unpooled

/**
 * N234 (owner 2026-10-23: "Altar of the Occult in de POH moet geluid maken als je erlangs loopt"; 23:05 answer 1 B: "een zacht
 * magisch zoem-geluid als je langsloopt"). OSRS has none (no ambient sound on OSRS loc 29150 or its variants, wiki silent), so the
 * owner chose the 667 Runecrafting altar hum: sound [HUM] with range [RANGE], exactly as loc 26847 "Runecrafting altar" carries it.
 *
 * The POH altar is multiloc [ALTARS].last() (62924) showing variant 62921 / 62922 / 62923 by spellbook; the client's
 * `PositionedSound` plays the sound of the variant it shows (`getMultiLoc`), so all four LocTypes get opcode 78 (sound, range),
 * appended before the terminator.
 *
 * Usage: `./gradlew :game:runOccultAltarSoundTool --args="plan|apply"`.
 */
object OccultAltarSoundTool {
    const val LOC_INDEX = 16
    const val OPCODE_SOUND = 78

    /** The standard, ancient and lunar variants and the multiloc itself (PlayerHouse.OCCULT_ALTAR_VARIANTS / OCCULT_ALTAR). */
    val ALTARS = listOf(62921, 62922, 62923, 62924)

    /** The Runecrafting altar hum of loc 26847 (op 78: 3932, range 5). */
    const val HUM = 3932
    const val RANGE = 5

    fun patched(current: ByteArray): ByteArray = current.copyOfRange(0, current.size - 1) + byteArrayOf(OPCODE_SOUND.toByte(), (HUM ushr 8).toByte(), (HUM and 0xFF).toByte(), RANGE.toByte(), 0)

    fun decode(
        id: Int,
        bytes: ByteArray,
    ): ObjectDef = ObjectDef(id).also { it.decode(Unpooled.wrappedBuffer(bytes)) }

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        val mutations = ArrayList<CacheMutation>()
        try {
            ALTARS.forEach { id ->
                val current = library.data(LOC_INDEX, id ushr 8, id and 0xFF) ?: error("loc $id missing")
                check(current.last() == 0.toByte()) { "loc $id does not end with the opcode-0 terminator" }
                val before = decode(id, current)
                if (before.ambientSound == HUM) return@forEach
                check(before.ambientSound == -1) { "loc $id already plays sound ${before.ambientSound}; refusing" }
                val updated = patched(current)
                val after = decode(id, updated)
                check(after.ambientSound == HUM && after.ambientSoundRange == RANGE && after.name == before.name)
                mutations += CacheMutation(LOC_INDEX, id ushr 8, id and 0xFF, updated, "N234 loc $id ambient sound $HUM range $RANGE", CacheItemProbeTool.sha1(current))
            }
        } finally {
            library.close()
        }
        if (mutations.isEmpty()) {
            println("NOTHING_TO_DO")
            return
        }
        val transaction = CacheTransaction(targets = OsrsItemImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        errors.forEach { println("  BLOCKING: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (mode == "plan") {
            println("DRY_RUN transaction=${transaction.id} mutations=${mutations.size}")
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
