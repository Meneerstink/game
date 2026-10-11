package gg.rsmod.plugins.content.mechanics.hud

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Owner 2026-10-11 ("f keys customize like osrs"): OSRS Keybinding defaults, one key per tab, choices None/F1-F12/Esc. */
class KeybindingConfigTests {
    @Test
    fun `every tab is a keybinding setting with the OSRS defaults`() {
        val defaults = ClientHudConfig.KEYBIND_TABS.toMap()
        assertEquals("F1", defaults["combat"])
        assertEquals("F2", defaults["skills"])
        assertEquals("F3", defaults["quests"])
        assertEquals("ESC", defaults["inventory"])
        assertEquals("F4", defaults["equipment"])
        assertEquals("F5", defaults["prayer"])
        assertEquals("F6", defaults["magic"])
        assertEquals("F7", defaults["clanchat"])
        assertEquals("F8", defaults["friends"])
        assertEquals("F10", defaults["settings"])
        assertEquals("F11", defaults["emotes"])
        assertEquals("F12", defaults["music"])
        assertEquals("NONE", defaults["logout"])
        // 667 has no Account Management tab: its row (OSRS F9) is the 667 Friends Chat tab.
        assertEquals("F9", defaults["friendschat"])
        assertEquals(14, ClientHudConfig.KEYBIND_TABS.size)
        ClientHudConfig.KEYBIND_TABS.forEach { (tab, key) ->
            val setting = ClientHudConfig.setting("keybinding.$tab")!!
            assertEquals(key, setting.default)
            assertEquals(ClientHudConfig.KEYBIND_KEYS.toList(), setting.options)
        }
        // Owner 2026-10-11 ("in de client ipv sidebar"): edited in the in-game window, not shown in the 78 Plugins sidebar.
        assertTrue("keybinding" !in ClientHudConfig.PLUGINS)
        // No key opens two tabs by default.
        val bound = ClientHudConfig.KEYBIND_TABS.map { it.second }.filter { it != "NONE" }
        assertEquals(bound.size, bound.toSet().size)
    }

    @Test
    fun `the in-game window lists the same tabs in the same order and offers the same keys`() {
        val tool = gg.rsmod.game.tools.importer.KeybindingInterfaceImportTool
        assertEquals(ClientHudConfig.KEYBIND_TABS.map { it.first }, tool.ROWS.map { it.first })
        assertEquals(ClientHudConfig.KEYBIND_KEYS.size, tool.KEY_LABELS.size)
        ClientHudConfig.KEYBIND_KEYS.zip(tool.KEY_LABELS).forEach { (key, label) -> assertEquals(key, label.uppercase()) }
    }
}
