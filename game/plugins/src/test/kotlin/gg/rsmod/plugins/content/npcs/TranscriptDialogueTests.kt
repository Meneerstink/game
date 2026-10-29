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

    /** K02 2026-10-29: Lil' Sot said Lil' Zik's lines, the Penance pet showed another npc's head, Greatish guardian had dead options. */
    @Test
    fun `every pet form talks with its own head and every option of a pet leads somewhere`() {
        val pets = listOf(File("../../data/cfg/pets/pets.tsv"), File("../data/cfg/pets/pets.tsv"), File("data/cfg/pets/pets.tsv")).first { it.exists() }
        val forms = pets.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }.associate { it[3] to it[1] }
        val problems = ArrayList<String>()
        fun walk(form: String, nodes: JsonArray) {
            nodes.forEach { el ->
                val n = el.asJsonObject
                when (n["t"].asString) {
                    "other" -> if (n["s"].asString !in PET_SIDE_SPEAKERS) problems += "$form speaks as another npc: ${n["s"].asString}"
                    "go" -> if (n["to"].asString == "above") problems += "$form has an unresolved 'same as above'"
                    "opt" -> n.getAsJsonArray("o").forEach { c ->
                        val body = c.asJsonArray[1].asJsonArray
                        if (body.size() == 0) problems += "$form option '${c.asJsonArray[0].asString}' does nothing"
                        walk(form, body)
                    }
                    "rnd" -> n.getAsJsonArray("o").forEach { walk(form, it.asJsonArray) }
                    "cond" -> n.getAsJsonArray("b").forEach { walk(form, it.asJsonArray[2].asJsonArray) }
                }
            }
        }
        forms.forEach { (npc, form) ->
            val row = root[npc]?.asJsonObject
            if (row == null) problems += "$form ($npc) has no conversation" else walk(form, row.getAsJsonArray("nodes"))
        }
        assertTrue(problems.take(30).joinToString("\n"), problems.isEmpty())
    }

    private companion object {
        /** Real second speakers on the OSRS Wiki pet pages: Ikkle Hydra's four heads, Scurry's rats, the voice in Butch's talk. */
        val PET_SIDE_SPEAKERS = setOf("Right-most head", "Left-most head", "2nd head from the left", "Central head", "Chewy", "Nibbles", "Mysterious Voice")
    }
}
