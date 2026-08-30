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
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.LAST_ACTIVE_CYCLE_ATTR
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.service.GameService
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
    private val messages: BlockingQueue<MessageHandle> = ArrayBlockingQueue<MessageHandle>(service.maxMessagesPerCycle)

    override fun receiveMessage(
        ctx: ChannelHandlerContext,
        msg: Any,
    ) {
        if (msg is GamePacket) {
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
            messages.add(MessageHandle(message, handler, msg.opcode, msg.payload.readableBytes()))

            /*
             * Release the allocated buffer for the [GamePacket].
             */
            msg.payload.release()
        }
    }

    override fun terminate() {
        client.requestLogout()
        logger.info("User '{}' requested disconnection from channel {}.", client.username, channel)
    }

    fun handleMessages() {
        for (i in 0 until service.maxMessagesPerCycle) {
            val next = messages.poll() ?: break
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
            }
            next.handler.handle(client, world, next.message)
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
        channel.disconnect()
    }

    private data class MessageHandle(
        val message: Message,
        val handler: MessageHandler<Message>,
        val opcode: Int,
        val length: Int,
    )

    companion object : KLogging()
}
