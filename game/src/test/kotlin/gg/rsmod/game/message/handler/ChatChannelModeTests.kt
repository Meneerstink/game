package gg.rsmod.game.message.handler

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * N239 (owner 2026-10-23): typing in the friends chat or clan chat went to public chat. The 2011 client strips the "/", "//", "///"
 * and sends CHAT_SETMODE (client opcode 23) first; the server now reads that packet and routes the line to the channel.
 */
class ChatChannelModeTests {
    @Test
    fun `a line in friends, clan or guest mode gets its channel back, public stays public`() {
        assertEquals("/hello", routeChannelMode("hello", 1))
        assertEquals("//hello", routeChannelMode("hello", 2))
        assertEquals("///hello", routeChannelMode("hello", 3))
        assertEquals("hello", routeChannelMode("hello", 0))
        assertEquals("/hello", routeChannelMode("/hello", 1), "an older client's own prefix is kept")
        assertEquals("::tele", routeChannelMode("::tele", 1), "commands are never channel text")
    }

    @Test
    fun `client opcode 23 is decoded as the chat mode, no longer ignored`() {
        val yml = File("../data/packets.yml").readText()
        val entry = yml.substringAfter("gg.rsmod.game.message.impl.ChatSetModeMessage").substringBefore("- message:")
        assertTrue("opcode: 23" in entry && "name: mode" in entry && "ignore" !in entry, entry)
    }
}
