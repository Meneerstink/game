package gg.rsmod.game

import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.service.GameService
import gg.rsmod.game.service.serializer.PlayerSerializerService
import mu.KLogging
import java.nio.file.Paths
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.EmptyCoroutineContext

object Launcher : KLogging() {
    @JvmStatic
    fun main(args: Array<String>) {
        val server = Server()
        server.startServer(apiProps = Paths.get("./data/api.yml"))

        // Start the game world
        val world = server.startGame(
            filestore = Paths.get("./data", "cache"),
            gameProps = Paths.get("./game.yml"),    // Already pointing to game.yml
            packets = Paths.get("./data", "packets.yml"),
            blocks = Paths.get("./data", "blocks.yml"),
            devProps = Paths.get("./dev-settings.yml"),
            args = args,
        )

        // Start the command server
        server.startCommandServer(world, tcpPort = world.gameContext.tcpPort)

        // Audit finding 16: Start-RSPS.ps1 used to only know Stop-Process -Force, which
        // TerminateProcess's the JVM with zero save/flush path - any progress a connected
        // player earned since their last logout/autosave was lost on every forced stop. A JVM
        // shutdown hook DOES still run on Windows for a console-close/Ctrl+C signal (just not
        // for -Force's raw TerminateProcess) - see the matching Start-RSPS.ps1 change that now
        // tries that signal first. Mirrors Client.handleLogout's own save call exactly, without
        // that method's other logout side effects (world-departure messaging, etc.) that assume
        // the world keeps running after.
        Runtime.getRuntime().addShutdownHook(
            Thread {
                saveAllOnShutdown(world)
                world.terminateServices(server)
                logger.info { "Graceful shutdown complete." }
            },
        )
    }

    /**
     * Audit S-10: the hook used to save every player from its own thread while the game loop kept
     * running - a save could snapshot a half-finished tick (one side of a trade before, the other
     * after the swap) and raced autosave/logout saves on the same file. Now the loop is paused
     * first and the saves run *on the game thread*, queued behind the cycle that may be running,
     * so they see a completed tick and nothing else runs meanwhile. Only if the game thread does
     * not answer (hung or dead) does the hook fall back to saving from its own thread.
     */
    private fun saveAllOnShutdown(world: World) {
        val gameService = world.getService(GameService::class.java)
        if (gameService != null) {
            gameService.pause = true
            val done = CountDownLatch(1)
            val queued =
                runCatching {
                    // The game executor is single-threaded: this runs after the current cycle has finished,
                    // and every later cycle sees pause = true and returns at once.
                    gameService.dispatcher.dispatch(
                        EmptyCoroutineContext,
                        Runnable {
                            try {
                                savePlayers(world)
                            } finally {
                                done.countDown()
                            }
                        },
                    )
                }.isSuccess
            if (queued && done.await(SHUTDOWN_SAVE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                return
            }
            logger.error { "Game thread did not run the shutdown save within ${SHUTDOWN_SAVE_TIMEOUT_SECONDS}s - saving from the shutdown hook." }
        }
        savePlayers(world)
    }

    private fun savePlayers(world: World) {
        val serializer = world.getService(PlayerSerializerService::class.java, searchSubclasses = true)
        world.players.forEach { player ->
            if (player is Client) {
                runCatching { serializer?.saveClientData(player) }
                    .onFailure { logger.error(it) { "Failed to save ${player.username} during shutdown." } }
            }
        }
    }

    private const val SHUTDOWN_SAVE_TIMEOUT_SECONDS = 20L
}
