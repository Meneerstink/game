package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary

/**
 * N606 (13-11 nachtrun): OSRS-imported locs carry the OSRS ambient byte (loc opcode 29) unchanged, but the two clients use it
 * differently ([OsrsItemImportTool.rev667Ambient]): OSRS adds it to the directional light (loc `light(ambient + 64, contrast * 25 + 768,
 * -50, -10, -50)`), every 667 toolkit multiplies the colour by (ambient + 64) / 64. The item and npc importers convert it; the loc
 * import did not - Zebak's acid pool (OSRS ambient 30) measured val 0.98 / sat 0.48 against the wiki's 0.86 / 0.26 (photobook-0205),
 * and the conversion (30 -> 18, x0.87) predicts 0.85.
 *
 * Rewrites only the value byte after opcode 29 of the given locs, and only while it still holds the raw OSRS value (OLD -> NEW printed;
 * nothing written when it already differs). Both caches, one [CacheTransaction].
 *
 * Usage: `LocAmbientTool plan|apply <localLoc>=<osrsAmbient>,<osrsContrast> ...`
 */
object LocAmbientTool {
    /** OSRS loc lighting: contrast base 768, contrast byte x25 ([OsrsItemImportTool.rev667Ambient] takes base + 5 x contrast). */
    fun rev667LocAmbient(osrsAmbient: Int, osrsContrast: Int): Int = OsrsItemImportTool.rev667Ambient(osrsAmbient, osrsContrast, 768 + 20 * osrsContrast)

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size >= 2 && args[0] in setOf("plan", "apply")) { "Usage: plan|apply <localLoc>=<osrsAmbient>,<osrsContrast> ..." }
        val jobs = args.drop(1).map { a ->
            val (loc, rest) = a.split('=')
            val (amb, con) = rest.split(',')
            Triple(loc.toInt(), amb.toInt(), con.toInt())
        }
        val mutations = ArrayList<CacheMutation>()
        val library = CacheLibrary(FeroxImportTool.GAME_CACHE)
        try {
            for ((id, osrsAmbient, osrsContrast) in jobs) {
                val data = library.data(FeroxImportTool.LOC_INDEX, id ushr 8, id and 0xFF) ?: error("loc $id missing")
                val def = Rev667LocType.decode(id, data)
                val index = def.opcodes.indexOf(29)
                if (index < 0) { println("SKIP loc $id: no opcode 29 (ambient 0)"); continue }
                val at = def.opcodeOffsets[index] + 1
                val current = data[at].toInt()
                val target = rev667LocAmbient(osrsAmbient, osrsContrast)
                if (current != osrsAmbient) { println("SKIP loc $id: ambient byte $current is not the raw OSRS $osrsAmbient (already converted?)"); continue }
                if (current == target) { println("NOTHING loc $id: ambient $current"); continue }
                val bytes = data.copyOf()
                bytes[at] = target.toByte()
                Rev667LocType.decode(id, bytes) // full opcode walk: throws on a malformed definition
                println("CHANGE loc $id '${def.name}': ambient $current -> $target (OSRS ambient $osrsAmbient contrast $osrsContrast)")
                mutations += CacheMutation(FeroxImportTool.LOC_INDEX, id ushr 8, id and 0xFF, bytes, "loc $id ambient $current -> $target (667 lighting)", CacheItemProbeTool.sha1(data))
            }
        } finally {
            library.close()
        }
        val tx = CacheTransaction(listOf(FeroxImportTool.GAME_CACHE, FeroxImportTool.FILE_SERVER_CACHE), mutations)
        val preflight = tx.preflight()
        println("PREFLIGHT transaction=${tx.id} mutations=${mutations.size} outcomes=${preflight.groupingBy { it.outcome }.eachCount()}")
        val blocking = tx.blockingErrors(preflight)
        blocking.forEach { println("BLOCKING: $it") }
        if (args[0] == "plan") return
        check(blocking.isEmpty()) { "Preflight has blocking errors; refusing to apply." }
        val result = tx.apply(preflight)
        println("APPLIED transaction=${tx.id} applied=${result.applied} skipped=${result.skipped}")
        val verify = tx.verify()
        verify.forEach { println("VERIFY_ERROR: $it") }
        check(verify.isEmpty()) { "Post-apply verification failed; see journal ${tx.id} for rollback." }
        println("VERIFY_OK both targets hold intended bytes")
    }
}
