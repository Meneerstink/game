package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.ByteArrayOutputStream

/**
 * Spellbook icons and tooltips for Blighted sacks (owner 2026-09-24: "blighted entangle, blighted teleport, blighted ancient
 * sack geven nog geen kleuren in spellbooks"; "de preview fixen"; "we hebben toch deadmanmode").
 *
 * Every spell icon runs CS2 6 on load; it calls CS2 21 (lit/grey icon: level check, then each rune through CS2 19) and
 * registers inventory/stat transmits that run CS2 16, which only forwards to CS2 21. Hovering runs CS2 10, the tooltip
 * (int args 0 = component, 2 = level, 3..10 = rune id/amount pairs, each counted through CS2 19).
 *
 * A sack only works where the server allows it (`BlightedSacks.allowedAt`: not in the Deadman guarded cities). The server
 * keeps VARC [VARC_SACKS_ALLOWED] at 1 where that is true and 0 elsewhere, and resends the inventory on a change so CS2 21
 * re-runs. Varc 1415 is defined in the 667 varc config (1660 entries) and read or written by none of the 5377 scripts.
 *
 * CS2 21: at index 56 (after the level check) one block per sack spell:
 *   if (component == spell && varc 1415 && inv_total(93, sack) > 0) goto 112 (the lit-icon tail)
 * CS2 10: at index 0 one block per sack spell:
 *   if (component == spell && varc 1415 && inv_total(93, sack) > 0) { rune1 = sack x1; runes 2..4 = none }
 * so the tooltip shows the sack and "have/1", exactly like a rune. Insertions sit where no existing branch crosses them;
 * every original instruction is re-checked after the patch. The first version of this patch (2026-09-24, CS2 21 only,
 * without the varc) is recognised and replaced.
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.BlightedSackSpellbookPatchTool [--apply]`
 */
object BlightedSackSpellbookPatchTool {
    const val CLIENTSCRIPT_INDEX = 12
    const val ICON_SCRIPT = 21
    const val TOOLTIP_SCRIPT = 10
    const val VARC_SACKS_ALLOWED = 1415

    const val ICON_INSERT_AT = 56
    const val ICON_CASTABLE_AT = 112
    const val ICON_ORIGINAL_SIZE = 127
    const val TOOLTIP_ORIGINAL_SIZE = 548

    private const val PUSH_CONSTANT_INT = 0
    private const val BRANCH = 6
    private const val BRANCH_NOT = 7
    private const val BRANCH_GREATER_THAN = 10
    private const val BRANCH_LESS_THAN_OR_EQUALS = 31
    private const val PUSH_INT_LOCAL = 33
    private const val POP_INT_LOCAL = 34
    private const val PUSH_VARC = 42
    private const val BRANCH_IF_FALSE = 87
    private const val INV_TOTAL = 3303
    private const val INVENTORY = 93
    private val BRANCH_OPCODES = (6..10) + (31..32) + (68..73) + (86..87)

    private const val ANCIENT_ICE_SACK = 22716
    private const val ENTANGLE_SACK = 22717
    private const val TELEPORT_SPELL_SACK = 22718
    private const val VENGEANCE_SACK = 22719

    private fun component(
        interfaceId: Int,
        child: Int,
    ) = (interfaceId shl 16) or child

    /** (spell component, sack) - the same spells as `BlightedSacks.Sack`, components from `SpellbookData`. */
    val SPELLS: List<Pair<Int, Int>> =
        listOf(
            component(193, 20) to ANCIENT_ICE_SACK, // Ice Rush
            component(193, 22) to ANCIENT_ICE_SACK, // Ice Burst
            component(193, 21) to ANCIENT_ICE_SACK, // Ice Blitz
            component(193, 23) to ANCIENT_ICE_SACK, // Ice Barrage
            component(192, 36) to ENTANGLE_SACK, // Bind
            component(192, 55) to ENTANGLE_SACK, // Snare
            component(192, 81) to ENTANGLE_SACK, // Entangle
            component(192, 86) to TELEPORT_SPELL_SACK, // Teleport Block
            component(430, 37) to VENGEANCE_SACK, // Vengeance
            component(430, 42) to VENGEANCE_SACK, // Vengeance Other
        )

    private const val V1_BLOCK = 8
    private const val ICON_BLOCK = 10
    private const val TOOLTIP_BLOCK = 27

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val library = CacheLibrary(LootKeyInterfaceImportTool.TARGETS[0])
        val icon: ByteArray
        val tooltip: ByteArray
        try {
            icon = requireNotNull(library.data(CLIENTSCRIPT_INDEX, ICON_SCRIPT, 0)) { "Missing clientscript $ICON_SCRIPT" }
            tooltip = requireNotNull(library.data(CLIENTSCRIPT_INDEX, TOOLTIP_SCRIPT, 0)) { "Missing clientscript $TOOLTIP_SCRIPT" }
        } finally {
            library.close()
        }
        val mutations = mutableListOf<CacheMutation>()
        val newIcon = patchIcon(icon)
        if (!newIcon.contentEquals(icon)) {
            mutations +=
                CacheMutation(indexId = CLIENTSCRIPT_INDEX, groupId = ICON_SCRIPT, fileId = 0, newBytes = newIcon, label = "clientscript $ICON_SCRIPT: blighted sack spells lit where usable", expectedCurrentSha1 = CacheItemProbeTool.sha1(icon))
        }
        val newTooltip = patchTooltip(tooltip)
        if (!newTooltip.contentEquals(tooltip)) {
            mutations +=
                CacheMutation(indexId = CLIENTSCRIPT_INDEX, groupId = TOOLTIP_SCRIPT, fileId = 0, newBytes = newTooltip, label = "clientscript $TOOLTIP_SCRIPT: blighted sack shown in the spell tooltip", expectedCurrentSha1 = CacheItemProbeTool.sha1(tooltip))
        }
        if (mutations.isEmpty()) {
            println("BLIGHTED_SACK_PATCH already_patched=true")
            return
        }
        val transaction = CacheTransaction(targets = LootKeyInterfaceImportTool.TARGETS, mutations = mutations)
        val preflight = transaction.preflight()
        val errors = transaction.blockingErrors(preflight)
        println("PREFLIGHT transaction=${transaction.id} mutations=${mutations.size} outcomes=${preflight.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY RUN: pass --apply to write")
            return
        }
        val applied = transaction.apply(preflight)
        val problems = transaction.verify()
        println("APPLIED ${applied.applied} skipped=${applied.skipped} journal=${applied.journalDir}")
        if (problems.isEmpty()) println("VERIFY_OK transaction=${transaction.id}") else {
            problems.forEach { println("  VERIFY_PROBLEM: $it") }
            error("verify failed")
        }
    }

    private class Asm {
        val out = ByteArrayOutputStream()
        var count = 0

        fun op(
            opcode: Int,
            operand: Int,
        ) {
            out.write(opcode ushr 8)
            out.write(opcode and 0xFF)
            if (opcode >= 150) {
                out.write(operand and 0xFF)
            } else {
                out.write(operand ushr 24 and 0xFF)
                out.write(operand ushr 16 and 0xFF)
                out.write(operand ushr 8 and 0xFF)
                out.write(operand and 0xFF)
            }
            count++
        }
    }

    /** CS2 21 with the sack blocks; the input unchanged when it already carries them. */
    fun patchIcon(current: ByteArray): ByteArray {
        var decoded = ProductionTabClientScriptPatchTool.decode(current)
        if (isIconV2(decoded)) return current
        var original = current
        if (decoded.size == ICON_ORIGINAL_SIZE + SPELLS.size * V1_BLOCK && isBlockStart(decoded, ICON_INSERT_AT, SPELLS[0].first, V1_BLOCK)) {
            original = remove(current, decoded, ICON_INSERT_AT, SPELLS.size * V1_BLOCK)
            decoded = ProductionTabClientScriptPatchTool.decode(original)
        }
        check(decoded.size == ICON_ORIGINAL_SIZE) { "clientscript $ICON_SCRIPT has ${decoded.size} instructions, expected $ICON_ORIGINAL_SIZE" }
        check(decoded[ICON_INSERT_AT].opcode == PUSH_INT_LOCAL && decoded[ICON_INSERT_AT].intOperand == 5) { "index $ICON_INSERT_AT: ${decoded[ICON_INSERT_AT]}" }
        check(decoded[ICON_CASTABLE_AT].opcode == PUSH_INT_LOCAL && decoded[ICON_CASTABLE_AT].intOperand == 0) { "index $ICON_CASTABLE_AT: ${decoded[ICON_CASTABLE_AT]}" }
        checkNoCrossing(decoded, ICON_INSERT_AT)

        val castable = ICON_CASTABLE_AT + SPELLS.size * ICON_BLOCK
        val asm = Asm()
        SPELLS.forEachIndexed { n, (component, sack) ->
            val base = ICON_INSERT_AT + n * ICON_BLOCK
            asm.op(PUSH_INT_LOCAL, 0)
            asm.op(PUSH_CONSTANT_INT, component)
            asm.op(BRANCH_NOT, 7)
            asm.op(PUSH_VARC, VARC_SACKS_ALLOWED)
            asm.op(BRANCH_IF_FALSE, 5)
            asm.op(PUSH_CONSTANT_INT, INVENTORY)
            asm.op(PUSH_CONSTANT_INT, sack)
            asm.op(INV_TOTAL, 0)
            asm.op(PUSH_CONSTANT_INT, 0)
            asm.op(BRANCH_GREATER_THAN, castable - (base + ICON_BLOCK))
        }
        check(asm.count == SPELLS.size * ICON_BLOCK)
        val patched = insert(original, decoded, ICON_INSERT_AT, asm)
        verifyShift(decoded, patched, ICON_INSERT_AT, asm.count)
        check(isIconV2(ProductionTabClientScriptPatchTool.decode(patched))) { "icon patch not recognised after writing" }
        return patched
    }

    /** CS2 10 with the sack blocks; the input unchanged when it already carries them. */
    fun patchTooltip(current: ByteArray): ByteArray {
        val decoded = ProductionTabClientScriptPatchTool.decode(current)
        if (isTooltipPatched(decoded)) return current
        check(decoded.size == TOOLTIP_ORIGINAL_SIZE) { "clientscript $TOOLTIP_SCRIPT has ${decoded.size} instructions, expected $TOOLTIP_ORIGINAL_SIZE" }
        check(decoded[0].opcode == PUSH_INT_LOCAL && decoded[0].intOperand == 1) { "index 0: ${decoded[0]}" }
        checkNoCrossing(decoded, 0)

        val end = SPELLS.size * TOOLTIP_BLOCK
        val asm = Asm()
        SPELLS.forEachIndexed { n, (component, sack) ->
            val base = n * TOOLTIP_BLOCK
            val next = base + TOOLTIP_BLOCK
            asm.op(PUSH_INT_LOCAL, 0)
            asm.op(PUSH_CONSTANT_INT, component)
            asm.op(BRANCH_NOT, next - (base + 3))
            asm.op(PUSH_VARC, VARC_SACKS_ALLOWED)
            asm.op(BRANCH_IF_FALSE, next - (base + 5))
            asm.op(PUSH_CONSTANT_INT, INVENTORY)
            asm.op(PUSH_CONSTANT_INT, sack)
            asm.op(INV_TOTAL, 0)
            asm.op(PUSH_CONSTANT_INT, 0)
            asm.op(BRANCH_LESS_THAN_OR_EQUALS, next - (base + 10))
            listOf(sack, 1, -1, 0, -1, 0, -1, 0).forEachIndexed { i, value ->
                asm.op(PUSH_CONSTANT_INT, value)
                asm.op(POP_INT_LOCAL, 3 + i)
            }
            asm.op(BRANCH, end - (base + TOOLTIP_BLOCK))
        }
        check(asm.count == end)
        val patched = insert(current, decoded, 0, asm)
        verifyShift(decoded, patched, 0, asm.count)
        check(isTooltipPatched(ProductionTabClientScriptPatchTool.decode(patched))) { "tooltip patch not recognised after writing" }
        return patched
    }

    private fun isBlockStart(
        decoded: List<ProductionTabClientScriptPatchTool.Instruction>,
        at: Int,
        component: Int,
        blockSize: Int,
    ): Boolean =
        decoded.size > at + blockSize &&
            decoded[at].opcode == PUSH_INT_LOCAL && decoded[at].intOperand == 0 &&
            decoded[at + 1].opcode == PUSH_CONSTANT_INT && decoded[at + 1].intOperand == component

    private fun isIconV2(decoded: List<ProductionTabClientScriptPatchTool.Instruction>): Boolean =
        isBlockStart(decoded, ICON_INSERT_AT, SPELLS[0].first, ICON_BLOCK) &&
            decoded[ICON_INSERT_AT + 3].opcode == PUSH_VARC && decoded[ICON_INSERT_AT + 3].intOperand == VARC_SACKS_ALLOWED

    private fun isTooltipPatched(decoded: List<ProductionTabClientScriptPatchTool.Instruction>): Boolean =
        isBlockStart(decoded, 0, SPELLS[0].first, TOOLTIP_BLOCK) && decoded[3].opcode == PUSH_VARC

    private fun checkNoCrossing(
        decoded: List<ProductionTabClientScriptPatchTool.Instruction>,
        at: Int,
    ) {
        decoded.forEachIndexed { i, ins ->
            if (ins.opcode in BRANCH_OPCODES) {
                val target = i + 1 + (ins.intOperand ?: 0)
                check((i < at && target <= at) || (i >= at && target >= at)) { "branch at $i -> $target crosses index $at" }
            }
        }
    }

    private fun insert(
        current: ByteArray,
        decoded: List<ProductionTabClientScriptPatchTool.Instruction>,
        at: Int,
        asm: Asm,
    ): ByteArray {
        val insertOffset = decoded[at].start
        val metadataOffset = decoded.last().end
        val out = ByteArrayOutputStream(current.size + asm.out.size())
        out.write(current, 0, insertOffset)
        out.write(asm.out.toByteArray())
        out.write(current, insertOffset, metadataOffset - insertOffset)
        writeInt(out, decoded.size + asm.count)
        out.write(current, metadataOffset + 4, current.size - metadataOffset - 4)
        return out.toByteArray()
    }

    private fun remove(
        current: ByteArray,
        decoded: List<ProductionTabClientScriptPatchTool.Instruction>,
        at: Int,
        count: Int,
    ): ByteArray {
        val from = decoded[at].start
        val to = decoded[at + count].start
        val metadataOffset = decoded.last().end
        val out = ByteArrayOutputStream(current.size)
        out.write(current, 0, from)
        out.write(current, to, metadataOffset - to)
        writeInt(out, decoded.size - count)
        out.write(current, metadataOffset + 4, current.size - metadataOffset - 4)
        return out.toByteArray()
    }

    private fun verifyShift(
        before: List<ProductionTabClientScriptPatchTool.Instruction>,
        patched: ByteArray,
        at: Int,
        inserted: Int,
    ) {
        val after = ProductionTabClientScriptPatchTool.decode(patched)
        check(after.size == before.size + inserted) { "instruction count ${after.size} != ${before.size + inserted}" }
        before.indices.forEach { i ->
            val a = before[i]
            val b = after[if (i < at) i else i + inserted]
            check(a.opcode == b.opcode && a.intOperand == b.intOperand && a.stringOperand == b.stringOperand) { "instruction $i moved wrong: $a vs $b" }
        }
        after.forEachIndexed { i, ins ->
            if (ins.opcode in BRANCH_OPCODES) {
                val target = i + 1 + (ins.intOperand ?: 0)
                check(target in 0 until after.size) { "branch at $i -> $target out of range" }
            }
        }
    }

    private fun writeInt(
        out: ByteArrayOutputStream,
        value: Int,
    ) {
        out.write(byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte()))
    }
}
