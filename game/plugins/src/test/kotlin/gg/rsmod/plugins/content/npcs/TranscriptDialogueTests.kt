package gg.rsmod.plugins.content.npcs

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import gg.rsmod.plugins.api.cfg.FacialExpression
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileReader

/** Guard over the whole generated transcript-dialogue table (every npc row, every node). */
class TranscriptDialogueTests {
    private val file = listOf(File(TranscriptDialogue.PATH), File("../../data/cfg/npcs/transcript-dialogue.json"), File("../data/cfg/npcs/transcript-dialogue.json"))
        .first { it.exists() }

    private val root: JsonObject = FileReader(file).use { JsonParser().parse(it).asJsonObject }

    @Test
    fun `the table covers the talkable npcs and every row names its wiki source`() {
        assertTrue("only ${root.size()} rows", root.size() > 1000)
        root.entrySet().forEach { (id, row) ->
            assertTrue("$id has no source", row.asJsonObject["src"].asString.contains("wiki"))
        }
    }

    @Test
    fun `every node is well formed, every expression exists and every exchange takes something`() {
        val problems = ArrayList<String>()
        val expressions = FacialExpression.values().map { it.name }.toSet()
        fun walk(id: String, nodes: JsonArray) {
            nodes.forEach { el ->
                val n = el.asJsonObject
                when (n["t"].asString) {
                    "npc", "player", "other" -> {
                        if (n["x"].asString.isBlank()) problems += "$id empty line"
                        if (n["e"].asString !in expressions) problems += "$id bad expression ${n["e"]}"
                    }
                    "msg" -> if (n["x"].asString.isBlank()) problems += "$id empty message"
                    "opt" -> {
                        val o = n.getAsJsonArray("o")
                        if (o.size() !in 1..5) problems += "$id ${o.size()} options"
                        o.forEach { c -> if (c.asJsonArray[0].asString.isBlank()) problems += "$id blank option"; walk(id, c.asJsonArray[1].asJsonArray) }
                    }
                    "rnd" -> n.getAsJsonArray("o").forEach { walk(id, it.asJsonArray) }
                    "cond" -> n.getAsJsonArray("b").forEach { walk(id, it.asJsonArray[2].asJsonArray) }
                    "trade" -> if (n.getAsJsonArray("rm").size() == 0) problems += "$id free hand-out"
                    "open", "go" -> Unit
                    else -> problems += "$id unknown node ${n["t"]}"
                }
            }
        }
        root.entrySet().forEach { (id, row) -> walk(id, row.asJsonObject.getAsJsonArray("nodes")) }
        assertTrue(problems.take(20).joinToString("\n"), problems.isEmpty())
    }
}
