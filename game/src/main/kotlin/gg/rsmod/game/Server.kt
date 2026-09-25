package gg.rsmod.game

import com.displee.cache.CacheLibrary
import com.google.common.base.Stopwatch
import gg.rsmod.game.message.impl.LogoutFullMessage
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.obj.ObjectCensus
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.game.protocol.ClientChannelInitializer
import gg.rsmod.game.service.GameService
import gg.rsmod.game.service.rsa.RsaService
import gg.rsmod.game.service.xtea.XteaKeyService
import gg.rsmod.util.ServerProperties
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.PooledByteBufAllocator
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import kotlin.concurrent.thread
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.LineBasedFrameDecoder
import io.netty.util.CharsetUtil
import io.netty.util.ReferenceCountUtil
import mu.KLogging
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.text.DecimalFormat
import java.util.concurrent.TimeUnit

/**
 * The [Server] is responsible for starting any and all games.
 *
 * @author Tom <rspsmods@gmail.com>
 */
class Server {
    /**
     * The properties specific to our API.
     */
    private val apiProperties = ServerProperties()

    private val acceptGroup = NioEventLoopGroup(2)

    private val ioGroup = NioEventLoopGroup(1)

    private val bootstrap = ServerBootstrap()

    /**
     * Audit S-09: the command port's shared secret (game.yml `commandServer.token`). Blank refuses every command.
     */
    @Volatile
    private var commandServerToken: String = ""

    /**
     * Prepares and handles any API related logic that must be handled
     * before the game can be launched properly.
     */
    fun startServer(apiProps: Path) {
        val bootThread = Thread.currentThread()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            logger.error("Uncaught server exception in thread $t!", e)
            if (t === bootThread) {
                /*
                 * A boot failure must never leave a half-started server behind. The game thread is
                 * already cycling by this point, so without this the process stays alive and busy
                 * while `startGame` never reaches the game-port bind - which presents as "the
                 * server hangs" instead of "the server crashed" (owner 2026-09-20, an hour lost to
                 * a running game-server that was never listening on 50015).
                 *
                 * halt() rather than exit(): shutdown hooks are for an orderly stop of a world that
                 * finished loading, and there are no players to save during boot.
                 */
                logger.error("Server boot failed - stopping so this is reported as a crash, not a hang.")
                Runtime.getRuntime().halt(1)
            }
        }
        val stopwatch = Stopwatch.createStarted()

        /*
         * Load the API property file.
         */
        apiProperties.loadYaml(apiProps.toFile())
        logger.info("Preparing ${getApiName()}...")

        /*
         * Inform the time it took to load the API related logic.
         */
        logger.info("${getApiName()} loaded up in ${stopwatch.elapsed(TimeUnit.MILLISECONDS)}ms.")
        logger.info("Visit our site ${getApiSite()} to purchase & sell plugins.")
    }

    fun startCommandServer(world: World, tcpPort: Int = 50017) {
        startTcpSocketListener(world, tcpPort)
    }

    private fun startTcpSocketListener(world: World, port: Int) {
        thread(start = true, name = "TcpSocketListener") {
            val bossGroup = NioEventLoopGroup(1)
            val workerGroup = NioEventLoopGroup(Runtime.getRuntime().availableProcessors())

            try {
                val serverBootstrap = ServerBootstrap()
                serverBootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel::class.java)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                    .childHandler(object : ChannelInitializer<Channel>() {
                        override fun initChannel(ch: Channel) {
                            // Audit S-09: one command per line, at most 256 bytes; a longer line closes the connection.
                            ch.pipeline().addLast(LineBasedFrameDecoder(256))
                            ch.pipeline().addLast(NettyTcpSocketHandler(world, { commandServerToken }, ::handleCommand))
                        }
                    })
                val serverChannelFuture = serverBootstrap.bind(InetSocketAddress("127.0.0.1", port)).sync()
                logger.info("Now listening for incoming server commands on 127.0.0.1:$port")
                serverChannelFuture.channel().closeFuture().sync()
            } catch (e: Exception) {
                logger.error("Error starting TCP Socket Server: ${e.message}", e)
            } finally {
                cleanupResources(bossGroup, workerGroup)
            }
        }
    }

    private fun cleanupResources(
        bossGroup: EventLoopGroup?,
        workerGroup: EventLoopGroup?) {
        try {
            bossGroup?.shutdownGracefully()?.sync()
            workerGroup?.shutdownGracefully()?.sync()
        } catch (e: Exception) {
            logger.error("Error during resource cleanup: ${e.message}", e)
        }
    }

    private class NettyTcpSocketHandler(
        private val world: World,
        private val token: () -> String,
        private val commandHandler: (String, World, (String) -> Unit) -> Unit,
    ) : io.netty.channel.ChannelInboundHandlerAdapter() {

        override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
            if (msg !is ByteBuf) {
                ReferenceCountUtil.release(msg)
                return
            }
            try {
                val line = msg.toString(CharsetUtil.UTF_8).trim()
                // Audit S-09: every line must start with the shared token; it is never logged.
                val command = CommandServerAuth.authorize(line, token())
                if (command == null) {
                    logger.warn("Command server: refused a line without a valid token from {}.", ctx.channel().remoteAddress())
                    reply(ctx, if (token().isBlank()) "Error: the command server has no token configured (commandServer.token in game.yml)." else "Error: invalid token.")
                    return
                }
                logger.info("Received command via TCP: {}", command)
                if (command.isBlank()) {
                    reply(ctx, "Error: Command cannot be empty.")
                    return
                }
                try {
                    commandHandler(command, world) { response -> reply(ctx, response) }
                } catch (e: Exception) {
                    logger.error("Error executing command via TCP: '$command': ${e.message}", e)
                    reply(ctx, "Error: An exception occurred while executing the command.")
                }
            } finally {
                msg.release()
            }
        }

        /** Thread-safe: may be called from the game thread once a command has run there. */
        private fun reply(
            ctx: ChannelHandlerContext,
            response: String,
        ) {
            ctx.writeAndFlush(Unpooled.copiedBuffer("$response\n", CharsetUtil.UTF_8))
        }

        override fun exceptionCaught(ctx: io.netty.channel.ChannelHandlerContext, cause: Throwable) {
            // Log and close on exception
            logger.error("TCP Handler: Error occurred: ${cause.message}", cause)
            ctx.close()
        }
    }

    /**
     * Audit S-09: runs [action] on the game thread and sends its answer to [reply]. The world is
     * single-threaded; `teleport` used to call moveTo on the Netty thread and `kick` ran in a loose Thread.
     */
    private fun onGameThread(
        world: World,
        reply: (String) -> Unit,
        action: () -> String,
    ) {
        val game = world.getService(GameService::class.java)
        if (game == null) {
            reply("Error: the game service is not running.")
            return
        }
        game.submitGameThreadJob {
            val response =
                try {
                    action()
                } catch (e: Exception) {
                    logger.error("Error while handling a command-server action: ${e.message}", e)
                    "An error occurred while processing your command: ${e.message}"
                }
            reply(response)
        }
    }

    /**
     * Processes a given command and performs the specified action within the game's world.
     *
     * The method supports commands such as "kick" (to remove a player from the game)
     * and "teleport" (to move a player to a specific location in the game world).
     * Anything that touches the world runs on the game thread ([onGameThread]).
     *
     * @param command The command string containing the action to be executed along with its arguments.
     * @param world The game world instance, used to interact and manipulate the state of the players.
     * @param reply Receives the response string (the result or an error message); may be called from the game thread.
     */
    private fun handleCommand(
        command: String,
        world: World,
        reply: (String) -> Unit,
    ) {
        when {
            command.startsWith("kick") -> {
                val username = command.substringAfter(" ").trim()
                if (username.isEmpty() || username == command) {
                    reply("Invalid usage! Expected: kick <username>")
                    return
                }
                onGameThread(world, reply) {
                    val player = world.getPlayerForName(username.replace("_", " "))
                    if (player != null) {
                        player.requestLogout()
                        player.write(LogoutFullMessage())
                        player.channelClose()
                        "Player $username has been kicked."
                    } else {
                        "Player $username not found."
                    }
                }
            }
            command.startsWith("teleport") -> {
                val args = command.substringAfter(" ").split(" ")
                if (args.size < 3 || args.size > 4) {
                    reply("Invalid usage! Expected: teleport <username> <x> <z> [height]")
                    return
                }
                val username = args[0]
                val x = args[1].toIntOrNull()
                val z = args[2].toIntOrNull()
                val height = if (args.size == 4) args[3].toIntOrNull() ?: 0 else 0

                if (x == null || z == null) {
                    reply("Invalid coordinates! <x> and <z> must be integers.")
                    return
                }

                onGameThread(world, reply) {
                    val player = world.getPlayerForName(username.replace("_", " "))
                    if (player != null) {
                        player.moveTo(Tile(x, z, height))
                        "Player $username has been teleported to [$x, $z, $height]."
                    } else {
                        "Player $username not found."
                    }
                }
            }
            command == "object_inventory" -> {
                onGameThread(world, reply) { ObjectCensus.writeCsv(world) }
            }
            command == "shutdown" -> {
                // Used by Start-RSPS.ps1 for a real graceful stop: System.exit runs the
                // Launcher shutdown hook (player saves + service termination). taskkill
                // without /F cannot stop a windowless JVM on Windows, so the launcher used
                // to fall back to a hard kill that skipped the saves.
                logger.info { "Shutdown requested via command server - saving players and exiting." }
                thread(start = true, name = "CommandShutdown") {
                    Thread.sleep(250)
                    System.exit(0)
                }
                reply("Shutting down: players are being saved.")
            }
            //TODO: Add moderation commands once report abuse interface is finished.
            else -> reply("Unknown command: $command")
        }
    }

    /**
     * Prepares and handles any game related logic that was specified by the
     * user.
     *
     * Due to being decoupled from the API logic that will always be used, you
     * can start multiple servers with different game property files.
     */
    fun startGame(
        filestore: Path,
        gameProps: Path,
        packets: Path,
        blocks: Path,
        devProps: Path?,
        @Suppress("UNUSED_PARAMETER") args: Array<String>,
    ): World {
        val stopwatch = Stopwatch.createStarted()
        val individualStopwatch = Stopwatch.createUnstarted()

        /*
         * Load the game property file.
         */
        val initialLaunch = Files.deleteIfExists(Paths.get("./first-launch"))
        val gameProperties = ServerProperties()
        val devProperties = ServerProperties()

        gameProperties.loadYaml(gameProps.toFile())
        if (devProps != null && Files.exists(devProps)) {
            devProperties.loadYaml(devProps.toFile())
        }

        logger.info("Loaded properties for ${gameProperties.get<String>("name")!!}.")

        /*
         * Extract the `commandServer` configuration as a map
         */
        val commandServerConfig = gameProperties.get<Map<String, Any>>("commandServer")
        val tcpPort = commandServerConfig?.get("tcpPort") as? Int ?: 50017
        commandServerToken = (commandServerConfig?.get("token") as? String)?.trim().orEmpty()
        if (commandServerToken.isBlank()) {
            logger.warn("commandServer.token is not set in game.yml: the command port (shutdown/kick/teleport) refuses every command.")
        }

        /*
         * Create a game context for our configurations and services to run.
         */
        val gameContext =
            GameContext(
                initialLaunch = initialLaunch,
                name = gameProperties.get<String>("name")!!,
                revision = gameProperties.get<Int>("revision")!!,
                cycleTime = gameProperties.getOrDefault("cycle-time", 600),
                playerLimit = gameProperties.getOrDefault("max-players", 2048),
                home =
                    Tile(
                        gameProperties.get<Int>("home-x")!!,
                        gameProperties.get<Int>("home-z")!!,
                        gameProperties.getOrDefault("home-height", 0),
                    ),
                skillCount = gameProperties.getOrDefault("skill-count", SkillSet.DEFAULT_SKILL_COUNT),
                npcStatCount = gameProperties.getOrDefault("npc-stat-count", Npc.Stats.DEFAULT_NPC_STAT_COUNT),
                runEnergy = gameProperties.getOrDefault("run-energy", true),
                gItemPublicDelay =
                    gameProperties.getOrDefault(
                        "gitem-public-spawn-delay",
                        GroundItem.DEFAULT_PUBLIC_SPAWN_CYCLES,
                    ),
                gItemDespawnDelay =
                    gameProperties.getOrDefault(
                        "gitem-despawn-delay",
                        GroundItem.DEFAULT_DESPAWN_CYCLES,
                    ),
                preloadMaps = gameProperties.getOrDefault("preload-maps", false),
                bonusExperience = gameProperties.getOrDefault("bonus-experience", false),
                tcpPort = tcpPort
            )

        val devContext =
            DevContext(
                debugExamines = devProperties.getOrDefault("debug-examines", false),
                debugObjects = devProperties.getOrDefault("debug-objects", false),
                debugButtons = devProperties.getOrDefault("debug-buttons", false),
                debugItemActions = devProperties.getOrDefault("debug-items", false),
                debugMagicSpells = devProperties.getOrDefault("debug-spells", false),
                debugInteractions = devProperties.getOrDefault("debug-interactions", false),
                // Audit S-12: off unless dev-settings.yml switches it on.
                debugNpcCensus = devProperties.getOrDefault("debug-npc-census", false),
            )

        val world = World(gameContext, devContext)

        /*
         * Load the file store.
         */
        individualStopwatch.reset().start()
        world.filestore = CacheLibrary(filestore.toFile().toString())
        logger.info(
            "Loaded filestore from path {} in {}ms.",
            filestore,
            individualStopwatch.elapsed(TimeUnit.MILLISECONDS),
        )

        /*
         * Load the definitions.
         */
        world.definitions.loadAll(world.filestore)

        /*
         * Load the services required to run the server.
         */
        world.loadServices(this, gameProperties)
        world.init()

        if (gameContext.preloadMaps) {
            /*
             * Preload region definitions.
             */
            world.getService(XteaKeyService::class.java)?.let { service ->
                world.definitions.loadRegions(world, world.chunks, service.validRegions)
            }
        }

        /*
         * Load the packets for the game.
         */
        world.getService(type = GameService::class.java)?.let { gameService ->
            individualStopwatch.reset().start()
            gameService.messageStructures.load(packets.toFile())
            gameService.messageEncoders.init()
            gameService.messageDecoders.init(gameService.messageStructures)
            logger.info(
                "Loaded message codec and handlers in {}ms.",
                individualStopwatch.elapsed(TimeUnit.MILLISECONDS),
            )
        }

        /*
         * Load the update blocks for the game.
         */
        individualStopwatch.reset().start()
        world.loadUpdateBlocks(blocks.toFile())
        logger.info("Loaded update blocks in {}ms.", individualStopwatch.elapsed(TimeUnit.MILLISECONDS))

        /*
         * Load the privileges for the game.
         */
        individualStopwatch.reset().start()
        world.privileges.load(gameProperties)
        logger.info(
            "Loaded {} privilege levels in {}ms.",
            world.privileges.size(),
            individualStopwatch.elapsed(TimeUnit.MILLISECONDS),
        )

        /*
         * Load the plugins for game content.
         */
        individualStopwatch.reset().start()
        world.plugins.init(
            server = this,
            world = world,
            jarPluginsDirectory = gameProperties.getOrDefault("plugin-packed-path", "./plugins"),
        )
        logger.info(
            "Loaded {} plugins in {}ms.",
            DecimalFormat().format(world.plugins.getPluginCount()),
            individualStopwatch.elapsed(TimeUnit.MILLISECONDS),
        )

        /*
         * Post load world.
         */
        world.postLoad()

        /*
         * The boot thread is done mutating the world; let the game thread start cycling.
         * See GameService.loaded - until this point a cycle would race the boot thread over
         * World.chunks and could kill the boot before the game port is ever bound.
         */
        world.getService(GameService::class.java)?.loaded = true

        /*
         * Inform the time it took to load up all non-network logic.
         */
        logger.info(
            "${gameProperties.get<String>("name")!!} loaded up in ${stopwatch.elapsed(TimeUnit.MILLISECONDS)}ms.",
        )

        /*
         * Set our bootstrap's groups and parameters.
         */
        val rsaService = world.getService(RsaService::class.java)
        val clientChannelInitializer =
            ClientChannelInitializer(
                revision = gameContext.revision,
                rsaExponent = rsaService?.getExponent(),
                rsaModulus = rsaService?.getModulus(),
                filestore = world.filestore,
                world = world,
                maxConnectionsPerIp =
                    gameProperties.getOrDefault("max-connections-per-ip", ClientChannelInitializer.DEFAULT_MAX_CONNECTIONS_PER_IP),
            )

        bootstrap.group(acceptGroup, ioGroup)
        bootstrap.channel(NioServerSocketChannel::class.java)
        bootstrap.childHandler(clientChannelInitializer)

        /*
         * Bind all service networks, if applicable.
         */
        world.bindServices(this)

        /*
         * Bind the game port.
         */
        val port = gameProperties.getOrDefault("game-port", 43594)
        bootstrap.bind(InetSocketAddress(port)).sync().awaitUninterruptibly()
        logger.info("Now listening for incoming connections on port $port...")

        System.gc()

        return world
    }

    /**
     * Gets the API-specific org name.
     */
    fun getApiName(): String = apiProperties.getOrDefault("org", "RS Mod")

    fun getApiSite(): String = apiProperties.getOrDefault("org-site", "rspsmods.com")

    companion object : KLogging() {}
}
