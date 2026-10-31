package gg.rsmod.plugins.content.items

import gg.rsmod.game.model.Tile
import gg.rsmod.plugins.api.cfg.Items

/** RS Wiki Ferocious ring; the 2011 Kuradal destination from Void ancient_cavern.areas.toml. */
object FerociousRing {
    val ids = (Items.FEROCIOUS_RING_5..Items.FEROCIOUS_RING_1).toList()
    val destination = Tile(1736, 5313, 1)
    fun next(id: Int): Int? = if (id !in ids) null else if (id == Items.FEROCIOUS_RING_1) -1 else id + 1
}
