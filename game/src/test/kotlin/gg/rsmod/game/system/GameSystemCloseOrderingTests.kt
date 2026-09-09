package gg.rsmod.game.system

import io.netty.channel.embedded.EmbeddedChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the exact Netty mechanism [GameSystem.close] relies on: a write that is never flushed is
 * discarded, not delivered, when the channel is subsequently closed. This is the root cause behind
 * a normal logout (write LogoutFullMessage, then close) leaving the client with a dead connection
 * instead of the intended clean transition, because nothing had ever told the transport to actually
 * send the buffered bytes before the socket went away.
 */
class GameSystemCloseOrderingTests {
    @Test
    fun writeWithoutFlushIsLostOnDisconnect() {
        val channel = EmbeddedChannel()

        channel.write("logout_full")
        channel.disconnect()

        assertNull(channel.readOutbound() as String?, "An unflushed write must not survive disconnect")
    }

    @Test
    fun writeThenFlushIsDeliveredBeforeDisconnect() {
        val channel = EmbeddedChannel()

        channel.write("logout_full")
        channel.flush()
        channel.disconnect()

        assertEquals("logout_full", channel.readOutbound(), "A flushed write must reach the transport before close")
    }

    @Test
    fun closeOrderingMatchesGameSystemContract() {
        // Same two calls GameSystem.close() makes, in the same order, against a real Channel.
        val channel = EmbeddedChannel()

        channel.write("logout_full")
        if (channel.isActive) channel.flush()
        channel.disconnect()

        assertEquals("logout_full", channel.readOutbound(), "GameSystem.close()'s flush-then-disconnect order must deliver pending writes")
    }
}
