package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.queue.QueueTask

/** Owns only this room's transient work. Later room cards spawn and queue through this scope. */
class ToaRoomScope(val raid: ToaRaid, val run: ToaRoomRun) {
    private var closed = false
    private val npcs = mutableListOf<Npc>()
    private val objects = mutableListOf<Pair<GameObject, GameObject?>>()
    private val ownedObjects = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<GameObject, Boolean>())
    private val cleanup = mutableListOf<() -> Unit>()
    /** Every npc this room spawned (killed ones too, until close) - their damage maps give the room points (ToaChest.creditRoom). */
    val spawned: List<Npc> get() = npcs.toList()
    val active: Boolean get() = !closed && raid.progress.current === run && run.status == ToaRoomStatus.RUNNING

    fun spawn(npc: Npc): Boolean {
        check(active && npc.world === raid.world && raid.map.area.contains(npc.tile))
        if (!raid.world.spawn(npc)) return false
        npcs += npc
        return true
    }

    fun spawn(obj: GameObject) {
        check(active && raid.map.area.contains(obj.tile))
        val original = raid.world.chunks.getOrCreate(obj.tile)
            .getEntities<GameObject>(obj.tile, EntityType.STATIC_OBJECT, EntityType.DYNAMIC_OBJECT)
            .firstOrNull { raid.world.locLayer(it.type) == raid.world.locLayer(obj.type) }
            //A previous state of this scope is transient too. Restoring a COPY of it during close leaves an unowned loc:
            //GameObject has identity equality, so a later remove(previousState) cannot remove that copy.
            ?.takeUnless { it in ownedObjects }
        raid.world.spawn(obj)
        ownedObjects += obj
        objects += obj to original
    }

    fun onClose(action: () -> Unit) { check(!closed); cleanup += action }

    fun queue(logic: suspend QueueTask.() -> Unit) {
        check(active)
        raid.world.queue roomWork@ {
            if (!active) return@roomWork
            onClose { terminate() }
            logic()
        }
    }

    fun close() {
        if (closed) return
        closed = true
        cleanup.toList().forEach { it() }
        npcs.forEach { raid.world.remove(it) }
        objects.asReversed().forEach { (obj, original) ->
            raid.world.remove(obj)
            if (original != null) raid.world.spawn(DynamicObject(original))
        }
        cleanup.clear(); npcs.clear(); objects.clear(); ownedObjects.clear()
    }
}
