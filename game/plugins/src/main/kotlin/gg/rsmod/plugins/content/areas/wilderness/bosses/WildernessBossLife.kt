package gg.rsmod.plugins.content.areas.wilderness.bosses

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Npc

/** Delayed ground attacks belong to one life, even when respawn reuses the same Npc object. */
object WildernessBossLife {
    private val LIFE = AttributeKey<Any>()

    fun token(npc: Npc): Any = npc.attr[LIFE] ?: Any().also { npc.attr[LIFE] = it }

    fun active(npc: Npc, token: Any): Boolean = npc.isSpawned() && !npc.isDead() && npc.attr[LIFE] === token
}
