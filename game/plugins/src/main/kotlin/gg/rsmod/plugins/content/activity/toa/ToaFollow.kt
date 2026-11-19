package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player

/**
 * TOA-1911 punt 17 (19-11 testserver: Baboon Thralls summoned diagonally next to the player never moved and never hit; owner
 * 'baboon thralls compleet nakijken'): ToA npcs followed with npc.walkTo(target tile) - a plain walk to the player's OWN tile. A npc
 * touching the player only at a corner then plans a single diagonal step onto the player, which is never taken, so it stood still
 * for good. The engine's OSRS npc route ([gg.rsmod.game.model.path.strategy.NaivePathFindingStrategy], interaction branch; OSRS Wiki
 * "Pathfinding" follow mode) handles that case with a straight step: x first when it can step, else z. This picks that step; every
 * other case keeps walking towards the target tile (a straight approach stops beside the occupied tile, as before).
 */
object ToaFollow {
    /** Tiles between two spans on one axis: 0 = touching, > 0 = apart, -1 = overlapping. */
    private fun gap(a: Int, aSize: Int, b: Int, bSize: Int): Int = when {
        b > a + aSize - 1 -> b - (a + aSize - 1) - 1
        a > b + bSize - 1 -> a - (b + bSize - 1) - 1
        else -> -1
    }

    fun approach(npc: Npc, target: Tile, targetSize: Int = 1): Tile {
        val tile = npc.tile
        val size = npc.getSize()
        if (gap(tile.x, size, target.x, targetSize) != 0 || gap(tile.z, size, target.z, targetSize) != 0) return target
        val dx = if (target.x > tile.x + size - 1) 1 else -1
        val dz = if (target.z > tile.z + size - 1) 1 else -1
        val water = (npc.walkMask and 0x4) != 0
        val collision = npc.world.collision
        val x = if (dx > 0) Direction.EAST else Direction.WEST
        val z = if (dz > 0) Direction.NORTH else Direction.SOUTH
        // MovementQueue refuses a step into another npc's or a player's footprint: take the other straight step then (a thrall queued
        // behind the first one at the player's corner goes round instead of waiting forever).
        fun free(d: Direction) = collision.canBodyStep(tile, size, size, d, water = water) && !occupied(npc, tile.step(d))
        return when {
            free(x) -> tile.step(x)
            free(z) -> tile.step(z)
            else -> target
        }
    }

    /** Same footprint rule as MovementQueue.isEntityBlocked (private there): another npc or a player overlapping [dest]. */
    private fun occupied(npc: Npc, dest: Tile): Boolean {
        if (npc.ignoresEntityCollision) return false
        val size = npc.getSize().coerceAtLeast(1)
        val reach = 8
        for (x in dest.x - reach until dest.x + size) for (z in dest.z - reach until dest.z + size) {
            val t = Tile(x, z, dest.height)
            val chunk = npc.world.chunks.get(t, createIfNeeded = false) ?: continue
            if (chunk.getEntities<Npc>(t, EntityType.NPC).any { it !== npc && !it.ignoresEntityCollision && it.tile == t &&
                    x + it.getSize() > dest.x && z + it.getSize() > dest.z }) return true
            if (x >= dest.x && z >= dest.z && chunk.getEntities<Player>(t, EntityType.CLIENT, EntityType.PLAYER).any { it.tile == t }) return true
        }
        return false
    }
}
