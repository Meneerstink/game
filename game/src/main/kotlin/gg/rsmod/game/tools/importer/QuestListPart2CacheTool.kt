package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.ByteArrayOutputStream

/**
 * Quest list part 2 (owner 2026-09-27): the Free/Members dropdown, the "Filter" and "Hide done" checkboxes and the
 * "Showing all ... items" line leave the 667 quest list (interface 190), which then shows two parts - every quest that is
 * not completed on top, the completed ones below, each part alphabetical - with the list using the freed space.
 *
 * How the list works (2026-09-27 disassembly of the 667 cache):
 *  - Clientscript 2162 builds the list. Its five arguments are the list type (1 = quests), the grouping (0 Free/Members,
 *    1 Progress, 2 Difficulty), "reversed", "Filter" and "Hide done"; 2169 (190's var transmit) and 2160 (on load) pass
 *    them from varbits 4536-4538 and client vars 692-694 and 1103. It sorts the slots by the quests' sort names (struct
 *    param 846, clientscript 2163), then draws one group after the other: the group order is enum 2250 (position ->
 *    group), the group titles enum 2249, and in Progress mode a quest's group is its clientscript 2193 status (0 not
 *    started, 1 in progress, 2 complete) - the same value that picks its red / yellow / green colour.
 *  - Interface 190: the filter controls are 9-12, the dropdown 14-16, "Showing" is 8 with the divider lines 4 and 5 around
 *    it; the list layer 13 is bottom-aligned 12 px from the bottom, 149 high (quest tab part 1), so it starts at y 76.
 *
 * The changes, all without moving an instruction (so no branch offset changes):
 *  - 2162 instructions 0-8 set the arguments to Progress, not reversed, no filter, nothing hidden (they were the "-1
 *    argument" checks, which no caller hits); 299-304 put a quest in group status / 2 (0 not completed, 1 completed)
 *    instead of its status; 582 jumps over the "Showing ..." text. Switch 2 case 3 (a list type enum 169 does not have,
 *    2162 returns before it) points at the same group code.
 *  - 2169 instruction 18 jumps to its return: no dropdown is built any more.
 *  - Enum 2249: the two titles "Not completed" and "Completed" (owner 2026-09-27); enum 2250: position 0 -> group 0,
 *    1 -> 1. The colours still come from the unchanged status, so red, yellow and green stay as they are.
 *  - Interface 190: 4, 5, 8, 9, 10, 11, 12 and 16 hidden; the list layer 13 200 high, so it starts at y 25 right under
 *    the top divider - the space of the filters.
 *
 * One [CacheTransaction] over both production caches (preflight, journal, verify); running it again is a no-op.
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.QuestListPart2CacheTool plan|apply`
 */
object QuestListPart2CacheTool {
    const val LIST_SCRIPT = 2162
    const val TRANSMIT_SCRIPT = 2169
    const val TITLE_ENUM = 2249
    const val ORDER_ENUM = 2250
    const val QUEST_LIST = 190
    const val LIST_LAYER = 13
    const val LIST_HEIGHT = 200

    /** Interface 190 components that belong to the filters (see the class comment). */
    val HIDDEN = listOf(4, 5, 8, 9, 10, 11, 12, 16)

    val TITLES = linkedMapOf(0 to "Not completed", 1 to "Completed")
    val ORDER = linkedMapOf(0 to 0, 1 to 1)

    val LIST_FIT = QuestTabInterfaceImportTool.FieldPatch(QUEST_LIST, LIST_LAYER, 10, 149, LIST_HEIGHT)

    private const val PUSH_INT = 0
    private const val BRANCH = 6
    private const val PUSH_INT_LOCAL = 33
    private const val POP_INT_LOCAL = 34
    private const val PUSH_ARRAY_INT = 45
    private const val DIVIDE = 4003

    /** Instruction 303 reads client var 699; only its operand is checked. */
    private const val ANY_OPCODE = -1

    class Op(val opcode: Int, val operand: Int)

    /** Instruction index -> (what the cache has, what it becomes). */
    val LIST_PATCH: Map<Int, Pair<Op, Op>> =
        mapOf(
            0 to (Op(PUSH_INT_LOCAL, 1) to Op(PUSH_INT, 1)),
            1 to (Op(PUSH_INT, -1) to Op(POP_INT_LOCAL, 1)),
            2 to (Op(BRANCH + 2, 10) to Op(PUSH_INT, 0)),
            3 to (Op(PUSH_INT_LOCAL, 2) to Op(POP_INT_LOCAL, 2)),
            4 to (Op(PUSH_INT, -1) to Op(PUSH_INT, 0)),
            5 to (Op(BRANCH + 2, 7) to Op(POP_INT_LOCAL, 3)),
            6 to (Op(PUSH_INT_LOCAL, 3) to Op(PUSH_INT, 0)),
            7 to (Op(PUSH_INT, -1) to Op(POP_INT_LOCAL, 4)),
            8 to (Op(BRANCH + 2, 4) to Op(BRANCH, 5)),
            301 to (Op(POP_INT_LOCAL, 22) to Op(PUSH_INT, 2)),
            302 to (Op(BRANCH, 4) to Op(DIVIDE, 0)),
            303 to (Op(ANY_OPCODE, 699) to Op(POP_INT_LOCAL, 22)),
            304 to (Op(PUSH_INT, 677) to Op(BRANCH, 2)),
            582 to (Op(PUSH_INT_LOCAL, 0) to Op(BRANCH, 32)),
        )

    val TRANSMIT_PATCH: Map<Int, Pair<Op, Op>> = mapOf(18 to (Op(PUSH_INT, 0) to Op(BRANCH, 39)))

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = args.firstOrNull() == "apply"
        val mutations = plan(LootKeyInterfaceImportTool.TARGETS[0])
        if (mutations.isEmpty()) {
            println("QUEST_LIST_PART2 already applied")
            return
        }
        val transaction = CacheTransaction(LootKeyInterfaceImportTool.TARGETS, mutations)
        val preflight = transaction.preflight()
        println(preflight.joinToString("\n"))
        val errors = transaction.blockingErrors(preflight)
        check(errors.isEmpty()) { errors.joinToString("\n") }
        if (!apply) return
        val result = transaction.apply(preflight)
        val problems = transaction.verify()
        check(problems.isEmpty()) { problems.joinToString("\n") }
        println("QUEST_LIST_PART2 transaction=${result.transactionId} applied=${result.applied} skipped=${result.skipped} verified=true")
    }

    /** The mutations still needed against [cache]; empty once everything is in place. */
    fun plan(cache: String): List<CacheMutation> {
        val library = CacheLibrary(cache)
        try {
            val mutations = mutableListOf<CacheMutation>()
            fun script(id: Int, patched: (ByteArray) -> ByteArray, what: String) {
                val current = requireNotNull(library.data(QuestListCacheTool.CLIENTSCRIPT_INDEX, id, 0)) { "clientscript $id missing" }
                val next = patched(current)
                if (!next.contentEquals(current)) {
                    mutations += CacheMutation(QuestListCacheTool.CLIENTSCRIPT_INDEX, id, 0, next, what, expectedCurrentSha1 = CacheItemProbeTool.sha1(current))
                }
            }
            script(LIST_SCRIPT, ::patchListScript, "clientscript $LIST_SCRIPT: no filters, two parts (not completed / completed)")
            script(TRANSMIT_SCRIPT, ::patchTransmitScript, "clientscript $TRANSMIT_SCRIPT: no Free/Members dropdown")
            fun enum(id: Int, next: ByteArray, what: String) {
                val current = requireNotNull(library.data(QuestListCacheTool.ENUM_INDEX, id ushr 8, id and 0xFF)) { "enum $id missing" }
                if (!next.contentEquals(current)) {
                    mutations += CacheMutation(QuestListCacheTool.ENUM_INDEX, id ushr 8, id and 0xFF, next, what, expectedCurrentSha1 = CacheItemProbeTool.sha1(current))
                }
            }
            enum(TITLE_ENUM, encodeTitles(), "enum $TITLE_ENUM: Not completed, Completed")
            val order = requireNotNull(library.data(QuestListCacheTool.ENUM_INDEX, ORDER_ENUM ushr 8, ORDER_ENUM and 0xFF))
            enum(ORDER_ENUM, QuestListCacheTool.EnumCodec.encode(QuestListCacheTool.EnumCodec.decode(order).copyWith(ORDER, replace = true)), "enum $ORDER_ENUM: Not completed, Completed")
            val index = DeathsOfficeInterfaceImportTool.INDEX_INTERFACES
            (HIDDEN + LIST_LAYER).distinct().forEach { component ->
                val current = requireNotNull(library.data(index, QUEST_LIST, component)) { "$QUEST_LIST:$component missing" }
                val next = if (component == LIST_LAYER) QuestTabInterfaceImportTool.patchField(current, LIST_FIT) else hide(current)
                if (!next.contentEquals(current)) {
                    mutations += CacheMutation(index, QUEST_LIST, component, next, "interface $QUEST_LIST:$component (quest list without filters)", expectedCurrentSha1 = CacheItemProbeTool.sha1(current))
                }
            }
            return mutations
        } finally {
            library.close()
        }
    }

    /** Sets the hidden flag of a legacy if3 component (version 255, no name: the flags byte is at 18). */
    fun hide(bytes: ByteArray): ByteArray {
        require(bytes[0].toInt() and 0xFF == 255 && bytes[1].toInt() and 0x80 == 0) { "not a nameless legacy if3 component" }
        if (bytes[HIDDEN_FLAGS].toInt() and 1 != 0) return bytes
        return bytes.copyOf().also { it[HIDDEN_FLAGS] = (it[HIDDEN_FLAGS].toInt() or 1).toByte() }
    }

    fun isHidden(bytes: ByteArray): Boolean = bytes[HIDDEN_FLAGS].toInt() and 1 != 0

    private const val HIDDEN_FLAGS = 18

    fun encodeTitles(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(1)
        out.write('i'.code)
        out.write(2)
        out.write('s'.code)
        out.write(7)
        out.write(0)
        out.write(TITLES.size)
        out.write(0)
        out.write(TITLES.size)
        TITLES.forEach { (key, text) ->
            out.write(key ushr 8)
            out.write(key)
            out.write(text.toByteArray(Charsets.ISO_8859_1))
            out.write(0)
        }
        out.write(3)
        out.write(0)
        out.write(0)
        return out.toByteArray()
    }

    fun patchListScript(data: ByteArray): ByteArray = patchCaseThree(patch(data, LIST_PATCH, LIST_SCRIPT))

    fun patchTransmitScript(data: ByteArray): ByteArray = patch(data, TRANSMIT_PATCH, TRANSMIT_SCRIPT)

    /**
     * Replaces the instructions of [changes] (same count, so every branch and switch offset stays valid). An instruction
     * that already has its new form is left alone; anything else than the expected old form refuses the patch.
     */
    fun patch(data: ByteArray, changes: Map<Int, Pair<Op, Op>>, id: Int): ByteArray {
        val instructions = ProductionTabClientScriptPatchTool.decode(data)
        var changed = false
        val body = ByteArrayOutputStream()
        val codeStart = instructions.first().start
        body.write(data, 0, codeStart)
        instructions.forEachIndexed { i, ins ->
            val change = changes[i]
            if (change == null) {
                body.write(data, ins.start, ins.end - ins.start)
                return@forEachIndexed
            }
            val (old, new) = change
            val isNew = ins.opcode == new.opcode && ins.intOperand == new.operand
            val isOld = (old.opcode == ANY_OPCODE || ins.opcode == old.opcode) && ins.intOperand == old.operand
            check(isNew || isOld) { "clientscript $id instruction $i is ${ins.opcode} ${ins.intOperand}, expected ${old.opcode} ${old.operand}" }
            if (isNew) {
                body.write(data, ins.start, ins.end - ins.start)
            } else {
                changed = true
                encode(body, new)
            }
        }
        if (!changed) return data
        val metadataOffset = instructions.last().end
        body.write(data, metadataOffset, data.size - metadataOffset)
        val out = body.toByteArray()
        check(ProductionTabClientScriptPatchTool.decode(out).size == instructions.size) { "clientscript $id instruction count changed" }
        return out
    }

    private fun encode(out: ByteArrayOutputStream, op: Op) {
        out.write(op.opcode ushr 8)
        out.write(op.opcode)
        if (op.opcode >= 150 || op.opcode == 21 || op.opcode == 38 || op.opcode == 39) {
            out.write(op.operand)
        } else {
            out.write(op.operand ushr 24)
            out.write(op.operand ushr 16)
            out.write(op.operand ushr 8)
            out.write(op.operand)
        }
    }

    /** 2162 switch 2 (over the list type): case 3 -> +1, the same group code as case 1. */
    fun patchCaseThree(data: ByteArray): ByteArray {
        val footerLength = u16(data, data.size - 2)
        var p = data.size - 2 - footerLength
        val tables = data[p++].toInt() and 0xFF
        check(tables == 4) { "clientscript $LIST_SCRIPT has $tables switch tables, expected 4" }
        for (t in 0 until tables) {
            val cases = u16(data, p)
            p += 2
            repeat(cases) {
                val key = s32(data, p)
                val offset = s32(data, p + 4)
                if (t == 2 && key == 3) {
                    if (offset == 1) return data
                    check(offset == 5) { "switch 2 case 3 is +$offset, expected +5" }
                    return data.copyOf().also { out ->
                        out[p + 4] = 0
                        out[p + 5] = 0
                        out[p + 6] = 0
                        out[p + 7] = 1
                    }
                }
                p += 8
            }
        }
        error("clientscript $LIST_SCRIPT has no switch 2 case 3")
    }

    private fun u16(data: ByteArray, offset: Int): Int = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    private fun s32(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 24) or ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or (data[offset + 3].toInt() and 0xFF)
}
