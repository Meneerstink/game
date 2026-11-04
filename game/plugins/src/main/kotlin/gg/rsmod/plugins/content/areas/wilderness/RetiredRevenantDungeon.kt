package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.collision.Landing

/** N527 owner 2026-11-02: retire the 667 dungeon, retaining NPC definitions used by WildernessBreach. */
object RetiredRevenantDungeon {
    val REGIONS = setOf(12190, 12445, 12446)
    val OBJECTS = setOf(20599, 20600, 18341, 18342)
    val SAFE_EXIT = Tile(3151, 3635) // Wiki Ring of dueling -> Ferox Enclave

    fun contains(tile: Tile): Boolean = tile.regionId in REGIONS

    fun evacuate(player: Player): Boolean {
        if (!contains(player.tile)) return false
        player.moveTo(Landing.resolve(player.world.collision, SAFE_EXIT))
        return true
    }

    /** Hide old access in the server scene; physical map removal remains Claude's cache task. */
    fun removeAccess(world: World) {
        val regions = REGIONS + setOf(Tile(3039, 3765).regionId, Tile(3071, 3649).regionId)
        regions.forEach { region ->
            val baseX = (region shr 8) * 64
            val baseZ = (region and 255) * 64
            for (x in baseX until baseX + 64) for (z in baseZ until baseZ + 64) {
                val tile = Tile(x, z)
                world.chunks.get(tile, createIfNeeded = true)!!
                    .getEntities<GameObject>(tile, EntityType.STATIC_OBJECT, EntityType.DYNAMIC_OBJECT)
                    .filter { it.id in OBJECTS }.forEach { world.remove(it) }
            }
        }
    }
}
