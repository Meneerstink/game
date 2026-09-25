package gg.rsmod.game.service.login

import com.google.common.util.concurrent.ThreadFactoryBuilder
import gg.rsmod.game.Server
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.protocol.GameHandler
import gg.rsmod.game.protocol.GameMessageEncoder
import gg.rsmod.game.protocol.PacketMetadata
import gg.rsmod.game.service.GameService
import gg.rsmod.game.service.Service
import gg.rsmod.game.service.rsa.RsaService
import gg.rsmod.game.service.serializer.PlayerSerializerService
import gg.rsmod.game.service.world.SimpleWorldVerificationService
import gg.rsmod.game.service.world.WorldVerificationService
import gg.rsmod.game.system.GameSystem
import gg.rsmod.net.codec.game.GamePacketDecoder
import gg.rsmod.net.codec.game.GamePacketEncoder
import gg.rsmod.net.codec.login.LoginRequest
import gg.rsmod.util.ServerProperties
import gg.rsmod.net.codec.login.LoginResultType
import gg.rsmod.util.io.IsaacRandom
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import mu.KLogging
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * A [Service] that is responsible for handling incoming login requests.
 *
 * @author Tom <rspsmods@gmail.com>
 */
class LoginService : Service {
    /**
     * The [PlayerSerializerService] implementation that will be used to decode
     * and encode the player data.
     */
    lateinit var serializer: PlayerSerializerService

    /**
     * The [LoginServiceRequest] requests that will be handled by our workers.
     *
     * Audit S-06: bounded (`queue-capacity`, default 256). The queue used to be unbounded, so a
     * login flood queued Argon2 work without limit; a request that does not fit is refused.
     */
    lateinit var requests: LinkedBlockingQueue<LoginServiceRequest>

    /**
     * Audit S-03: accounts that are online or logging in/out. Claimed by [LoginWorker] before the
     * save is read, released by [Client.handleLogout] after the logout save is written.
     */
    val sessions = AccountSessionRegistry()

    /** Audit S-06: per-IP attempt limit, per-account lock after wrong passwords, per-IP registration limit. */
    var throttle = LoginThrottle()
        private set

    private val requestCounter = AtomicInteger()

    private var threadCount = 1

    override fun init(
        server: Server,
        world: World,
        serviceProperties: ServerProperties,
    ) {
        threadCount = serviceProperties.getOrDefault("thread-count", 3)
        requests = LinkedBlockingQueue(serviceProperties.getOrDefault("queue-capacity", 256))
        throttle =
            LoginThrottle(
                attemptsPerIp = serviceProperties.getOrDefault("attempts-per-ip-per-minute", 10),
                failuresBeforeLock = serviceProperties.getOrDefault("failures-before-lock", 5),
                lockMs = serviceProperties.getOrDefault("lock-minutes", 5) * 60_000L,
                registrationsPerIp = serviceProperties.getOrDefault("registrations-per-ip-per-hour", 5),
            )
    }

    override fun postLoad(
        server: Server,
        world: World,
    ) {
        serializer = world.getService(PlayerSerializerService::class.java, searchSubclasses = true)!!
        serializer.registrationGate = { request -> throttle.allowRegistration(ipOf(request.channel)) }

        val worldVerificationService =
            world.getService(WorldVerificationService::class.java, searchSubclasses = true)
                ?: SimpleWorldVerificationService()

        val executorService =
            Executors.newFixedThreadPool(
                threadCount,
                ThreadFactoryBuilder()
                    .setNameFormat(
                        "login-worker",
                    ).setUncaughtExceptionHandler { t, e -> logger.error("Error with thread $t", e) }
                    .build(),
            )
        for (i in 0 until threadCount) {
            executorService.execute(LoginWorker(this, worldVerificationService))
        }
    }

    override fun bindNet(
        server: Server,
        world: World,
    ) {
    }

    override fun terminate(
        server: Server,
        world: World,
    ) {
    }

    fun addLoginRequest(
        world: World,
        request: LoginRequest,
    ) {
        if ((requestCounter.incrementAndGet() and 0xFF) == 0) {
            throttle.purge()
        }
        // Audit S-06: count the attempt per IP before any save is read or password is hashed.
        if (!throttle.allowAttempt(ipOf(request.channel))) {
            logger.info("Login attempt from {} refused: too many attempts from that address.", request.channel)
            reject(request.channel, LoginResultType.MAX_ATTEMPTS)
            return
        }
        val serviceRequest = LoginServiceRequest(world, request)
        if (!requests.offer(serviceRequest)) {
            logger.warn("Login queue is full ({} waiting); refusing {}.", requests.size, request.channel)
            reject(request.channel, LoginResultType.COULD_NOT_COMPLETE_LOGIN)
        }
    }

    fun successfulLogin(
        client: Client,
        world: World,
        encodeRandom: IsaacRandom,
        decodeRandom: IsaacRandom,
    ) {
        val gameSystem =
            GameSystem(
                channel = client.channel,
                world = world,
                client = client,
                service = client.world.getService(GameService::class.java)!!,
            )

        client.gameSystem = gameSystem
        client.channel.attr(GameHandler.SYSTEM_KEY).set(gameSystem)

        /*
         * NOTE(Tom): we should be able to use an parallel task to handle
         * the pipeline work and then schedule for the [client] to log in on the
         * next game cycle after completion. Should benchmark first.
         */
        val pipeline = client.channel.pipeline()
        val isaacEncryption = client.world.getService(RsaService::class.java) != null
        val encoderIsaac = if (isaacEncryption) encodeRandom else null
        val decoderIsaac = if (isaacEncryption) decodeRandom else null

        if (client.channel.isActive) {
            pipeline.remove("handshake_encoder")
            pipeline.remove("login_decoder")
            pipeline.remove("login_encoder")

            pipeline.addFirst("packet_encoder", GamePacketEncoder(encoderIsaac))
            pipeline.addAfter(
                "packet_encoder",
                "message_encoder",
                GameMessageEncoder(gameSystem.service.messageEncoders, gameSystem.service.messageStructures),
            )

            pipeline.addBefore(
                "handler",
                "packet_decoder",
                GamePacketDecoder(decoderIsaac, PacketMetadata(gameSystem.service.messageStructures)),
            )

            client.login()
            client.channel.flush()
        }
    }

    companion object : KLogging() {
        /** The remote IP address of [channel], or its string form when it has none (tests, embedded channels). */
        fun ipOf(channel: Channel): String =
            (channel.remoteAddress() as? InetSocketAddress)?.address?.hostAddress ?: channel.remoteAddress()?.toString() ?: "unknown"

        /** Sends a login result code and closes the channel (the handshake encoder writes [LoginResultType]s). */
        fun reject(
            channel: Channel,
            result: LoginResultType,
        ) {
            channel.writeAndFlush(result).addListener(ChannelFutureListener.CLOSE)
        }
    }
}
