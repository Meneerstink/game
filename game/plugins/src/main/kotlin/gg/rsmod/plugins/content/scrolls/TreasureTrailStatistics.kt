package gg.rsmod.plugins.content.scrolls

import com.google.gson.JsonParser
import gg.rsmod.game.RspsPaths
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.*
import java.io.File

/** Wiki Treasure Trail Statistics + its reference image; capacities use the real case unlocks.
 * The owner excludes the beginner tier and calls master78. The OSRS rank "Beginner" remains a rank.
 */
object TreasureTrailStatistics {
    const val STATISTICS_OBJECT = 62628 // source6567, exact existing Watson wall placement, dual-cache proof
    const val PINBOARD_OBJECT = 62629 // source6615; no duplicate scenery import
    const val FIRST_LINE = 2
    const val LINE_COUNT = 12

    fun rank(total: Long): String {
        require(total >= 0)
        return when {
            total <= 100 -> "Beginner"
            total <= 200 -> "Novice"
            total <= 400 -> "The Explorer"
            total <= 600 -> "Treasure Hunter"
            total <= 1000 -> "Expert"
            total <= 1500 -> "Elite"
            total <= 2000 -> "Master"
            else -> "Legend"
        }
    }

    fun remaining(player: Player, tier: ClueScrollTier): Int? = ClueContainers.cases
        .filter { it.tier == tier && !ClueContainers.opened(player,it) }
        .minOfOrNull { (it.threshold.toLong() - ClueCompletions.get(player,tier).coerceAtLeast(0)).coerceAtLeast(0).toInt() }

    fun lines(player: Player): List<String> {
        val counts = ClueScrollTier.values().associateWith { ClueCompletions.get(player,it).coerceAtLeast(0) }
        fun yellow(value: Any) = "<col=ffff00>$value</col><col=ffffff>"
        fun count(tier: ClueScrollTier) = "${tier.displayName}: ${yellow(counts.getValue(tier))}"
        val total = counts.values.sumOf { it.toLong() }
        val mimic = ClueContainers.cases.single { it.tier == null }
        val rows = listOf("Treasure Trails Completed:",
            count(ClueScrollTier.EASY)+" | "+count(ClueScrollTier.MEDIUM),
            count(ClueScrollTier.HARD)+" | "+count(ClueScrollTier.ELITE),
            count(ClueScrollTier.MASTER),
            "Total: ${yellow(total)} | Your rank is: ${yellow(rank(total))}",
            "Scroll Box Capacities:") + ClueScrollTier.values().map { tier ->
                "${tier.displayName}: ${yellow(ClueContainers.capacity(player,tier))} scrolls | " +
                    (remaining(player,tier)?.let { "Next scroll case: ${yellow(it)} caskets" } ?: "No more scroll cases")
            } + "Mimic scroll case: ${yellow(if (ClueContainers.opened(player,mimic)) "Obtained" else "Not obtained") }"
        return rows.map { "<col=ffffff>$it" } // Wiki reference: white labels, yellow values, native q8 shadow
    }

    fun open(player: Player, iface: Int? = ClueInterfaces.of(221)): Boolean {
        if (iface == null) {
            println("PROBLEM CLUE statistics paper not imported; account state unchanged")
            return false
        }
        val rows = lines(player)
        check(rows.size == LINE_COUNT)
        player.openInterface(iface,InterfaceDestination.MAIN_SCREEN)
        rows.forEachIndexed { index,text -> player.setComponentText(iface,FIRST_LINE+index,text) }
        player.setInterfaceEvents(iface,0,-1..-1,0x2)
        return true
    }

    fun contributors(text: String): List<String> = JsonParser().parse(text).asJsonArray.map { it.asString }.also {
        require(it.isNotEmpty() && it.distinct().size == it.size && it.all { name -> name.isNotBlank() && '\n' !in name && '<' !in name })
    }
    private val names by lazy { contributors(File(RspsPaths.data("cfg/clue/watson-contributors.json")).readText()) }
    fun openPinboard(player: Player, iface: Int? = ClueInterfaces.of(625)): Boolean {
        if (iface == null) {
            println("PROBLEM CLUE contributor paper not imported; account state unchanged")
            return false
        }
        player.openInterface(iface,InterfaceDestination.MAIN_SCREEN)
        player.setComponentText(iface,4,"Pinboard") // source scenery name, not an invented contributor headline
        player.setComponentText(iface,7,names.joinToString("<br>"))
        player.setInterfaceEvents(iface,2,-1..-1,0x2)
        // Existing reviewed native scroll program; preflight proves its exact bytes in both caches.
        player.runClientScript(gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool.SCROLL_SCRIPT,
            (iface shl 16) or 5,(iface shl 16) or 6,1200) // source625:7 text height
        return true
    }
}
