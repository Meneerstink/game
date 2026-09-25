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
     * With this on, the whitelist below stops being the limit: [GrandExchangeService] hands the matcher a house quote
     * ([GeHousePricing]) for *every* exchangeable item, so an unmatched buy is always filled from the house and an
     * unmatched sell is always bought by the house.
     *
     * The one thing this must not become is a money printer, and the prices are chosen so it cannot be:
     *  - the house SELLS at its ask (the buyer's overbid is refunded), and
     *  - the house BUYS at its bid, and only from a seller asking at most that bid,
     * where bid <= ask, both derived from the fixed OSRS seed rather than the player-steerable guide price
     * (audit E-01), and held to the shop and alchemy bounds of audit E-03.
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

    /** The whitelist quote: the listed unit price on both sides (a round trip is break-even). */
    fun quote(itemId: Int): GeHouseQuote? = UNIT_PRICE[itemId]?.let { GeHouseQuote(ask = it, bid = it) }
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
     * [houseQuote] is the house's quote for an item, or null when the house does not deal in it. The default keeps
     * the old cheap-materials whitelist so the pure unit tests stay meaningful; [GrandExchangeService] passes a
     * quote for every exchangeable item (see [GeSystemLiquidity.ALL_ITEMS_AT_GUIDE_PRICE]).
     * [taxPerItem] is the OSRS convenience fee per item sold ([GeTax]); the seller pays it on every sale, player and
     * house alike. It comes before [houseQuote] so that a trailing lambda still means the house quote.
     */
    fun match(
        book: List<GrandExchangeOffer>,
        newOffer: GrandExchangeOffer,
        allowance: GeBuyAllowance = GeBuyAllowance.UNLIMITED,
        taxPerItem: (itemId: Int, unitPrice: Int) -> Int = { _, _ -> 0 },
        houseQuote: (Int) -> GeHouseQuote? = GeSystemLiquidity::quote,
    ): List<GeFill> {
        // OSRS convenience fee: the seller receives the execution price minus the fee on every item sold.
        fun paySeller(
            seller: GrandExchangeOffer,
            quantity: Int,
            unitPrice: Int,
        ) {
            val tax = taxPerItem(seller.itemId, unitPrice).coerceIn(0, unitPrice).toLong() * quantity
            seller.collectableCoins += unitPrice.toLong() * quantity - tax
            seller.taxPaid += tax
        }
        val fills = mutableListOf<GeFill>()
        val opposite =
            book
                .asSequence()
                .filter { it.status == OfferStatus.ACTIVE && it.id != newOffer.id }
                // Audit E-01: an account never trades with itself (a wash trade that only moved the guide price).
                .filter { it.username != newOffer.username }
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
            paySeller(sellOffer, quantity, execPrice)
            sellOffer.coinsTraded += execPrice.toLong() * quantity
            if (sellOffer.remaining <= 0) sellOffer.status = OfferStatus.COMPLETED

            allowance.record(buyOffer.username, newOffer.itemId, quantity)
            fills.add(GeFill(buyOffer.id, sellOffer.id, quantity, execPrice, fromSystem = false))
        }

        val quote = houseQuote(newOffer.itemId)

        if (newOffer.type == OfferType.BUY && newOffer.remaining > 0 && quote != null) {
            val house = quote.ask
            if (house > 0 && newOffer.pricePerItem >= house) {
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
         * It buys only from a seller asking at most its bid, and pays the bid (OSRS: an order fills at the price of
         * the order already resting in the book - the house is that resting buyer; a low ask is not taken at face
         * value). bid <= ask and the seller pays the convenience fee ([taxPerItem], [GeTax]) on the bid as on any
         * sale, so buying from the house and selling straight back never gains anything. A seller asking above the
         * bid is left resting for a real player, exactly as before.
         *
         * No buy limit is recorded here - limits exist to stop one account draining supply, and selling into the
         * house is the opposite of draining it.
         */
        if (newOffer.type == OfferType.SELL && newOffer.remaining > 0 && quote != null) {
            if (quote.bid > 0 && newOffer.pricePerItem <= quote.bid) {
                val quantity = newOffer.remaining
                val paid = quote.bid
                newOffer.quantityFilled += quantity
                paySeller(newOffer, quantity, paid)
                newOffer.coinsTraded += paid.toLong() * quantity
                newOffer.status = OfferStatus.COMPLETED
                fills.add(GeFill(null, newOffer.id, quantity, paid, fromSystem = true))
            }
        }

        return fills
    }
}
