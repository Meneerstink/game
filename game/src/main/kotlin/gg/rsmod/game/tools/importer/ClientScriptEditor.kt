package gg.rsmod.game.tools.importer

import java.io.ByteArrayOutputStream

/**
 * A revision-667 clientscript as an editable instruction list (shop window, N42/N62/N114/N221). The byte patch tools before it
 * ([TypingSoundClientScriptPatchTool], [SummoningInterfaceScriptTool]) could only swap an instruction for one of the same width;
 * this one inserts and removes instructions: every branch and switch case points at an [Ins] object instead of a relative
 * offset, and [encode] recomputes the offsets from the final positions. Format = the client's `ClientScript.decode`: name
 * string, instructions (u16 opcode, operand: string for 3, long for 54, byte for 21/38/39 and >= 150, int otherwise), then
 * opcode count, the six local/argument counts, the switch tables and a u16 footer with the length of the switch tables.
 */
class ClientScriptEditor private constructor(
    private val name: ByteArray,
    val code: MutableList<Ins>,
    var intLocals: Int,
    var stringLocals: Int,
    var longLocals: Int,
    var intArgs: Int,
    var stringArgs: Int,
    var longArgs: Int,
    private val switches: List<MutableList<Pair<Int, Ins>>>,
    /** The script had no switch-table section at all (footer length 0); kept so an unchanged script encodes byte for byte. */
    private val footerless: Boolean,
) {
    /** One instruction; [target] is the jump destination of a branch (BRANCH, the conditional branches). */
    class Ins(
        var opcode: Int,
        var int: Int = 0,
        val string: String? = null,
        val long: Long = 0L,
        var target: Ins? = null,
    ) {
        override fun toString(): String = "$opcode ${string ?: if (opcode == LONG_CONSTANT) long else int}"
    }

    fun indexOf(ins: Ins): Int = code.indexOfFirst { it === ins }.also { check(it >= 0) { "instruction $ins is not in the script" } }

    /** Inserts [added] at [index] (before the instruction now there); a branch to that instruction keeps landing on it. */
    fun insert(
        index: Int,
        added: List<Ins>,
    ) {
        code.addAll(index, added)
    }

    /** Every branch and switch case that lands on [from] lands on [to] instead (to put a new check in front of a jump target). */
    fun retarget(
        from: Ins,
        to: Ins,
    ) {
        code.forEach { if (it.target === from) it.target = to }
        switches.forEach { cases -> cases.replaceAll { (key, target) -> key to if (target === from) to else target } }
    }

    /** Indices where [pattern] (opcode, operand or null for any) starts. */
    fun find(vararg pattern: Pair<Int, Any?>): List<Int> =
        (0..code.size - pattern.size).filter { start ->
            pattern.withIndex().all { (k, p) ->
                val ins = code[start + k]
                ins.opcode == p.first && (p.second == null || p.second == (ins.string ?: ins.int))
            }
        }

    fun encode(): ByteArray {
        val position = HashMap<Ins, Int>()
        code.forEachIndexed { i, ins -> position[ins] = i }
        fun at(ins: Ins): Int = position[ins] ?: error("branch target $ins was removed")
        val out = ByteArrayOutputStream()
        fun u16(v: Int) {
            out.write(v ushr 8 and 0xFF)
            out.write(v and 0xFF)
        }
        fun i32(v: Int) {
            u16(v ushr 16 and 0xFFFF)
            u16(v and 0xFFFF)
        }
        out.write(name)
        code.forEachIndexed { i, ins ->
            u16(ins.opcode)
            when {
                ins.opcode == STRING_CONSTANT -> {
                    out.write(ins.string!!.toByteArray(Charsets.ISO_8859_1))
                    out.write(0)
                }
                ins.opcode == LONG_CONSTANT -> {
                    i32((ins.long ushr 32).toInt())
                    i32(ins.long.toInt())
                }
                ins.opcode in BRANCHES -> i32(at(ins.target!!) - (i + 1))
                byteOperand(ins.opcode) -> out.write(ins.int and 0xFF)
                else -> i32(ins.int)
            }
        }
        i32(code.size)
        u16(intLocals)
        u16(stringLocals)
        u16(longLocals)
        u16(intArgs)
        u16(stringArgs)
        u16(longArgs)
        if (footerless && switches.isEmpty()) {
            u16(0)
            return out.toByteArray()
        }
        val tables = ByteArrayOutputStream()
        tables.write(switches.size)
        switches.forEachIndexed { table, cases ->
            val switchAt = code.indices.single { code[it].opcode == SWITCH && code[it].int == table }
            tables.write(cases.size ushr 8 and 0xFF)
            tables.write(cases.size and 0xFF)
            cases.forEach { (key, target) ->
                val offset = at(target) - (switchAt + 1)
                listOf(key, offset).forEach { v -> listOf(24, 16, 8, 0).forEach { s -> tables.write(v ushr s and 0xFF) } }
            }
        }
        val footer = tables.toByteArray()
        out.write(footer)
        u16(footer.size)
        return out.toByteArray()
    }

    companion object {
        const val PUSH_CONSTANT_INT = 0
        const val STRING_CONSTANT = 3
        const val BRANCH = 6
        const val RETURN = 21
        const val SWITCH = 51
        const val LONG_CONSTANT = 54

        /** BRANCH, BRANCH_NOT/EQUALS/LESS_THAN/GREATER_THAN, _OR_EQUALS, the LONG_BRANCH_* ops and BRANCH_IF_TRUE/FALSE. */
        val BRANCHES = setOf(BRANCH, 7, 8, 9, 10, 31, 32, 68, 69, 70, 71, 72, 73, 86, 87)

        fun byteOperand(opcode: Int): Boolean = opcode >= 150 || opcode == RETURN || opcode == 38 || opcode == 39

        fun decode(data: ByteArray): ClientScriptEditor {
            fun u16(at: Int) = ((data[at].toInt() and 0xFF) shl 8) or (data[at + 1].toInt() and 0xFF)
            fun i32(at: Int) = (u16(at) shl 16) or u16(at + 2)
            val footer = u16(data.size - 2)
            val meta = data.size - footer - 2 - 16
            val count = i32(meta)
            var pos = 0
            while (data[pos].toInt() != 0) pos++
            val name = data.copyOfRange(0, pos + 1)
            pos++
            val code = ArrayList<Ins>(count)
            while (pos < meta) {
                val opcode = u16(pos)
                pos += 2
                code +=
                    when {
                        opcode == STRING_CONSTANT -> {
                            val s = pos
                            while (data[pos].toInt() != 0) pos++
                            Ins(opcode, string = String(data, s, pos - s, Charsets.ISO_8859_1)).also { pos++ }
                        }
                        opcode == LONG_CONSTANT -> Ins(opcode, long = (i32(pos).toLong() shl 32) or (i32(pos + 4).toLong() and 0xFFFFFFFFL)).also { pos += 8 }
                        byteOperand(opcode) -> Ins(opcode, int = data[pos].toInt() and 0xFF).also { pos++ }
                        else -> Ins(opcode, int = i32(pos)).also { pos += 4 }
                    }
            }
            check(pos == meta && code.size == count) { "clientscript did not decode cleanly ($pos/$meta, ${code.size}/$count)" }
            code.forEachIndexed { i, ins ->
                if (ins.opcode in BRANCHES) ins.target = code[i + 1 + ins.int]
            }
            var t = meta + 16
            val switches = ArrayList<MutableList<Pair<Int, Ins>>>()
            if (footer > 0) {
                val tables = data[t++].toInt() and 0xFF
                repeat(tables) { table ->
                    val cases = u16(t)
                    t += 2
                    val switchAt = code.indices.single { code[it].opcode == SWITCH && code[it].int == table }
                    val list = ArrayList<Pair<Int, Ins>>()
                    repeat(cases) {
                        list += i32(t) to code[switchAt + 1 + i32(t + 4)]
                        t += 8
                    }
                    switches += list
                }
            }
            return ClientScriptEditor(
                name, code,
                u16(meta + 4), u16(meta + 6), u16(meta + 8), u16(meta + 10), u16(meta + 12), u16(meta + 14),
                switches,
                footerless = footer == 0,
            )
        }
    }
}
