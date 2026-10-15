package gg.rsmod.game.system

import gg.rsmod.game.message.Message
import gg.rsmod.game.message.MessageHandler
import gg.rsmod.game.message.impl.BugShotChunkMessage
import gg.rsmod.game.message.impl.ClientCheatMessage
import gg.rsmod.game.message.impl.DetectModifiedClientMessage
import gg.rsmod.game.message.impl.EventAppletFocusMessage
import gg.rsmod.game.message.impl.EventCameraPositionMessage
import gg.rsmod.game.message.impl.EventMouseIdleMessage
import gg.rsmod.game.message.impl.SoundSongEndMessage
import gg.rsmod.game.message.impl.WindowStatusMessage
import gg.rsmod.game.model.AvTrace
import gg.rsmod.game.model.BugShotUploads
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
                val packetBytes = msg.payload.readableBytes()
                val message = decoder.decode(msg.opcode, service.messageStructures.get(msg.opcode)!!, GamePacketReader(msg))
                // Debug toolkit (ROOT_CAUSES #34): a decoder that leaves bytes unread reads a wrong layout - every value of the
                // packet is shifted (missing first letters, wrong ids).
                if (gg.rsmod.game.model.Trace.enabled(gg.rsmod.game.model.Trace.Cat.INPUT) && msg.payload.readableBytes() > 0) {
                    val left = msg.payload.readableBytes()
                    gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.INPUT) {
                        "packet opcode=${msg.opcode} ${message.javaClass.simpleName} left $left of $packetBytes bytes unread - its decoder reads the wrong layout"
                    }
                }
                if (message is BugShotChunkMessage) {
                    // T2: a bug-report screenshot comes in hundreds of pieces; through the queue they would take the
                    // messages-per-cycle slots (and the buffer) of the player's real clicks. BugShotUploads is thread-safe.
                    BugShotUploads.receive(client.username, message.total, message.offset, message.data)
                    return
                }
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
        checkSilentClick()
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
            // OSRS Wiki "Deadman Mode": during the 7-second countdown "the player must not perform any further actions or be attacked".
            // A key press (the client sends every key, so the arrow keys turning the camera), a chat line, a chat filter change or the
            // map-loaded notice is activity but not a game action - they cancelled a skulled player's teleport/logout countdown
            // (2026-10-01). They still count for AFK below.
            val notAnAction =
                next.message is gg.rsmod.game.message.impl.KeyTypedMessage ||
                    next.message is gg.rsmod.game.message.impl.MessagePublicMessage ||
                    next.message is gg.rsmod.game.message.impl.MessagePrivateMessage ||
                    next.message is gg.rsmod.game.message.impl.MessageQuickChatPublicMessage ||
                    next.message is gg.rsmod.game.message.impl.MessageQuickChatPrivateMessage ||
                    next.message is gg.rsmod.game.message.impl.ChatFilterMessage ||
                    next.message is gg.rsmod.game.message.impl.MapBuildCompleteMessage ||
                    (next.message is gg.rsmod.game.message.impl.IfButtonMessage && (next.message as gg.rsmod.game.message.impl.IfButtonMessage).hash in TAB_BUTTONS)
            if (!isPassive) {
                client.attr[LAST_ACTIVE_CYCLE_ATTR] = world.currentCycle
            }
            if (!isPassive && !notAnAction) {
                // Consume before invoking so the callback can safely clear/re-arm itself. This is
                // the one server-side boundary every deliberate client action crosses.
                client.attr[PLAYER_ACTION_INTERRUPT_ATTR]?.let { interrupt ->
                    client.attr.remove(PLAYER_ACTION_INTERRUPT_ATTR)
                    interrupt()
                }
            }
            val messageName = next.message.javaClass.simpleName
            if (!isPassive && gg.rsmod.game.model.Trace.anyOn() && !messageName.startsWith("Event") && messageName !in UNTRACED_INPUT) {
                lastAction = "$messageName${traceFields(next.message)} cycle=${world.currentCycle}"
                gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.INPUT) { "${client.username} at=${client.tile.x},${client.tile.z},${client.tile.height} $lastAction" }
                // Silent-click detector: a menu option / button / dialogue answer that gets no answer at all within
                // SILENT_CLICK_CYCLES (no message, interface, var, sound, animation, movement or queued action).
                if ((messageName.startsWith("Op") || messageName.startsWith("IfButton") || messageName.startsWith("Resume")) &&
                    !isClientTabStone(next.message)
                ) {
                    silentCheck = SilentCheck(lastAction, world.currentCycle, client.tile, client.lastAnimation)
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
                    gg.rsmod.game.model.Trace.crash("packet handler, ${client.username}'s $lastAction", e)
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

    /**
     * Conformance suite: queues [message] exactly like a decoded client packet - same buffer, same per-cycle handling, same
     * handler and gates - so a test bot's clicks go through the real input path. False when the type has no handler.
     */
    fun inject(message: Message): Boolean {
        val opcode = service.messageDecoders.opcodeFor(message.javaClass) ?: return false
        val handler = service.messageDecoders.getHandler(opcode) ?: return false
        return messages.offer(MessageHandle(message = message, handler = handler, opcode = opcode, length = 0, queuedAtNanos = System.nanoTime()))
    }

    /**
     * A gameframe tab stone: its cache onOp is clientscript 1303 (the client switches varc 168 itself), so the server rightly
     * sends nothing back - not a "click did nothing" (owner session 2026-10-01: 746:41 x7; unhandled tsv 548:99..136).
     */
    private fun isClientTabStone(message: Message): Boolean {
        val hash = (message as? gg.rsmod.game.message.impl.IfButtonMessage)?.hash ?: return false
        return (hash ushr 16) to (hash and 0xFFFF) in CLIENT_TAB_STONES
    }

    /** The last deliberate action of this player (trace context: which click caused a message or an error). */
    @Volatile
    var lastAction: String = "-"

    private class SilentCheck(val action: String, val cycle: Int, val tile: gg.rsmod.game.model.Tile, val animation: Int) {
        @Volatile
        var answered = false
    }

    @Volatile
    private var silentCheck: SilentCheck? = null

    private fun checkSilentClick() {
        val check = silentCheck ?: return
        if (world.currentCycle < check.cycle + SILENT_CLICK_CYCLES) return
        silentCheck = null
        if (check.answered || !gg.rsmod.game.model.Trace.anyOn()) return
        val moved = client.tile != check.tile || client.hasMoveDestination()
        val busy = client.queues.size > 0 || client.lastAnimation != check.animation
        if (!moved && !busy) {
            gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.INPUT) {
                "click did nothing: ${client.username}'s ${check.action} got no message, interface, var, sound, animation, movement or action within $SILENT_CLICK_CYCLES ticks"
            }
        }
    }

    fun write(message: Message) {
        if (gg.rsmod.game.model.Trace.anyOn()) {
            silentCheck?.let { if (isAnswer(message)) it.answered = true }
            traceOutgoing(message)
        }
        gg.rsmod.game.model.Probe.message(client, message)
        channel.write(message)
    }

    /** A message that shows the player something happened (not the per-tick sync / map traffic every client gets anyway). */
    private fun isAnswer(message: Message): Boolean {
        val name = message.javaClass.simpleName
        return name !in PER_TICK_OUTPUT && !name.startsWith("UpdateZone") && !name.startsWith("Rebuild")
    }

    /** The server -> client side of the traces: chat messages, interfaces and vars, with PROBLEM verdicts. */
    private fun traceOutgoing(message: Message) {
        val who = client.username
        when (message) {
            is gg.rsmod.game.message.impl.MessageGameMessage -> {
                val text = message.message
                // The client's own HUD protocol (__RSPS_HUD__:dmmz / dmmt ...) is no chat: 974 of 1,379 msg lines (2026-10-09).
                if (text.startsWith("__RSPS_HUD__")) return
                gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.MSG) { "$who type=${message.type} '$text'" }
                if (text == gg.rsmod.game.model.entity.Entity.NOTHING_INTERESTING_HAPPENS || text.startsWith("Nothing interesting happens")) {
                    gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.MSG) { "$who got 'Nothing interesting happens.' (no handler) after $lastAction" }
                }
                if (message.type == 109) {
                    gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.MSG) { "note: filterable message (hidden when the player's game filter is on): '$text'" }
                }
            }
            is gg.rsmod.game.message.impl.IfOpenSubMessage, is gg.rsmod.game.message.impl.IfOpenTopMessage, is gg.rsmod.game.message.impl.IfCloseSubMessage,
            is gg.rsmod.game.message.impl.IfMoveSubMessage ->
                gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.INTERFACE) { "$who ${message.javaClass.simpleName}${traceFields(message)} after $lastAction" }
            is gg.rsmod.game.message.impl.VarpSmallMessage, is gg.rsmod.game.message.impl.VarpLargeMessage,
            is gg.rsmod.game.message.impl.VarbitSmallMessage, is gg.rsmod.game.message.impl.VarbitLargeMessage ->
                gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.VAR) { "$who ${message.javaClass.simpleName}${traceFields(message)}" }
            else -> {}
        }
    }

    /** "(a=1, b=2)" for a data-class message, else the declared fields read reflectively (trace only). */
    private fun traceFields(message: Any): String {
        val text = message.toString()
        if (!text.contains('@')) return text.substringAfter(message.javaClass.simpleName, text)
        return runCatching {
            message.javaClass.declaredFields.filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }.joinToString(", ", "(", ")") { f ->
                f.isAccessible = true
                val v = f.get(message)
                "${f.name}=${if (v is IntArray) v.joinToString(",", "[", "]") else v}"
            }
        }.getOrDefault("")
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

        /** Interface 746 / 548 components whose cache onOp is clientscript 1303 (InterfaceHookProbeTool, 2026-10-02). */
        private val CLIENT_TAB_STONES: Set<Pair<Int, Int>> =
            ((39..54) + 176).map { 746 to it }.toSet() + ((99..106) + (129..136)).map { 548 to it }

        /** Debug toolkit: ticks a click may take before "click did nothing" is reported. */
        private const val SILENT_CLICK_CYCLES = 3

        /** Output every client gets on its own (regen, run energy, music, timers) - not an answer to a click. */
        private val PER_TICK_OUTPUT =
            setOf(
                "UpdateRunEnergyMessage", "UpdateRunWeightMessage", "UpdateRebootTimerMessage", "UpdateStatMessage", "MidiSongMessage",
                "UpdateStockmarketSlotMessage", "UpdateFriendListMessage", "UpdateIgnoreListMessage", "UpdateFriendChatChannelFullMessage",
            )

        /** Input that is noise in the input trace (keep-alives, window/camera/mouse telemetry). */
        /**
         * The gameframe tab buttons (667 cache: every component of the fixed 548 and resizable 746 top-levels whose op runs CS2 1303,
         * the client-side tab switch): opening a tab is not a game action - OSRS sends no packet for it - so it does not cancel the
         * Deadman 7-second countdown (2026-10-01).
         */
        val TAB_BUTTONS: Set<Int> =
            ((99..106) + (129..136)).map { (548 shl 16) or it }.toSet() + (39..54).map { (746 shl 16) or it }.toSet()

        private val UNTRACED_INPUT = setOf("NoTimeoutMessage", "KeyTypedMessage", "WindowStatusMessage", "ChatFilterMessage")

        /** Audit T-15: the packet buffer capacity for [messagesPerCycle] handled packets per cycle. */
        internal fun bufferCapacity(messagesPerCycle: Int): Int = (messagesPerCycle * PACKET_BUFFER_CYCLES).coerceAtLeast(1)
    }
}
