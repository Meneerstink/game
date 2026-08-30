package gg.rsmod.plugins.content.cmd

import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Npc

/**
 * R12.2 `::clearspawns`: tracks exactly which npcs/objects were placed via the owner test
 * commands (`::npc`, `::obj`, `::tempobj`) so a bulk clear can remove only those - never real
 * world content. Missing tagging was the reason `clearspawns` wasn't built before; this is
 * that tagging, not a design change to what gets removed.
 *
 * Session-only (in-memory, not persisted) - intentional: these are always throwaway test
 * spawns, a restart is meant to lose the list, not require the owner to keep a persisted
 * cleanup log around.
 */
object TestSpawnRegistry {
    private val npcs = HashSet<Npc>()
    private val objects = HashSet<GameObject>()

    fun trackNpc(npc: Npc) {
        npcs.add(npc)
    }

    fun trackObject(obj: GameObject) {
        objects.add(obj)
    }

    /** Removes only registered test spawns still actually present in [world]. Returns
     * (npcsRemoved, objectsRemoved). */
    fun clear(world: World): Pair<Int, Int> {
        var npcCount = 0
        val npcIterator = npcs.iterator()
        while (npcIterator.hasNext()) {
            val npc = npcIterator.next()
            if (world.npcs.contains(npc)) {
                world.remove(npc)
                npcCount++
            }
            npcIterator.remove()
        }

        var objCount = 0
        val objIterator = objects.iterator()
        while (objIterator.hasNext()) {
            val obj = objIterator.next()
            world.remove(obj)
            objCount++
            objIterator.remove()
        }

        return npcCount to objCount
    }
}
