package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.model.attr.DEATH_RECOVERY_EXPIRY_ATTR
import gg.rsmod.game.model.attr.DEATH_RECOVERY_FEE_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.service.log.LoggerService
import gg.rsmod.plugins.api.cfg.Items

/**
 * The outcome of a [DeathRecoveryService.reclaim] attempt.
 */
sealed class DeathReclaimOutcome {
    object NothingToReclaim : DeathReclaimOutcome()

    object InsufficientFunds : DeathReclaimOutcome()

    data class Reclaimed(val itemCount: Int, val feePaid: Int) : DeathReclaimOutcome()
}

/**
 * Reclaim logic for a player's [Player.deathRecovery] container. Kept
 * separate from `death.plugin.kts`'s `::reclaim` command so it can be unit
 * tested directly against a mocked [Player], independent of command
 * dispatch.
 */
object DeathRecoveryService {
    /**
     * Attempts to reclaim every item in [player]'s death-recovery container
     * for the coin fee stored in
     * [gg.rsmod.game.model.attr.DEATH_RECOVERY_FEE_ATTR] (falling back to
     * [DeathRecoveryConfig.PLACEHOLDER]'s fee if that attribute is somehow
     * absent while the container is non-empty).
     *
     * No partial mutation on insufficient funds: if [player] cannot afford
     * the fee, no coins are removed and no recovered item is moved -
     * [DeathReclaimOutcome.InsufficientFunds] is returned and state is left
     * completely unchanged. Once the fee is confirmed affordable it is
     * charged up-front; recovered items are then moved into the inventory on
     * a best-effort basis so a full inventory doesn't lose an item outright -
     * anything that doesn't fit is simply left in death-recovery for a later
     * reclaim attempt.
     *
     * Idempotent: calling this again once death-recovery is already empty
     * returns [DeathReclaimOutcome.NothingToReclaim] without charging a fee
     * or mutating anything further - a repeated reclaim cannot duplicate
     * items or charge twice.
     */
    fun reclaim(
        player: Player,
        coinItemId: Int = Items.COINS_995,
        logger: LoggerService? = null,
    ): DeathReclaimOutcome {
        if (player.deathRecovery.isEmpty) {
            return DeathReclaimOutcome.NothingToReclaim
        }

        val fee = player.attr[DEATH_RECOVERY_FEE_ATTR] ?: DeathRecoveryConfig.PLACEHOLDER.reclaimFee
        if (fee > 0) {
            // Checked and removed against the inventory specifically (not
            // Player.hasItem's all-containers check) so affordability and the
            // actual deduction agree; assureFullRemoval guards against a
            // partial coin deduction if inventory contents change between the
            // check and the remove call.
            if (player.inventory.getItemCount(coinItemId) < fee) {
                return DeathReclaimOutcome.InsufficientFunds
            }
            val paid = player.inventory.remove(Item(coinItemId, fee), assureFullRemoval = true)
            if (paid.completed < fee) {
                return DeathReclaimOutcome.InsufficientFunds
            }
        }

        // A paid recovery batch stays paid when inventory space requires another visit.
        player.attr[DEATH_RECOVERY_FEE_ATTR] = 0
        var reclaimedCount = 0
        for (slot in 0 until player.deathRecovery.capacity) {
            val item = player.deathRecovery[slot] ?: continue
            val transaction = player.inventory.add(item.id, item.amount, assureFullInsertion = false)
            if (transaction.completed > 0) {
                player.deathRecovery[slot] = if (transaction.completed == item.amount) null
                    else Item(item.id, item.amount - transaction.completed).copyAttr(item)
                reclaimedCount++
            }
        }

        if (player.deathRecovery.isEmpty) {
            player.attr.remove(DEATH_RECOVERY_EXPIRY_ATTR)
            player.attr.remove(DEATH_RECOVERY_FEE_ATTR)
        }

        logger?.logDeathReclaim(player, fee, reclaimedCount)
        return DeathReclaimOutcome.Reclaimed(reclaimedCount, fee)
    }
}
