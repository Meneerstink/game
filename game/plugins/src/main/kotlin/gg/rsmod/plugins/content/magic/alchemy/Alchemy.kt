package gg.rsmod.plugins.content.magic.alchemy

import gg.rsmod.game.fs.def.ItemDef

/**
 * Coins an alchemy spell pays for one item: 60 % (high) or 40 % (low) of the item's cache value, rounded down. The one
 * formula for the spell itself (alchemy.plugin.kts) and for the Ground Items overlay's "HA" price (ClientHud).
 *
 * N191 (owner 2026-10-22, "alch max = GE-prijs"): never more than the item's Grand Exchange price [gePrice] (the OSRS price,
 * [gg.rsmod.plugins.content.mechanics.exchange.GePrice]) - the 667 cache values are often far above the OSRS prices the shops
 * and the GE now charge (Halibut: 170 coins, high alchemy 2,204), so buying and alching would print coins.
 */
object Alchemy {
    fun coins(
        def: ItemDef,
        highAlchemy: Boolean,
        gePrice: Int? = null,
    ): Int {
        val value = (def.cost * (if (highAlchemy) 0.6 else 0.4)).toInt()
        return if (gePrice != null && gePrice > 0) minOf(value, gePrice) else value
    }
}
