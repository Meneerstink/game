package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.model.World
import gg.rsmod.plugins.content.quests.tab.DropSources

/** Per casket chances from the actual roll, including the owner's shop-only kit rerolls. No bracket multiplier. */
object ClueRewardView {
    fun lines(tier: ClueScrollTier, local: (Int) -> Int?, name: (Int) -> String, catalogue: ClueRewardCatalogue): List<DropSources.Line> =
        catalogue.outcomes.getValue(tier).flatMap { it.rows }.groupBy { it.osrsId }.map { (osrs, rows) ->
            val item = checkNotNull(local(osrs)) { "CLUE viewer item $osrs not imported" }
            val low = rows.minOf { it.minimum }
            val high = rows.maxOf { it.maximum }
            val chance = catalogue.casketChance(tier, osrs)
            DropSources.Line("clue:$osrs", item, name(item), if (low == high) "$low" else "$low-$high", chance, chance,
                note = "${tier.rewardRolls.first}-${tier.rewardRolls.last} rolls; quantity per roll")
        }

    fun view(world: World, tier: ClueScrollTier): DropSources.View = DropSources.View(emptyList(), DropSources.sorted(world,
        lines(tier, ClueItems::local, { DropSources.itemName(world, it) }, ClueRewardCatalogue.current)))
}
