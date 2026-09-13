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
    fun match(
        book: List<GrandExchangeOffer>,
        newOffer: GrandExchangeOffer,
        allowance: GeBuyAllowance = GeBuyAllowance.UNLIMITED,
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

        if (newOffer.type == OfferType.BUY && newOffer.remaining > 0) {
            val systemPrice = GeSystemLiquidity.UNIT_PRICE[newOffer.itemId]
            if (systemPrice != null && newOffer.pricePerItem >= systemPrice) {
                val quantity = minOf(newOffer.remaining, allowance.remaining(newOffer.username, newOffer.itemId))
                if (quantity > 0) {
                    newOffer.quantityFilled += quantity
                    newOffer.collectableItems += quantity
                    newOffer.coinsTraded += systemPrice.toLong() * quantity
                    val refund = (newOffer.pricePerItem - systemPrice).toLong() * quantity
                    if (refund > 0) newOffer.collectableCoins += refund
                    if (newOffer.remaining <= 0) newOffer.status = OfferStatus.COMPLETED
                    allowance.record(newOffer.username, newOffer.itemId, quantity)
                    fills.add(GeFill(newOffer.id, null, quantity, systemPrice, fromSystem = true))
                }
            }
        }

        return fills
    }
}
