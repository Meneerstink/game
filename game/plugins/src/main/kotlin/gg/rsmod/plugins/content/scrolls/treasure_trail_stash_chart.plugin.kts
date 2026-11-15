package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.model.Tile

// Both the source cache and the existing imported square place this wall board here.
on_obj_option(TreasureTrailStashChart.NOTICEBOARD, "read") {
    val board = player.getInteractingGameObj()
    if (board.tile == Tile(1644, 3575, 0) && board.type == 4 && board.rot == 1) TreasureTrailStashChart.open(player)
}
TreasureTrailStashChart.layout?.let { row ->
    on_button(row.group, TreasureTrailStashChart.close) { player.closeInterface(row.group) }
    TreasureTrailStashChart.tiers.forEachIndexed { index, tier ->
        on_button(row.group, tier.tab) { TreasureTrailStashChart.select(player, index) }
    }
}
