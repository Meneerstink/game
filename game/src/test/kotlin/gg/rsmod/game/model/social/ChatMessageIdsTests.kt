package gg.rsmod.game.model.social

import gg.rsmod.game.message.encoder.MessagePrivateReceivedEncoder
import gg.rsmod.game.message.impl.MessagePrivateReceivedMessage
import gg.rsmod.game.model.priv.Privilege
import io.mockk.mockk
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * N264 / N265 (owner 2026-10-23: clan chat lines never showed, friends chat lines got lost): the client drops a private, friends chat
 * or clan chat line whose id is among the last 100 it showed of ANY of them. Friends chat and clan chat both counted from 1, so their
 * ids collided (reproduced on the test server: after two clan lines, the first two friends chat lines vanished).
 */
class ChatMessageIdsTests {
    @Test
    fun `friends chat, clan chat and private lines never share an id`() {
        val friendsChat = FriendsChat(File.createTempFile("friends-chat", ".json").apply { delete() })
        val ids = mutableListOf<Long>()
        repeat(200) {
            ids += friendsChat.nextMessageId()
            ids += ChatMessageIds.next()
        }
        assertEquals(ids.size, ids.toSet().size, "an id was handed out twice")
        assertTrue(ids.all { it in 1..ChatMessageIds.MASK }, "0 is the ring's empty slot; ids fit the packets' 40 bits")
    }

    @Test
    fun `a restart starts above the previous run, so an open client does not drop the new lines`() {
        val start = 1_792_800_000_000L
        val previousRunLastId = ChatMessageIds.seed(start) + 255
        assertTrue(ChatMessageIds.seed(start + 1_000) > previousRunLastId)
        assertTrue(ChatMessageIds.seed(4_200_000_000_000L) in 1..ChatMessageIds.MASK, "still inside 40 bits in 2103")
    }

    @Test
    fun `high and low words carry the whole id, the same split for private messages`() {
        val id = ChatMessageIds.next()
        assertEquals(id, (ChatMessageIds.high(id).toLong() shl 24) + ChatMessageIds.low(id))
        val pm = MessagePrivateReceivedMessage("someone", Privilege.DEFAULT, id, "hi", mockk(relaxed = true))
        val encoder = MessagePrivateReceivedEncoder()
        assertEquals(ChatMessageIds.high(id), encoder.extract(pm, "id_hi").toInt())
        assertEquals(ChatMessageIds.low(id), encoder.extract(pm, "id_lo").toInt())
    }
}
