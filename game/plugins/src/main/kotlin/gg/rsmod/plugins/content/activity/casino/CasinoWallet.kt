package gg.rsmod.plugins.content.activity.casino

import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items

/**
 * Every coin that enters or leaves a casino game passes through here.
 *
 * Owner 2026-09-20: "Server-authoritative wagers/payouts; client must never decide results" and "Atomic
 * transactions: no dupes, double bets, disconnect exploits or packet abuse".
 *
 * The wager currency is ordinary coins, which is what "our existing economy" means on this server: the Grand
 * Exchange, shops, Skully and the Duel Arena all settle in coins, so casino winnings stay inside the same economy
 * instead of minting a parallel one. The three point currencies in `StoreCatalogue` (Donator, Deadman, Loyalty) are
 * deliberately *not* wagerable - they are account-bound by design and cannot be traded away, so letting them move
 * between accounts through a game would break that guarantee.
 *
 * Two invariants hold everywhere:
 *
 *  - **A stake is debited before a result is computed.** [withdraw] is all-or-nothing, so a player can never be in
 *    a round they have not paid for, and a duplicated or replayed "place bet" packet either finds the coins and
 *    debits them again as a second round, or fails - it can never create a free round.
 *  - **A payout is never silently lost.** Coins cap at [MAX_STACK] per stack, so [deposit] falls back to the bank
 *    and then to a player-owned ground item rather than dropping the remainder on the floor of a `null` check.
 */
object CasinoWallet {
    /** Coins - `Items.COINS_995`. */
    const val CURRENCY: Int = Items.COINS_995

    /** A single coin stack cannot exceed the container's signed 32-bit amount. */
    const val MAX_STACK: Long = Int.MAX_VALUE.toLong()

    /** Smallest accepted wager; anything under this is not worth a round of interface traffic. */
    const val MIN_WAGER: Long = 1_000

    /**
     * Largest accepted wager. A win must still fit the coin stack, so the real ceiling is per-game: a game asks
     * [fitsPayout] with the biggest payout the round could produce before it accepts the bet.
     */
    const val MAX_WAGER: Long = 100_000_000

    /** Coins the player is holding in their inventory. */
    fun balance(player: Player): Long = player.inventory.getItemCount(CURRENCY).toLong()

    /** True when [payout] can still be handed over as coins at all. */
    fun fitsPayout(payout: Long): Boolean = payout in 0..MAX_STACK

    /**
     * Debits [amount] coins, all or nothing.
     *
     * Returns false and changes nothing when the player cannot cover it; the caller must treat that as "the round
     * did not start". `assureFullRemoval` is what makes this atomic - a partial removal would leave the player
     * short with no round to show for it.
     */
    fun withdraw(
        player: Player,
        amount: Long,
    ): Boolean {
        if (amount <= 0 || amount > MAX_STACK) {
            return false
        }
        if (balance(player) < amount) {
            return false
        }
        return player.inventory
            .remove(CURRENCY, amount.toInt(), assureFullRemoval = true)
            .hasSucceeded()
    }

    /**
     * Credits [amount] coins and guarantees none are lost.
     *
     * Inventory first, then the bank for whatever would overflow the inventory coin stack, then a ground item owned
     * by the player as the last resort. Returns where the coins ended up so the caller can tell the player.
     */
    fun deposit(
        player: Player,
        amount: Long,
    ): Payout {
        if (amount <= 0) {
            return Payout(0, 0, 0)
        }
        var left = amount
        var toInventory = 0L
        var toBank = 0L

        val invPortion = minOf(left, MAX_STACK)
        if (invPortion > 0) {
            val added = player.inventory.add(CURRENCY, invPortion.toInt()).completed.toLong()
            toInventory += added
            left -= added
        }

        if (left > 0) {
            val bankPortion = minOf(left, MAX_STACK)
            val added = player.bank.add(CURRENCY, bankPortion.toInt()).completed.toLong()
            toBank += added
            left -= added
        }

        var dropped = 0L
        while (left > 0) {
            val chunk = minOf(left, MAX_STACK)
            player.world.spawn(GroundItem(CURRENCY, chunk.toInt(), player.tile, player))
            dropped += chunk
            left -= chunk
        }
        return Payout(toInventory, toBank, dropped)
    }

    /** Refunds a stake that a round could not use, on the same no-loss guarantee as [deposit]. */
    fun refund(
        player: Player,
        amount: Long,
    ): Payout = deposit(player, amount)

    /** The coin count a container can still accept, used to warn before a bet rather than after a win. */
    fun freeInventorySlots(player: Player): Int = player.inventory.freeSlotCount

    /** Where a payout landed. */
    data class Payout(
        val inventory: Long,
        val bank: Long,
        val ground: Long,
    ) {
        val total: Long
            get() = inventory + bank + ground
    }

    /*
     * Number formatting for the whole casino lives here, and every call pins [java.util.Locale.ENGLISH].
     *
     * `String.format` without a locale uses the JVM default, which on this server's host (a Dutch Windows) groups
     * with `.` and puts a comma where the decimal point belongs: the dice screen would have read "50,01%" and
     * "1,9800x", and the audit log would have written a comma into a number other tools parse. RuneScape's own
     * interfaces are English, so the locale is fixed rather than inherited.
     */

    /** A readable coin amount, e.g. `1,250,000`. */
    fun format(amount: Long): String = String.format(java.util.Locale.ENGLISH, "%,d", amount)

    /** A win chance as a percentage, e.g. `50.01%`. */
    fun percent(value: Double): String = String.format(java.util.Locale.ENGLISH, "%.2f%%", value)

    /**
     * A payout multiplier, e.g. `1.98x`.
     *
     * Two decimals, the convention every dice and mines site uses, because the multiplier is a headline number to
     * read at a glance; the exact coin payout is shown next to it and is what the player is actually owed.
     */
    fun multiplier(value: Double): String = String.format(java.util.Locale.ENGLISH, "%.2fx", value)

    /** The stake-and-payout item, for callers that need an [Item] rather than a count. */
    fun coins(amount: Int): Item = Item(CURRENCY, amount)
}
