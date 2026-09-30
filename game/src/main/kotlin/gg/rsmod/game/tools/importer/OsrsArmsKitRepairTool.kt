package gg.rsmod.game.tools.importer

import java.io.File

/**
 * Puts `remove_arms` of every already imported OSRS item in `data/cfg/items.yml` on [OsrsItemImportTool.removesArms]
 * (OSRS wearPos2/3 = 6). On 2026-09-30 the rule briefly kept the 667 arms kit under OSRS bodies without an arms model; the
 * 667 arm then showed through their sleeves and armbands (owner `fotodd.png`). The missing forearm under those bodies is
 * [WornForearm] (see [OsrsForearmTool]). Only the `remove_arms` line of an imported item's block changes; the file is
 * otherwise copied as is.
 *
 * Usage: `[--apply]` - without it the planned changes are only printed. items.yml is read at server start.
 */
object OsrsArmsKitRepairTool {
    const val ITEMS_YML = "C:\\RSPS\\game\\game\\data\\cfg\\items.yml"

    private val ID = Regex("""^- id: (\d+)\s*$""")
    private val REMOVE_ARMS = Regex("""^(\s+remove_arms: )(true|false)\s*$""")

    /** [lines] with `remove_arms` set to [wanted] for the item ids it names; returns the lines and the changed ids. */
    fun rewrite(
        lines: List<String>,
        wanted: Map<Int, Boolean>,
    ): Pair<List<String>, List<Int>> {
        val out = ArrayList<String>(lines.size)
        val changed = ArrayList<Int>()
        var current: Int? = null
        for (line in lines) {
            ID.matchEntire(line)?.let { current = it.groupValues[1].toInt() }
            val match = REMOVE_ARMS.matchEntire(line)
            val target = current?.let { wanted[it] }
            if (match != null && target != null && match.groupValues[2].toBoolean() != target) {
                out += match.groupValues[1] + target
                changed += current!!
            } else {
                out += line
            }
        }
        return out to changed
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val reader = ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE))
        val wanted =
            try {
                val itemFiles = reader.files(ModernCacheReader.INDEX_CONFIG, ModernCacheReader.CONFIG_GROUP_ITEM)
                OsrsFxImportTool.importedItems(File(OsrsItemImportTool.ASSET_MAP)).mapNotNull { (upstream, local) ->
                    val bytes = itemFiles[upstream] ?: return@mapNotNull null
                    local to OsrsItemImportTool.removesArms(ModernItemDefDecoder.decode(upstream, bytes))
                }.toMap()
            } finally {
                reader.close()
            }
        val file = File(ITEMS_YML)
        val text = file.readText()
        val newline = if (text.contains("\r\n")) "\r\n" else "\n"
        val (lines, changed) = rewrite(text.split(newline), wanted)
        changed.forEach { println("PLAN item $it remove_arms -> ${wanted[it]}") }
        if (changed.isEmpty()) {
            println("NOTHING_TO_DO")
            return
        }
        if (!apply) {
            println("DRY_RUN ${changed.size} item(s)")
            return
        }
        file.writeText(lines.joinToString(newline))
        println("APPLIED ${changed.size} item(s) in $ITEMS_YML")
    }
}
