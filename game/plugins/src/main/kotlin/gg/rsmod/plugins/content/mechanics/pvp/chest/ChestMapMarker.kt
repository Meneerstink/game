package gg.rsmod.plugins.content.mechanics.pvp.chest

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.hud.ClientHud

/**
 * N273 (owner 2026-10-24): the Deadman's Chest players can loot is a blue arrow on every player's world map, as long as it stands
 * (client ChestWorldMapMarker). Checked at the end of every cycle with the Deadman HUD (client_hud.plugin.kts): the payload is sent
 * only when it changes - the chest appears ("chest|x|z|plane"), it is looted or gone ("chest|") - and again after a login, when
 * the client starts empty.
 */
object ChestMapMarker {
    private val SENT = AttributeKey<String>()

    fun payload(chest: DeadmanChestWorld.Chest?): String = if (chest == null) "chest|" else "chest|${chest.tile.x}|${chest.tile.z}|${chest.tile.height}"

    fun sync(player: Player) {
        val payload = payload(DeadmanChestWorld.activeTimed())
        if (player.attr[SENT] == payload) return
        player.attr[SENT] = payload
        ClientHud.send(player, payload)
    }

    /** A login starts a client without the marker: the next [sync] sends it again. */
    fun reset(player: Player) {
        player.attr.remove(SENT)
    }
}
