package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.def.NpcDef
import io.netty.buffer.Unpooled
import java.io.ByteArrayOutputStream

/**
 * Gives an npc another npc's look and keeps everything else (name, options, map element, combat level ...).
 *
 * Owner 2026-10-09 201749: "deadmanstore en breach trader gebruiken de zelfde npc verander 1 naar een ander dus uiterlijk" - both are
 * clones of the OSRS Emblem Trader (OsrsNpcImportTool store-npcs / breach-trader). The owner picked the Breach Trader for a fitting
 * dark rift look: the Mage of Zamorak (Jagex 667 npc [SOURCE] = the Varrock mage who opens the rifts into the Abyss).
 *
 * The NPCType stream (client `NPCType.decode`) is split into opcodes; the look opcodes ([LOOK]) of the target are dropped and the
 * source's are written before the terminator. One [CacheTransaction] over both caches, idempotent.
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.NpcLookCopyTool plan|apply`
 */
object NpcLookCopyTool {
    val TARGETS = OsrsItemImportTool.TARGETS
    private const val NPC_INDEX = 18

    const val SOURCE = 2259
    const val TARGET = 14478

    /** Models, size, recolour / retexture / palette, chathead models, scale, lighting, model translations, BAS, height, tint. */
    val LOOK = setOf(1, 12, 40, 41, 42, 60, 97, 98, 100, 101, 121, 123, 127, 155)

    /** The opcode stream as (opcode, whole segment incl. the opcode byte); the terminator is not included. */
    fun segments(def: ByteArray): List<Pair<Int, ByteArray>> {
        val out = ArrayList<Pair<Int, ByteArray>>()
        var p = 0
        fun u8(at: Int) = def[at].toInt() and 0xFF
        fun string(at: Int): Int {
            var q = at
            while (def[q] != 0.toByte()) q++
            return q - at + 1
        }
        while (true) {
            val op = u8(p)
            if (op == 0) {
                check(p == def.size - 1) { "data after the terminator" }
                return out
            }
            val start = p
            p++
            p +=
                when (op) {
                    1, 60 -> 1 + 2 * u8(p)
                    2, in 30..34, in 150..154 -> string(p)
                    12, 100, 101, 119, 125, 128, 140, 163, 165, 168 -> 1
                    40, 41 -> 1 + 4 * u8(p)
                    42 -> 1 + u8(p)
                    93, 99, 107, 109, 111, 141, 143, 158, 159, 162 -> 0
                    95, 97, 98, 102, 103, 122, 123, 127, 137, 138, 139, 142 -> 2
                    106 -> 4 + 1 + 2 * (u8(p + 4) + 1)
                    118 -> 6 + 1 + 2 * (u8(p + 6) + 1)
                    113, 155, 164 -> 4
                    114 -> 2
                    121 -> 1 + 4 * u8(p)
                    134 -> 9
                    135, 136 -> 3
                    160 -> 1 + 2 * u8(p)
                    249 -> {
                        var q = p + 1
                        repeat(u8(p)) {
                            val isString = u8(q) == 1
                            q += 4
                            q += if (isString) string(q) else 4
                        }
                        q - p
                    }
                    else -> error("npc opcode $op not handled")
                }
            out += op to def.copyOfRange(start, p)
        }
    }

    fun withLook(
        target: ByteArray,
        source: ByteArray,
    ): ByteArray =
        ByteArrayOutputStream().use { out ->
            segments(target).filter { it.first !in LOOK }.forEach { out.write(it.second) }
            segments(source).filter { it.first in LOOK }.forEach { out.write(it.second) }
            out.write(0)
            out.toByteArray()
        }

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val library = CacheLibrary(TARGETS[0])
        val mutations = ArrayList<CacheMutation>()
        try {
            val source = library.data(NPC_INDEX, SOURCE ushr 7, SOURCE and 0x7F) ?: error("npc $SOURCE missing")
            val target = library.data(NPC_INDEX, TARGET ushr 7, TARGET and 0x7F) ?: error("npc $TARGET missing")
            val wanted = withLook(target, source)
            val before = NpcDef(TARGET).also { it.decode(Unpooled.wrappedBuffer(target)) }
            val after = NpcDef(TARGET).also { it.decode(Unpooled.wrappedBuffer(wanted)) }
            check(before.name == after.name && before.options.toList() == after.options.toList() && before.mapElement == after.mapElement) {
                "the copy changed more than the look"
            }
            if (wanted.contentEquals(target)) {
                println("NOTHING TO DO: npc $TARGET '${after.name}' already looks like npc $SOURCE")
                return
            }
            println("REPLACE npc $TARGET '${after.name}' look <- npc $SOURCE (${target.size} -> ${wanted.size} bytes), options ${after.options.toList()}")
            mutations += CacheMutation(NPC_INDEX, TARGET ushr 7, TARGET and 0x7F, wanted, "npc $TARGET look of npc $SOURCE", CacheItemProbeTool.sha1(target))
        } finally {
            library.close()
        }
        val transaction = CacheTransaction(targets = TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        errors.forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (mode == "plan") {
            println("PLAN_ONLY transaction=${transaction.id} mutations=${mutations.size} (nothing written)")
            return
        }
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        println("APPLIED ${applied.applied} skipped=${applied.skipped} journal=${applied.journalDir}")
        if (problems.isEmpty()) println("VERIFY_OK transaction=${transaction.id}") else {
            problems.forEach { println("  VERIFY_PROBLEM: $it") }
            error("verify failed")
        }
    }
}
