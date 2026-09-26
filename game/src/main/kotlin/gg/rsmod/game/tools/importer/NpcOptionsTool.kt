package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.def.NpcDef
import io.netty.buffer.Unpooled
import java.io.ByteArrayOutputStream

/**
 * Sets right-click options (NPCType opcodes 30-34) of npc definitions in both production caches. Owner 2026-09-26 (death
 * rework): the gravestone npcs get "Bless" and "Repair" next to "Check" and "Loot" (RS 2009 gravestones, 2009scape
 * `GraveController`).
 *
 * Same discipline as [NpcRenameTool]: the stream is copied opcode by opcode with the payload sizes of [NpcDef.decode], only
 * option opcodes are replaced, an unknown opcode aborts, the result is decoded again and compared field by field (only the
 * options may differ) and [CacheTransaction] writes both caches atomically with preflight, verify and rollback.
 *
 * Usage: `./gradlew :game:runNpcOptionsTool --args="<npcId> [npcId ...] --op1=Check --op3=Loot --op4=Bless --op5=Repair [--apply]"`
 * - slots not named keep their current option; `--opN=_` clears slot N.
 */
object NpcOptionsTool {
    const val INDEX_NPC = OsrsNpcImportTool.INDEX_NPC
    val TARGETS = OsrsItemImportTool.TARGETS

    /** Copies [bytes] with the options set to [options] (index 0..4, null = no option). */
    fun withOptions(
        bytes: ByteArray,
        options: Array<String?>,
    ): ByteArray {
        val out = ByteArrayOutputStream(bytes.size + 32)
        var pos = 0
        fun u8(): Int = bytes[pos++].toInt() and 0xFF
        fun skip(n: Int) {
            pos += n
        }
        fun str() {
            while (bytes[pos] != 0.toByte()) pos++
            pos++
        }
        while (true) {
            val segmentStart = pos
            val opcode = u8()
            if (opcode == 0) {
                options.forEachIndexed { i, option ->
                    if (option == null) return@forEachIndexed
                    out.write(30 + i)
                    out.write(option.toByteArray(Charsets.ISO_8859_1))
                    out.write(0)
                }
                out.write(0)
                break
            }
            var drop = false
            when (opcode) {
                1 -> skip(u8() * 2)
                2 -> str()
                12 -> skip(1)
                in 30 until 35 -> {
                    str()
                    drop = true
                }
                in 150 until 155 -> str()
                40, 41 -> skip(u8() * 4)
                42 -> skip(u8())
                60, 160 -> skip(u8() * 2)
                93, 99, 107, 158, 159, 162 -> {}
                95, 103 -> skip(2)
                97, 98, 102, 122, 123, 137, 138, 139, 142, 127 -> skip(2)
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
                else -> error("unknown NPCType opcode $opcode at offset $segmentStart - refusing to re-encode")
            }
            if (!drop) out.write(bytes, segmentStart, pos - segmentStart)
        }
        check(pos == bytes.size) { "trailing ${bytes.size - pos} byte(s) after the terminator" }
        return out.toByteArray()
    }

    private fun decode(
        id: Int,
        bytes: ByteArray,
    ): NpcDef = NpcDef(id).also { it.decode(Unpooled.wrappedBuffer(bytes)) }

    @JvmStatic
    fun main(args: Array<String>) {
        val ids = args.filter { !it.startsWith("--") }.map { it.toInt() }
        require(ids.isNotEmpty()) { "Usage: <npcId> [npcId ...] --opN=Name ... [--apply]" }
        val apply = "--apply" in args
        val wanted = HashMap<Int, String?>()
        args.filter { it.matches(Regex("--op[1-5]=.*")) }.forEach {
            val slot = it.substring(4, 5).toInt() - 1
            val name = it.substringAfter('=').replace('_', ' ').trim()
            wanted[slot] = name.ifEmpty { null }
        }
        require(wanted.isNotEmpty()) { "name at least one --opN=Name" }
        val library = CacheLibrary(TARGETS[0])
        val mutations = ArrayList<CacheMutation>()
        try {
            ids.forEach { id ->
                val group = id ushr 7
                val file = id and 0x7F
                val current = library.data(INDEX_NPC, group, file) ?: error("npc $id missing from ${TARGETS[0]}")
                val before = decode(id, current)
                val options = Array<String?>(5) { before.options.getOrNull(it)?.takeIf { o -> o.isNotEmpty() } }
                wanted.forEach { (slot, name) -> options[slot] = name }
                val patched = withOptions(current, options)
                val after = decode(id, patched)
                val problems = ArrayList<String>()
                if (after.name != before.name) problems += "name"
                if (after.size != before.size) problems += "size"
                if (after.combatLevel != before.combatLevel) problems += "combatLevel"
                if (after.basId != before.basId) problems += "basId"
                if (after.interactable != before.interactable) problems += "interactable"
                if (after.options.toList().take(5).map { it?.ifEmpty { null } } != options.toList()) problems += "options ${after.options.toList()} != ${options.toList()}"
                check(problems.isEmpty()) { "npc $id re-encode mismatch: $problems" }
                println("PLAN npc $id '${before.name}': options ${before.options.toList()} -> ${options.toList()}")
                if (patched.contentEquals(current)) return@forEach
                mutations +=
                    CacheMutation(
                        INDEX_NPC,
                        group,
                        file,
                        patched,
                        "npc $id options ${options.toList()}",
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
