package gg.rsmod.plugins.content.areas.godwars

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * RCV-005 (owner: "alle minions van alle gwd bosses moeten gecontroleerd worden op aggressive"): every God Wars faction
 * npc id must hunt exactly like its Void (rev 634) definition, independent of the bulk combat table's aggressive flag.
 */
class GodWarsHuntModeTests {
    @Test
    fun `every GWD faction npc hunts players as its Void definition does`() {
        val void = voidHuntData()
        val problems = mutableListOf<String>()
        GodWars.God.values().filter { it != GodWars.God.ZAROS }.flatMap { it.npcs }.plus(GodWars.COWARDLY_HUNTERS).sorted().forEach { id ->
            val entry = void[id]
            if (entry == null) {
                problems += "$id: no Void npc definition"
                return@forEach
            }
            val expected =
                when (entry.mode) {
                    "aggressive" -> GodWars.HuntMode.GENERAL
                    "zamorak_aggressive", "anti_zamorak_aggressive" -> GodWars.HuntMode.FOLLOWER
                    "cowardly" -> GodWars.HuntMode.COWARDLY
                    "" -> null
                    else -> {
                        problems += "$id ${entry.key}: unknown Void hunt mode ${entry.mode}"
                        return@forEach
                    }
                }
            val actual = GodWars.huntMode(id)
            if (actual != expected) problems += "$id ${entry.key}: hunt mode $actual, Void ${entry.mode.ifEmpty { "none" }}"
            if (GodWars.huntRange(id) != entry.range) problems += "$id ${entry.key}: hunt range ${GodWars.huntRange(id)}, Void ${entry.range}"
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    private data class VoidHunt(val key: String, val mode: String, val range: Int)

    /** Void npc id -> hunt_mode / hunt_range (default 5), resolving `clone = "<npc key>"` inheritance. */
    private fun voidHuntData(): Map<Int, VoidHunt> {
        val sections = HashMap<String, MutableMap<String, String>>()
        File(VOID_DATA).walkTopDown().filter { it.isFile && it.name.endsWith(".npcs.toml") }.forEach { file ->
            var current: MutableMap<String, String>? = null
            file.forEachLine { raw ->
                val line = raw.substringBefore('#').trim()
                val header = Regex("""^\[([a-z0-9_]+)]$""").find(line)
                if (header != null) {
                    current = HashMap<String, String>().also { sections[header.groupValues[1]] = it }
                } else {
                    val kv = Regex("""^(id|hunt_mode|hunt_range|clone)\s*=\s*"?([^"]+)"?$""").find(line)
                    if (kv != null) current?.put(kv.groupValues[1], kv.groupValues[2].trim())
                }
            }
        }
        fun field(key: String, name: String, depth: Int = 0): String? {
            val section = sections[key] ?: return null
            return section[name] ?: section["clone"]?.takeIf { depth < 5 }?.let { field(it, name, depth + 1) }
        }
        return sections.entries.mapNotNull { (key, section) ->
            val id = section["id"]?.toIntOrNull() ?: return@mapNotNull null
            id to VoidHunt(key, field(key, "hunt_mode") ?: "", field(key, "hunt_range")?.toIntOrNull() ?: 5)
        }.toMap()
    }

    companion object {
        private const val VOID_DATA = "C:/RSPS/Donors/void/data"
    }
}
