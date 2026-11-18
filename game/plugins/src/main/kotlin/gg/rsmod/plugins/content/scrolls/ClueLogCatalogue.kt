package gg.rsmod.plugins.content.scrolls

import com.google.gson.JsonParser
import gg.rsmod.game.RspsPaths
import java.io.File

/** Exact item order from OSRS cache 2686 enums, including milestone scroll cases. */
class ClueLogCatalogue private constructor(val pages: Map<String, Page>) {
    data class Page(val sourceEnum: Int, val osrsItems: List<Int>)

    companion object {
        val current by lazy { load(File(RspsPaths.data("cfg/clue/collection-log.json"))) }
        fun load(file: File): ClueLogCatalogue {
            val root = JsonParser().parse(file.readText()).asJsonObject
            return ClueLogCatalogue(root.entrySet().associate { (key, value) ->
                val obj = value.asJsonObject
                key to Page(obj["enum"].asInt, obj.getAsJsonArray("items").map { it.asInt })
            })
        }
    }
}
