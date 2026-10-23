package gg.rsmod.plugins.content.mechanics.clan

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * N48 (LC18 10-16): a guest who joined another clan's channel kept the 'Visited Clan Chat' tab at "You are not currently a guest
 * in a Clan Chat". Novite ClansManager:1011-1014 runs cache script 4453 at a successful guest join; it arms 4454, which waits for
 * the listened channel and builds the rows (4444). Checked in the client 10-23 (screens\N48\10-guest-joined.png).
 */
class ClanGuestViewTests {
    @Test
    fun `a successful guest join runs the guest-view bootstrap before the channel is sent, a refused one does not`() {
        assertEquals(4453, Clans.GUEST_VIEW_SCRIPT)
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/clan/Clans.kt").readText()
        val listen = source.substringAfter("    fun listen(player: Player, clanName: String) {").substringBefore("\n    fun stopListening(")
        val success = listen.substringAfter("            else -> {")
        assertTrue(success.indexOf("player.runClientScript(GUEST_VIEW_SCRIPT)") in 0 until success.indexOf("refreshChannel(clan)"))
        assertEquals(1, Regex("GUEST_VIEW_SCRIPT").findAll(listen).count(), "only the successful join")
    }
}
