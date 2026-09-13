package gg.rsmod.game.model.social

import gg.rsmod.game.model.ChatFilterType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** RCV-010 D4: the private message delivery rule, for every combination of its inputs. */
class PrivateMessagePolicyTests {
    @Test
    fun `delivery refusal over every input combination`() {
        for (online in listOf(true, false)) {
            for (ignored in listOf(true, false)) {
                for (admin in listOf(true, false)) {
                    val expected =
                        when {
                            !online -> PrivateMessagePolicy.UNAVAILABLE
                            ignored && !admin -> PrivateMessagePolicy.UNAVAILABLE
                            else -> null
                        }
                    assertEquals(expected, PrivateMessagePolicy.deliveryRefusal(online, ignored, admin), "online=$online ignored=$ignored admin=$admin")
                }
            }
        }
        assertNull(PrivateMessagePolicy.deliveryRefusal(targetOnline = true, targetIgnoresSender = false, senderIsAdmin = false))
        assertEquals("Unable to send message - player unavailable.", PrivateMessagePolicy.UNAVAILABLE)
    }

    @Test
    fun `sending switches only an Off private status to On`() {
        ChatFilterType.values().forEach { status ->
            val expected = if (status == ChatFilterType.OFF) ChatFilterType.ON else status
            assertEquals(expected, PrivateMessagePolicy.senderPrivateStatusAfterSending(status), "$status")
        }
    }
}
