package gg.rsmod.plugins.content.magic.alchemy

import gg.rsmod.game.fs.def.ItemDef

/**
 * Coins an alchemy spell pays for one item: 60 % (high) or 40 % (low) of the item's cache value, rounded down. The one
 * formula for the spell itself (alchemy.plugin.kts) and for the Ground Items overlay's "HA" price (ClientHud).
 */
object Alchemy {
    fun coins(
        def: ItemDef,
        highAlchemy: Boolean,
    ): Int = (def.cost * (if (highAlchemy) 0.6 else 0.4)).toInt()
}
