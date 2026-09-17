package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.def.NpcDef
import io.netty.buffer.Unpooled
import java.io.ByteArrayOutputStream

/**
 * Removes the "Attack" interact option from rev-667 npc definitions in both production caches
 * (owner 2026-09-17: "Remove the attack option from all guards, a player cannot attack them!").
 *
 * The NPCType stream is copied opcode by opcode with the exact payload sizes of the server's own
 * decoder ([NpcDef.decode], the same table the 2011 client uses), dropping only the option opcodes
 * (30-34 and their 150-154 twins) whose string is "Attack". Any opcode outside that table aborts the
 * run before anything is written, so an unfamiliar definition can never be re-encoded wrongly. The
 * result is decoded again with [NpcDef] and compared field by field with the original (name, size,
 * combat level, BAS, the remaining options) before it is handed to [CacheTransaction], which writes
 * both targets atomically with preflight, verify and rollback.
 *
 * Usage: `./gradlew :game:runNpcAttackOptionStripTool --args="<npcId> [npcId ...] [--apply]"`
 * - without `--apply` only plan + preflight run.
 */
object NpcAttackOptionStripTool {
    const val INDEX_NPC = OsrsNpcImportTool.INDEX_NPC
    val TARGETS = OsrsItemImportTool.TARGETS

    class Result(
        val original: ByteArray,
        val stripped: ByteArray,
        val removed: List<String>,
    )

    /** Copies [bytes] without the option opcodes whose text is "Attack" (case-insensitive). */
    fun strip(bytes: ByteArray): Result {
        val out = ByteArrayOutputStream(bytes.size)
        val removed = ArrayList<String>()
        var pos = 0
        fun u8(): Int = bytes[pos++].toInt() and 0xFF
        fun skip(n: Int) {
            pos += n
        }
        fun str(): String {
            val start = pos
            while (bytes[pos] != 0.toByte()) pos++
            val s = String(bytes, start, pos - start, Charsets.ISO_8859_1)
            pos++
            return s
        }
        while (true) {
            val segmentStart = pos
            val opcode = u8()
            if (opcode == 0) {
                out.write(0)
                break
            }
            var drop = false
            when (opcode) {
                1 -> skip(u8() * 2)
                2 -> str()
                12 -> skip(1)
                in 30 until 35, in 150 until 155 -> {
                    val option = str()
                    if (option.equals("Attack", ignoreCase = true)) {
                        drop = true
                        removed += "opcode $opcode '$option'"
                    }
                }
                40, 41 -> skip(u8() * 4)
                42 -> skip(u8())
                60, 160 -> skip(u8() * 2)
                93, 99, 107, 158, 159, 162 -> {}
                95, 97, 98, 102, 103, 122, 123, 137, 138, 139, 142, 127 -> skip(2)
                100, 101, 125, 128, 140, 163, 165, 168, 119 -> skip(1)
                106, 118 -> {
                    skip(4)
                    if (opcode == 118) skip(2)
                    skip((u8() + 1) * 2)
                }
                113, 164, 155 -> skip(4)
                114, 115 -> skip(2)
                121 -> skip(u8() * 3)
                134 -> skip(9)
                135, 136 -> skip(3)
                249 -> {
                    val count = u8()
                    repeat(count) {
                        val isString = u8() == 1
                        skip(3)
                        if (isString) str() else skip(4)
                    }
                }
                else -> error("unknown NPCType opcode $opcode at offset ${segmentStart} - refusing to re-encode")
            }
            if (!drop) out.write(bytes, segmentStart, pos - segmentStart)
        }
        check(pos == bytes.size) { "trailing ${bytes.size - pos} byte(s) after the terminator" }
        return Result(bytes, out.toByteArray(), removed)
    }

    private fun decode(
        id: Int,
        bytes: ByteArray,
    ): NpcDef {
        val def = NpcDef(id)
        def.decode(Unpooled.wrappedBuffer(bytes))
        return def
    }

    /** Field-by-field check that only the Attack option changed. */
    fun verifyEquivalent(
        id: Int,
        result: Result,
    ): List<String> {
        val before = decode(id, result.original)
        val after = decode(id, result.stripped)
        val problems = ArrayList<String>()
        if (before.name != after.name) problems += "name ${before.name} -> ${after.name}"
        if (before.size != after.size) problems += "size"
        if (before.combatLevel != after.combatLevel) problems += "combatLevel"
        if (before.basId != after.basId) problems += "basId"
        if (before.walkMask != after.walkMask) problems += "walkMask"
        if (before.interactable != after.interactable) problems += "interactable"
        // NpcDef initialises every option slot to "" and an absent opcode leaves it so; normalise both.
        fun norm(option: String?): String? = if (option.isNullOrEmpty() || option.equals("Attack", ignoreCase = true)) null else option
        val expected = before.options.map { norm(it) }
        val actual = after.options.map { if (it.isNullOrEmpty()) null else it }
        if (expected != actual) problems += "options ${before.options.toList()} -> ${after.options.toList()}"
        if (after.isAttackable()) problems += "still attackable"
        return problems
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val ids = args.filter { !it.startsWith("--") }.map { it.toInt() }
        require(ids.isNotEmpty()) { "Usage: <npcId> [npcId ...] [--apply]" }
        val apply = "--apply" in args
        val library = CacheLibrary(TARGETS[0])
        val mutations = ArrayList<CacheMutation>()
        try {
            ids.forEach { id ->
                val group = id ushr 7
                val file = id and 0x7F
                val current = library.data(INDEX_NPC, group, file) ?: error("npc $id missing from ${TARGETS[0]}")
                val result = strip(current)
                val problems = verifyEquivalent(id, result)
                check(problems.isEmpty()) { "npc $id re-encode mismatch: $problems" }
                val name = decode(id, current).name
                if (result.removed.isEmpty()) {
                    println("PLAN npc $id '$name': no Attack option, nothing to do")
                    return@forEach
                }
                println("PLAN npc $id '$name': remove ${result.removed} (${current.size} -> ${result.stripped.size} bytes)")
                mutations +=
                    CacheMutation(
                        INDEX_NPC,
                        group,
                        file,
                        result.stripped,
                        "strip Attack option from npc $id $name",
                        expectedCurrentSha1 = CacheItemProbeTool.sha1(current),
                    )
            }
        } finally {
            library.close()
        }
        if (mutations.isEmpty()) {
            println("NOTHING_TO_DO")
            return
        }
        val transaction = CacheTransaction(targets = TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} mutations=${mutations.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKING: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY_RUN")
            return
        }
        val applied = transaction.apply(plan)
        val verifyProblems = transaction.verify()
        if (verifyProblems.isNotEmpty()) {
            verifyProblems.forEach { println("  VERIFY_FAILURE: $it") }
            println("ROLLED_BACK ${transaction.rollback()}")
            error("transaction ${transaction.id} failed verification and was rolled back")
        }
        println("APPLIED transaction=${applied.transactionId} writes=${applied.applied} skipped=${applied.skipped} VERIFY_OK")
    }
}
