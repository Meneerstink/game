package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Turns the gameframe's spare "Production" tab into the Follower Details tab (owner decision
 * 2026-09-13: summon icon and "Follower Details" label).
 *
 * Clientscript 1766 refreshes the whole tab strip. For the spare tab (548:99 / 746:47, icon 548:107 /
 * 746:31) it clears the graphic and every op unless VARC 823 == 2, and otherwise draws sprite 447 and
 * sets op1 "Production" on both tab buttons. The server now sets VARC 823 = 2; this tool only swaps
 * the two presentation constants inside that branch:
 *
 *   [460] PUSH_CONSTANT_INT(447)          -> 1200 (summon icon, the orb's own sprite)
 *   [463] PUSH_CONSTANT_INT(447)          -> 1200
 *   [467] PUSH_CONSTANT_STRING("Production") -> "Follower Details"
 *   [471] PUSH_CONSTANT_STRING("Production") -> "Follower Details"
 *
 * Nothing else is touched: instruction count and every branch operand (relative instruction offsets)
 * are unchanged, and the metadata footer is located from the end of the file, so a longer string is
 * safe. Applied transactionally (journal + SHA-1 preflight/verify) to the game and file-server caches.
 */
object ProductionTabClientScriptPatchTool {
    const val CLIENTSCRIPT_INDEX = 12
    const val SCRIPT_ID = 1766
    private const val PUSH_CONSTANT_INT = 0
    private const val PUSH_CONSTANT_STRING = 3
    private val SPRITE_INSTRUCTIONS = intArrayOf(460, 463)
    private val LABEL_INSTRUCTIONS = intArrayOf(467, 471)
    const val OLD_SPRITE = 447
    const val NEW_SPRITE = 1200
    const val OLD_LABEL = "Production"
    const val NEW_LABEL = "Follower Details"

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 2) { "Usage: <gameCache>;<fileServerCache> <journalRoot>" }
        val targets = args[0].split(';').filter { it.isNotBlank() }
        require(targets.size == 2) { "First argument must contain exactly two cache paths separated by ';'" }

        val library = CacheLibrary(targets.first())
        val current =
            try {
                requireNotNull(library.data(CLIENTSCRIPT_INDEX, SCRIPT_ID, 0)) { "Missing clientscript $SCRIPT_ID" }
            } finally {
                library.close()
            }
        val patched = patch(current)
        if (patched.contentEquals(current)) {
            println("PRODUCTION_TAB_PATCH script=$SCRIPT_ID already_patched=true")
            return
        }
        val mutation =
            CacheMutation(
                indexId = CLIENTSCRIPT_INDEX,
                groupId = SCRIPT_ID,
                fileId = 0,
                newBytes = patched,
                label = "clientscript $SCRIPT_ID: spare tab -> Follower Details (sprite $NEW_SPRITE, op1 '$NEW_LABEL')",
                expectedCurrentSha1 = CacheItemProbeTool.sha1(current),
            )
        val transaction = CacheTransaction(targets, listOf(mutation), journalRoot = File(args[1]))
        val plan = transaction.preflight()
        println(plan.joinToString("\n"))
        check(transaction.blockingErrors(plan).isEmpty()) { transaction.blockingErrors(plan).joinToString("\n") }
        val result = transaction.apply(plan)
        val problems = transaction.verify()
        check(problems.isEmpty()) { problems.joinToString("\n") }
        println("PRODUCTION_TAB_PATCH transaction=${result.transactionId} applied=${result.applied} skipped=${result.skipped} verified=true")
    }

    /** Returns the patched script, or the input unchanged when it is already patched. */
    fun patch(current: ByteArray): ByteArray {
        val decoded = decode(current)
        val alreadyPatched =
            SPRITE_INSTRUCTIONS.all { decoded[it].opcode == PUSH_CONSTANT_INT && decoded[it].intOperand == NEW_SPRITE } &&
                LABEL_INSTRUCTIONS.all { decoded[it].opcode == PUSH_CONSTANT_STRING && decoded[it].stringOperand == NEW_LABEL }
        if (alreadyPatched) return current

        SPRITE_INSTRUCTIONS.forEach {
            check(decoded[it].opcode == PUSH_CONSTANT_INT && decoded[it].intOperand == OLD_SPRITE) {
                "clientscript $SCRIPT_ID instruction $it is not PUSH_CONSTANT_INT($OLD_SPRITE): ${decoded[it]}"
            }
        }
        LABEL_INSTRUCTIONS.forEach {
            check(decoded[it].opcode == PUSH_CONSTANT_STRING && decoded[it].stringOperand == OLD_LABEL) {
                "clientscript $SCRIPT_ID instruction $it is not PUSH_CONSTANT_STRING(\"$OLD_LABEL\"): ${decoded[it]}"
            }
        }

        val out = ByteArrayOutputStream(current.size + 16)
        var cursor = 0
        decoded.forEachIndexed { index, instruction ->
            when (index) {
                in SPRITE_INSTRUCTIONS -> {
                    out.write(current, cursor, instruction.start + 2 - cursor)
                    out.write(intBytes(NEW_SPRITE))
                    cursor = instruction.end
                }
                in LABEL_INSTRUCTIONS -> {
                    out.write(current, cursor, instruction.start + 2 - cursor)
                    out.write(NEW_LABEL.toByteArray(Charsets.ISO_8859_1))
                    out.write(0)
                    cursor = instruction.end
                }
            }
        }
        out.write(current, cursor, current.size - cursor)
        val patched = out.toByteArray()

        val check = decode(patched)
        check(check.size == decoded.size) { "instruction count changed" }
        decoded.indices.forEach { i ->
            val expected =
                when (i) {
                    in SPRITE_INSTRUCTIONS -> decoded[i].copy(intOperand = NEW_SPRITE)
                    in LABEL_INSTRUCTIONS -> decoded[i].copy(stringOperand = NEW_LABEL)
                    else -> decoded[i]
                }
            check(check[i].opcode == expected.opcode && check[i].intOperand == expected.intOperand && check[i].stringOperand == expected.stringOperand) {
                "instruction $i differs after patch: ${check[i]} vs $expected"
            }
        }
        return patched
    }

    data class Instruction(
        val start: Int,
        val end: Int,
        val opcode: Int,
        val intOperand: Int?,
        val stringOperand: String?,
    )

    /** Same instruction-span decode as [ClientScriptUnitMigrationTool], with string operands kept. */
    fun decode(data: ByteArray): List<Instruction> {
        val footerLength = readUnsignedShort(data, data.size - 2)
        val metadataOffset = data.size - footerLength - 2 - 16
        var pos = 0
        while (data[pos].toInt() != 0) pos++
        pos++
        val result = mutableListOf<Instruction>()
        while (pos < metadataOffset) {
            val start = pos
            val opcode = readUnsignedShort(data, pos)
            pos += 2
            var intOperand: Int? = null
            var stringOperand: String? = null
            when {
                opcode == 3 -> {
                    val s = pos
                    while (data[pos].toInt() != 0) pos++
                    stringOperand = String(data, s, pos - s, Charsets.ISO_8859_1)
                    pos++
                }
                opcode == 54 -> pos += 8
                opcode >= 150 || opcode == 21 || opcode == 38 || opcode == 39 -> intOperand = data[pos++].toInt() and 0xFF
                else -> {
                    intOperand = readInt(data, pos)
                    pos += 4
                }
            }
            result += Instruction(start, pos, opcode, intOperand, stringOperand)
        }
        check(pos == metadataOffset) { "clientscript instruction stream did not end at metadata" }
        val count = readInt(data, metadataOffset)
        check(count == result.size) { "metadata opcode count $count != decoded ${result.size}" }
        return result
    }

    private fun intBytes(value: Int) = byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte())

    private fun readUnsignedShort(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    private fun readInt(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)
}
