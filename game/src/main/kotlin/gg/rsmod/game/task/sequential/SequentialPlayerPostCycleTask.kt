package gg.rsmod.game.task.sequential

import gg.rsmod.game.model.World
import gg.rsmod.game.service.GameService
import gg.rsmod.game.task.GameTask
import gg.rsmod.game.task.rethrowIfFatal
import mu.KLogging

/**
 * A [GameTask] responsible for executing [gg.rsmod.game.model.entity.Pawn]
 * "post" cycle logic, sequentially. Post cycle means that the this task
 * will be handled near the end of the cycle, after the synchronization
 * tasks.
 *
 * @author Tom <rspsmods@gmail.com>
 */
class SequentialPlayerPostCycleTask : GameTask {
    override fun execute(
        world: World,
        service: GameService,
    ) {
        world.players.forEach { p ->
            try {
                p.postCycle()
            } catch (e: Throwable) {
                // Audit T-01: catch every non-fatal throwable, not only Exception.
                e.rethrowIfFatal()
                logger.error("Error post-cycling player ${p.username}.", e)
            }
        }
    }

    companion object : KLogging()
}
