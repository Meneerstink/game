package gg.rsmod.game.sync.task

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.sync.SynchronizationTask

/**
 * @author Tom <rspsmods@gmail.com>
 */
object PlayerPostSynchronizationTask : SynchronizationTask<Player> {
    override fun run(pawn: Player) {
        val oldTile = pawn.lastTile
        val changedHeight = oldTile?.height != pawn.tile.height
        val moved = oldTile == null || !oldTile.sameAs(pawn.tile) || changedHeight

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
                val newSurroundings = newChunk.coords.getSurroundingCoords()
                // A zone's full state (clear + every spawned/removed object) is only sent when the zone newly enters
                // view, or after a map rebuild / plane change. Re-sending it on every step made the client clear and
                // redraw every changed object each step - the player-owned house flickered (owner 2026-09-19).
                val alreadyVisible =
                    if (oldTile == null || changedHeight || pawn.regionRebuilt) {
                        emptySet()
                    } else {
                        oldTile.chunkCoords.getSurroundingCoords()
                    }
                newSurroundings.filter { it !in alreadyVisible }.forEach { coords ->
                    val chunk = pawn.world.chunks.get(coords, createIfNeeded = false) ?: return@forEach
                    chunk.sendUpdates(pawn)
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
