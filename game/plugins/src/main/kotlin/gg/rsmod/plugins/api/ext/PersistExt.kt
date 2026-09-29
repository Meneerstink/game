package gg.rsmod.plugins.api.ext

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.service.serializer.PlayerSerializerService

/**
 * Writes this player's save file now, outside the two-minute autosave and the logout save.
 *
 * Used right after an item transfer that also lives in another store (the other party's save, the
 * Grand Exchange book): if only one side had been persisted when the server went down, the
 * transfer would replay for one player and roll back for the other - a duplicate or a loss.
 * Saving both sides at the moment of transfer closes that window. The save is atomic
 * (see JsonPlayerSerializer) and cheap enough for these rare events.
 */
/** Headless test players (conformance bots) are never written to disk - not even by a death or a trade. */
val NEVER_SAVE_ATTR = AttributeKey<Boolean>()

fun Player.persistNow() {
    val client = this as? Client ?: return
    if (attr[NEVER_SAVE_ATTR] == true) return
    world.getService(PlayerSerializerService::class.java, searchSubclasses = true)?.saveClientData(client)
}
