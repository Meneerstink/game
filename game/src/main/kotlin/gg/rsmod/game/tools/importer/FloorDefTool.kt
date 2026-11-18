package gg.rsmod.game.tools.importer

import gg.rsmod.game.RspsPaths

/**
 * GRAFISCH 16-11: schrijft EEN vloerdefinitie (config index 2, groep 1 = underlay / 4 = overlay) met exact opgegeven bytes, gepind op de
 * huidige bytes. Alleen voor definities die GEEN ENKELE tegel gebruikt (grafisch\underlay_gebruik.py / overlay_gebruik.py, 2290 kaartvakken).
 * Gebruik voor CoX: OSRS verborgen overlays (ff00ff + minimapkleur) waarvan 667 geen gelijke def heeft -> exacte kopie in een vrij slot,
 * daarna ToaHiddenFloorTool cox (hiddenMap vindt hem dan).
 * Usage: plan|apply <groep 1|4> <def> <nieuwe bytes hex> <huidige bytes hex>
 */
object FloorDefTool {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 5 && args[0] in setOf("plan", "apply")) { "Usage: plan|apply <group> <def> <new-hex> <current-hex>" }
        val group = args[1].toInt()
        require(group == 1 || group == 4) { "group must be 1 (underlay) or 4 (overlay)" }
        val def = args[2].toInt()
        val want = args[3].chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val pinned = args[4].chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        require(want.last() == 0.toByte()) { "definition must end with opcode 0" }
        val paths = listOf(RspsPaths.data("cache"), RspsPaths.root("file-server/cache"))
        for (p in paths) {
            val lib = com.displee.cache.CacheLibrary(p)
            try {
                val cur = requireNotNull(lib.data(2, group, def)) { "def $group/$def missing in $p" }
                check(cur.contentEquals(pinned) || cur.contentEquals(want)) { "def $group/$def in $p is ${cur.joinToString(",")} - not pinned" }
            } finally { lib.close() }
        }
        val tx = CacheTransaction(paths, listOf(CacheMutation(2, group, def, want,
            "GRAFISCH: floor def $group/$def = ${args[3]}", CacheItemProbeTool.sha1(pinned))))
        val plan = tx.preflight()
        check(tx.blockingErrors(plan).isEmpty()) { tx.blockingErrors(plan).joinToString("\n") }
        if (args[0] == "plan") { println("DRY_RUN ${tx.id} def $group/$def -> ${args[3]}"); return }
        val result = tx.apply(plan)
        check(tx.verify().isEmpty()) { "Cache verification failed: ${tx.id}" }
        println("FLOOR_DEF transaction=${tx.id} applied=${result.applied} skipped=${result.skipped} verified=true")
    }
}
