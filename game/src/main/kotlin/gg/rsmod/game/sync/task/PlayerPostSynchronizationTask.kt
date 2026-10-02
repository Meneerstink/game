package gg.rsmod.game.sync.task

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.region.Chunk
import gg.rsmod.game.sync.SynchronizationTask

/**
 * @author Tom <rspsmods@gmail.com>
 */
object PlayerPostSynchronizationTask : SynchronizationTask<Player> {
    override fun run(pawn: Player) {
        val oldTile = pawn.lastTile
        val changedHeight = oldTile?.height != pawn.tile.height
        val moved = oldTile == null || !oldTile.sameAs(pawn.tile) || changedHeight

        // ROOT_CAUSES #147: every move reaches the client as a delta from lastTile, so lastTile must be the tile the client drew
        // after the previous update. Anything else writing it puts the client off the server tile for good (noclip everywhere).
        val drawn = pawn.clientDrawnTile
        if (drawn != null && oldTile != null && !drawn.sameAs(oldTile)) {
            pawn.clientDesyncs++
            gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.NPCPATH) {
                "client/server position desync: ${pawn.username}'s client drew ${drawn.x},${drawn.z},${drawn.height} but the update " +
                    "was based on ${oldTile.x},${oldTile.z},${oldTile.height} (lastTile written outside the post-sync task)"
            }
        }

        if (moved) {
            val oldRegion = oldTile?.regionId ?: -1
            val currentRegion = pawn.tile.regionId
            if (oldRegion != currentRegion) {
                if (oldRegion != -1) {
                    pawn.world.plugins.executeRegionExit(pawn, oldRegion)
                }
                pawn.world.plugins.executeRegionEnter(pawn, currentRegion)
            }
            pawn.lastTile = Tile(pawn.tile)
        }
        pawn.clientDrawnTile = Tile(pawn.tile)
        gg.rsmod.game.model.Probe.endOfCycle(pawn)
        pawn.moved = false
        pawn.steps = null
        pawn.blockBuffer.clean()

        if (moved) {
            val oldChunk =
                if (oldTile !=
                    null
                ) {
                    pawn.world.chunks.get(oldTile.chunkCoords, createIfNeeded = false)
                } else {
                    null
                }
            val newChunk = pawn.world.chunks.get(pawn.tile.chunkCoords, createIfNeeded = false)
            if (newChunk != null) {
                // A zone's full state (clear + every spawned/removed object) is sent for every zone of the loaded map
                // after a login, map rebuild or plane change, and not again while the map stays: re-sending it on every
                // step made the client clear and redraw every changed object each step - the player-owned house
                // flickered (owner 2026-09-19). Live changes reach the whole map through Chunk.sendUpdate, so a
                // building of spawned objects is complete from any distance (Royal Hall, owner 2026-09-26).
                if (oldTile == null || changedHeight || pawn.regionRebuilt) {
                    pawn.lastKnownRegionBase?.let { base ->
                        Chunk.buildAreaChunks(base.x, base.z).forEach { coords ->
                            pawn.world.chunks.get(coords, createIfNeeded = false)?.sendUpdates(pawn)
                        }
                    }
                }
                if (!changedHeight) {
                    if (oldChunk != null) {
                        pawn.world.plugins.executeChunkExit(pawn, oldChunk.coords.hashCode())
                    }
                    pawn.world.plugins.executeChunkEnter(pawn, newChunk.coords.hashCode())
                }
            }
            pawn.world.plugins.simplePolygonAreas.forEach {
                val hash = it.hashCode()
                val inside = pawn.tile.regionId in it.associatedRegionIds && it.containsTile(pawn.tile)
                if (inside) {
                    // Finding 6 fix: only fire on a real outside->inside transition (first tick
                    // `inside` becomes true), not on every subsequent tile step still inside -
                    // see the `insidePolygonAreas` doc comment on Player for the interaction bug
                    // this caused.
                    if (pawn.insidePolygonAreas.add(hash)) {
                        pawn.world.plugins.executeSimplePolygonAreaEnter(pawn, hash)
                    }
                } else {
                    pawn.insidePolygonAreas.remove(hash)
                }
            }
        }
    }
}
