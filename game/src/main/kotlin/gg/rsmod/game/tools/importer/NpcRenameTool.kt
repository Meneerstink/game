package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.def.NpcDef
import io.netty.buffer.Unpooled
import java.io.ByteArrayOutputStream

/**
 * Rewrites the cache name (NPCType opcode 2) of rev-667 npc definitions in both production caches
 * (owner "deadmanmode vervijning" 2026-09-17: every Deadman guard "need[s] to be named 1337 guard").
 *
 * Same discipline as [NpcAttackOptionStripTool]: the stream is copied opcode by opcode with the exact
 * payload sizes of the server's own decoder ([NpcDef.decode]), only the opcode-2 payload is replaced,
 * an unknown opcode aborts before anything is written, the result is decoded again and compared
 * field by field (only the name may differ), and [CacheTransaction] writes both targets atomically
 * with preflight, verify and rollback.
 *
 * Usage: `./gradlew :game:runNpcRenameTool --args="<newName> <npcId> [npcId ...] [--apply]"`
 * - without `--apply` only plan + preflight run. Use `_` for spaces in the name (`1337_guard`).
 */
object NpcRenameTool {
    const val INDEX_NPC = OsrsNpcImportTool.INDEX_NPC
    val TARGETS = OsrsItemImportTool.TARGETS

    class Result(
        val original: ByteArray,
        val renamed: ByteArray,
        val oldName: String?,
    )

    /** Copies [bytes] with the opcode-2 name replaced by [newName] (inserted first when absent). */
    fun rename(
        bytes: ByteArray,
        newName: String,
        newLevel: Int? = null,
    ): Result {
        val out = ByteArrayOutputStream(bytes.size + newName.length + 2)
        var oldName: String? = null
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
        fun writeName() {
            out.write(2)
            out.write(newName.toByteArray(Charsets.ISO_8859_1))
            out.write(0)
        }
        var levelWritten = false
        fun writeLevel() {
            val level = newLevel ?: return
            out.write(95)
            out.write((level shr 8) and 0xFF)
            out.write(level and 0xFF)
            levelWritten = true
        }
        while (true) {
            val segmentStart = pos
            val opcode = u8()
            if (opcode == 0) {
                if (oldName == null) writeName()
                if (newLevel != null && !levelWritten) writeLevel()
                out.write(0)
                break
            }
            var replaced = false
            when (opcode) {
                1 -> skip(u8() * 2)
                2 -> {
                    oldName = str()
                    writeName()
                    replaced = true
                }
                12 -> skip(1)
                in 30 until 35, in 150 until 155 -> str()
                40, 41 -> skip(u8() * 4)
                42 -> skip(u8())
                60, 160 -> skip(u8() * 2)
                93, 99, 107, 158, 159, 162 -> {}
                95 -> {
                    skip(2)
                    if (newLevel != null) {
                        writeLevel()
                        replaced = true
                    }
                }
                97, 98, 102, 103, 122, 123, 137, 138, 139, 142, 127 -> skip(2)
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
            if (!replaced) out.write(bytes, segmentStart, pos - segmentStart)
        }
        check(pos == bytes.size) { "trailing ${bytes.size - pos} byte(s) after the terminator" }
        return Result(bytes, out.toByteArray(), oldName)
    }

    private fun decode(
        id: Int,
        bytes: ByteArray,
    ): NpcDef {
        val def = NpcDef(id)
        def.decode(Unpooled.wrappedBuffer(bytes))
        return def
    }

    /** Field-by-field check that only the name changed. */
    fun verifyEquivalent(
        id: Int,
        result: Result,
        newName: String,
        newLevel: Int? = null,
    ): List<String> {
        val before = decode(id, result.original)
        val after = decode(id, result.renamed)
        val problems = ArrayList<String>()
        if (after.name != newName) problems += "name '${after.name}' != '$newName'"
        if (before.size != after.size) problems += "size"
        if (after.combatLevel != (newLevel ?: before.combatLevel)) problems += "combatLevel ${before.combatLevel} -> ${after.combatLevel}"
        if (before.basId != after.basId) problems += "basId"
        if (before.walkMask != after.walkMask) problems += "walkMask"
        if (before.interactable != after.interactable) problems += "interactable"
        if (before.options.toList() != after.options.toList()) problems += "options ${before.options.toList()} -> ${after.options.toList()}"
        if (before.isAttackable() != after.isAttackable()) problems += "attackable"
        return problems
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val positional = args.filter { !it.startsWith("--") }
        require(positional.size >= 2) { "Usage: <newName> <npcId> [npcId ...] [--level=N] [--apply]" }
        val newName = positional[0].replace('_', ' ')
        val ids = positional.drop(1).map { it.toInt() }
        val apply = "--apply" in args
        val newLevel = args.firstOrNull { it.startsWith("--level=") }?.substringAfter('=')?.toInt()
        val library = CacheLibrary(TARGETS[0])
        val mutations = ArrayList<CacheMutation>()
        try {
            ids.forEach { id ->
                val group = id ushr 7
                val file = id and 0x7F
                val current = library.data(INDEX_NPC, group, file) ?: error("npc $id missing from ${TARGETS[0]}")
                val result = rename(current, newName, newLevel)
                val problems = verifyEquivalent(id, result, newName, newLevel)
                check(problems.isEmpty()) { "npc $id re-encode mismatch: $problems" }
                val oldLevel = decode(id, current).combatLevel
                if (result.oldName == newName && (newLevel == null || oldLevel == newLevel)) {
                    println("PLAN npc $id: already named '$newName' (level $oldLevel), nothing to do")
                    return@forEach
                }
                println("PLAN npc $id: rename '${result.oldName}' -> '$newName', level $oldLevel -> ${newLevel ?: oldLevel} (${current.size} -> ${result.renamed.size} bytes)")
                mutations +=
                    CacheMutation(
                        INDEX_NPC,
                        group,
                        file,
                        result.renamed,
                        "rename npc $id '${result.oldName}' -> '$newName'" + (if (newLevel != null) " level $newLevel" else ""),
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
