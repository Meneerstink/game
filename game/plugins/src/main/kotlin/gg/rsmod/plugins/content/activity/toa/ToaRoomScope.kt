package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.queue.QueueTask

/** Owns only this room's transient work. Later room cards spawn and queue through this scope. */
class ToaRoomScope(val raid: ToaRaid, val run: ToaRoomRun) {
    private var closed = false
    private val npcs = mutableListOf<Npc>()
    private val objects = mutableListOf<GameObject>()
    private val cleanup = mutableListOf<() -> Unit>()
    val active: Boolean get() = !closed && raid.progress.current === run && run.status == ToaRoomStatus.RUNNING

    fun spawn(npc: Npc): Boolean {
        check(active && npc.world === raid.world && raid.map.area.contains(npc.tile))
        if (!raid.world.spawn(npc)) return false
        npcs += npc
        return true
    }

    fun spawn(obj: GameObject) {
        check(active && raid.map.area.contains(obj.tile))
        raid.world.spawn(obj)
        objects += obj
    }

    fun onClose(action: () -> Unit) { check(!closed); cleanup += action }

    fun queue(logic: suspend QueueTask.() -> Unit) {
        check(active)
        raid.world.queue {
            if (!active) return@queue
            onClose { terminate() }
            logic()
        }
    }

    fun close() {
        if (closed) return
        closed = true
        cleanup.toList().forEach { it() }
        npcs.forEach { raid.world.remove(it) }
        objects.forEach { raid.world.remove(it) }
        cleanup.clear(); npcs.clear(); objects.clear()
    }
}
