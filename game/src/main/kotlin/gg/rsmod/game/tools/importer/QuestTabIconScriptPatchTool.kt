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
 * Only those two operands change (same instruction count and length, so every branch stays valid). Applied
 * transactionally to both caches. `--apply` writes; without it the tool only reports the preflight.
 */
object QuestTabIconScriptPatchTool {
    const val CLIENTSCRIPT_INDEX = 12
    const val SCRIPT_ID = 1766
    private const val PUSH_CONSTANT_INT = 0
    val SPRITE_INSTRUCTIONS = intArrayOf(180, 183)
    const val OLD_SPRITE = 1819
    val NEW_SPRITE get() = QuestTabInterfaceImportTool.GAMEFRAME_ICON

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val library = CacheLibrary(LootKeyInterfaceImportTool.TARGETS[0])
        val current =
            try {
                requireNotNull(library.data(CLIENTSCRIPT_INDEX, SCRIPT_ID, 0)) { "Missing clientscript $SCRIPT_ID" }
            } finally {
                library.close()
            }
        val patched = patch(current)
        val mutation =
            CacheMutation(
                CLIENTSCRIPT_INDEX,
                SCRIPT_ID,
                0,
                patched,
                "clientscript $SCRIPT_ID: quest tab icon $OLD_SPRITE -> $NEW_SPRITE (Deadman skull)",
                expectedCurrentSha1 = CacheItemProbeTool.sha1(current),
            )
        val transaction = CacheTransaction(targets = LootKeyInterfaceImportTool.TARGETS, mutations = listOf(mutation))
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY RUN: pass --apply to write clientscript $SCRIPT_ID")
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
}
