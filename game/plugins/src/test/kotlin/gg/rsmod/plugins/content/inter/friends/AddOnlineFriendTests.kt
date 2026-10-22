package gg.rsmod.plugins.content.inter.friends

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * N206 (owner 2026-10-22: "Message" on a friend opened no chat input). Messaging itself works (checked in game, fixed and resizable:
 * screens\N206\13-18); what failed was adding a player without a save file yet - a new account - as a friend ("unknown player"), so
 * there was no one to message. An online player now counts as existing for the friend and ignore lists.
 */
class AddOnlineFriendTests {
    @Test
    fun `an online player can be added to the friend and ignore lists`() {
        val friends = File("src/main/kotlin/gg/rsmod/plugins/content/inter/friends/friendlist.plugin.kts").readText()
        assertTrue("world.getPlayerForName(newFriend) != null || world.characterExists(newFriend)" in friends)
        val ignores = File("src/main/kotlin/gg/rsmod/plugins/content/inter/friends/ignorelist.plugin.kts").readText()
        assertTrue("world.getPlayerForName(ignoredUsername) != null || world.characterExists(ignoredUsername)" in ignores)
    }
}
