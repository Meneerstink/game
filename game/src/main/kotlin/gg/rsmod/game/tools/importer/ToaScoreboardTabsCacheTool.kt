package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.RspsPaths
import java.io.File

/**
 * TOA-1911 punt 7 (owner 19-11 'statistics-interface: snijdt elkaar, niet gecentreerd'): OSRS 775 draws its three mode tabs
 * (toa_statistics:entry/normal/expert = 775:85/86/87, op 'View') at runtime with [proc,script3723] (scripts 6771-6774); the port
 * (OsrsInterfacePortTool, local group [localGroup]) kept the click layers but not the tab graphics, so the tab strip was an empty band.
 * This adds them as static children exactly like the party window's tabs (OsrsInterfacePortTool group 774, 'tabs' block): per tab a left
 * cap 20x21, a tiled middle (w-40)x21 from x 20, an h-flipped right cap (tabs_modal_short,2/3 = 2566/2567 unselected) and a p12 label
 * 0xff981f. The server swaps sprites/colour for the selected tab (ToaScoreboardUi). No existing component changes.
 *
 * Usage: java -cp ... gg.rsmod.game.tools.importer.ToaScoreboardTabsCacheTool plan|apply   (servers stopped for apply)
 */
object ToaScoreboardTabsCacheTool {
    val TABS = listOf(85 to "Entry", 86 to "Normal", 87 to "Expert")
    /** Highest native/ported component of the local 775 (steelborder 88..99, thinbox 100..101). */
    const val PORTED_LAST = 101
    const val FIRST = PORTED_LAST + 1

    fun localGroup(): Int = File(RspsPaths.data("cfg/toa/interfaces.tsv")).readLines()
        .map { it.split('\t') }.first { it.size >= 3 && it[0] == "interface" && it[1] == "775" }[2].toInt()

    fun sprite(osrs: Int): Int = File(RspsPaths.data("cfg/toa/interfaces.tsv")).readLines()
        .map { it.split('\t') }.first { it.size >= 3 && it[0] == "sprite" && it[1] == osrs.toString() }[2].toInt()

    fun components(font: Int, cap: Int, middle: Int): List<OsrsInterfacePortTool.C> {
        var next = FIRST
        val list = ArrayList<OsrsInterfacePortTool.C>()
        TABS.forEach { (tab, label) ->
            list += OsrsInterfacePortTool.C(next++, 5, 0, 0, 20, 21, tab, 0, 0, 0, 0, false, sprite = cap)
            list += OsrsInterfacePortTool.C(next++, 5, 20, 0, 40, 21, tab, 1, 0, 0, 0, false, sprite = middle, tiling = true)
            list += OsrsInterfacePortTool.C(next++, 5, 0, 0, 20, 21, tab, 0, 0, 2, 0, false, sprite = cap, flipH = true)
            list += OsrsInterfacePortTool.C(next++, 4, 0, 1, 0, 19, tab, 1, 0, 1, 0, false, font = font, text = label, alignX = 1, alignY = 1,
                shadow = true, colour = 0xff981f)
        }
        return list
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val group = localGroup()
        val caches = OsrsItemImportTool.TARGETS.map { CacheLibrary(it) }
        val mutations = ArrayList<CacheMutation>()
        try {
            val originals = caches.map { cache -> cache.index(3).archive(group)!!.fileIds().associateWith { cache.data(3, group, it)!! } }
            check(originals.all { it.keys == originals.first().keys }) { "group $group component sets differ between the caches" }
            val fonts = caches.map { it.index(8).archive("p12_full")?.id ?: error("p12_full missing") }
            check(fonts.distinct().size == 1)
            val wanted = components(fonts.first(), sprite(2566), sprite(2567))
            val keys = originals.first().keys
            check(keys.all { it <= PORTED_LAST || it in wanted.map { c -> c.id } }) { "group $group has components beyond $PORTED_LAST that are not ours: ${keys.filter { it > PORTED_LAST }}" }
            check((0..PORTED_LAST).all { it in keys }) { "group $group is not the reviewed 0..$PORTED_LAST port" }
            wanted.forEach { c ->
                val bytes = OsrsInterfacePortTool.encode(c)
                val old = originals.first()[c.id]
                if (old != null && old.contentEquals(bytes)) { println("IN PLACE $group:${c.id}"); return@forEach }
                mutations += CacheMutation(3, group, c.id, bytes, "TOA-1911 statistics tab $group:${c.id} (parent ${c.parent})", old?.let(CacheItemProbeTool::sha1))
            }
            println("TABS group=$group components=$FIRST..${FIRST + wanted.size - 1} (per tab ${TABS.map { it.first }}: left, middle, right, text)")
        } finally { caches.forEach { it.close() } }
        if (mutations.isEmpty()) { println("NOTHING_TO_DO"); return }
        val tx = CacheTransaction(OsrsItemImportTool.TARGETS, mutations)
        val plan = tx.preflight()
        check(tx.blockingErrors(plan).isEmpty()) { tx.blockingErrors(plan).joinToString("\n") }
        if (mode == "plan") { println("DRY_RUN ${tx.id} mutations=${mutations.size}"); return }
        val result = tx.apply(plan)
        val problems = tx.verify()
        if (problems.isNotEmpty()) { println("ROLLED_BACK ${tx.rollback()}"); error("verification failed: $problems") }
        println("APPLIED transaction=${tx.id} writes=${result.applied} VERIFY_OK")
    }
}
