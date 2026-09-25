package gg.rsmod.game.protocol

import com.displee.cache.CacheLibrary
import gg.rsmod.game.model.World
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.timeout.IdleStateHandler
import io.netty.handler.traffic.ChannelTrafficShapingHandler
import io.netty.handler.traffic.GlobalTrafficShapingHandler
import mu.KLogging
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Initializes a channel and appends any required pipelines.
 *
 * @author Tom <rspsmods@gmail.com>
 */
class ClientChannelInitializer(
    private val revision: Int,
    private val rsaExponent: BigInteger?,
    private val rsaModulus: BigInteger?,
    private val filestore: CacheLibrary,
    world: World,
    /** Audit S-06: open connections allowed per remote IP (game.yml `max-connections-per-ip`). */
    private val maxConnectionsPerIp: Int = DEFAULT_MAX_CONNECTIONS_PER_IP,
) : ChannelInitializer<SocketChannel>() {
    /**
     * A global traffic handler that limits the amount of bandwidth all channels
     * can take up at once.
     */
    private val globalTrafficHandler =
        GlobalTrafficShapingHandler(Executors.newSingleThreadScheduledExecutor(), 0, 0, 1000)

    /**
     * The [io.netty.channel.ChannelHandler.Sharable] channel inbound adapter that
     * handles the messages sent and received from [SocketChannel]s.
     */
    private val handler = GameHandler(world)

    /**
     * Audit S-06: open connections per remote IP. Without a limit, thousands of idle sockets
     * from one address used up the server's file descriptors.
     */
    private val connectionsPerIp = ConcurrentHashMap<String, Int>()

    override fun initChannel(ch: SocketChannel) {
        val ip = ch.remoteAddress()?.address?.hostAddress ?: "unknown"
        val open = connectionsPerIp.compute(ip) { _, count -> (count ?: 0) + 1 } ?: 1
        ch.closeFuture().addListener(
            ChannelFutureListener {
                connectionsPerIp.computeIfPresent(ip) { _, count -> if (count <= 1) null else count - 1 }
            },
        )
        if (open > maxConnectionsPerIp) {
            logger.info("Refusing connection from {}: {} connections already open from that address.", ip, open - 1)
            ch.close()
            return
        }

        val p = ch.pipeline()
        val crcs = filestore.indices().map { it.crc }.toIntArray()

        p.addLast("global_traffic", globalTrafficHandler)
        p.addLast("channel_traffic", ChannelTrafficShapingHandler(0, 1024 * 5, 1000))
        // Audit S-06: GameHandler closes a channel that is still idle before login when this fires.
        p.addLast("timeout", IdleStateHandler(30, 0, 0))
        p.addLast("handshake_encoder", HandshakeEncoder())
        p.addLast(
            "handshake_decoder",
            HandshakeDecoder(revision = revision, cacheCrcs = crcs, rsaExponent = rsaExponent, rsaModulus = rsaModulus),
        )
        p.addLast("handler", handler)
    }

    companion object : KLogging() {
        const val DEFAULT_MAX_CONNECTIONS_PER_IP = 8
    }
}
