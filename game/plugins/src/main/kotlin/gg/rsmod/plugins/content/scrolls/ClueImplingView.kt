package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.quests.tab.DropSources
import gg.rsmod.plugins.content.skills.hunter.ImplingData
import gg.rsmod.plugins.content.skills.hunter.ImplingTier

/** Existing jar loot plus its sourced clue tertiary, in the approved viewer renderer. */
object ClueImplingView {
    fun view(player: Player, tier: ImplingTier): DropSources.View {
        val lines = mutableListOf<DropSources.Line>()
        val rare = if (tier.rare.isEmpty()) 0.0 else 0.3
        fun append(pool: List<Pair<Int, IntRange>>, probability: Double, key: String) {
            if (pool.isEmpty() || probability == 0.0) return
            for ((index, entry) in pool.withIndex()) {
                val (item, amounts) = entry
                val quantity = if (amounts.first == amounts.last) "${amounts.first}" else "${amounts.first}-${amounts.last}"
                val chance = probability / pool.size
                lines += DropSources.Line("$key:$index", item, DropSources.itemName(player.world, item), quantity, chance, chance)
            }
        }
        append(ImplingData.mainLoot(player, tier), 1.0 - rare, "impling:common")
        append(tier.rare, rare, "impling:rare")
        lines += DropSources.Line("impling:jar", Items.IMPLING_JAR, DropSources.itemName(player.world, Items.IMPLING_JAR), "1", 0.9, 0.9)
        ClueImplings.sources[tier.npcId]?.let { source ->
            ClueItems.local(ClueContainers.boxes.getValue(source.tier))?.let { box ->
                val eligible = ClueContainers.count(player, source.tier) < ClueContainers.capacity(player, source.tier)
                val chance = 1.0 / source.denominator
                lines += DropSources.Line("impling:clue", box, DropSources.itemName(player.world, box), "1",
                    if (eligible) chance else 0.0, chance, note = "Subject to scroll box capacity")
            }
        }
        return DropSources.View(emptyList(), DropSources.sorted(player.world, DropSources.merge(lines)))
    }
}
