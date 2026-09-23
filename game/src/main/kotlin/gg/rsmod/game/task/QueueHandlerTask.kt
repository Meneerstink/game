package gg.rsmod.game.task

import gg.rsmod.game.model.World
import gg.rsmod.game.service.GameService
import mu.KLogging

/**
 * A [GameTask] responsible for going over all the active
 * [gg.rsmod.game.model.queue.QueueTask]s.
 *
 * @author Tom <rspsmods@gmail.com>
 */
class QueueHandlerTask : GameTask {
    override fun execute(
        world: World,
        service: GameService,
    ) {
        var playerQueues = 0
        var npcQueues = 0

        world.players.forEach { player ->
            try {
                player.queues.cycle()
                playerQueues += player.queues.size
            } catch (e: Exception) {
                logger.error("Error cycling queues for player ${player.username}.", e)
            }
        }

        world.npcs.forEach { npc ->
            try {
                npc.queues.cycle()
                npcQueues += npc.queues.size
            } catch (e: Exception) {
                logger.error("Error cycling queues for npc ${npc.id} (${npc.name}).", e)
            }
        }

        val worldQueues: Int = world.queues.size
        try {
            world.queues.cycle()
        } catch (e: Exception) {
            logger.error("Error cycling world queues.", e)
        }

        service.totalPlayerQueues = playerQueues
        service.totalNpcQueues = npcQueues
        service.totalWorldQueues = worldQueues
    }

    companion object : KLogging()
}
