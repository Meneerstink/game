package gg.rsmod.plugins.content.mechanics.clan

import gg.rsmod.game.message.Message
import gg.rsmod.game.message.impl.IfSetHideMessage
import gg.rsmod.game.message.impl.RunClientScriptMessage
import gg.rsmod.game.model.entity.Player
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A44 / N54 (owner 10-13 "Clan Settings 1096 knoppen 118 + 413 volledig werkend"): the Clan Settings and Permissions tabs, the
 * five permission categories and "Show details" did nothing. Ids from OUR 1096 (dossier A44), checked in the client 10-23
 * (screens\A44: Settings page, Permissions with Admin / Chat / Skills, the details panel of a clanmate).
 */
class ClanSettingsTabsTests {
    private fun recorder(): Pair<Player, MutableList<Message>> {
        val sent = mutableListOf<Message>()
        val p = mockk<Player>(relaxed = true)
        every { p.write(*anyVararg()) } answers { (args[0] as Array<*>).filterIsInstance<Message>().forEach { sent += it } }
        return p to sent
    }

    private fun hide(sent: List<Message>, component: Int) =
        sent.filterIsInstance<IfSetHideMessage>().lastOrNull { it.hash == (1096 shl 16) or component }?.hidden

    private fun scripts(sent: List<Message>) = sent.filterIsInstance<RunClientScriptMessage>().map { it.id }

    @Test
    fun `the three tabs switch the pages 81, 82 and 83`() {
        val (p, sent) = recorder()
        ClanSettingsInterface.showSettings(p)
        assertEquals(listOf(4296), scripts(sent))
        assertEquals(true, hide(sent, 83))
        sent.clear()
        ClanSettingsInterface.showPermissions(p)
        assertEquals(true, hide(sent, 81))
        assertEquals(true, hide(sent, 82))
        assertEquals(false, hide(sent, 83))
        sent.clear()
        ClanSettingsInterface.showClanmates(p)
        assertEquals(listOf(4298), scripts(sent))
        assertEquals(true, hide(sent, 83))
    }

    @Test
    fun `a permission category shows its own page and hides 'Select a category above'`() {
        val (p, sent) = recorder()
        ClanSettingsInterface.selectCategory(p, 2)
        assertEquals(5136, scripts(sent).single())
        assertEquals(true, hide(sent, 24))
        assertEquals(false, hide(sent, 15))
        listOf(14, 16, 17, 18).forEach { assertEquals(true, hide(sent, it), "page $it") }
    }

    @Test
    fun `the tabs, categories and the details panel are bound`() {
        val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/clan/clan.plugin.kts").readText()
        listOf("TAB_CLANMATES", "TAB_SETTINGS", "TAB_PERMISSIONS").forEach { assertTrue("ClanSettingsInterface.$it" in plugin, it) }
        assertTrue("ClanSettingsInterface.CATEGORY_TABS.forEach" in plugin)
        val ui = File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/clan/ClanSettingsInterface.kt").readText()
        val send = ui.substringAfter("private fun sendMember(").substringBefore("\n    }\n")
        assertTrue(send.indexOf("player.setVarc(VARC_RANK, rank)") < send.indexOf("player.runClientScript(SCRIPT_SHOW_DETAILS)"), "the panel opens after its varcs")
    }
}
