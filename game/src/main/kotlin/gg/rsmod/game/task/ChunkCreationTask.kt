package gg.rsmod.game.task

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.service.GameService
import mu.KLogging

/**
 * A [GameTask] responsible for creating any non-existent [gg.rsmod.game.model.region.Chunk]
 * that players are standing on as well as registering and de-registering the
 * player from the respective [gg.rsmod.game.model.region.Chunk]s.
 *
 * @author Tom <rspsmods@gmail.com>
 */
class ChunkCreationTask : GameTask {
    override fun execute(
        world: World,
        service: GameService,
    ) {
        world.players.forEach { p ->
            try {
                p.changeChunks(world, createChunkIfNeeded = true)
            } catch (e: Throwable) {
                // Audit T-01: catch every non-fatal throwable, not only Exception.
                e.rethrowIfFatal()
                logger.error("Error updating chunks for player ${p.username}.", e)
            }
        }

        world.npcs.forEach { npc ->
            try {
                // Breach conformance 2026-09-27: an npc no player can see still walks (random walk, walking home), but its tile
                // registration only followed it while it was active - collision lookups (MovementQueue.isEntityBlocked, breach
                // landings) then saw it on its old tile and other npcs walked or landed straight through it. Every npc whose tile
                // changed is re-registered; unmoved npcs return at once in changeChunks.
                if (npc.isActive() || npc.lastChunkTile?.sameAs(npc.tile) == false) {
                    npc.changeChunks(world, createChunkIfNeeded = CREATE_CHUNK_FOR_NPC)
                }
            } catch (e: Throwable) {
                // Audit T-01: catch every non-fatal throwable, not only Exception.
                e.rethrowIfFatal()
                logger.error("Error updating chunks for npc $npc.", e)
            }
        }
    }

    private fun <T : Pawn> T.changeChunks(
        world: World,
        createChunkIfNeeded: Boolean,
    ) {
        val lastTile = lastChunkTile
        val sameTile = lastTile?.sameAs(tile) ?: false

        if (sameTile) {
            return
        }

        if (lastTile != null) {
            world.chunks.get(lastTile)?.removeEntity(world, this, lastTile)
        }

        world.chunks.get(tile, createIfNeeded = createChunkIfNeeded)?.addEntity(world, this, tile)
        lastChunkTile = Tile(tile)
    }

    companion object : KLogging() {
        /**
         * Flag that specifies if [gg.rsmod.game.model.region.Chunk] should be
         * created if an npc is on it and it doesn't already exist.
         */
        private const val CREATE_CHUNK_FOR_NPC = false
    }
}
