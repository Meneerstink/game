package gg.rsmod.game.message

import gg.rsmod.game.message.encoder.UpdateFriendChatChannelFullEncoder
import gg.rsmod.game.message.impl.MessageGameMessage
import gg.rsmod.game.message.impl.UpdateFriendChatChannelFullMessage
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.priv.Privilege
import gg.rsmod.game.model.social.FriendsChat
import gg.rsmod.game.protocol.PacketMetadata
import gg.rsmod.net.codec.game.GamePacketDecoder
import gg.rsmod.net.packet.GamePacket
import gg.rsmod.net.packet.GamePacketReader
import gg.rsmod.util.Base37
import gg.rsmod.util.Misc
import gg.rsmod.util.io.IsaacRandom
import io.mockk.every
import io.mockk.mockk
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A34 (owner 2026-10-11 19:35 live: "Server reageert niet meer, klikken doet niets"; Keurmeester FAIL 2026-10-13: the leave path
 * was never run while IN a channel). The whole wire path of the Friends Chat tab, the way the 667 client drives it:
 *
 * client bytes (ISAAC-encoded opcodes, `FriendChat.java`: join = `p1(len) pjstr(name)`, Leave chat = a lone `p1(0)`)
 * -> [GamePacketDecoder] with the real `data/packets.yml` metadata (the live pipeline, LoginService.successfulLogin)
 * -> the registered FRIENDS_CHAT_CHANGE decoder + handler -> [FriendsChat]
 * -> UPDATE_FRIENDCHAT_CHANNEL_FULL bytes read back the way the client reads them (owner gjstr, g8 base-37 channel name, members).
 *
 * Class covered: every friends-chat join/leave alternation (5 rounds, a relog while in the channel), every name shape the
 * client's base-37 can carry (spaces, digits, 12 characters), whole stream and one byte per read (TCP fragments), and a normal
 * packet after every Leave must still decode (the stream + ISAAC stay aligned = the server keeps answering clicks).
 */
class FriendsChatWireTests {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "data/packets.yml").isFile }
    private val structures = MessageStructureSet().load(File(root, "data/packets.yml"))
    private val decoders = MessageDecoderSet().apply { init(structures) }

    private val world = mockk<World>(relaxed = true)
    private val settingsFile = File.createTempFile("a34_friends_chat", ".json").also { it.delete(); it.deleteOnExit() }
    private val chat = FriendsChat(settingsFile)
    private val online = HashMap<String, Client>()
    private val written = HashMap<String, MutableList<Message>>()

    init {
        every { world.friendsChat } returns chat
        every { world.getPlayerForName(any()) } answers { online[firstArg<String>().lowercase()] }
        every { world.characterExists(any()) } answers { firstArg<String>().lowercase() in online }
    }

    private fun player(name: String): Client {
        val p = mockk<Client>(relaxed = true)
        val sent = written.getOrPut(name) { mutableListOf() }
        every { p.username } returns name
        every { p.world } returns world
        every { p.friends } returns mutableListOf()
        every { p.ignoredPlayers } returns mutableListOf()
        every { p.privilege } returns Privilege(0, 0, "player", emptySet())
        every { p.attr } returns AttributeMap()
        every { p.write(*anyVararg<Message>()) } answers {
            for (arg in args) {
                when (arg) {
                    is Message -> sent += arg
                    is Array<*> -> arg.filterIsInstance<Message>().forEach { sent += it }
                }
            }
        }
        // Player.writeMessage is what the server calls for chatbox lines; on a mock it must be routed to write() by hand.
        every { p.writeMessage(any()) } answers { sent += MessageGameMessage(type = 0, message = firstArg(), username = null) }
        online[name.lowercase()] = p
        return p
    }

    /** Channel owners with every name shape the 667 base-37 name can carry, each with a prefix of the same kind. */
    private val owners =
        listOf(
            "Mod Ash" to "Mod Ash",
            "Abcdefghijkl" to "Abcdefghijkl",
            "Pk3r 99" to "Deadman 2026",
            "zezima" to "Old School 1",
            "A1 B2 C3 D4" to "Z 9",
        )

    @Test
    fun `join and Leave chat alternated in a real channel keep the stream aligned, whole stream`() = run(fragmented = false)

    @Test
    fun `join and Leave chat alternated in a real channel keep the stream aligned, one byte per read`() = run(fragmented = true)

    private fun run(fragmented: Boolean) {
        val guest = player("Sumtest")
        for ((owner, prefix) in owners) chat.setPrefix(player(owner), prefix)

        val seed = intArrayOf(0x1234, 0x5678, 0x9ABC, 0x0DEF)
        val clientCipher = IsaacRandom(seed)
        val channel = EmbeddedChannel(GamePacketDecoder(IsaacRandom(seed), PacketMetadata(structures)))
        var marker = 0x0A0B0C00

        fun send(bytes: ByteBuf) {
            if (!fragmented) {
                channel.writeInbound(bytes)
                return
            }
            while (bytes.isReadable) channel.writeInbound(bytes.readBytes(1))
            bytes.release()
        }

        fun nextPacket(label: String): GamePacket = assertNotNull(channel.readInbound() as GamePacket?, "$label: no packet decoded (stream out of step)")

        /** A normal click after the friends chat packet (RESUME_P_COUNTDIALOG, fixed 4): it must arrive whole with its own opcode. */
        fun clickAfter(label: String) {
            marker++
            send(Unpooled.buffer().writeByte(cipher(clientCipher, COUNT_DIALOG)).writeInt(marker))
            val next = nextPacket("$label -> next click")
            try {
                assertEquals(COUNT_DIALOG, next.opcode, "$label: the packet after it decoded as another opcode (desync)")
                assertEquals(marker, next.payload.readInt(), "$label: the packet after it carries shifted bytes")
            } finally {
                next.payload.release()
            }
            assertNull(channel.readInbound(), "$label: extra packets decoded")
            assertTrue(channel.isOpen, "$label: the connection was closed")
        }

        /** Runs a decoded FRIENDS_CHAT_CHANGE through the registered decoder and handler, like GameSystem does. */
        fun handle(label: String, packet: GamePacket) {
            try {
                assertEquals(FRIENDS_CHAT_CHANGE, packet.opcode, "$label: decoded as another opcode")
                val decoder = assertNotNull(decoders.get(packet.opcode), "no decoder for FRIENDS_CHAT_CHANGE")
                val message = decoder.decode(packet.opcode, structures.get(packet.opcode)!!, GamePacketReader(packet))
                assertEquals(0, packet.payload.readableBytes(), "$label: decoder left bytes unread")
                @Suppress("UNCHECKED_CAST")
                val handler = decoders.getHandler(packet.opcode) as MessageHandler<Message>
                handler.handle(guest, world, message)
            } finally {
                packet.payload.release()
            }
        }

        fun join(owner: String, prefix: String, label: String) {
            val name = owner.toByteArray(Charsets.ISO_8859_1)
            send(Unpooled.buffer().writeByte(cipher(clientCipher, FRIENDS_CHAT_CHANGE)).writeByte(name.size + 1).writeBytes(name).writeByte(0))
            handle(label, nextPacket(label))
            val inChannel = assertNotNull(chat.channelOf(guest), "$label: not in a channel after the join")
            assertEquals(Misc.formatForDisplay(owner), inChannel.ownerName, label)
            val list = assertNotNull(channelLists().lastOrNull(), "$label: the channel list was never sent")
            val shown = clientRead(list)
            assertEquals(Misc.formatForDisplay(owner), shown.owner, "$label: owner name in the client's list")
            assertEquals(Misc.formatForDisplay(prefix), shown.channelName, "$label: base-37 channel name as the client decodes it")
            assertTrue("Sumtest" in shown.members, "$label: the joiner is not in the member list ${shown.members}")
            clickAfter(label)
        }

        fun leave(label: String) {
            written.getValue("Sumtest").clear()
            send(Unpooled.buffer().writeByte(cipher(clientCipher, FRIENDS_CHAT_CHANGE)).writeByte(0))
            handle(label, nextPacket(label))
            assertNull(chat.channelOf(guest), "$label: still in the channel")
            val lines = written.getValue("Sumtest").filterIsInstance<MessageGameMessage>().map { it.message }
            assertTrue("You have left the channel." in lines, "$label: no 'You have left the channel.' ($lines)")
            val cleared = channelLists()
            assertTrue(cleared.isNotEmpty() && cleared.last() == null, "$label: the client was not told to clear the list")
            assertEquals(0, encode(null).size, "$label: a cleared list must be an empty UPDATE_FRIENDCHAT_CHANNEL_FULL")
            clickAfter(label)
        }

        for (round in 1..5) {
            for ((owner, prefix) in owners) {
                join(owner, prefix, "round $round join '$owner'")
                leave("round $round leave '$owner'")
            }
        }
        // Relog while in a channel (logout keeps it, login rejoins it), then Leave chat through the wire.
        val (owner, prefix) = owners.first()
        join(owner, prefix, "before relog")
        chat.leave(guest, notifyLeaver = false)
        chat.rejoinOnLogin(guest)
        assertEquals(Misc.formatForDisplay(owner), chat.channelOf(guest)?.ownerName, "relog: not back in the channel")
        leave("after relog")
        // A Leave with no channel (what the old Keurmeester run saw) stays harmless and the stream stays aligned.
        send(Unpooled.buffer().writeByte(cipher(clientCipher, FRIENDS_CHAT_CHANGE)).writeByte(0))
        handle("leave outside a channel", nextPacket("leave outside a channel"))
        clickAfter("leave outside a channel")
        channel.finish()
    }

    private fun channelLists(): List<FriendsChatSnapshot?> =
        written.getValue("Sumtest").filterIsInstance<UpdateFriendChatChannelFullMessage>().map { m ->
            if (m.channel == null) null else FriendsChatSnapshot(encode(m))
        }

    private fun encode(message: UpdateFriendChatChannelFullMessage?): ByteArray =
        UpdateFriendChatChannelFullEncoder().extractBytes(message ?: UpdateFriendChatChannelFullMessage(channel = null), "channel")

    private class FriendsChatSnapshot(val bytes: ByteArray)

    private data class ClientView(val owner: String, val channelName: String, val members: List<String>)

    /** The client's UPDATE_FRIENDCHAT_CHANNEL_FULL reader (ServerConnectionReader) + Base37.decodeName for the channel name. */
    private fun clientRead(snapshot: FriendsChatSnapshot): ClientView {
        val buf = Unpooled.wrappedBuffer(snapshot.bytes)
        fun gjstr(): String {
            val sb = StringBuilder()
            while (true) {
                val b = buf.readUnsignedByte().toInt()
                if (b == 0) return sb.toString()
                sb.append(b.toChar())
            }
        }
        val owner = gjstr()
        if (buf.readUnsignedByte().toInt() == 1) gjstr()
        val name = Base37.decodeName(buf.readLong())
        buf.readByte()
        val count = buf.readUnsignedByte().toInt()
        val members = ArrayList<String>()
        repeat(count) {
            members += gjstr()
            if (buf.readUnsignedByte().toInt() == 1) gjstr()
            buf.readUnsignedShort()
            buf.readByte()
            gjstr()
        }
        assertEquals(0, buf.readableBytes(), "the client would leave bytes of the channel list unread")
        return ClientView(owner, name, members)
    }

    /** The client's opcode cipher: `p1isaac` = opcode + ISAAC. */
    private fun cipher(isaac: IsaacRandom, opcode: Int): Int = (opcode + isaac.nextInt()) and 0xFF

    private companion object {
        /** ClientProt FRIENDS_CHAT_CHANGE (1, var-byte): join with a name, Leave chat with size 0. */
        const val FRIENDS_CHAT_CHANGE = 1

        /** ClientProt RESUME_P_COUNTDIALOG (3, fixed 4) - a normal click that must still decode afterwards. */
        const val COUNT_DIALOG = 3
    }
}
