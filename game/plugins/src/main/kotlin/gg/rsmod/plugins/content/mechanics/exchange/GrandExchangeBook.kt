package gg.rsmod.plugins.content.mechanics.exchange

/**
 * Provisional, configurable system-liquidity whitelist (PROJECT_PLAN.md SS7 -
 * "exact liquidity whitelist" is explicitly OPEN). Buy offers for these items
 * that can't be fully matched against real player sell offers are topped up
 * from an unlimited system source at the listed unit price, so the low-
 * population Alpha market never starves for basic supplies. Deliberately
 * restricted to cheap, ordinary tool/material items - never food, potions,
 * runes, or anything that could substitute for real supply-and-demand;
 * never boss uniques or high-end equipment, per the confirmed restriction.
 * The system never buys (no matching sell-side liquidity), so it can only
 * ever be a supply source, not a sink for player-farmed items.
 */
object GeSystemLiquidity {
    /**
     * Owner 2026-09-20: "make every item we have in game buyable in the grand exchange so it sells for mid price
     * also" (and, when asked whether the house should also buy back, "all").
     *
     * With this on, the whitelist below stops being the limit: [GrandExchangeService] hands the matcher the item's
     * guide price for *every* item, so an unmatched buy is always filled from the house and an unmatched sell is
     * always bought by the house. The GE becomes a guaranteed two-way market at the guide price instead of a
     * thin player-to-player book.
     *
     * The one thing this must not become is a money printer, and the prices are chosen so it cannot be:
     *  - the house SELLS at the guide price (the buyer's overbid is refunded), and
     *  - the house BUYS at the seller's asking price, but only when that ask is at or below the guide price.
     * The best possible round trip is therefore buy at guide, sell at guide - exactly break-even. Selling at the
     * top of the allowed band (`guide * 1.05`) is simply never taken by the house.
     *
     * What it does change, deliberately: every item is infinitely available, so drop rarity no longer gates
     * supply. Set this to false to go back to the old cheap-materials-only whitelist.
     */
    const val ALL_ITEMS_AT_GUIDE_PRICE = true

    val UNIT_PRICE: Map<Int, Int> =
        mapOf(
            gg.rsmod.plugins.api.cfg.Items.FEATHER to 2,
            gg.rsmod.plugins.api.cfg.Items.BUCKET to 3,
            gg.rsmod.plugins.api.cfg.Items.EMPTY_POT to 2,
            gg.rsmod.plugins.api.cfg.Items.VIAL to 3,
            gg.rsmod.plugins.api.cfg.Items.KNIFE to 8,
            gg.rsmod.plugins.api.cfg.Items.CHISEL to 8,
            gg.rsmod.plugins.api.cfg.Items.NEEDLE to 2,
            gg.rsmod.plugins.api.cfg.Items.THREAD to 2,
            gg.rsmod.plugins.api.cfg.Items.HAMMER to 8,
            gg.rsmod.plugins.api.cfg.Items.TINDERBOX_590 to 8,
        )
}

/**
 * A single fill event produced while matching an offer, describing exactly
 * which two offers (or an offer and the system) exchanged how much at what
 * price - callers use this to move real coins/items into escrow.
 */
data class GeFill(
    val buyOfferId: Long?,
    val sellOfferId: Long?,
    val quantity: Int,
    val unitPrice: Int,
    val fromSystem: Boolean,
)

/**
 * Pure, side-effect-free Grand Exchange matching engine: given the current
 * book and a newly-submitted offer, decides how it fills against resting
 * opposite-side offers (best price first, then oldest first) and, for a buy
 * order only, against system liquidity for any remainder. Mutates offer
 * quantities/escrow directly (there's no separate "apply" step, unlike
 * `DeathResolver`) since a partially-filled offer's state IS the escrow
 * ledger - callers only need to react to the returned [GeFill] list to know
 * what to persist/notify.
 *
 * RCV-011: every fill is capped by the buyer's [GeBuyAllowance] (Void `GrandExchange.exchange`: `traded` is coerced
 * to the buyer's remaining limit and nothing trades at 0). A new buy offer stops at its limit and rests; a new sell
 * offer skips a buyer who is at their limit and moves on to the next one.
 */
object GrandExchangeBook {
    /**
     * [systemPrice] is the house price for an item, or null when the house does not deal in it. The default keeps
     * the old cheap-materials whitelist so the pure unit tests stay meaningful; [GrandExchangeService] passes the
     * item's guide price for every item (see [GeSystemLiquidity.ALL_ITEMS_AT_GUIDE_PRICE]).
     */
    fun match(
        book: List<GrandExchangeOffer>,
        newOffer: GrandExchangeOffer,
        allowance: GeBuyAllowance = GeBuyAllowance.UNLIMITED,
        systemPrice: (Int) -> Int? = { GeSystemLiquidity.UNIT_PRICE[it] },
    ): List<GeFill> {
        val fills = mutableListOf<GeFill>()
        val opposite =
            book
                .asSequence()
                .filter { it.status == OfferStatus.ACTIVE && it.id != newOffer.id }
                .filter { it.itemId == newOffer.itemId }
                .filter { it.type != newOffer.type }
                .filter { candidate ->
                    if (newOffer.type == OfferType.BUY) {
                        candidate.pricePerItem <= newOffer.pricePerItem
                    } else {
                        candidate.pricePerItem >= newOffer.pricePerItem
                    }
                }.sortedWith(
                    compareBy(
                        { if (newOffer.type == OfferType.BUY) it.pricePerItem else -it.pricePerItem },
                        { it.createdAtMs },
                    ),
                ).toList()

        for (resting in opposite) {
            if (newOffer.remaining <= 0) break
            val buyOffer = if (newOffer.type == OfferType.BUY) newOffer else resting
            val sellOffer = if (newOffer.type == OfferType.SELL) newOffer else resting
            val allowed = allowance.remaining(buyOffer.username, newOffer.itemId)
            val quantity = minOf(newOffer.remaining, resting.remaining, allowed)
            if (quantity <= 0) {
                if (newOffer.type == OfferType.BUY && allowed <= 0) break
                continue
            }

            // The resting (already-queued) offer's price is the execution
            // price, matching standard exchange convention.
            val execPrice = resting.pricePerItem

            buyOffer.quantityFilled += quantity
            buyOffer.collectableItems += quantity
            buyOffer.coinsTraded += execPrice.toLong() * quantity
            // Price-improvement refund: the buyer only ever pays the lower
            // resting price, so any excess already escrowed at their own
            // listed price comes straight back rather than being destroyed.
            val refund = (buyOffer.pricePerItem - execPrice).toLong() * quantity
            if (refund > 0) buyOffer.collectableCoins += refund
            if (buyOffer.remaining <= 0) buyOffer.status = OfferStatus.COMPLETED

            sellOffer.quantityFilled += quantity
            sellOffer.collectableCoins += quantity.toLong() * execPrice
            sellOffer.coinsTraded += execPrice.toLong() * quantity
            if (sellOffer.remaining <= 0) sellOffer.status = OfferStatus.COMPLETED

            allowance.record(buyOffer.username, newOffer.itemId, quantity)
            fills.add(GeFill(buyOffer.id, sellOffer.id, quantity, execPrice, fromSystem = false))
        }

        val house = systemPrice(newOffer.itemId)

        if (newOffer.type == OfferType.BUY && newOffer.remaining > 0) {
            if (house != null && newOffer.pricePerItem >= house) {
                val quantity = minOf(newOffer.remaining, allowance.remaining(newOffer.username, newOffer.itemId))
                if (quantity > 0) {
                    newOffer.quantityFilled += quantity
                    newOffer.collectableItems += quantity
                    newOffer.coinsTraded += house.toLong() * quantity
                    val refund = (newOffer.pricePerItem - house).toLong() * quantity
                    if (refund > 0) newOffer.collectableCoins += refund
                    if (newOffer.remaining <= 0) newOffer.status = OfferStatus.COMPLETED
                    allowance.record(newOffer.username, newOffer.itemId, quantity)
                    fills.add(GeFill(newOffer.id, null, quantity, house, fromSystem = true))
                }
            }
        }

        /*
         * The house also buys, so a sell offer never sits unsold (owner 2026-09-20).
         *
         * It pays the seller's own asking price and only when that ask is at or below the guide price, which is
         * what stops the two sides becoming a money loop: the house sells at guide and buys at no more than
         * guide, so buying from it and selling straight back is break-even at best, never profitable. A seller
         * asking above guide is left resting for a real player, exactly as before.
         *
         * No buy limit is recorded here - limits exist to stop one account draining supply, and selling into the
         * house is the opposite of draining it.
         */
        if (newOffer.type == OfferType.SELL && newOffer.remaining > 0) {
            if (house != null && newOffer.pricePerItem <= house) {
                val quantity = newOffer.remaining
                val paid = newOffer.pricePerItem
                newOffer.quantityFilled += quantity
                newOffer.collectableCoins += paid.toLong() * quantity
                newOffer.coinsTraded += paid.toLong() * quantity
                newOffer.status = OfferStatus.COMPLETED
                fills.add(GeFill(null, newOffer.id, quantity, paid, fromSystem = true))
            }
        }

        return fills
    }
}
