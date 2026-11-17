package gg.rsmod.game.tools.importer

import gg.rsmod.game.RspsPaths

/**
 * GRAFISCH 16-11: zet een 667-underlay-definitie op een EXACTE effen OSRS-kleur (opcode 1 rgb, geen textuur) - zoals de ToA-vloeren
 * (ToaUnderlayColourTool: 14160 OSRS def 103 343b4b -> onze effen def 172 343b4b). Alleen voor een def die GEEN ENKELE tegel gebruikt
 * (bewezen met work\claude-scripts\grafisch\underlay_gebruik.py over alle 2290 kaartvakken) - pinned op de huidige bytes.
 * Usage: plan|apply <def> <rrggbb> <huidige bytes hex>
 */
object UnderlayDefTool {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 4 && args[0] in setOf("plan", "apply")) { "Usage: plan|apply <def> <rrggbb> <current-bytes-hex>" }
        val def = args[1].toInt()
        val rgb = args[2].toInt(16)
        require(def in 0..173 && rgb in 0..0xFFFFFF)
        val want = byteArrayOf(1, (rgb shr 16).toByte(), (rgb shr 8).toByte(), rgb.toByte(), 0)
        val pinned = args[3].chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val paths = listOf(RspsPaths.data("cache"), RspsPaths.root("file-server/cache"))
        for (p in paths) {
            val lib = com.displee.cache.CacheLibrary(p)
            try {
                val cur = requireNotNull(lib.data(2, 1, def)) { "underlay def $def missing in $p" }
                check(cur.contentEquals(pinned) || cur.contentEquals(want)) { "def $def in $p is ${cur.joinToString(",")} - not the pinned bytes" }
            } finally { lib.close() }
        }
        val mutations = listOf(CacheMutation(2, 1, def, want, "GRAFISCH: underlay def $def = plain OSRS %06x (no texture)".format(rgb),
            CacheItemProbeTool.sha1(pinned)))
        val tx = CacheTransaction(paths, mutations)
        val plan = tx.preflight()
        check(tx.blockingErrors(plan).isEmpty()) { tx.blockingErrors(plan).joinToString("\n") }
        if (args[0] == "plan") { println("DRY_RUN ${tx.id} underlay def $def -> %06x".format(rgb)); return }
        val result = tx.apply(plan)
        check(tx.verify().isEmpty()) { "Cache verification failed: ${tx.id}" }
        println("UNDERLAY_DEF transaction=${tx.id} applied=${result.applied} skipped=${result.skipped} verified=true")
    }
}
