package gg.rsmod.plugins.content.areas.wilderness.bosses

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Npc

/** Delayed ground attacks belong to one life, even when respawn reuses the same Npc object. */
object WildernessBossLife {
    private val LIFE = AttributeKey<Any>()

    fun token(npc: Npc): Any = npc.attr[LIFE] ?: Any().also { npc.attr[LIFE] = it }

    fun active(npc: Npc, token: Any): Boolean = npc.isSpawned() && !npc.isDead() && npc.attr[LIFE] === token

    /** A failed world insert is retryable; neither the completion callback nor a second insert may run twice. */
    class PendingSpawn(private val insert: () -> Boolean, private val ready: () -> Unit) {
        private var complete = false
        fun attempt(): Boolean {
            if (complete) return true
            if (!insert()) return false
            complete = true
            ready()
            return true
        }
    }

    private val SUCCESSOR = AttributeKey<Npc>()
    private val RESPAWN_PENDING = AttributeKey<Boolean>()

    fun successor(origin: Npc, create: () -> Npc, ready: (Npc) -> Unit = {}): Npc? {
        origin.attr[SUCCESSOR]?.let { return it.takeIf { n -> n.isSpawned() } }
        val next = create()
        origin.attr[SUCCESSOR] = next
        val pending = PendingSpawn({ origin.world.spawn(next) }) { ready(next) }
        if (pending.attempt()) return next
        gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.COMBAT) { "wildy boss successor ${origin.id}->${next.id} insert failed; retrying once per tick" }
        origin.world.queue {
            do { wait(1) } while (!pending.attempt())
        }
        return null
    }

    fun respawn(origin: Npc, id: Int, tile: gg.rsmod.game.model.Tile, walkRadius: Int, delay: Int) {
        if (origin.attr[RESPAWN_PENDING] == true) return
        origin.attr[RESPAWN_PENDING] = true
        origin.world.queue {
            wait(delay)
            successor(origin, { Npc(id, gg.rsmod.game.model.Tile(tile), origin.world).also { it.walkRadius = walkRadius } })
        }
    }
}
