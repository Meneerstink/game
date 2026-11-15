package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.content.quests.tab.CollectionLog
import gg.rsmod.plugins.content.skills.summoning.Pets
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.playJingle
import kotlin.random.Random

/** One loot planner/transaction for ordinary caskets and GPT-B's defeated Mimic. */
object ClueCasketRewards {
    fun milestone(tier: ClueScrollTier): Pair<Int, Int>? = when (tier) {
        ClueScrollTier.EASY -> 500 to 20164
        ClueScrollTier.MEDIUM -> 400 to 20249
        ClueScrollTier.ELITE -> 200 to 19941
        ClueScrollTier.MASTER -> 100 to 22675
        else -> null // Hard unlocks Uri transform, not a reward item.
    }
    data class Plan(val tier: ClueScrollTier, val rolls: Int, val rewards: List<Item>, val megaRare: Set<Int> = emptySet(), val bloodhound: Boolean = false)
    data class Award(val count: Int, val rewards: List<Item>, val overflow: List<Item>)

    /**
     * Resolve the COMPLETE tier before rolling. Missing assets never silently
     * become nothing or boost the remaining outcomes. Forced lower roll counts
     * are for the Mimic after failed attempts; its encounter validates victory.
     */
    fun plan(
        tier: ClueScrollTier,
        random: Random = Random.Default,
        rolls: Int? = null,
        extra: List<Item> = emptyList(),
        catalogue: ClueRewardCatalogue = ClueRewardCatalogue.current,
        local: (Int) -> Int? = ClueItems::local,
    ): Plan? {
        val missing = catalogue.missing(tier, local)
        if (missing.isNotEmpty()) {
            println("PROBLEM CLUE ${tier.name} reward assets not mapped: ${missing.sorted()}; casket retained")
            return null
        }
        val count = rolls ?: random.nextInt(tier.rewardRolls.first, tier.rewardRolls.last + 1)
        require(count in 1..tier.rewardRolls.last)
        val items = mutableListOf<Item>()
        val rare = mutableSetOf<Int>()
        repeat(count) {
            items += catalogue.roll(tier, random).map {
                val id = checkNotNull(local(it.osrsId))
                if (it.megaRare) rare += id
                Item(id, it.amount)
            }
        }
        items += extra.map { Item(it) }
        return Plan(tier, count, items, rare)
    }

    /** Once-per-casket rolls and earned milestones, independent of the ordinary reward slots. */
    fun planForPlayer(player: Player, tier: ClueScrollTier, random: Random = Random.Default, rolls: Int? = null,
                      extra: List<Item> = emptyList(), catalogue: ClueRewardCatalogue = ClueCasketLocation.catalogue(player)): Plan? {
        if (!ClueCasketLocation.permitted(player, tier)) {
            player.message("You cannot open this on Entrana, the monks are watching you too closely.")
            return null
        }
        fun loaded(osrs: Int): Int? = ClueItems.local(osrs)?.takeIf {
            player.world.definitions.getNullable(ItemDef::class.java, it) != null
        }
        val missingRewards = catalogue.missing(tier, ::loaded)
        if (missingRewards.isNotEmpty()) {
            println("PROBLEM CLUE ${tier.name} reward definitions not loaded: ${missingRewards.sorted()}; casket retained")
            return null
        }
        val logMissing = ClueLogCatalogue.current.pages.values.flatMap { it.osrsItems }.distinct().filter { loaded(it) == null }
        if (logMissing.isNotEmpty()) {
            println("PROBLEM CLUE collection-log assets not mapped: ${logMissing.sorted()}; casket retained")
            return null
        }
        val completed = (ClueCompletions.get(player, tier).toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val sourceExtras = ClueContainers.dueCases(player, tier, completed).toMutableList()
        val milestone = milestone(tier)
        if (milestone != null && completed >= milestone.first && !ClueContainers.held(player, ClueItems.local(milestone.second) ?: -1)) sourceExtras += milestone.second
        val mapped = sourceExtras.map { osrs ->
            val id = loaded(osrs) ?: run { println("PROBLEM CLUE milestone item $osrs not imported/loaded; casket retained"); return null }
            Item(id)
        }.toMutableList()
        val master = if (tier.masterChance > 0) ClueContainers.rewardId(player, ClueScrollTier.MASTER, notify = false) else null
        // Eligible master outcomes must exist before a casket is consumed. No failed asset turns into a losing roll.
        if (tier.masterChance > 0) {
            val masterEligible = if (ClueContainers.enabled(player))
                ClueContainers.count(player, ClueScrollTier.MASTER) < ClueContainers.capacity(player, ClueScrollTier.MASTER)
                else !ClueOwnership.owns(player, ClueScrollTier.MASTER)
            if (masterEligible && (master == null || player.world.definitions.getNullable(ItemDef::class.java, master) == null)) {
                println("PROBLEM CLUE 78 clue/scroll box reward import missing; casket retained")
                return null
            }
        }
        if (tier.masterChance > 0 && random.nextInt(tier.masterChance) == 0) {
            if (master != null) mapped += Item(master) else ClueContainers.rewardId(player, ClueScrollTier.MASTER)
        }
        val base = plan(tier, random, rolls, extra + mapped, catalogue) ?: return null
        return base.copy(bloodhound = tier == ClueScrollTier.MASTER && random.nextInt(1000) == 0)
    }

    /**
     * No suspension between snapshot and commit. [source] is the exact held
     * casket object, not just its item ID; a stale dialogue cannot spend it twice.
     * Overflow is private ground loot (Wiki The Fremennik Trials casket section).
     */
    fun award(player: Player, slot: Int, source: Item, plan: Plan): Award? {
        if (slot !in 0 until player.inventory.capacity || player.inventory[slot] !== source || source.amount < 1) return null
        if (source.id != plan.tier.casketId) {
            val mimic = gg.rsmod.plugins.content.areas.watson.MimicChallenge
            if (mimic.tier(source) != plan.tier || !mimic.defeated(source) || mimic.active(source) ||
                plan.rolls != mimic.remaining(source)) return null
        }
        if (plan.rewards.isEmpty() || plan.rewards.any { it.amount <= 0 }) return null
        if (plan.rewards.any { player.world.definitions.getNullable(ItemDef::class.java, it.id) == null }) {
            println("PROBLEM CLUE ${plan.tier.name} reward definition missing; casket retained")
            return null
        }
        val staged = ItemContainer(player.inventory)
        val held = staged[slot] ?: return null
        staged[slot] = if (held.amount == 1) null else Item(held, held.amount - 1)
        val overflow = mutableListOf<Item>()
        for (reward in plan.rewards) {
            val result = staged.add(Item(reward), assureFullInsertion = false)
            if (result.getLeftOver() > 0) overflow += Item(reward, result.getLeftOver())
        }
        // Unchanged items keep their identity for other pending interactions.
        for (index in 0 until staged.capacity) {
            val before = player.inventory[index]
            val after = staged[index]
            if (before?.id != after?.id || before?.amount != after?.amount || before?.attr != after?.attr) player.inventory[index] = after
        }
        val count = ClueCompletions.opened(player, plan.tier)
        // Wiki Treasure! (Treasure Trails), cache 2686 index 11/group 193.
        // Byte-identical to our 667 jingle (docs/CLUE/ui-asset-evidence.json).
        player.playJingle(193)
        player.message("Well done, you've completed the Treasure Trail!")
        player.message("<col=ff0000>You have completed $count ${plan.tier.displayName.lowercase()} Treasure Trails.</col>")
        overflow.forEach { player.world.spawn(ClueGroundItems.configure(GroundItem(it, player.tile, player))) }
        runCatching { CollectionLog.record(player, plan.tier.key, plan.rewards.map { it.id to it.amount }) }
            .onFailure { gg.rsmod.game.model.Trace.crash("clue collection log", it) }
        plan.rewards.forEach { ClueOwnership.tier(it.id)?.let { tier -> player.inventory.firstOrNull { it?.id == tier.scrollId }?.let { ClueScrollManager.prepare(player, it, tier) } } }
        if (plan.bloodhound) Pets.award(player, "Bloodhound", source = plan.tier.key)
        if (plan.tier == ClueScrollTier.MASTER) {
            plan.megaRare.forEach { id ->
                val name = player.world.definitions.get(ItemDef::class.java, id).name
                val line = "<col=ffd700>${gg.rsmod.util.Misc.formatForDisplay(player.username)} has received $name from a Reward casket (78)!</col>"
                player.world.players.forEach { it.message(line) }
            }
        }
        return Award(count, plan.rewards.map { Item(it) }, overflow)
    }
}
