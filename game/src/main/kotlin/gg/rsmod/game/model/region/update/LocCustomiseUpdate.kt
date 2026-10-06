package gg.rsmod.game.model.region.update

import gg.rsmod.game.message.Message
import gg.rsmod.game.message.impl.LocCustomiseMessage
import gg.rsmod.game.model.entity.GameObject

/**
 * The per-instance fit ([GameObject.fit]) of a spawned loc, sent right after its [LocAddChangeUpdate] - live and to
 * players who enter the chunk later, so every client shows the same placement.
 */
class LocCustomiseUpdate(
    override val type: EntityUpdateType,
    override val entity: GameObject,
) : EntityUpdate<GameObject>(type, entity) {
    override fun toMessage(): Message =
        LocCustomiseMessage(
            entity.id,
            entity.settings.toInt(),
            ((entity.tile.chunkOffsetX) shl 4) or (entity.tile.chunkOffsetZ and 0x7),
            entity.fit,
        )
}
