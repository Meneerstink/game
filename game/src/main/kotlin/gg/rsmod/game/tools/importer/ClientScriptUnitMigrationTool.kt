package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.ByteArrayOutputStream

/**
 * Removes the legacy x10 max-total multipliers from the three production orb/stat CS2 scripts.
 *
 * The tool deliberately edits only the proven instruction pairs in scripts 2915, 2916 and 5255,
 * and applies the same mutation transactionally to the game and file-server caches.
 */
object ClientScriptUnitMigrationTool {
    private const val CLIENTSCRIPT_INDEX = 12
    private val SCRIPT_IDS = intArrayOf(2915, 2916, 5255)
    private const val PUSH_CONSTANT_INT = 0
    private const val MULTIPLY = 4002

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 2) { "Usage: <gameCache>;<fileServerCache> [journalRoot]" }
        val targets = args[0].split(';').filter { it.isNotBlank() }
        require(targets.size == 2) { "First argument must contain exactly two cache paths separated by ';'" }
        val journalRoot = java.io.File(args[1])

        val mutations = targets.first().let { cache ->
            val library = CacheLibrary(cache)
            try {
                SCRIPT_IDS.map { scriptId ->
                    val current = requireNotNull(library.data(CLIENTSCRIPT_INDEX, scriptId, 0)) {
                        "Missing clientscript $scriptId in $cache"
                    }
                    val migrated = migrate(scriptId, current)
                    CacheMutation(
                        indexId = CLIENTSCRIPT_INDEX,
                        groupId = scriptId,
                        fileId = 0,
                        newBytes = migrated,
                        label = "clientscript $scriptId: remove legacy x10 max-total multiplier",
                        expectedCurrentSha1 = CacheItemProbeTool.sha1(current),
                    )
                }
            } finally {
                library.close()
            }
        }

        val transaction = CacheTransaction(targets, mutations, journalRoot = journalRoot)
        val plan = transaction.preflight()
        println(plan.joinToString("\n"))
        check(transaction.blockingErrors(plan).isEmpty()) { transaction.blockingErrors(plan).joinToString("\n") }
        val result = transaction.apply(plan)
        val problems = transaction.verify()
        check(problems.isEmpty()) { problems.joinToString("\n") }
        println("CLIENTSCRIPT_UNIT_MIGRATION transaction=${result.transactionId} applied=${result.applied} skipped=${result.skipped} verified=true")
    }

    internal fun migrate(scriptId: Int, current: ByteArray): ByteArray {
        val decoded = decodeInstructionSpans(current)
        val pairs = decoded.zipWithNext().filter { (first, second) ->
            first.opcode == PUSH_CONSTANT_INT && first.operand == 10 &&
                second.opcode == MULTIPLY && second.operand == 0
        }
        val expectedPairs = when (scriptId) {
            2915, 5255 -> 1
            2916 -> 2
            else -> error("Unsupported clientscript $scriptId")
        }
        check(pairs.size == expectedPairs) {
            "clientscript $scriptId expected $expectedPairs x10 multiplier pair(s), found ${pairs.size}"
        }

        val footerLength = readUnsignedShort(current, current.size - 2)
        val metadataOffset = current.size - footerLength - 2 - 16
        // Switch tables hold absolute jump offsets in the script footer, which this tool does not rewrite.
        check(decoded.none { it.opcode == SWITCH }) { "clientscript $scriptId has a switch table; removal would corrupt its jumps" }
        val removedInstructions = pairs.flatMap { (first, second) -> listOf(first, second) }.toSet()
        val removed = pairs.flatMap { (first, second) -> first.start until second.end }.toSet()
        val retained = ByteArrayOutputStream(current.size)
        current.indices.filter { index -> index !in removed }.forEach { retained.write(current[it].toInt()) }
        val migrated = retained.toByteArray()

        // CS2 branch operands are relative instruction offsets. Removing instructions without
        // retargeting branches corrupts control flow (script 2916's first branch used to jump over
        // an x10 pair and would return without pushing a value after that pair was removed).
        val retainedInstructions = decoded.filterNot { it in removedInstructions }
        retainedInstructions.forEachIndexed { newIndex, instruction ->
            if (instruction.opcode in BRANCH_OPCODES) {
                val oldIndex = decoded.indexOf(instruction)
                val oldTarget = oldIndex + 1 + requireNotNull(instruction.operand)
                check(oldTarget in 0..decoded.size) { "clientscript $scriptId branch target out of range" }
                check(oldTarget == decoded.size || decoded[oldTarget] !in removedInstructions) {
                    "clientscript $scriptId branch targets a removed instruction"
                }
                val newTarget = decoded.take(oldTarget).count { it !in removedInstructions }
                val newOperand = newTarget - newIndex - 1
                val newStart = instruction.start - removed.count { it < instruction.start }
                writeInt(migrated, newStart + 2, newOperand)
            }
        }

        val newMetadataOffset = metadataOffset - pairs.sumOf { (first, second) -> second.end - first.start }
        val oldOpcodeCount = readInt(current, metadataOffset)
        check(oldOpcodeCount == decoded.size) { "clientscript $scriptId metadata opcode count mismatch" }
        writeInt(migrated, newMetadataOffset, oldOpcodeCount - pairs.size * 2)
        return migrated
    }

    private data class Instruction(val start: Int, val end: Int, val opcode: Int, val operand: Int?)

    // Every relative branch of the client's ClientScriptOpCode: BRANCH 6, BRANCH_NOT..GREATER_THAN 7-10, <= / >= 31-32,
    // LONG_BRANCH_* 68-73, BRANCH_IF_TRUE/FALSE 86-87 (RCV-012 B6: the set used to stop at 10).
    private val BRANCH_OPCODES = setOf(6, 7, 8, 9, 10, 31, 32, 68, 69, 70, 71, 72, 73, 86, 87)
    private const val SWITCH = 51

    private fun decodeInstructionSpans(data: ByteArray): List<Instruction> {
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
            val operand = when {
                opcode == 3 -> { while (data[pos++].toInt() != 0) {}; null }
                opcode == 54 -> { pos += 8; null }
                opcode >= 150 || opcode == 21 || opcode == 38 || opcode == 39 -> data[pos++].toInt() and 0xFF
                else -> { val value = readInt(data, pos); pos += 4; value }
            }
            result += Instruction(start, pos, opcode, operand)
        }
        check(pos == metadataOffset) { "clientscript instruction stream did not end at metadata" }
        return result
    }

    private fun readUnsignedShort(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    private fun readInt(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)

    private fun writeInt(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value ushr 24).toByte()
        data[offset + 1] = (value ushr 16).toByte()
        data[offset + 2] = (value ushr 8).toByte()
        data[offset + 3] = value.toByte()
    }
}
