package gg.rsmod.plugins.content.areas.alkharid

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * RCV-012 B12 roster. Evidence: NpcDefProbeTool `option heal` on the production 667 cache lists exactly the four Duel
 * Arena healers with op3 "Heal" (959 A'abla, 960 Sabreen, 961 Surgeon General Tafani, 962 Jaraah); the other hits are
 * Ivory npcs 11335/11338/11341 and farming patches (parked Farming).
 */
class DuelArenaNursesTests {
    private val cacheHealers = setOf(959, 960, 961, 962)
    private val content = File("src/main/kotlin/gg/rsmod/plugins/content/areas/alkharid")

    @Test
    fun `every Duel Arena npc with a cache Heal option is bound to the shared heal`() {
        assertEquals(cacheHealers, DuelArenaNurses.HEALERS.toSet())
        val plugin = File(content, "duel_arena_nurses.plugin.kts").readText()
        assertTrue(plugin.contains("DuelArenaNurses.HEALERS.forEach") && plugin.contains("option = \"heal\"") && plugin.contains("DuelArenaNurses.heal(this, npc)"))
    }

    @Test
    fun `every Can you heal me dialogue choice heals`() {
        listOf("surgeon_general_tafani.plugin.kts", "duel_arena_nurses.plugin.kts").forEach { name ->
            val text = File(content, name).readText().replace("\r\n", "\n")
            val choices = Regex("""chatPlayer\("Can you heal me\?"\)""").findAll(text).toList()
            assertTrue(choices.isNotEmpty(), "$name has no heal choice")
            choices.forEach { m ->
                val next = text.substring(m.range.last, (m.range.last + 220).coerceAtMost(text.length))
                assertTrue(next.contains("DuelArenaNurses.heal("), "$name: 'Can you heal me?' at ${m.range.first} does not heal")
            }
        }
    }

    @Test
    fun `heal follows Void Nurses heal`() {
        assertEquals(881, DuelArenaNurses.HEAL_ANIM, "Void thieving.anims.toml pick_pocket")
        assertEquals(166, DuelArenaNurses.HEAL_SOUND, "Void monastery.sounds.toml heal")
        val source = File(content, "DuelArenaNurses.kt").readText()
        assertTrue(source.contains("\"You feel a little better.\"") && source.contains("\"You look healthy to me!\""))
        assertTrue(source.contains("getCurrentLifepoints() < max") && source.contains("setCurrentLifepoints(max)"))
    }
}
