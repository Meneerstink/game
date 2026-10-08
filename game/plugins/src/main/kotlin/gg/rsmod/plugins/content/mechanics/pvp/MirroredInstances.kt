package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.instance.InstancedMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Private instanced copies of real places that keep that place's Deadman rules: a tile inside one counts as the real tile
 * it copies ([InstancedMap.sourceTile]) for [GuardedZones], so the zone box, the guards' zones and the PvP gate read the
 * same as outside. Registered like [gg.rsmod.plugins.content.areas.poh.PlayerHouse]'s house areas (the zone predicate
 * has only a tile). First user: the new-player intro's guard scene on the Edgeville / Grand Exchange border (2026-10-08).
 */
object MirroredInstances {
    private val maps = CopyOnWriteArrayList<InstancedMap>()

    fun register(map: InstancedMap) {
        maps += map
    }

    fun unregister(map: InstancedMap) {
        maps -= map
    }

    /** The real tile [tile] copies, or null outside every mirrored copy. */
    fun realTile(tile: Tile): Tile? = maps.firstOrNull { it.area.contains(tile) }?.sourceTile(tile)
}
