package gg.rsmod.plugins.content.scrolls

import com.google.gson.JsonParser
import gg.rsmod.game.RspsPaths
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.*
import java.io.File

/** Personal source chart for Watson's board; POH Study/chart excluded by dossier Q5. */
object TreasureTrailStashChart {
    const val NOTICEBOARD = 62735 // Source 29718; installed Watson-house ledger and scene readback.
    data class Layout(val group: Int, val control: Int, val sprites: Map<Int, Int>)
    data class Tier(val name: String, val body: Int, val tab: Int, val graphics: List<Int>, val label: Int, val text: String)
    data class Mark(val component: Int, val source: Int, val filled: Boolean)
    private val source by lazy { JsonParser().parse(File(RspsPaths.data("cfg/clue/stash-chart.json")).readText()).asJsonObject }
    val tiers by lazy { source["tiers"].asJsonObject.entrySet().map { (name, value) -> value.asJsonObject.let {
        Tier(name, it["body"].asInt, it["tab"].asInt, it["sprites"].asJsonArray.map { it.asInt }, it["label"].asInt, it["text"].asString)
    } }.also { require(it.map { t -> t.name } == listOf("easy", "medium", "hard", "elite", "master")) } }
    val marks by lazy { source["checks"].asJsonArray.map { value -> value.asJsonObject.let {
        Mark(it["component"].asInt, it["sourceId"].asInt, it["filled"].asBoolean)
    } }.also { rows ->
        require(rows.size == 2 * TreasureTrailStashCatalogue.current.units.size && rows.map { it.component }.distinct().size == rows.size)
        require(rows.map { it.source }.toSet() == TreasureTrailStashCatalogue.current.units.map { it.sourceId }.toSet())
        require(rows.groupBy { it.source }.values.all { it.map { mark -> mark.filled }.toSet() == setOf(false, true) })
    } }
    val layout by lazy { File(RspsPaths.data("cfg/clue/stash-chart-mapping.tsv")).takeIf(File::isFile)?.readText()?.let(::parseMapping) }

    fun parseMapping(text: String): Layout {
        require(text.lineSequence().first().endsWith("applied=true"))
        val rows = text.lineSequence().filter { it.isNotBlank() && !it.startsWith('#') }.map { line ->
            val f = line.split('\t'); require(f.size == 3); (f[0] to f[1].toInt()) to f[2].toInt()
        }.toList()
        require(rows.map { it.first }.distinct().size == rows.size)
        val map = rows.toMap()
        require(map.keys.all { it.first in setOf("interface", "control", "script", "sprite") })
        require(map.getValue("script" to 1483) >= 0)
        return Layout(map.getValue("interface" to 493), map.getValue("control" to 493),
            map.filterKeys { it.first == "sprite" }.mapKeys { it.key.second }).also {
            require(it.group in 0..65534 && it.control in 0 until 10000)
        }
    }

    fun checkmarks(player: Player): Map<Int, Boolean> {
        val units = TreasureTrailStashCatalogue.current.units.associateBy { it.sourceId }
        return marks.associate { mark ->
            val unit = units.getValue(mark.source)
            mark.component to if (mark.filled) TreasureTrailStashes.contents(player, unit)?.isNotEmpty() == true
                else TreasureTrailStashes.built(player, unit)
        }
    }

    fun open(player: Player) {
        val row = layout ?: run {
            println("PROBLEM CLUE STASH chart cache batch missing")
            player.message("This noticeboard is temporarily unavailable."); return
        }
        player.openInterface(row.group, InterfaceDestination.MAIN_SCREEN)
        player.setInterfaceEvents(row.group, source["close"].asInt, -1..-1, 0x2)
        tiers.forEach { player.setInterfaceEvents(row.group, it.tab, -1..-1, 0x2) }
        select(player, 0)
        checkmarks(player).forEach { (component, visible) -> player.setComponentHidden(row.group, component, !visible) }
    }

    fun select(player: Player, index: Int) {
        val row = layout ?: return
        if (index !in tiers.indices || !player.interfaces.isVisible(row.group)) return
        player.setVarp(row.control, index)
        tiers.forEachIndexed { i, tier ->
            player.setComponentHidden(row.group, tier.body, i != index)
            val state = if (i == index) "selected" else "unselected"
            val graphics = source["states"].asJsonObject["${state}Sprites"].asJsonArray
            tier.graphics.forEachIndexed { n, component -> player.setComponentSprite(row.group, component, row.sprites.getValue(graphics[n].asInt)) }
            val colour = source["states"].asJsonObject["${state}Colour"].asInt.toString(16)
            player.setComponentText(row.group, tier.label, "<col=$colour>${tier.text}</col>")
        }
    }
    val close get() = source["close"].asInt
}
