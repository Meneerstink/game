package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.ByteArrayOutputStream

/**
 * The quest tab's gameframe icon (owner 2026-09-26: the Deadman skull of tab1.png, OSRS sprite 4815 imported as
 * [QuestTabInterfaceImportTool.GAMEFRAME_ICON]).
 *
 * [QuestTabInterfaceImportTool] put the skull on the icon components 548:141 (fixed) and 746:26 (resizable), but
 * clientscript 1766 - which redraws the whole tab strip on every gameframe load and every VARC 234 change - sets the
 * graphic of both components again. For the normal game mode (VARC 234 case "Quest Journals") it pushes sprite 1819,
 * the old blue quest icon, so the skull never showed and the old icon was stretched to the new 33 x 36 size:
 *
 *   [180] PUSH_CONSTANT_INT(1819)   -> skull   (then 35913869 = 548:141, OP_2105 = cc_setgraphic)
 *   [183] PUSH_CONSTANT_INT(1819)   -> skull   (then 48889882 = 746:26)
 *
 * Only those two operands change (same instruction count and length, so every branch stays valid).
 *
 * Resizable / fullscreen: clientscript 722 lays the tab strip out in one row or two (by window width) and moves every
 * icon with cc_setposition (OP_2000: x, y, xmode 2, ymode 2 = from the right and bottom). Its positions are for the old
 * 22 x 22 icon, so the 33 x 36 skull sat 5 px left and 5 px high of its tab (owner 2026-09-27; fixed mode, which has
 * no such script, was centred). Both positions move by (-5, -5), which is also where [QuestTabInterfaceImportTool]
 * stores the icon (359, 1):
 *
 *   [257] PUSH_CONSTANT_INT(124) -> 119, [258] PUSH_CONSTANT_INT(43) -> 38   (two rows)
 *   [516] PUSH_CONSTANT_INT(364) -> 359, [517] PUSH_CONSTANT_INT(6)  -> 1    (one row)
 *
 * Applied transactionally to both caches. `--apply` writes; without it the tool only reports the preflight.
 */
object QuestTabIconScriptPatchTool {
    const val CLIENTSCRIPT_INDEX = 12
    const val SCRIPT_ID = 1766
    private const val PUSH_CONSTANT_INT = 0
    val SPRITE_INSTRUCTIONS = intArrayOf(180, 183)
    const val OLD_SPRITE = 1819
    val NEW_SPRITE get() = QuestTabInterfaceImportTool.GAMEFRAME_ICON

    const val LAYOUT_SCRIPT_ID = 722

    /** Script 722 instruction -> (old operand, new operand): the 746:26 positions for the 33 x 36 skull. */
    val LAYOUT_POSITIONS = mapOf(257 to (124 to 119), 258 to (43 to 38), 516 to (364 to 359), 517 to (6 to 1))

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val library = CacheLibrary(LootKeyInterfaceImportTool.TARGETS[0])
        val (current, layout) =
            try {
                requireNotNull(library.data(CLIENTSCRIPT_INDEX, SCRIPT_ID, 0)) { "Missing clientscript $SCRIPT_ID" } to
                    requireNotNull(library.data(CLIENTSCRIPT_INDEX, LAYOUT_SCRIPT_ID, 0)) { "Missing clientscript $LAYOUT_SCRIPT_ID" }
            } finally {
                library.close()
            }
        val patched = patch(current)
        val mutations =
            listOf(
                CacheMutation(
                    CLIENTSCRIPT_INDEX,
                    SCRIPT_ID,
                    0,
                    patched,
                    "clientscript $SCRIPT_ID: quest tab icon $OLD_SPRITE -> $NEW_SPRITE (Deadman skull)",
                    expectedCurrentSha1 = CacheItemProbeTool.sha1(current),
                ),
                CacheMutation(
                    CLIENTSCRIPT_INDEX,
                    LAYOUT_SCRIPT_ID,
                    0,
                    patchOperands(layout, LAYOUT_SCRIPT_ID, LAYOUT_POSITIONS),
                    "clientscript $LAYOUT_SCRIPT_ID: resizable quest tab icon 746:26 centred for the 33x36 skull",
                    expectedCurrentSha1 = CacheItemProbeTool.sha1(layout),
                ),
            )
        val transaction = CacheTransaction(targets = LootKeyInterfaceImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY RUN: pass --apply to write clientscripts $SCRIPT_ID and $LAYOUT_SCRIPT_ID")
            return
        }
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        println("APPLIED ${applied.applied} skipped=${applied.skipped} journal=${applied.journalDir}")
        check(problems.isEmpty()) { "verify failed: $problems" }
        println("VERIFY_OK transaction=${transaction.id}")
    }

    /** The patched script; the input unchanged when it already carries the skull. */
    fun patch(current: ByteArray): ByteArray {
        val decoded = ProductionTabClientScriptPatchTool.decode(current)
        if (SPRITE_INSTRUCTIONS.all { decoded[it].opcode == PUSH_CONSTANT_INT && decoded[it].intOperand == NEW_SPRITE }) return current
        SPRITE_INSTRUCTIONS.forEach {
            check(decoded[it].opcode == PUSH_CONSTANT_INT && decoded[it].intOperand == OLD_SPRITE) {
                "clientscript $SCRIPT_ID instruction $it is not PUSH_CONSTANT_INT($OLD_SPRITE): ${decoded[it]}"
            }
        }
        val out = ByteArrayOutputStream(current.size)
        var cursor = 0
        SPRITE_INSTRUCTIONS.forEach { i ->
            val instruction = decoded[i]
            out.write(current, cursor, instruction.start + 2 - cursor)
            val v = NEW_SPRITE
            out.write(byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte()))
            cursor = instruction.end
        }
        out.write(current, cursor, current.size - cursor)
        val patched = out.toByteArray()
        val check = ProductionTabClientScriptPatchTool.decode(patched)
        check(check.size == decoded.size && patched.size == current.size) { "script shape changed" }
        decoded.indices.forEach { i ->
            val expected = if (i in SPRITE_INSTRUCTIONS) NEW_SPRITE else decoded[i].intOperand
            check(check[i].opcode == decoded[i].opcode && check[i].intOperand == expected && check[i].stringOperand == decoded[i].stringOperand) {
                "instruction $i differs after patch"
            }
        }
        return patched
    }

    /**
     * [current] with the PUSH_CONSTANT_INT operands of [changes] (instruction -> old to new) replaced in place; unchanged
     * when every operand already has its new value.
     */
    fun patchOperands(current: ByteArray, scriptId: Int, changes: Map<Int, Pair<Int, Int>>): ByteArray {
        val decoded = ProductionTabClientScriptPatchTool.decode(current)
        if (changes.all { (i, v) -> decoded[i].opcode == PUSH_CONSTANT_INT && decoded[i].intOperand == v.second }) return current
        val out = current.copyOf()
        changes.forEach { (i, v) ->
            val instruction = decoded[i]
            check(instruction.opcode == PUSH_CONSTANT_INT && instruction.intOperand == v.first) {
                "clientscript $scriptId instruction $i is not PUSH_CONSTANT_INT(${v.first}): $instruction"
            }
            val at = instruction.start + 2
            val n = v.second
            out[at] = (n ushr 24).toByte()
            out[at + 1] = (n ushr 16).toByte()
            out[at + 2] = (n ushr 8).toByte()
            out[at + 3] = n.toByte()
        }
        val check = ProductionTabClientScriptPatchTool.decode(out)
        check(check.size == decoded.size) { "script $scriptId shape changed" }
        decoded.indices.forEach { i ->
            val expected = changes[i]?.second ?: decoded[i].intOperand
            check(check[i].opcode == decoded[i].opcode && check[i].intOperand == expected && check[i].stringOperand == decoded[i].stringOperand) {
                "script $scriptId instruction $i differs after patch"
            }
        }
        return out
    }
}
