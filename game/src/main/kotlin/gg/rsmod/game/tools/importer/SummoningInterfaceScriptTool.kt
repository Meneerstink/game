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
 * 3. Tab 8 (the spare tab that carries Follower Details) opens the Follower Details slot (owner 2026-10-02 overnight run:
 *    "Summoning tab disappears ... on login with a familiar or pet out, on teleport and other events"). Live repro: opening
 *    any chatbox dialogue (Dismiss familiar? Yes/No) removed the tab's icon and button. Cause, read from the cache: the
 *    gameframe script 1364 runs on every sub-interface change and, for each tab 0..15, shows the tab's icon (script 121)
 *    and button (script 2458) only when the tab's own container (script 8) holds a sub-interface - and hides both
 *    otherwise. Tab 8's container is 746:98 / 548:212, but 662 lives on slot 95 (746:107 / 548:221), the container the
 *    orb's own "Follower Details" op (747:9/18 onOp 2457(95)) and its option layer (1364: 747:8 needs slot 95 mounted)
 *    depend on. So every dialogue, bank, shop or teleport interface change hid the tab until the server happened to
 *    re-arm it. Fix at the source: every script that names tab 8's container names the Follower Details slot instead,
 *    so the tab is "mounted" exactly when 662 is and selecting it shows the panel client-side:
 *      script 8    (tab -> container)      746:98 -> 746:107, 548:212 -> 548:221
 *      script 1387 (select tab: show it)   746:98 -> 746:107, 548:212 -> 548:221
 *      script 1314 (is the tab open)       746:98 -> 746:107, 548:212 -> 548:221
 *    Constant swaps only (PUSH_CONSTANT_INT operand), so no branch offset moves. 1308/1311 (hide every container) and
 *    1364's "all hidden" check keep naming 746:98 / 548:212, which simply stays empty and hidden.
 *
 * 4. N425 (owner 2026-10-29: a pet's Follower Details head lay flat or huge after a familiar pouch's "You can only have one
 *    follower at a time"): clientscript 751, run by the client on every transmit of the follower varps, poses 662:1 from enum
 *    1275/1276 by the selector varbit 4282. A pet has no selector; the server sends -1, which the 7-bit varbit holds as 127.
 *    751 took that as "> 50": it WROTE 4282 = 127 - 50 (instruction 67) and posed the head with enum 1275[77] - a creature
 *    pose (8374/8444) that won over the server's own animation whenever 751 ran again without a fresh server resend. In front
 *    of the selector read (instruction 60) the script now skips both poses when the varbit is [PET_SELECTOR]:
 *      PUSH_VARBIT(4282), PUSH_CONSTANT_INT(127), BRANCH_EQUALS -> the instruction after the second IF_SETMODELANIM (87)
 *    so a pet's head keeps exactly what the server sent, and the varbit is never rewritten. Familiars are untouched.
 *
 * 5. N427 (owner 2026-10-30: "elke pet heet in Follower Details Pet ... waarom staat er overal Pet voor?"): 751 writes the name
 *    bar 662:54 from enum 1279 (follower npc -> name); a follower it does not know gets the enum's default "Animal" and 751
 *    then writes OC_NAME(varp 448) - the ITEM's name ("Pet Kraken", "Pet kitten") - over the server's text on every transmit.
 *    The "Animal" branch now jumps past both IF_SETTEXTs:
 *      PUSH_CONSTANT_STRING("Animal"), COMPARE, BRANCH_IF_FALSE -> the instruction after the second IF_SETTEXT(662:54)
 *    so for every pet (and any unmapped familiar) the name bar keeps what the server wrote: the follower npc's own name
 *    ("Kraken", "Zilyana Jr.", "Kitten" - OSRS names the creature, not the item). Known familiars keep their enum name.
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

    /** The scripts that name tab 8's container (see point 3 above). */
    val TAB8_CONTAINER_SCRIPTS = intArrayOf(8, 1387, 1314)

    /** Tab 8's own container -> the Follower Details slot (slot 95), resizable and fixed. */
    val TAB8_CONTAINER_REMAP: Map<Int, Int> =
        mapOf(
            ((746 shl 16) or 98) to ((746 shl 16) or 107),
            ((548 shl 16) or 212) to ((548 shl 16) or 221),
        )

    const val PANEL_SCRIPT = 751
    const val SELECTOR_VARBIT = 4282

    /** The selector the server sends for a follower with no enum 1275/1276 pose (every pet): -1 in 7 bits. */
    const val PET_SELECTOR = 127

    private const val PUSH_VARBIT = 25
    private const val BRANCH_GREATER_THAN = 10
    private const val BRANCH_EQUALS = 8
    private const val IF_SETMODELANIM = 2110
    private const val PANEL_HEAD = (662 shl 16) or 1
    private const val PANEL_NAME = (662 shl 16) or 54
    private const val PUSH_CONSTANT_STRING = 3
    private const val COMPARE = 4107
    private const val BRANCH_IF_FALSE = 87
    private const val BRANCH = 6
    private const val IF_SETTEXT = 2112
    const val UNKNOWN_FOLLOWER_NAME = "Animal"

    /**
     * Point 5: 751's "Animal" branch (OC_NAME of the item into 662:54) jumps past both name writes, so the server's follower name
     * stays. Idempotent; refuses any other shape of the script.
     */
    fun patchPanelName(current: ByteArray): ByteArray {
        val script = ClientScriptEditor.decode(current)
        val hits = script.find(PUSH_CONSTANT_STRING to UNKNOWN_FOLLOWER_NAME, COMPARE to null, BRANCH_IF_FALSE to null, BRANCH to null)
        check(hits.size == 1) { "clientscript $PANEL_SCRIPT: the 'Animal' name check found ${hits.size} times - refusing to guess" }
        val at = hits[0]
        val onAnimal = script.code[at + 2]
        val onKnown = script.code[at + 3]
        val known = script.indexOf(onKnown.target!!)
        // The known-name write: PUSH_STRING_LOCAL, PUSH 662:54, IF_SETTEXT; the instruction after it is where both paths meet.
        check(script.code[known + 1].opcode == PUSH_CONSTANT_INT && script.code[known + 1].int == PANEL_NAME && script.code[known + 2].opcode == IF_SETTEXT) {
            "clientscript $PANEL_SCRIPT: the known-name IF_SETTEXT(662:54) is not where the branch lands"
        }
        val after = script.code[known + 3]
        if (onAnimal.target === after) return current
        val item = script.indexOf(onAnimal.target!!)
        check(script.code.subList(item, known).count { it.opcode == IF_SETTEXT } == 1) { "clientscript $PANEL_SCRIPT: the 'Animal' path is not one IF_SETTEXT" }
        onAnimal.target = after
        return script.encode()
    }

    /** Point 4: 751 leaves the head's animation alone for [PET_SELECTOR]. Idempotent; refuses any other shape of the script. */
    fun patchPanelScript(current: ByteArray): ByteArray {
        val script = ClientScriptEditor.decode(current)
        val reads = script.find(PUSH_VARBIT to SELECTOR_VARBIT, PUSH_CONSTANT_INT to 50, BRANCH_GREATER_THAN to null)
        check(reads.size == 1) { "clientscript $PANEL_SCRIPT: selector check found ${reads.size} times - refusing to guess" }
        val at = reads[0]
        val guard = script.find(PUSH_VARBIT to SELECTOR_VARBIT, PUSH_CONSTANT_INT to PET_SELECTOR, BRANCH_EQUALS to null)
        if (guard.isNotEmpty()) {
            check(guard == listOf(at - 3)) { "clientscript $PANEL_SCRIPT: pet guard at $guard, expected ${at - 3}" }
            return current
        }
        // The two poses (enum 1275 / 1276) each end in IF_SETMODELANIM on 662:1; the guard jumps past the second.
        val poses = (at until script.code.size).filter { script.code[it].opcode == IF_SETMODELANIM }.take(2)
        check(poses.size == 2 && poses.all { script.code[it - 1].opcode == PUSH_CONSTANT_INT && script.code[it - 1].int == PANEL_HEAD }) {
            "clientscript $PANEL_SCRIPT: the two IF_SETMODELANIM(662:1) after the selector check were not found ($poses)"
        }
        val resume = script.code[poses[1] + 1]
        val first = ClientScriptEditor.Ins(PUSH_VARBIT, SELECTOR_VARBIT)
        script.retarget(script.code[at], first)
        script.insert(at, listOf(first, ClientScriptEditor.Ins(PUSH_CONSTANT_INT, PET_SELECTOR), ClientScriptEditor.Ins(BRANCH_EQUALS, target = resume)))
        return script.encode()
    }

    /** Point 3: swaps tab 8's container constants for slot 95's. Idempotent; refuses anything but exactly one hit per constant. */
    fun remapTab8Container(current: ByteArray): ByteArray {
        val decoded = ProductionTabClientScriptPatchTool.decode(current)
        val patched = current.copyOf()
        val changed = mutableSetOf<Int>()
        TAB8_CONTAINER_REMAP.forEach { (old, new) ->
            val hits = decoded.indices.filter { decoded[it].opcode == PUSH_CONSTANT_INT && decoded[it].intOperand == old }
            if (hits.isEmpty()) return@forEach
            check(hits.size == 1) { "tab 8 container ${old ushr 16}:${old and 0xFFFF} pushed ${hits.size} times - refusing to guess" }
            val at = decoded[hits[0]].start + 2
            patched[at] = (new ushr 24).toByte()
            patched[at + 1] = (new ushr 16).toByte()
            patched[at + 2] = (new ushr 8).toByte()
            patched[at + 3] = new.toByte()
            changed += hits[0]
        }
        val check = ProductionTabClientScriptPatchTool.decode(patched)
        check(check.size == decoded.size) { "instruction count changed" }
        decoded.indices.forEach { i ->
            val expected = if (i in changed) decoded[i].copy(intOperand = TAB8_CONTAINER_REMAP.getValue(decoded[i].intOperand!!)) else decoded[i]
            check(check[i] == expected) { "instruction $i differs after patch: ${check[i]} vs $expected" }
        }
        return patched
    }

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
            TAB8_CONTAINER_SCRIPTS.forEach { id ->
                val current = requireNotNull(library.data(CLIENTSCRIPT_INDEX, id, 0)) { "clientscript $id missing" }
                val remapped = remapTab8Container(current)
                if (!remapped.contentEquals(current)) {
                    mutations += CacheMutation(CLIENTSCRIPT_INDEX, id, 0, remapped, "clientscript $id: tab 8 opens the Follower Details slot", expectedCurrentSha1 = CacheItemProbeTool.sha1(current))
                }
            }
            val panel = requireNotNull(library.data(CLIENTSCRIPT_INDEX, PANEL_SCRIPT, 0)) { "clientscript $PANEL_SCRIPT missing" }
            val guarded = patchPanelName(patchPanelScript(panel))
            if (!guarded.contentEquals(panel)) {
                mutations += CacheMutation(CLIENTSCRIPT_INDEX, PANEL_SCRIPT, 0, guarded, "clientscript $PANEL_SCRIPT: no enum pose on a pet's Follower Details head (N425), no item name over a pet's name bar (N427)", expectedCurrentSha1 = CacheItemProbeTool.sha1(panel))
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
