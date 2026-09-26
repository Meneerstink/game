package gg.rsmod.plugins.content.mechanics.death

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Owner 2026-09-26: the safe minigames stay safe - Castle Wars included (it had never registered a SafeDeath check, so a
 * Castle Wars death lost items). Every safe minigame script must register one.
 */
class SafeDeathRegistrationTests {
    private val content = File("src/main/kotlin/gg/rsmod/plugins/content")

    @Test
    fun `castle wars registers a safe death on its match attribute`() {
        val script = File(content, "areas/kandarin/castlewars/castle_wars.plugin.kts").readText()
        assertTrue("SafeDeath.register { it.attr[CW_PLAYING] == true }" in script)
    }

    @Test
    fun `every safe minigame registers a safe death`() {
        listOf(
            "areas/kandarin/castlewars/castle_wars.plugin.kts",
            "areas/tzhaar/fightcaves/fight_caves.plugin.kts",
            "areas/wilderness/clan_wars_ffa.plugin.kts",
            "mechanics/clan/clan_wars_full.plugin.kts",
        ).forEach { path -> assertTrue("SafeDeath.register" in File(content, path).readText(), "$path registers no SafeDeath") }
    }
}