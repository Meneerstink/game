package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.ByteArrayOutputStream

/**
 * A22 (owner 2026-10-10 "obelisk heeft geen echte gui in de poh", wanted the Wilderness obelisk exact OSRS): OSRS Wilderness obelisks and
 * the house obelisk offer "Activate", "Teleport to Destination" and "Set Destination" (OSRS Wiki "Wilderness Obelisk", options). The
 * 667 obelisk locs 14826-14831 (the house garden uses 14826 too) carry only "Activate"; this appends the other two as LocType
 * opcodes 31 / 32 (option slots 2 and 3). Nothing else in the definitions changes.
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.ObeliskOptionTool [--apply]` with the servers stopped.
 */
object ObeliskOptionTool {
    const val LOC_INDEX = 16
    val LOCS = 14826..14831
    val OPTIONS = listOf("Teleport to Destination", "Set Destination")

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        val mutations = mutableListOf<CacheMutation>()
        try {
            for (id in LOCS) {
                val current = library.data(LOC_INDEX, id ushr 8, id and 0xFF) ?: error("loc $id missing")
                val before = Rev667LocType.decode(id, current)
                check(before.options[0].equals("Activate", ignoreCase = true)) { "loc $id: option 1 is ${before.options[0]}" }
                check(before.options[1] == null && before.options[2] == null) { "loc $id already has ${before.options.toList()}" }
                check(current.last() == 0.toByte()) { "loc $id does not end with opcode 0" }
                val updated =
                    ByteArrayOutputStream().use { out ->
                        out.write(current, 0, current.size - 1)
                        OPTIONS.forEachIndexed { i, option ->
                            out.write(31 + i)
                            option.forEach { out.write(it.code) }
                            out.write(0)
                        }
                        out.write(0)
                        out.toByteArray()
                    }
                val after = Rev667LocType.decode(id, updated)
                check(after.options[1] == OPTIONS[0] && after.options[2] == OPTIONS[1] && after.allModels == before.allModels && after.name == before.name) { "loc $id re-decode ${after.options.toList()}" }
                println("PLAN loc $id '${before.name}' ${before.options.toList()} -> ${after.options.toList()}")
                mutations += CacheMutation(LOC_INDEX, id ushr 8, id and 0xFF, updated, "A22 obelisk $id options", CacheItemProbeTool.sha1(current))
            }
        } finally {
            library.close()
        }
        val transaction = CacheTransaction(OsrsItemImportTool.TARGETS, mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKING: $it") }
        check(errors.isEmpty()) { "preflight blocked" }
        if (!apply) {
            println("DRY_RUN")
            return
        }
        val result = transaction.apply(plan)
        val problems = transaction.verify()
        check(problems.isEmpty()) { "verify failed: $problems" }
        println("APPLIED transaction=${result.transactionId} writes=${result.applied} VERIFY_OK")
    }
}
