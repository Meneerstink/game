package gg.rsmod.game.task.sequential

import gg.rsmod.game.model.World
import gg.rsmod.game.service.GameService
import gg.rsmod.game.sync.task.*
import gg.rsmod.game.task.GameTask
import mu.KLogging

/**
 * A [GameTask] that is responsible for sending [gg.rsmod.game.model.entity.Pawn]
 * data to [gg.rsmod.game.model.entity.Pawn]s.
 *
 * @author Tom <rspsmods@gmail.com>
 */
class SequentialSynchronizationTask : GameTask {
    override fun execute(
        world: World,
        service: GameService,
    ) {
        val worldPlayers = world.players
        val worldNpcs = world.npcs
        val rawNpcs = world.npcs.entries
        val npcSync = NpcSynchronizationTask(rawNpcs)

        worldPlayers.forEach { p ->
            try {
                PlayerPreSynchronizationTask.run(p)
            } catch (e: Exception) {
                logger.error(e) { "Error during player pre-synchronization for ${p.username}." }
            }
        }

        for (n in worldNpcs.entries) {
            if (n != null) {
                try {
                    NpcPreSynchronizationTask.run(n)
                } catch (e: Exception) {
                    logger.error(e) { "Error during NPC pre-synchronization for ${n.id} (${n.name})." }
                }
            }
        }

        worldPlayers.forEach { p ->
            /*
             * Non-human [gg.rsmod.game.model.entity.Player]s do not need this
             * to send any synchronization data to their game-client as they do
             * not have one.
             */
            if (p.entityType.isHumanControlled && p.initiated) {
                try {
                    PlayerSynchronizationTask.run(p)
                } catch (e: Exception) {
                    logger.error(e) { "Error during player synchronization for ${p.username}." }
                }
            }
        }

        worldPlayers.forEach { p ->
            /*
             * Non-human [gg.rsmod.game.model.entity.Player]s do not need this
             * to send any synchronization data to their game-client as they do
             * not have one.
             */
            if (p.entityType.isHumanControlled && p.initiated) {
                try {
                    npcSync.run(p)
                } catch (e: Exception) {
                    logger.error(e) { "Error during NPC synchronization for ${p.username}." }
                }
            }
        }

        worldPlayers.forEach { p ->
            try {
                PlayerPostSynchronizationTask.run(p)
            } catch (e: Exception) {
                logger.error(e) { "Error during player post-synchronization for ${p.username}." }
            }
        }

        for (n in worldNpcs.entries) {
            if (n != null) {
                try {
                    NpcPostSynchronizationTask.run(n)
                } catch (e: Exception) {
                    logger.error(e) { "Error during NPC post-synchronization for ${n.id} (${n.name})." }
                }
            }
        }
    }

    companion object : KLogging()
}
