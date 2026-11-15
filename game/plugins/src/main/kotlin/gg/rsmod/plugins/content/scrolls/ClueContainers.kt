package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.ext.message

/** Wiki Scroll box / Scroll case, cache 2686 IDs. Cases increase capacity; they never permit two active clues of one tier. */
object ClueContainers {
    data class Case(val osrs: Int, val tier: ClueScrollTier?, val threshold: Int, val flag: String)
    val boxes = mapOf(ClueScrollTier.EASY to 24362, ClueScrollTier.MEDIUM to 24363, ClueScrollTier.HARD to 24364,
        ClueScrollTier.ELITE to 24365, ClueScrollTier.MASTER to 24366)
    val cases = listOf(
        Case(30906, ClueScrollTier.EASY, 100, "easy_minor"), Case(30908, ClueScrollTier.EASY, 200, "easy_major"),
        Case(30910, ClueScrollTier.MEDIUM, 100, "medium_minor"), Case(30912, ClueScrollTier.MEDIUM, 250, "medium_major"),
        Case(30914, ClueScrollTier.HARD, 50, "hard_minor"), Case(30916, ClueScrollTier.HARD, 150, "hard_major"),
        Case(30918, ClueScrollTier.ELITE, 50, "elite_minor"), Case(30920, ClueScrollTier.ELITE, 150, "elite_major"),
        Case(30922, ClueScrollTier.MASTER, 25, "master_minor"), Case(30924, ClueScrollTier.MASTER, 75, "master_major"),
        Case(30926, null, 1, "mimic"),
    )
    private val flags = cases.associateWith { AttributeKey<Boolean>(persistenceKey = "clue_case_${it.flag}") }
    private val missingBoxesReported = mutableSetOf<Int>()
    fun opened(player: Player, case: Case): Boolean = player.attr[flags.getValue(case)] == true
    // Owner 2026-11-15 00:2x: everyone has the X Marks the Spot box unlock; no beginner quest here.
    fun enabled(@Suppress("UNUSED_PARAMETER") player: Player): Boolean = true
    fun boxTier(item: Int): ClueScrollTier? = boxes.entries.firstOrNull { ClueItems.local(it.value) == item }?.key
    fun containers(player: Player) = listOf(player.inventory, player.bank, player.gravestone, player.deathRecovery)
    fun held(player: Player, item: Int): Boolean = item >= 0 && ClueOwnership.stored(player).any { it.id == item }
    fun capacity(player: Player, tier: ClueScrollTier): Int = 2 + cases.count { (it.tier == tier || it.tier == null) && opened(player, it) }
    fun count(player: Player, tier: ClueScrollTier): Int {
        val box = ClueItems.local(boxes.getValue(tier))
        return ClueOwnership.stored(player).filter { it.id == tier.scrollId || it.id == box }
            .sumOf { it.amount.coerceAtLeast(0).toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
    fun mayTakeBox(player: Player, tier: ClueScrollTier): Boolean = (count(player, tier) < capacity(player, tier)).also {
        if (!it) player.message("You can't take any more of that tier of scroll box.")
    }
    fun pickupAmount(player: Player, tier: ClueScrollTier, amount: Int): Int {
        val take = amount.coerceAtMost((capacity(player, tier) - count(player, tier)).coerceAtLeast(0))
        val destroyed = amount - take
        if (destroyed > 0) player.message(if (destroyed == 1) "One of your scroll boxes was destroyed as you have too many of that tier."
            else "Some of your scroll boxes were destroyed as you have too many of that tier.")
        return take
    }

    /** Returns the eligible source item; a failed roll/cap never substitutes a different tier. */
    fun rewardId(player: Player, tier: ClueScrollTier, notify: Boolean = true): Int? {
        if (!enabled(player)) return tier.scrollId.takeIf { it >= 0 && !ClueOwnership.owns(player, tier) }
        if (count(player, tier) >= capacity(player, tier)) {
            if (notify) player.message("You have a sneaking suspicion that you would have received a ${tier.displayName.lowercase()} scroll box.")
            return null
        }
        val source = boxes.getValue(tier)
        return ClueItems.local(source).also {
            if (it == null && missingBoxesReported.add(source)) println("PROBLEM CLUE ${tier.name} scroll box $source not mapped; reward unavailable")
        }
    }

    fun openBox(player: Player, slot: Int, source: Item, tier: ClueScrollTier): Boolean {
        if (player.inventory[slot] !== source || ClueItems.local(boxes.getValue(tier)) != source.id || tier.scrollId < 0) return false
        if (ClueOwnership.owns(player, tier)) {
            player.message("The clue box refuses to open - you already have a clue."); return false
        }
        val staged = ItemContainer(player.inventory)
        staged[slot] = if (source.amount == 1) null else Item(source, source.amount - 1)
        if (!staged.add(tier.scrollId, 1, assureFullInsertion = true).hasSucceeded()) {
            player.message("You don't have enough inventory space to hold that item."); return false
        }
        commit(player, staged)
        player.inventory.firstOrNull { it?.id == tier.scrollId }?.let { ClueScrollManager.prepare(player, it, tier) }
        return true
    }

    fun openCase(player: Player, slot: Int, source: Item, case: Case): Boolean {
        if (player.inventory[slot] !== source || ClueItems.local(case.osrs) != source.id || source.amount < 1) return false
        if (opened(player, case)) { player.message("You've already obtained this scroll case!"); return false }
        player.inventory[slot] = if (source.amount == 1) null else Item(source, source.amount - 1)
        player.attr[flags.getValue(case)] = true
        // The award transaction records the case when received. Consuming it must not log a second copy.
        return true
    }

    fun dueCases(player: Player, tier: ClueScrollTier, completed: Int): List<Int> = cases.filter {
        it.tier == tier && completed >= it.threshold && !opened(player, it) && !held(player, ClueItems.local(it.osrs) ?: -1)
    }.map { it.osrs }

    private fun commit(player: Player, staged: ItemContainer) {
        for (slot in 0 until staged.capacity) {
            val old = player.inventory[slot]; val next = staged[slot]
            if (old?.id != next?.id || old?.amount != next?.amount || old?.attr != next?.attr) player.inventory[slot] = next
        }
    }
}
