package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.ByteArrayOutputStream

/**
 * Two clientscript changes behind the Summoning surfaces (owner 2026-09-30: "Summoning-tab verdwijnt regelmatig na
 * herinloggen / knippert / Fixed-Resizable-Fullscreen"; "osrs Pets moeten ook pickup functie hebben in de summoning orb").
 *
 * 1. Clientscript 1766 (the gameframe tab-strip refresh, run as the top level's onLoad on every `IF_OPENTOP` and on every
 *    sub-interface change) draws the Follower Details tab (548:99/746:47, icon 548:107/746:31) only while VARC 823 == 2 and
 *    otherwise clears its icon and every op ([ProductionTabClientScriptPatchTool]). VARC 823 is a temporary varc: the client
 *    resets it to -1 on every login (`LoginManager.connected`) and applies the server's VARC packet as a delayed state change,
 *    while `IF_OPENTOP` runs 1766 at once - so the login rebuild drew the tab blank and only a later, incidental refresh
 *    (the server re-mounting 662) brought it back. The tab is this server's permanent Follower Details tab; no script
 *    writes VARC 823 (scan of all 5378 scripts), so both reads become the constant 2:
 *      [441] PUSH_VARC(823) -> PUSH_CONSTANT_INT(2)      [456] PUSH_VARC(823) -> PUSH_CONSTANT_INT(2)
 *    Same instruction width (u16 opcode + 4-byte operand), so no branch offset moves.
 *
 * 2. A new clientscript [LABEL_SCRIPT] `(string orb, string panel1, string panel2)` sets the follower's dismiss labels:
 *    IF_SETOP(6, orb, 747:11), IF_SETOP(1, orb, 747:20), IF_SETOP(1, panel1, 662:51), IF_SETOP(2, panel2, 662:51).
 *    The labels are baked "Dismiss" / "Dismiss Familiar" / "Dismiss Now" and no server packet in this revision renames an
 *    op, so the server runs this script with "Pick-up" for an OSRS pet and the baked words for a familiar.
 *
 * Usage: `[--apply]`.
 */
object SummoningInterfaceScriptTool {
    const val CLIENTSCRIPT_INDEX = 12
    const val TAB_SCRIPT = 1766
    const val LABEL_SCRIPT = 5378

    private const val PUSH_CONSTANT_INT = 0
    private const val PUSH_STRING_LOCAL = 35
    private const val PUSH_VARC = 42
    private const val RETURN = 21
    private const val IF_SETOP = 2300
    private const val TAB_VARC = 823
    private val TAB_VARC_READS = intArrayOf(441, 456)

    fun patchTabScript(current: ByteArray): ByteArray {
        val decoded = ProductionTabClientScriptPatchTool.decode(current)
        if (TAB_VARC_READS.all { decoded[it].opcode == PUSH_CONSTANT_INT && decoded[it].intOperand == 2 }) return current
        TAB_VARC_READS.forEach {
            check(decoded[it].opcode == PUSH_VARC && decoded[it].intOperand == TAB_VARC) {
                "clientscript $TAB_SCRIPT instruction $it is not PUSH_VARC($TAB_VARC): ${decoded[it]}"
            }
            check(decoded[it + 1].opcode == PUSH_CONSTANT_INT && decoded[it + 1].intOperand == 2) {
                "clientscript $TAB_SCRIPT instruction ${it + 1} is not PUSH_CONSTANT_INT(2): ${decoded[it + 1]}"
            }
        }
        val patched = current.copyOf()
        TAB_VARC_READS.forEach {
            val at = decoded[it].start
            patched[at] = 0
            patched[at + 1] = PUSH_CONSTANT_INT.toByte()
            patched[at + 2] = 0
            patched[at + 3] = 0
            patched[at + 4] = 0
            patched[at + 5] = 2
        }
        val check = ProductionTabClientScriptPatchTool.decode(patched)
        check(check.size == decoded.size) { "instruction count changed" }
        decoded.indices.forEach { i ->
            val expected = if (i in TAB_VARC_READS) decoded[i].copy(opcode = PUSH_CONSTANT_INT, intOperand = 2) else decoded[i]
            check(check[i] == expected) { "instruction $i differs after patch: ${check[i]} vs $expected" }
        }
        return patched
    }

    /** The label script: 3 string arguments, 17 instructions, no switch tables. */
    fun labelScript(): ByteArray {
        val out = ByteArrayOutputStream()
        fun u16(v: Int) {
            out.write(v ushr 8 and 0xFF)
            out.write(v and 0xFF)
        }
        fun i32(v: Int) {
            out.write(v ushr 24 and 0xFF)
            out.write(v ushr 16 and 0xFF)
            out.write(v ushr 8 and 0xFF)
            out.write(v and 0xFF)
        }
        var count = 0
        fun int(op: Int, v: Int) {
            u16(op)
            i32(v)
            count++
        }
        fun small(op: Int, v: Int) {
            u16(op)
            out.write(v)
            count++
        }
        fun setOp(op: Int, stringLocal: Int, component: Int) {
            int(PUSH_CONSTANT_INT, op)
            int(PUSH_STRING_LOCAL, stringLocal)
            int(PUSH_CONSTANT_INT, component)
            small(IF_SETOP, 0)
        }
        out.write(0) // no name
        setOp(6, 0, (747 shl 16) or 11)
        setOp(1, 0, (747 shl 16) or 20)
        setOp(1, 1, (662 shl 16) or 51)
        setOp(2, 2, (662 shl 16) or 51)
        small(RETURN, 0)
        i32(count)
        u16(0) // int locals
        u16(3) // string locals (the arguments)
        u16(0) // long locals
        u16(0) // int args
        u16(3) // string args
        u16(0) // long args
        out.write(0) // switch tables
        u16(1) // footer length: the switch-table count byte
        return out.toByteArray()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        val mutations = ArrayList<CacheMutation>()
        try {
            val tab = requireNotNull(library.data(CLIENTSCRIPT_INDEX, TAB_SCRIPT, 0)) { "clientscript $TAB_SCRIPT missing" }
            val patched = patchTabScript(tab)
            if (!patched.contentEquals(tab)) {
                mutations += CacheMutation(CLIENTSCRIPT_INDEX, TAB_SCRIPT, 0, patched, "clientscript $TAB_SCRIPT: Follower Details tab without VARC $TAB_VARC", expectedCurrentSha1 = CacheItemProbeTool.sha1(tab))
            }
            val existing = library.data(CLIENTSCRIPT_INDEX, LABEL_SCRIPT, 0)
            val label = labelScript()
            val decoded = ProductionTabClientScriptPatchTool.decode(label)
            check(decoded.size == 17 && decoded.last().opcode == RETURN) { "label script encodes to $decoded" }
            if (existing == null) {
                check(library.index(CLIENTSCRIPT_INDEX).archiveIds().maxOrNull() == LABEL_SCRIPT - 1) { "clientscript $LABEL_SCRIPT is not the next free id" }
                mutations += CacheMutation(CLIENTSCRIPT_INDEX, LABEL_SCRIPT, 0, label, "clientscript $LABEL_SCRIPT: follower dismiss / pick-up labels")
            } else {
                check(existing.contentEquals(label)) { "clientscript $LABEL_SCRIPT exists with other bytes - refusing to overwrite" }
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
        println(plan.joinToString("\n"))
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
        println("APPLIED transaction=${applied.transactionId} writes=${applied.applied} skipped=${applied.skipped} VERIFY_OK")
    }
}
