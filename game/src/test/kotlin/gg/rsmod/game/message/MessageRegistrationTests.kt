package gg.rsmod.game.message

import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

/**
 * Guards the wiring between `data/packets.yml` and the three registries the server builds from it.
 *
 * Every one of these failures is otherwise invisible until boot or, worse, until a packet arrives:
 * a message registered in [MessageDecoderSet] with no structure in the file throws only when the
 * server starts, and a message with a structure but no decoder is never noticed at all - the opcode
 * simply falls through to `GamePacketDecoder`'s unknown-opcode path, which discards every byte
 * currently readable and so eats whatever unrelated packets shared that read. That is exactly how
 * the friends-chat join packet went missing.
 */
class MessageRegistrationTests {
    @Test
    fun `every registered message has a structure and every structure a registration`() {
        val structures = MessageStructureSet().load(packets)

        // Throws if any message put() here has no structure in the file.
        MessageDecoderSet().init(structures)
        MessageEncoderSet()
    }

    @Test
    fun `the friends chat packets are registered in both directions`() {
        val structures = MessageStructureSet().load(packets)
        val decoders = MessageDecoderSet().apply { init(structures) }

        // ClientProt FRIENDS_CHAT_CHANGE(1): joining and leaving a channel.
        assertNotNull(decoders.get(FRIENDS_CHAT_CHANGE))
        assertNotNull(decoders.getHandler(FRIENDS_CHAT_CHANGE))

        // ServerProt UPDATE_FRIENDCHAT_CHANNEL_FULL(12) and MESSAGE_FRIENDCHANNEL(40).
        assertNotNull(structures.get(gg.rsmod.game.message.impl.UpdateFriendChatChannelFullMessage::class.java))
        assertNotNull(structures.get(gg.rsmod.game.message.impl.MessageFriendChannelMessage::class.java))
    }

    private companion object {
        private const val FRIENDS_CHAT_CHANGE = 1

        /** The tests run from the `game` subproject; the server loads the same file as `./data`. */
        private val packets = File("../data/packets.yml")
    }
}
