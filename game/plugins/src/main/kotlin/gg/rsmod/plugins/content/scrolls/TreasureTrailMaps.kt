package gg.rsmod.plugins.content.scrolls

import com.google.gson.JsonParser
import gg.rsmod.game.RspsPaths
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.*
import java.io.File

/** Exact source illustrations, mapped only after Claude's verified UI import. */
object TreasureTrailMaps {
    data class MapSource(val key: String, val group: Int, val close: Int)
    val sources by lazy { parse(File(RspsPaths.data("cfg/clue/map-sources.json")).readText()) }
    fun parse(json: String): List<MapSource> {
        val rows=JsonParser().parse(json).asJsonArray.map { node -> node.asJsonObject.let { row ->
            require(row["imageSha256"].asString.matches(Regex("[0-9a-f]{64}")))
            require(row["dbSha256"].asString.matches(Regex("[0-9a-f]{64}")))
            MapSource(row["key"].asString,row["group"].asInt,row["close"].asInt)
        } }
        require(rows.map { it.key }.distinct().size==rows.size && rows.map { it.group }.distinct().size==rows.size)
        require(rows.all { it.group in setOf(86,337,338,339,340,341,342,343,344,346,347,348,349,350,351,352,353,354,355,356,357,358,359,360,361,362) && it.close in 0..255 })
        return rows
    }
    /** Handled even when the UI is missing: never disclose the answer as fallback hint text. */
    fun read(player: Player, step: ClueStep): Boolean {
        val source = (step as? TreasureTrailSourceStep)?.source ?: return false
        if (source.kind != "MAP") return false
        val map = sources.singleOrNull { it.key == source.key }
        val local = map?.let { ClueInterfaces.of(it.group) }
        if (local == null) {
            println("PROBLEM CLUE map interface unavailable: ${source.key}")
            player.message("This clue scroll is temporarily unavailable.")
            return true
        }
        player.openInterface(local, InterfaceDestination.MAIN_SCREEN)
        player.setInterfaceEvents(local, map.close, -1..-1, 0x2)
        return true
    }
    fun interfaceOf(row: TreasureTrailStepCatalogue.Step, resolve: (Int)->Int? = ClueInterfaces::of): Int? =
        if(row.kind!="MAP") null else sources.singleOrNull { it.key==row.key }?.let { resolve(it.group) }
    fun imported(resolve: (Int)->Int? = ClueInterfaces::of): List<Pair<MapSource,Int>> =
        sources.mapNotNull { row -> resolve(row.group)?.let { row to it } }
}
