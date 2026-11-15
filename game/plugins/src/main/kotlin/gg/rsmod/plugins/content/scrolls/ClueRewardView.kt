package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.quests.tab.DropSources
import gg.rsmod.plugins.content.skills.summoning.Pets

/** Per casket chances from the actual roll, including the owner's shop-only kit rerolls. No bracket multiplier. */
object ClueRewardView {
    fun lines(tier: ClueScrollTier, local: (Int) -> Int?, name: (Int) -> String, catalogue: ClueRewardCatalogue,
              rolls: IntRange = tier.rewardRolls): List<DropSources.Line> =
        catalogue.outcomes.getValue(tier).flatMap { it.rows }.groupBy { it.osrsId }.map { (osrs, rows) ->
            val item = checkNotNull(local(osrs)) { "CLUE viewer item $osrs not imported" }
            val low = rows.minOf { it.minimum }
            val high = rows.maxOf { it.maximum }
            val chance = catalogue.casketChance(tier, osrs, rolls)
            DropSources.Line("clue:$osrs", item, name(item), if (low == high) "$low" else "$low-$high", chance, chance,
                note = "${rolls.first}-${rolls.last} rolls; quantity per roll")
        }

    fun view(player: Player, tier: ClueScrollTier, rolls: IntRange = tier.rewardRolls, includeMimic: Boolean = true): DropSources.View {
        val world = player.world
        val catalogue = if (ClueCasketLocation.permitted(player, tier)) ClueCasketLocation.catalogue(player) else ClueRewardCatalogue.current
        val entries = lines(tier, ClueItems::local, { DropSources.itemName(world, it) }, catalogue, rolls).toMutableList()
        val completed = ClueCompletions.get(player, tier).toLong() + 1
        fun extra(osrs: Int, chance: Double, eligible: Boolean, note: String, tag: DropSources.Tag = DropSources.Tag.NONE) {
            val item = checkNotNull(ClueItems.local(osrs)) { "CLUE viewer extra $osrs not imported" }
            entries += DropSources.Line("clue-extra:$osrs", item, DropSources.itemName(world, item), "1",
                if (eligible) chance else 0.0, chance, tag = tag, note = note)
        }
        if (tier.masterChance > 0) {
            val boxed = ClueContainers.enabled(player)
            val allowed = if (boxed) ClueContainers.count(player, ClueScrollTier.MASTER) < ClueContainers.capacity(player, ClueScrollTier.MASTER)
                else !ClueOwnership.owns(player, ClueScrollTier.MASTER)
            extra(if (boxed) 24366 else 19835, 1.0 / tier.masterChance, allowed, "Once per casket; subject to 78 clue capacity")
        }
        // Source cache 2686: item 19730 = Bloodhound, also in master collection-log enum 2145.
        if (tier == ClueScrollTier.MASTER) extra(19730, 1.0 / 1000, !Pets.owns(player, "Bloodhound"), "Once per casket", DropSources.Tag.PET)
        ClueContainers.cases.filter { it.tier == tier }.forEach { case ->
            val available = !ClueContainers.opened(player, case) && !ClueContainers.held(player, ClueItems.local(case.osrs) ?: -1)
            extra(case.osrs, 1.0, completed >= case.threshold && available, "${case.threshold} completions; until opened")
        }
        ClueCasketRewards.milestone(tier)?.let { (threshold, osrs) ->
            extra(osrs, 1.0, completed >= threshold && !ClueContainers.held(player, ClueItems.local(osrs) ?: -1), "$threshold completions; if not owned")
        }
        if (includeMimic && gg.rsmod.plugins.content.areas.watson.MimicCaskets.supported(tier)) {
            val chance = gg.rsmod.plugins.content.areas.watson.MimicCaskets.chance(player, tier)
            val normal = 1.0 / if (tier == ClueScrollTier.ELITE) 35 else 15
            // Conversion replaces the whole opening, including once-per-casket extras.
            // Its rewards are shown on the separate post-fight Mimic source.
            for (index in entries.indices) {
                val line = entries[index]
                entries[index] = DropSources.Line(line.key, line.item, line.name, line.quantity,
                    line.your * (1.0 - chance), line.normal * (1.0 - normal), line.tag, line.kids, line.note, line.sub)
            }
            val mimic = checkNotNull(ClueItems.local(23184))
            entries += DropSources.Line("clue:mimic", mimic, DropSources.itemName(world, mimic), "1", chance, normal,
                note = "Opt-in; first encounter guaranteed at 25 elite or 10 78 caskets")
        }
        return DropSources.View(emptyList(), DropSources.sorted(world, entries))
    }
}
