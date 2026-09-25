package gg.rsmod.game.task.sequential

import gg.rsmod.game.model.World
import gg.rsmod.game.service.GameService
import gg.rsmod.game.task.GameTask
import gg.rsmod.game.task.rethrowIfFatal
import mu.KLogging

/**
 * A [GameTask] responsible for executing [gg.rsmod.game.model.entity.Player]
 * cycle logic, sequentially.
 *
 * @author Tom <rspsmods@gmail.com>
 */
class SequentialPlayerCycleTask : GameTask {
    override fun execute(
        world: World,
        service: GameService,
    ) {
        world.players.forEach { p ->
            val start = System.currentTimeMillis()
            try {
                p.cycle()
            } catch (e: Throwable) {
                // Audit T-01: catch every non-fatal throwable, not only Exception.
                e.rethrowIfFatal()
                logger.error("Error cycling player ${p.username}.", e)
            } finally {
                val time = System.currentTimeMillis() - start
                service.playerTimes.merge(p.username, time) { _, oldTime -> oldTime + time }
            }
            /*
             * Log the time it takes for task to handle the player's cycle
             * logic.
             */
        }
    }

    companion object : KLogging()
}
