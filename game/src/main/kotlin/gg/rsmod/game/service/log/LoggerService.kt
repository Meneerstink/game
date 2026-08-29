package gg.rsmod.game.service.log

import gg.rsmod.game.event.Event
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.service.Service


/**
 * A [Service] responsible for logging in-game events when requested.
 *
 * Keep in mind that all methods are called from the game-thread. Any expensive
 * IO operation needing to be done should be queued independently from the
 * logger using the data provided by the logger.
 *
 * @author Tom <rspsmods@gmail.com>
 */

interface LoggerService : Service {
    fun logPacket(
        client: Client,
        message: String,
    )

    fun logLogin(player: Player)

    fun logPublicChat(
        player: Player,
        message: String,
    )

    fun logPrivateChat(
        fromPlayer: Player,
        toPlayer: Player,
        message: String
    )

    fun logClanChat(
        player: Player,
        clan: String,
        message: String,
    )

    fun logCommand(
        player: Player,
        command: String,
        vararg args: String,
    )

    fun logItemDrop(
        player: Player,
        item: Item,
        slot: Int,
    )

    fun logItemPickUp(
        player: Player,
        item: Item,
    )

    fun logNpcKill(
        player: Player,
        npc: Npc,
    )

    fun logPlayerKill(
        killer: Player,
        killed: Player,
    )

    /**
     * Records the outcome of a player's death resolution: whether it was a
     * Wilderness/PvP death or a PvM/safe death, who (if anyone) is credited as
     * the killer, and how many items were protected vs. lost.
     */
    fun logPlayerDeath(
        player: Player,
        killer: Player?,
        context: String,
        protectedItemCount: Int,
        lostItemCount: Int,
    )

    /**
     * Records a Wilderness/PvP death's items being transferred to the ground as
     * loot, and who (if anyone) they are owned by.
     */
    fun logDeathLootTransfer(
        player: Player,
        killer: Player?,
        items: List<Item>,
    )

    /**
     * Records a PvM/safe death's non-protected items being moved into
     * death-recovery state, pending reclaim.
     */
    fun logDeathRecoveryCreated(
        player: Player,
        itemCount: Int,
        expiresAtMs: Long,
        reclaimFee: Int,
    )

    /**
     * Records a successful reclaim of a player's death-recovery items.
     */
    fun logDeathReclaim(
        player: Player,
        feePaid: Int,
        itemCount: Int,
    )

    fun logEvent(
        pawn: Pawn,
        event: Event,
    )
}
