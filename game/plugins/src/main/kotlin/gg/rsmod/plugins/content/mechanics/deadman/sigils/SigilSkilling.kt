package gg.rsmod.plugins.content.mechanics.deadman.sigils

import gg.rsmod.game.model.container.ItemTransaction
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.filterableMessage

/**
 * PROJECT48 SIGILS - the gathering sigils (OSRS Wiki Deadman: Armageddon/Sigils): S03 Enhanced Harvest (3x resources), S14 the Forager
 * (2x; no stacking with Enhanced Harvest), S43 Storage (90% of new resources go straight to the bank) and S58 Remote Storage (all of them).
 * Mining, Fishing, Woodcutting and Farming give their resource through [give] instead of `inventory.add`.
 */
object SigilSkilling {
    /** The multiplier of the gathered resource: 3 (Enhanced Harvest), 2 (the Forager) or 1. */
    fun multiplier(player: Player): Int =
        when {
            Sigils.effective(player, Sigil.ENHANCED_HARVEST) -> 3
            Sigils.effective(player, Sigil.THE_FORAGER) -> 2
            else -> 1
        }

    /** S10 Sigil of the Abyss: 5x as many runes when Runecrafting. */
    fun runecraftMultiplier(player: Player): Int = if (Sigils.effective(player, Sigil.THE_ABYSS)) 5 else 1

    /** Whether this gathered resource goes to the bank (S58 always, S43 in 90% of the cases). */
    fun toBank(player: Player): Boolean =
        Sigils.effective(player, Sigil.REMOTE_STORAGE) ||
            (Sigils.effective(player, Sigil.STORAGE) && player.world.randomDouble() < 0.90)

    /**
     * Gives [amount] of [itemId] as the gathered resource of the player: multiplied by the harvest sigils, then sent to the bank or the
     * inventory. Returns the inventory transaction (a bank delivery counts as a complete one); when the bank cannot take it the
     * inventory is used.
     */
    fun give(
        player: Player,
        itemId: Int,
        amount: Int = 1,
    ): ItemTransaction {
        if (player.attuned().isEmpty()) return player.inventory.add(itemId, amount)
        val total = amount * multiplier(player)
        if (toBank(player)) {
            val banked = player.bank.add(itemId, total)
            if (banked.hasSucceeded()) {
                player.filterableMessage("Your sigil sends the resources to your bank.")
                return ItemTransaction(total, total, emptyList())
            }
        }
        return player.inventory.add(itemId, total)
    }

    private fun Player.attuned() = Sigils.attuned(this)
}
