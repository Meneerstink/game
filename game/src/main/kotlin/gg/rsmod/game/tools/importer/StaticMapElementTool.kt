package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary

/**
 * Adds ONE world-map static element (index 23 'main_staticelements', 7 bytes: u32 level<<28|x<<14|z, u16 element, u8 members - client
 * MapElementList.load, encoder [MansionMapIconTool.staticEntry]) - the route the client uses for BOTH the world map and the minimap
 * (Minimap.java r127). First use N618 (16-11): the ToA entrance raid icon, OSRS RAID_ICON 46201 at 3355,2714 plane 1 (area 91 ->
 * our mapelement 1149, the same icon the CoX entrance shows); the scene route (loc 65762 with mapelement 1149) did not draw it.
 *
 * Usage: `<element> <x> <z> <level> [--apply]`. An identical entry already present = nothing to do.
 */
object StaticMapElementTool {
    private const val WORLD_MAP_INDEX = 23

    @JvmStatic
    fun main(args: Array<String>) {
        val n = args.filterNot { it.startsWith("--") }.map { it.toInt() }
        require(n.size == 4) { "Usage: <element> <x> <z> <level> [--apply]" }
        val (element, x, z) = n
        val level = n[3]
        val apply = "--apply" in args
        val entry = MansionMapIconTool.staticEntry(element, x, z, level)
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0].toString())
        val mutation =
            try {
                val group = library.index(WORLD_MAP_INDEX).archive("main_staticelements") ?: error("main_staticelements missing")
                val files = group.fileIds()
                if (files.any { library.data(WORLD_MAP_INDEX, group.id, it)?.contentEquals(entry) == true }) {
                    println("NOTHING_TO_DO element $element at $x,$z level $level already in main_staticelements")
                    return
                }
                val next = (files.maxOrNull() ?: -1) + 1
                println("PLAN main_staticelements (group ${group.id}) file $next: element $element at $x,$z level $level (${files.size} entries)")
                CacheMutation(WORLD_MAP_INDEX, group.id, next, entry, "static map element $element at $x,$z,$level")
            } finally {
                library.close()
            }
        val tx = CacheTransaction(targets = OsrsItemImportTool.TARGETS, mutations = listOf(mutation))
        val plan = tx.preflight()
        val errors = tx.blockingErrors(plan)
        plan.forEach { println(it) }
        check(errors.isEmpty()) { errors.joinToString("\n") }
        if (!apply) { println("PLAN_ONLY transaction=${tx.id}"); return }
        val result = tx.apply(plan)
        val problems = tx.verify()
        check(problems.isEmpty()) { problems.joinToString("\n") }
        println("APPLIED transaction=${result.transactionId} applied=${result.applied} VERIFY_OK")
    }
}
