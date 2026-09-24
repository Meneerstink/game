package gg.rsmod.game.model.social

import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.priv.Privilege
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test
import java.io.File

/** Friends-chat rules (owner 2026-09-24 "friendchat volledig werkend maken"): setup, enter/talk/kick ranks, one-hour kick ban. */
class FriendsChatTests {
    private val world = mockk<World>(relaxed = true)
    private val file = File.createTempFile("friends_chat", ".json").also { it.delete(); it.deleteOnExit() }
    private val chat = FriendsChat(file)
    private val online = HashMap<String, Player>()

    init {
        every { world.getPlayerForName(any()) } answers { online[firstArg<String>().lowercase()] }
    }

    private fun player(name: String, friends: List<String> = emptyList(), privilege: Int = 0, ignores: List<String> = emptyList()): Player {
        val p = mockk<Player>(relaxed = true)
        every { p.username } returns name
        every { p.world } returns world
        every { p.friends } returns friends.toMutableList()
        every { p.privilege } returns Privilege(privilege, 0, "p$privilege", emptySet())
        every { p.attr } returns gg.rsmod.game.model.attr.AttributeMap()
        every { p.ignoredPlayers } returns ignores.toMutableList()
        online[name.lowercase()] = p
        return p
    }

    @Test
    fun `the channel is remembered at logout and rejoined at login, an explicit leave forgets it`() {
        every { world.characterExists(any()) } returns true
        val owner = player("Owner")
        val guest = player("Guest")
        chat.setPrefix(owner, "Crew")
        assertTrue(chat.joinWithMessages(guest, "Owner"))
        chat.leave(guest, notifyLeaver = false)
        assertNull(chat.channelOf(guest))
        chat.rejoinOnLogin(guest)
        assertEquals("Crew", chat.channelOf(guest)!!.name)
        chat.leave(guest)
        chat.rejoinOnLogin(guest)
        assertNull(chat.channelOf(guest))
    }

    @Test
    fun `a sender on the receiver's ignore list is not heard`() {
        val sender = player("Spammer")
        val receiver = player("Quiet", ignores = listOf("spammer"))
        assertTrue(FriendsChat.ignores(receiver, sender))
        assertFalse(FriendsChat.ignores(sender, receiver))
    }

    @Test
    fun `a channel must be set up before anyone can join`() {
        val owner = player("Owner")
        val guest = player("Guest")
        assertFalse(chat.join(guest, "Owner"))
        chat.setPrefix(owner, "Hangout")
        assertTrue(chat.join(guest, "Owner"))
        assertEquals("Hangout", chat.channelOf(guest)!!.name)
        assertEquals(FriendsChatRank.GUEST, chat.channelOf(guest)!!.rankOf(guest))
        assertTrue(file.readText().contains("Hangout"), "settings are saved")
    }

    @Test
    fun `enter and talk ranks and friend ranks decide who joins and talks`() {
        val owner = player("Owner", friends = listOf("Pal"))
        val pal = player("Pal")
        val guest = player("Guest")
        chat.setPrefix(owner, "Crew")
        chat.setEnterRank(owner, FriendsChatRank.FRIEND)
        assertFalse(chat.join(guest, "Owner"))
        assertTrue(chat.join(pal, "Owner"))
        chat.setFriendRank(owner, "Pal", 3)
        assertEquals(3, chat.channelOf(pal)!!.rankOf(pal))
        assertEquals(3, chat.friendRank("Owner", "Pal"))
        chat.setTalkRank(owner, 4)
        assertTrue(chat.talk(world, pal, "hi"), "handled: refused with a message")
        chat.setFriendRank(owner, "Pal", 4)
        assertTrue(chat.talk(world, pal, "hi"))
        assertTrue(chat.join(owner, "Owner"))
        assertEquals(FriendsChatRank.OWNER, chat.channelOf(owner)!!.rankOf(owner))
    }

    @Test
    fun `kick needs the kick rank and bans for an hour`() {
        val owner = player("Owner")
        val guest = player("Guest")
        chat.setPrefix(owner, "Crew")
        chat.join(owner, "Owner")
        chat.join(guest, "Owner")
        chat.kick(guest, "Owner")
        assertTrue(chat.channelOf(owner) != null)
        chat.kick(owner, "Guest")
        assertNull(chat.channelOf(guest))
        assertFalse(chat.join(guest, "Owner"))
    }

    @Test
    fun `disabling the channel empties it and the crown is the sender's privilege`() {
        val owner = player("Owner")
        val admin = player("Staff", privilege = 2)
        chat.setPrefix(owner, "Crew")
        chat.join(admin, "Owner")
        assertEquals(FriendsChatRank.STAFF, chat.channelOf(admin)!!.rankOf(admin))
        assertEquals(2, chat.crownOf(admin))
        assertEquals(0, chat.crownOf(owner))
        chat.setPrefix(owner, null)
        assertNull(chat.channelOf(admin))
    }

    @Test
    fun `prefixes are what the base-37 channel name can hold`() {
        assertEquals("My Chat 1", FriendsChat.validPrefix("  My   Chat 1 "))
        assertNull(FriendsChat.validPrefix(""))
        assertNull(FriendsChat.validPrefix("thirteen char"))
        assertNull(FriendsChat.validPrefix("bad!"))
    }
}
