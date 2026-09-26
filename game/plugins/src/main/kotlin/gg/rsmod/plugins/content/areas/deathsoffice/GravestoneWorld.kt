package gg.rsmod.plugins.content.areas.deathsoffice

import gg.rsmod.game.message.impl.HintArrowMessage
import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.GRAVESTONE_ANGEL_ATTR
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.mechanics.death.Gravestone

/**
 * The gravestone in the world: the OSRS "Grave" npc (9856, or the Angel of Death 9857) on the gravestone tile, owned by the
 * player so only they see it ("It appears to you, but it aims to be invisible to everyone else" - Death), marked with a hint
 * arrow ("Your gravestone would be marked with an arrow to help to spot it"). An owned npc is removed by the world when its
 * owner logs out; [respawn] puts it back on login. OSRS trivia: "upon unloading and reloading ... it will rotate by a random
 * multiple of 45 degrees".
 */
object GravestoneWorld {
    private val NPC = AttributeKey<Npc>()

    /** Hint arrow slot used for the gravestone. */
    private const val HINT_SLOT = 0

    fun npc(player: Player): Npc? = player.attr[NPC]?.takeIf { it.isSpawned() }

    fun respawn(player: Player) {
        despawn(player)
        val tile = Gravestone.tile(player) ?: return
        if (player.gravestone.isEmpty) return
        val id = if (player.attr[GRAVESTONE_ANGEL_ATTR] == true) DeathsOfficeArea.GRAVE_ANGEL else DeathsOfficeArea.GRAVE
        val npc = Npc(player, id, tile, player.world)
        npc.respawns = false
        npc.walkRadius = 0
        npc.setSpawnFacing(Direction.RS_ORDER.random())
        if (!player.world.spawn(npc)) return
        player.attr[NPC] = npc
        player.write(HintArrowMessage(slot = HINT_SLOT, type = HintArrowMessage.TYPE_NPC, target = npc.index))
    }

    fun despawn(player: Player) {
        val npc = player.attr[NPC]
        player.attr.remove(NPC)
        if (npc != null) {
            if (npc.isSpawned()) player.world.remove(npc)
            player.write(HintArrowMessage(slot = HINT_SLOT, type = HintArrowMessage.TYPE_CLEAR))
        }
    }
}
