package gg.rsmod.game.system

import gg.rsmod.game.message.Message
import gg.rsmod.game.message.MessageHandler
import gg.rsmod.game.message.impl.ClientCheatMessage
import gg.rsmod.game.message.impl.DetectModifiedClientMessage
import gg.rsmod.game.message.impl.EventAppletFocusMessage
import gg.rsmod.game.message.impl.EventCameraPositionMessage
import gg.rsmod.game.message.impl.EventMouseIdleMessage
import gg.rsmod.game.message.impl.SoundSongEndMessage
import gg.rsmod.game.message.impl.WindowStatusMessage
import gg.rsmod.game.model.AvTrace
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.LAST_ACTIVE_CYCLE_ATTR
import gg.rsmod.game.model.attr.PLAYER_ACTION_INTERRUPT_ATTR
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.service.GameService
import gg.rsmod.game.task.rethrowIfFatal
import gg.rsmod.net.packet.GamePacket
import gg.rsmod.net.packet.GamePacketReader
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import mu.KLogging
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue

/**
 * A [ServerSystem] responsible for decoding and encoding [Message]s from and
 * to the [Client.channel].
 *
 * @author Tom <rspsmods@gmail.com>
 */
class GameSystem(
    channel: Channel,
    val world: World,
    val client: Client,
    val service: GameService,
) : ServerSystem(channel) {
    /**
     * Audit T-15: the buffer holds [PACKET_BUFFER_CYCLES] cycles' worth of packets. It was exactly one
     * cycle (`messages-per-cycle`, 30), so a burst - passive packets included - silently dropped real
     * clicks. [handleMessages] still handles at most `messages-per-cycle` packets per cycle; the rest
     * wait, in order, for the following cycles. Only a sustained flood overflows it.
     */
    private val messages: BlockingQueue<MessageHandle> =
        ArrayBlockingQueue<MessageHandle>(bufferCapacity(service.maxMessagesPerCycle))

    /** Audit T-15: packets this connection dropped because the buffer was full. */
    @Volatile
    private var droppedPackets = 0

    override fun receiveMessage(
        ctx: ChannelHandlerContext,
        msg: Any,
    ) {
        if (msg is GamePacket) {
            try {
                val decoder = service.messageDecoders.get(msg.opcode)
                if (decoder == null) {
                    logger.warn("No decoder found for message $msg.")
                    return
                }
                val handler = service.messageDecoders.getHandler(msg.opcode)
                if (handler == null) {
                    logger.warn("No handler found for message $msg")
                    return
                }
                val message = decoder.decode(msg.opcode, service.messageStructures.get(msg.opcode)!!, GamePacketReader(msg))
                val queued = messages.offer(
                    MessageHandle(
                        message = message,
                        handler = handler,
                        opcode = msg.opcode,
                        length = msg.payload.readableBytes(),
                        queuedAtNanos = System.nanoTime(),
                    ),
                )
                if (!queued) {
                    // A burst above the buffer must not throw from Netty's receive path and turn a
                    // temporary input backlog into the intermittent client freeze/disconnect. The
                    // bounded queue deliberately drops only the overflowing packet; the event is
                    // visible when AvTrace is enabled so the client action can be correlated.
                    AvTrace.log {
                        "packet queue full user=${client.username} opcode=${msg.opcode} " +
                            "length=${msg.payload.readableBytes()} capacity=${bufferCapacity(service.maxMessagesPerCycle)}"
                    }
                    // Audit T-15: overflow now means a sustained flood, so it is no longer silent.
                    if (droppedPackets++ % OVERFLOW_WARN_EVERY == 0) {
                        logger.warn(
                            "Packet buffer of {} is full ({} packets dropped so far); dropping opcode {}.",
                            client.username,
                            droppedPackets,
                            msg.opcode,
                        )
                    }
                }
            } finally {
                /*
                 * Every Netty packet owns a reference-counted payload, including unknown opcodes,
                 * missing handlers and decoder failures. Release it on every receive-path exit.
                 */
                msg.payload.release()
            }
        }
    }

    override fun terminate() {
        client.requestLogout()
        logger.info("User '{}' requested disconnection from channel {}.", client.username, channel)
    }

    /**
     * Handles at most `messages-per-cycle` queued packets; the rest stay queued, in order, for the
     * next cycle (Audit T-15).
     */
    fun handleMessages() {
        for (i in 0 until service.maxMessagesPerCycle) {
            if (!client.isOnline || client.isLogoutPending) {
                // Do not apply packets queued before a disconnect/logout request.
                messages.clear()
                return
            }
            val next = messages.poll() ?: break
            val dequeueNanos = System.nanoTime()
            val queueWaitNanos = dequeueNanos - next.queuedAtNanos
            val lockBefore = client.lock
            val queueSizeBefore = client.queues.size
            // R14.24: marks the player as actively playing on a real deliberate packet - the
            // single choke point every incoming message already passes through, so this needs
            // no per-handler wiring. Deliberately a blocklist of the few message types that are
            // NOT real user action, rather than an allowlist of the 140+ that are - missing a
            // legitimate action type here would only make AFK trigger slightly early (safe
            // direction), but the reverse (an automatic/passive message silently counting as
            // "still playing") would defeat AFK detection entirely, which is the actual risk:
            // - EventMouseIdleMessage: the client's own explicit "now idle" signal.
            // - SoundSongEndMessage: the client reports every looping background track finishing,
            //   with zero user input involved - confirmed by reading the handler, not assumed.
            // - EventCameraPositionMessage / EventAppletFocusMessage / WindowStatusMessage:
            //   plausibly automatic/passive depending on client build (camera drift, window
            //   manager focus churn, resize on connect) - not confirmed harmless, excluded to
            //   be safe rather than assumed to count.
            // - DetectModifiedClientMessage / ClientCheatMessage: anti-cheat internals, not
            //   user actions.
            val isPassive =
                next.message is EventMouseIdleMessage ||
                    next.message is SoundSongEndMessage ||
                    next.message is EventCameraPositionMessage ||
                    next.message is EventAppletFocusMessage ||
                    next.message is WindowStatusMessage ||
                    next.message is DetectModifiedClientMessage ||
                    next.message is ClientCheatMessage
            if (!isPassive) {
                client.attr[LAST_ACTIVE_CYCLE_ATTR] = world.currentCycle
                // Consume before invoking so the callback can safely clear/re-arm itself. This is
                // the one server-side boundary every deliberate client action crosses.
                client.attr[PLAYER_ACTION_INTERRUPT_ATTR]?.let { interrupt ->
                    client.attr.remove(PLAYER_ACTION_INTERRUPT_ATTR)
                    interrupt()
                }
            }
            try {
                try {
                    next.handler.handle(client, world, next.message)
                } catch (e: Throwable) {
                    // A malformed or stale packet must not abort this player's remaining input
                    // or the MessageHandlerTask for every later player in the same cycle.
                    // Audit T-01: any non-fatal throwable (a handler's TODO()), not only Exception.
                    e.rethrowIfFatal()
                    logger.error(
                        "Error handling incoming message ${next.message.javaClass.simpleName} " +
                            "opcode=${next.opcode} for user=${client.username}",
                        e,
                    )
                }
            } finally {
                val handlerNanos = System.nanoTime() - dequeueNanos
                if (queueWaitNanos >= TRACE_SLOW_PACKET_NANOS || handlerNanos >= TRACE_SLOW_PACKET_NANOS) {
                    gg.rsmod.game.model.AvTrace.log {
                        "packet timing user=${client.username} message=${next.message.javaClass.simpleName} " +
                            "opcode=${next.opcode} length=${next.length} cycle=${world.currentCycle} " +
                            "queueWaitMs=${queueWaitNanos / 1_000_000.0} handlerMs=${handlerNanos / 1_000_000.0} " +
                            "lockBefore=$lockBefore lockAfter=${client.lock} queuesBefore=$queueSizeBefore queuesAfter=${client.queues.size}"
                    }
                }
            }
        }
    }

    fun write(message: Message) {
        channel.write(message)
    }

    fun flush() {
        if (channel.isActive) {
            channel.flush()
        }
    }

    fun close() {
        /*
         * Netty does not send pending unflushed writes on disconnect/close - they are failed
         * instead. Every caller of this method writes a message (e.g. LogoutFullMessage) it
         * expects the client to actually receive before the connection drops, so that message
         * must be flushed first or the client only ever sees the raw connection die and shows
         * a "disconnected" state instead of a clean logout.
         */
        flush()
        channel.disconnect()
    }

    private data class MessageHandle(
        val message: Message,
        val handler: MessageHandler<Message>,
        val opcode: Int,
        val length: Int,
        val queuedAtNanos: Long,
    )

    companion object : KLogging() {
        private const val TRACE_SLOW_PACKET_NANOS = 50_000_000L

        /** Audit T-15: how many cycles' worth of packets (at `messages-per-cycle`) the buffer holds. */
        internal const val PACKET_BUFFER_CYCLES = 8

        /** Audit T-15: log one warning per this many dropped packets. */
        private const val OVERFLOW_WARN_EVERY = 100

        /** Audit T-15: the packet buffer capacity for [messagesPerCycle] handled packets per cycle. */
        internal fun bufferCapacity(messagesPerCycle: Int): Int = (messagesPerCycle * PACKET_BUFFER_CYCLES).coerceAtLeast(1)
    }
}
