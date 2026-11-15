package gg.rsmod.plugins.content.scrolls

import com.google.gson.JsonParser
import gg.rsmod.game.RspsPaths
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.World
import java.io.File

/** Only ledger-owned, dual-cache-verified imports published by build_npc_mapping.py. */
object ClueNpcs {
    private val expected by lazy {
        JsonParser().parse(File(RspsPaths.data("cfg/clue/npc-assets.json")).readText()).asJsonObject
    }
    private val mapping by lazy {
        File(RspsPaths.data("cfg/clue/npcs.tsv")).takeIf(File::isFile)?.readText()?.let(::parse).orEmpty()
    }
    fun parse(text: String): Map<Int, Int> {
        require(text.lineSequence().firstOrNull()?.endsWith("applied=true") == true)
        val rows = text.lineSequence().filter { it.isNotBlank() && !it.startsWith('#') }.map { line ->
            val fields = line.split('\t')
            require(fields.size == 3 && fields[0] == "npc")
            fields[1].toInt() to fields[2].toInt()
        }.toList()
        require(rows.map { it.first }.distinct().size == rows.size && rows.map { it.second }.distinct().size == rows.size)
        require(rows.all { it.second in 0..65534 })
        require(rows.map { it.first }.toSet() == expected.entrySet().map { it.key.toInt() }.toSet())
        return rows.toMap()
    }
    fun local(source: Int): Int? = mapping[source]

    fun validate(world: World, table: Map<Int, Int> = mapping) {
        for ((source, id) in table) {
            val row = expected[source.toString()].asJsonObject
            val npc = world.definitions.get(NpcDef::class.java, id)
            val options = row["options"].asJsonArray.map { if (it.isJsonNull) null else it.asString }
            check(npc.name == row["name"].asString && npc.combatLevel.coerceAtLeast(0) == row["level"].asInt &&
                npc.options.map { it?.takeUnless(String::isBlank) } == options &&
                (npc.chatheadModels?.size ?: 0) == row["heads"].asInt && npc.basId >= 0) {
                "CLUE NPC mapping $source -> $id does not match its verified imported definition"
            }
        }
    }

    fun install(world: World) {
        if (mapping.isEmpty()) {
            println("PROBLEM CLUE step NPC cache batch/mapping missing; sourced services unavailable")
            return
        }
        validate(world)
        // The Wiki supplies NPC examines; OSRS npc cache definitions do not contain them.
        mapping.forEach { (source, id) -> world.definitions.get(NpcDef::class.java, id).examine = expected[source.toString()].asJsonObject["examine"].asString }
    }
}
