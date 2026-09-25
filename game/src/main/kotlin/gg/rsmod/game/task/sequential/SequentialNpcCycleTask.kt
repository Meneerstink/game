package gg.rsmod.game.task.sequential

import gg.rsmod.game.model.World
import gg.rsmod.game.model.AvTrace
import gg.rsmod.game.service.GameService
import gg.rsmod.game.task.GameTask
import gg.rsmod.game.task.rethrowIfFatal
import mu.KLogging

/**
 * A [GameTask] responsible for executing [gg.rsmod.game.model.entity.Npc]
 * cycle logic, sequentially.
 *
 * @author Tom <rspsmods@gmail.com>
 */
class SequentialNpcCycleTask : GameTask {
    override fun execute(
        world: World,
        service: GameService,
    ) {
        world.npcs.forEach { n ->
            val startNanos = System.nanoTime()
            try {
                n.cycle()
            } catch (e: Throwable) {
                // Audit T-01: catch every non-fatal throwable, not only Exception.
                e.rethrowIfFatal()
                logger.error("Error cycling npc ${n.id} (${n.name}).", e)
            } finally {
                val elapsedNanos = System.nanoTime() - startNanos
                if (elapsedNanos >= TRACE_SLOW_NPC_NANOS) {
                    AvTrace.log {
                        "npc cycle timing id=${n.id} name=${n.name} index=${n.index} " +
                            "tile=${n.tile} active=${n.isActive()} cycleMs=${elapsedNanos / 1_000_000.0}"
                    }
                }
            }
        }
    }

    companion object : KLogging() {
        private const val TRACE_SLOW_NPC_NANOS = 50_000_000L
    }
}
