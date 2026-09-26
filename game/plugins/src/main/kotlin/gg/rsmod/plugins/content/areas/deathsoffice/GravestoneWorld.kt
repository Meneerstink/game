package gg.rsmod.plugins.content.areas.deathsoffice

import gg.rsmod.game.message.impl.HintArrowMessage
import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.GRAVESTONE_ANGEL_ATTR
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.mechanics.death.Gravestone

/**
 * The gravestone in the world: the OSRS "Grave" npc (9856, or the Angel of Death 9857) on the gravestone tile. Owner decision
 * 2026-09-26 (RS 2009 gravestones): every player sees it and may Bless or Repair it, only its owner can Loot it, and only the
 * owner gets the hint arrow ("Your gravestone would be marked with an arrow to help to spot it"). The npc is public, so the
 * owner is kept on the npc ([ownerOf]); it is removed when its owner logs out and [respawn] puts it back on login. OSRS
 * trivia: "upon unloading and reloading ... it will rotate by a random multiple of 45 degrees".
 */
object GravestoneWorld {
    private val NPC = AttributeKey<Npc>()
    private val OWNER = AttributeKey<Player>()

    /** The player whose gravestone [npc] is, while they are online. */
    fun ownerOf(npc: Npc): Player? = npc.attr[OWNER]?.takeIf { it.isOnline && npc(it) === npc }

    /** Hint arrow slot used for the gravestone. */
    private const val HINT_SLOT = 0

    fun npc(player: Player): Npc? = player.attr[NPC]?.takeIf { it.isSpawned() }

    fun respawn(player: Player) {
        despawn(player)
        val tile = Gravestone.tile(player) ?: return
        if (player.gravestone.isEmpty) return
        val id = if (player.attr[GRAVESTONE_ANGEL_ATTR] == true) DeathsOfficeArea.GRAVE_ANGEL else DeathsOfficeArea.GRAVE
        val npc = Npc(id, tile, player.world)
        npc.attr[OWNER] = player
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
