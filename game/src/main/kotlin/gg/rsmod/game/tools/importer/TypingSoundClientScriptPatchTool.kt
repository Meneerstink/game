package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.RspsPaths

/**
 * A19 (owner 2026-10-11: "a weird fart sound" on backspace in the chat; owner rule: "that sound must not play - OSRS:
 * nobody hears your typing"). Every 667 text box (chat line 137:56 via script 73, Enter amount / name dialogs via 112,
 * search boxes 74, 1221, 3022, 3160, 3185, 3216, 3944, 4555) edits its text through the shared clientscript 802. 802 keeps
 * a "rejected key" flag in int local 7 (backspace with at most one character before the cursor, Delete at the end, a
 * character the box does not take, max length) and ends with
 *
 *   #381 PUSH_INT_LOCAL 7; #382 BRANCH_IF_TRUE 1; #383 BRANCH 5; #384..387 PUSH 4173, 1, 0, 255; #388 SOUND_VORBIS_VOLUME
 *
 * which plays vorbis 4173 on the typing player's own client (original Jagex data, OpenRS2 cache 278; OSRS has no input
 * sound). Local 7 is read nowhere else, so this tool replaces only #381 with PUSH_CONSTANT_INT 0 (same 4-byte operand,
 * same instruction count, no branch target moves): the sound can never play and every edit/cursor result is unchanged.
 * It also keeps the A19-unrelated "Bb" suffix edit of [CountInputSuffixTool] (both tools patch 802, each is idempotent on
 * the other's output). No donor changes this (dossier A19: Darkan/Void/Novite/2009scape/matrix ship the Jagex script).
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.TypingSoundClientScriptPatchTool plan|apply`
 * (apply only with 50015/50016 stopped; writes both caches through one [CacheTransaction]).
 */
object TypingSoundClientScriptPatchTool {
    private val GAME_CACHE = RspsPaths.data("cache")
    private val FILE_SERVER_CACHE = RspsPaths.root("file-server/cache")
    const val CLIENTSCRIPT_INDEX = 12
    const val SCRIPT = 802
    const val REJECT_SOUND = 4173
    const val REJECT_FLAG_LOCAL = 7

    private const val PUSH_CONSTANT_INT = 0
    private const val BRANCH = 6
    private const val RETURN = 21
    private const val PUSH_INT_LOCAL = 33
    private const val GOSUB_WITH_PARAMS = 40
    private const val SWITCH = 51
    private const val BRANCH_IF_TRUE = 86
    private const val BRANCH_IF_FALSE = 87
    private const val SOUND_VORBIS_VOLUME = 3206
    private val CONDITIONAL = setOf(7, 8, 9, 10, 31, 32, 68, 69, 70, 71, 72, 73, BRANCH_IF_TRUE, BRANCH_IF_FALSE)

    /** Every SOUND_* opcode of the 667 client (ClientScriptOpCode 3200..3209: synth, song, jingle, vorbis, speech). */
    val SOUND_OPCODES = (3200..3209).toSet()

    /** [current] with the reject-flag test before the 4173 sound replaced by a constant 0; unchanged when already patched. */
    fun patch(current: ByteArray): ByteArray {
        val code = ProductionTabClientScriptPatchTool.decode(current)
        val guard = soundGuards(code)
        check(guard.size == 1) { "clientscript $SCRIPT: expected one 'PUSH_INT_LOCAL $REJECT_FLAG_LOCAL / BRANCH_IF_TRUE 1 / BRANCH 5 / sound $REJECT_SOUND' guard, found ${guard.size}" }
        val at = guard.single()
        val instruction = code[at]
        if (instruction.opcode == PUSH_CONSTANT_INT) return current
        val patched = current.copyOf()
        patched[instruction.start] = 0
        patched[instruction.start + 1] = PUSH_CONSTANT_INT.toByte()
        for (k in 0 until 4) patched[instruction.start + 2 + k] = 0
        val after = ProductionTabClientScriptPatchTool.decode(patched)
        check(after.size == code.size) { "instruction count changed" }
        code.indices.forEach { i ->
            val expected = if (i == at) code[i].copy(opcode = PUSH_CONSTANT_INT, intOperand = 0) else code[i]
            check(after[i] == expected) { "instruction $i differs after patch: ${after[i]} vs $expected" }
        }
        check(reachableSounds(patched).none { it.second == REJECT_SOUND }) { "sound $REJECT_SOUND still reachable after patch" }
        return patched
    }

    /** Indices of the flag push (PUSH_INT_LOCAL 7, or the patched PUSH_CONSTANT_INT 0) that guards the 4173 sound. */
    private fun soundGuards(code: List<ProductionTabClientScriptPatchTool.Instruction>): List<Int> =
        (0..code.size - 8).filter { i ->
            val flag = code[i]
            (flag.opcode == PUSH_INT_LOCAL && flag.intOperand == REJECT_FLAG_LOCAL || flag.opcode == PUSH_CONSTANT_INT && flag.intOperand == 0) &&
                code[i + 1].opcode == BRANCH_IF_TRUE && code[i + 1].intOperand == 1 &&
                code[i + 2].opcode == BRANCH && code[i + 2].intOperand == 5 &&
                code[i + 3].opcode == PUSH_CONSTANT_INT && code[i + 3].intOperand == REJECT_SOUND &&
                code[i + 7].opcode == SOUND_VORBIS_VOLUME
        }

    /** Script ids every [GOSUB_WITH_PARAMS] of [data] calls. */
    fun gosubs(data: ByteArray): Set<Int> =
        ProductionTabClientScriptPatchTool.decode(data).filter { it.opcode == GOSUB_WITH_PARAMS }.mapNotNull { it.intOperand }.toSet()

    /**
     * Every SOUND_* instruction the client can reach when it runs [data] from instruction 0, as (instruction index,
     * sound id when it is a constant pushed 4 instructions earlier, else -1). Follows BRANCH, every conditional branch
     * (both ways), SWITCH tables + default, RETURN; a BRANCH_IF_TRUE/FALSE right after a PUSH_CONSTANT_INT (and not a
     * jump target itself) only takes the way the constant decides, exactly as the client's ScriptRunner does.
     */
    fun reachableSounds(data: ByteArray): List<Pair<Int, Int>> {
        val code = ProductionTabClientScriptPatchTool.decode(data)
        val tables = switchTables(data)
        val targets = HashSet<Int>()
        code.forEachIndexed { i, ins ->
            if (ins.opcode == BRANCH || ins.opcode in CONDITIONAL) targets += i + 1 + (ins.intOperand ?: 0)
            if (ins.opcode == SWITCH) tables[ins.intOperand ?: 0].forEach { targets += i + 1 + it }
        }
        val seen = BooleanArray(code.size)
        val todo = ArrayDeque(listOf(0))
        while (todo.isNotEmpty()) {
            val i = todo.removeLast()
            if (i !in code.indices || seen[i]) continue
            seen[i] = true
            val ins = code[i]
            val jump = i + 1 + (ins.intOperand ?: 0)
            when {
                ins.opcode == RETURN -> {}
                ins.opcode == BRANCH -> todo += jump
                ins.opcode == SWITCH -> {
                    todo += i + 1
                    tables[ins.intOperand ?: 0].forEach { todo += i + 1 + it }
                }
                (ins.opcode == BRANCH_IF_TRUE || ins.opcode == BRANCH_IF_FALSE) && i > 0 &&
                    code[i - 1].opcode == PUSH_CONSTANT_INT && i !in targets -> {
                    val value = code[i - 1].intOperand
                    val taken = if (ins.opcode == BRANCH_IF_TRUE) value == 1 else value == 0
                    todo += if (taken) jump else i + 1
                }
                ins.opcode in CONDITIONAL -> {
                    todo += i + 1
                    todo += jump
                }
                else -> todo += i + 1
            }
        }
        return code.indices.filter { seen[it] && code[it].opcode in SOUND_OPCODES }.map { i ->
            val id = code.getOrNull(i - 4)?.takeIf { it.opcode == PUSH_CONSTANT_INT }?.intOperand ?: -1
            i to id
        }
    }

    /** Relative jump offsets of every switch table (ClientScript.decode: count byte after the 16-byte header, then tables). */
    private fun switchTables(data: ByteArray): List<List<Int>> {
        val footer = u16(data, data.size - 2)
        var pos = data.size - footer - 2 - 16 + 16
        if (footer == 0) return emptyList()
        val count = data[pos++].toInt() and 0xFF
        return List(count) {
            val cases = u16(data, pos)
            pos += 2
            List(cases) {
                val offset = s32(data, pos + 4)
                pos += 8
                offset
            }
        }
    }

    private fun u16(data: ByteArray, at: Int) = ((data[at].toInt() and 0xFF) shl 8) or (data[at + 1].toInt() and 0xFF)

    private fun s32(data: ByteArray, at: Int) = (u16(data, at) shl 16) or u16(data, at + 2)

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val library = CacheLibrary(GAME_CACHE)
        val current: ByteArray
        val patched: ByteArray
        try {
            current = library.data(CLIENTSCRIPT_INDEX, SCRIPT, 0) ?: error("clientscript $SCRIPT missing")
            patched = patch(current)
        } finally {
            library.close()
        }
        if (patched.contentEquals(current)) {
            println("NOTHING_TO_DO clientscript $SCRIPT plays no typing sound")
            return
        }
        println("PLAN clientscript $SCRIPT: instruction reject-flag test -> constant 0 (sound $REJECT_SOUND never plays)")
        val tx =
            CacheTransaction(
                listOf(GAME_CACHE, FILE_SERVER_CACHE),
                listOf(CacheMutation(CLIENTSCRIPT_INDEX, SCRIPT, 0, patched, "A19 clientscript $SCRIPT: no typing/backspace sound $REJECT_SOUND", CacheItemProbeTool.sha1(current))),
            )
        val preflight = tx.preflight()
        preflight.forEach { println(it) }
        val blocking = tx.blockingErrors(preflight)
        blocking.forEach { println("BLOCKING: $it") }
        if (mode == "plan") {
            println("PLAN_ONLY transaction=${tx.id} (nothing written)")
            return
        }
        check(blocking.isEmpty()) { "Preflight has blocking errors; refusing to apply." }
        val result = tx.apply(preflight)
        println("APPLIED transaction=${tx.id} applied=${result.applied} skipped=${result.skipped}")
        val verify = tx.verify()
        verify.forEach { println("VERIFY_ERROR: $it") }
        check(verify.isEmpty()) { "Post-apply verification failed; see journal ${tx.id} for rollback." }
        println("VERIFY_OK")
    }
}
