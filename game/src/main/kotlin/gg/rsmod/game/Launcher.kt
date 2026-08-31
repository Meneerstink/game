package gg.rsmod.game

import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.service.serializer.PlayerSerializerService
import mu.KLogging
import java.nio.file.Paths

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
                val serializer = world.getService(PlayerSerializerService::class.java, searchSubclasses = true)
                world.players.forEach { player ->
                    if (player is Client) {
                        runCatching { serializer?.saveClientData(player) }
                            .onFailure { logger.error(it) { "Failed to save ${player.username} during shutdown." } }
                    }
                }
                world.terminateServices(server)
                logger.info { "Graceful shutdown complete." }
            },
        )
    }
}
