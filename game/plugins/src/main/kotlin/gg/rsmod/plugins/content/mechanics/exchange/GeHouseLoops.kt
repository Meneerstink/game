package gg.rsmod.plugins.content.mechanics.exchange

import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.items.combine.CombinationData
import gg.rsmod.plugins.content.items.osrs.CrystalEquipment
import gg.rsmod.plugins.content.items.osrs.OsrsOrnamentKits
import gg.rsmod.plugins.content.items.osrs.PoweredStaves

/**
 * TRIDENT-FULL-ARBITRAGE (found 2026-11-25, N537 step 3b): the house deals every item at its own fixed OSRS price, so an
 * instant item conversion turns any OSRS price gap into an endless gold loop. Trident of the Seas (full) was bought from
 * the house for 812,384 gp, uncharged and the runes + uncharged trident sold back for 848,615 gp (+36k per round).
 *
 * The house therefore holds two rules for every conversion a player can do on the spot, without a skill:
 *  - **break** (uncharge, dismantle, split, revert): its ask for the whole is at least what it pays for the parts;
 *  - **make** (charge, attach a kit, assemble): its bid for the whole is at most what it asks for the parts (coins at 1 gp).
 * Skill products (amulet stringing, chiselling, leather work) only get the break rule: selling the product below the
 * value of its inputs is never right, but buying inputs and training a skill to sell the product is ordinary skilling.
 * Parts the house does not buy count 0 in a break; a make that needs a part the house does not sell is no house loop.
 * The convenience fee ([GeTax]) comes on top of these bounds, so a round trip always loses.
 */
object GeHouseLoops {
    /** One conversion between [whole] and [parts] (item id to amount); [make] = the parts can be turned into the whole for free. */
    data class Route(
        val whole: Int,
        val parts: Map<Int, Int>,
        val breaks: Boolean,
        val makes: Boolean,
    )

    val ROUTES: List<Route> by lazy { buildRoutes() }

    private val byWhole: Map<Int, List<Route>> by lazy { ROUTES.groupBy { it.whole } }

    fun routesOf(itemId: Int): List<Route> = byWhole[itemId] ?: emptyList()

    /**
     * [quote] of [itemId] bounded by its routes; [base] gives the unbounded quote of a part (null: not in the house).
     * Using the unbounded part quotes is the safe side: a part's real bid is never above it and its real ask never below.
     */
    fun bound(
        itemId: Int,
        quote: GeHouseQuote,
        base: (Int) -> GeHouseQuote?,
    ): GeHouseQuote {
        val routes = routesOf(itemId)
        if (routes.isEmpty()) return quote
        var ask = quote.ask.toLong()
        var bid = quote.bid.toLong()
        for (route in routes) {
            if (route.breaks) {
                val partsBid = route.parts.entries.sumOf { (id, amount) -> priceOf(id, base)?.bid?.toLong()?.times(amount) ?: 0L }
                ask = maxOf(ask, partsBid)
            }
            if (route.makes) {
                val partsAsk =
                    route.parts.entries.fold(0L as Long?) { sum, (id, amount) ->
                        val partAsk = priceOf(id, base)?.ask?.toLong() ?: return@fold null
                        sum?.plus(partAsk * amount)
                    }
                if (partsAsk != null) bid = minOf(bid, partsAsk)
            }
        }
        val boundedAsk = ask.coerceIn(1L, Int.MAX_VALUE.toLong())
        return GeHouseQuote(boundedAsk.toInt(), bid.coerceIn(0L, boundedAsk).toInt())
    }

    private fun priceOf(
        itemId: Int,
        base: (Int) -> GeHouseQuote?,
    ): GeHouseQuote? = if (itemId == Items.COINS_995) GeHouseQuote(1, 1) else base(itemId)

    private fun buildRoutes(): List<Route> {
        val routes = mutableListOf<Route>()
        // Powered staves with a "(full)" id: Uncharge gives the uncharged staff + the refunded runes of every charge; charging
        // to the cap costs the uncharged staff + the full charge cost (coins included).
        for (staff in PoweredStaves.Staff.values()) {
            if (!staff.available) continue
            val full = staff.full ?: continue
            val refund = mutableMapOf(staff.uncharged to 1)
            PoweredStaves.refund(staff, staff.maxCharges).forEach { refund.merge(it.id, it.amount, Int::plus) }
            routes += Route(full, refund, breaks = true, makes = false)
            val charge = mutableMapOf(staff.uncharged to 1)
            staff.chargeCost.forEach { charge.merge(it.id, it.amount * staff.maxCharges, Int::plus) }
            routes += Route(full, charge, breaks = false, makes = true)
        }
        // Magic fang + uncharged trident <-> uncharged toxic trident (Dismantle).
        for ((trident, toxic) in PoweredStaves.TOXIC_UPGRADE) {
            routes += Route(toxic, mapOf(trident to 1, Items.MAGIC_FANG to 1), breaks = true, makes = true)
        }
        // Ornament kits: attach and Dismantle/Split both ways; consumed kits (paint, mixes) only give the base back.
        for (kit in OsrsOrnamentKits.ALL) {
            routes += Route(kit.ornamented, partsOf(kit.base, kit.kit), breaks = true, makes = true)
        }
        for (kit in OsrsOrnamentKits.CONSUMED) {
            routes += Route(kit.ornamented, partsOf(kit.base, kit.kit), breaks = kit.returnsKit, makes = true)
            if (!kit.returnsKit) routes += Route(kit.ornamented, mapOf(kit.base to 1), breaks = true, makes = false)
        }
        // Use-item-on-item combinations: free attachments (no experience: godswords, Kodai wand, wards, (or) kits) both ways,
        // skill products only the break rule (see the class comment).
        for (data in CombinationData.values) {
            val parts = mutableMapOf<Int, Int>()
            data.items.forEach { id -> parts.merge(id, data.quantities[id] ?: 1) { a, _ -> a } }
            routes += Route(data.resultItem, parts, breaks = true, makes = data.experience == 0.0)
        }
        // Crystal armour Revert gives the crystal armour seed back.
        for ((armour, seed) in CrystalEquipment.REVERT_SEED) {
            routes += Route(armour, mapOf(seed to 1), breaks = true, makes = false)
        }
        return routes
    }

    private fun partsOf(vararg ids: Int): Map<Int, Int> = mutableMapOf<Int, Int>().also { map -> ids.forEach { map.merge(it, 1, Int::plus) } }
}
