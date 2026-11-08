package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/** N601: mark existing ToA spot definitions for OSRS priority/alpha layering. No model/frame/colour changes.
 * Deploy the matching client patch first. Usage: plan|apply [beam|all-toa]. Only Claude runs apply.
 */
object ToaSpotAlphaPatchTool {
    const val OSRS_ALPHA_OPCODE = 242
    val BEAM_SPOTS = setOf(2064, 2113, 2114, 2115, 2116, 2117, 2118, 2119, 2120)

    /** Strict walk: an opcode-looking byte inside a model id/recolour/sequence is not a marker. */
    internal fun patch(bytes: ByteArray): ByteArray {
        val c = OsrsFxImportTool.Cursor(bytes)
        var marked = false
        while (true) {
            when (val op = c.u8()) {
                0 -> {
                    check(c.remaining == 0) { "trailing spot definition bytes" }
                    return if (marked) bytes.copyOf() else bytes.copyOf(bytes.size - 1) + byteArrayOf(OSRS_ALPHA_OPCODE.toByte(), 0)
                }
                OSRS_ALPHA_OPCODE -> marked = true
                1, 2, 4, 5, 6, 15 -> c.u16()
                7, 8, 14 -> c.u8()
                9, 10, 11, 12, 13 -> Unit
                16, 241 -> repeat(4) { c.u8() }
                40, 41 -> repeat(c.u8()) { c.u16(); c.u16() }
                else -> error("unsupported spot opcode $op")
            }
        }
    }

    internal fun plan(targets: List<String>, spots: Map<Int, Int>, journalRoot: File = File(CacheTransaction.DEFAULT_JOURNAL_ROOT)): CacheTransaction {
        require(targets.size == 2 && spots.isNotEmpty())
        require(spots.keys.all { it >= 0 } && spots.values.all { it in 0..65535 } && spots.values.toSet().size == spots.size)
        val originals = HashMap<Int, ByteArray>()
        val mutations = ArrayList<CacheMutation>()
        targets.forEachIndexed { index, target ->
            val cache = CacheLibrary(target)
            try {
                spots.toSortedMap().forEach { (osrs, local) ->
                    val before = cache.data(21, local ushr 8, local and 255) ?: error("$target: ToA spot $osrs->$local missing")
                    if (index == 0) {
                        originals[local] = before
                        val after = patch(before)
                        mutations += CacheMutation(21, local ushr 8, local and 255, after,
                            "N601 OSRS alpha order spot $osrs->$local", CacheItemProbeTool.sha1(before))
                        println("PLAN spot $osrs->$local ${before.size}->${after.size} bytes opcode=$OSRS_ALPHA_OPCODE sha1=${CacheItemProbeTool.sha1(before)}")
                    } else {
                        check(before.contentEquals(originals.getValue(local))) { "$target: spot $osrs->$local differs between caches" }
                    }
                }
            } finally { cache.close() }
        }
        return CacheTransaction(targets, mutations, journalRoot)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        val scope = args.getOrNull(1) ?: "beam"
        require(args.size <= 2 && mode in setOf("plan", "apply") && scope in setOf("beam", "all-toa")) { "Usage: plan|apply [beam|all-toa]" }
        val ids = File("data/cfg/toa/ids.tsv").readLines().mapNotNull { line ->
            val p = line.split('\t')
            if (p.size >= 3 && p[0] == "spotanim") p[1].toInt() to p[2].toInt() else null
        }.toMap().filterKeys { scope == "all-toa" || it in BEAM_SPOTS }
        if (scope == "beam") check(ids.keys == BEAM_SPOTS) { "missing beam mapping: ${BEAM_SPOTS - ids.keys}" }
        val tx = plan(listOf(FeroxImportTool.GAME_CACHE, FeroxImportTool.FILE_SERVER_CACHE), ids)
        val preflight = tx.preflight()
        preflight.forEach { println("PREFLIGHT $it") }
        check(tx.blockingErrors(preflight).isEmpty()) { "blocking preflight errors; refusing $mode" }
        if (mode == "plan") {
            println("DRY_RUN ${tx.mutations.size} spot definitions, zero model/frame/seq writes; client opcode242 patch required")
            return
        }
        val result = tx.apply(preflight)
        println("APPLIED ${tx.id} applied=${result.applied} skipped=${result.skipped}")
        val errors = tx.verify()
        check(errors.isEmpty()) { "verification failed: $errors (journal ${tx.journalDir})" }
        println("VERIFY_OK both caches (journal ${tx.journalDir})")
    }
}
