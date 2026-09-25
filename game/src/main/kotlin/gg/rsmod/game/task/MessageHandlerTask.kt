package gg.rsmod.game.task

import gg.rsmod.game.model.World
import gg.rsmod.game.service.GameService
import mu.KLogging

/**
 * A [GameTask] responsible for handling all incoming
 * [gg.rsmod.game.message.Message]s, sequentially.
 *
 * @author Tom <rspsmods@gmail.com>
 */
class MessageHandlerTask : GameTask {
    override fun execute(
        world: World,
        service: GameService,
    ) {
        world.players.forEach { p ->
            val start = System.currentTimeMillis()
            try {
                p.handleMessages()
            } catch (e: Throwable) {
                // Audit T-01: a non-Exception throwable must not abort every later player's input.
                e.rethrowIfFatal()
                logger.error("Error handling messages for player ${p.username}.", e)
            } finally {
                val time = System.currentTimeMillis() - start
                service.playerTimes.merge(p.username, time) { _, oldTime -> oldTime + time }
            }
            /*
             * Log the time it takes for the task to handle all the player's
             * incoming messages.
             */
        }
    }

    companion object : KLogging()
}
