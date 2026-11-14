package gg.rsmod.plugins.content.areas.watson

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.quests.tab.DropSources
import gg.rsmod.plugins.content.scrolls.*

/** Actual post-fight ordinary rolls plus bonus events; existing viewer renderer stays unchanged. */
object MimicRewardView {
    fun view(player: Player, tier: ClueScrollTier): DropSources.View {
        val held = player.inventory.firstOrNull { it != null && MimicChallenge.tier(it) == tier }
        val failures = held?.let { MimicChallenge.failures(it) } ?: 0
        val table = MimicRewards.current.tables.getValue(tier)
        val rolls = (table.attempts - failures).coerceAtLeast(0)
        val catalogue = ClueRewardCatalogue.current
        val ordinary = ClueRewardView.lines(tier, ClueItems::local, { DropSources.itemName(player.world, it) }, catalogue).map { line ->
            val osrs = line.key.substringAfter("clue:").toInt()
            val chance = catalogue.outcomes.getValue(tier).filter { it.rows.any { row -> row.osrsId == osrs } }.sumOf { it.probability }
            val perCasket = 1.0 - Math.pow(1.0 - chance, rolls.toDouble())
            DropSources.Line(line.key, line.item, line.name, line.quantity, perCasket, perCasket, note = "$rolls ordinary rolls; quantity per roll")
        }
        fun line(osrs: Int, quantity: Int, chance: Double, key: String) = ClueItems.local(osrs)?.let { local ->
            DropSources.Line(key, local, DropSources.itemName(player.world, local), quantity.toString(), chance, chance)
        } ?: error("CLUE Mimic viewer asset $osrs not imported")
        val guaranteed = mutableListOf(line(table.plank, 1, 1.0, "mimic:plank"))
        val case = ClueContainers.cases.single { it.tier == null }
        if (!ClueContainers.opened(player, case)) guaranteed += line(case.osrs, 1, 1.0, "mimic:case")
        val bonus = table.rows.map { row ->
            line(row.osrs, if (row.scales) row.maximum * rolls / table.attempts else row.maximum, row.chance(failures), "mimic:bonus:${row.osrs}")
        }
        return DropSources.View(guaranteed, DropSources.sorted(player.world, ordinary + bonus))
    }
}
