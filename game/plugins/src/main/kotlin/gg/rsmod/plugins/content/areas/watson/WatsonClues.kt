package gg.rsmod.plugins.content.areas.watson

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.content.scrolls.*

/** Wiki Watson: persistent partial hand-ins; duplicates remain with the player. */
object WatsonClues {
    val STORED = AttributeKey<Int>(persistenceKey = "watson_stored_clues")
    val INTRODUCED = AttributeKey<Boolean>(persistenceKey = "watson_introduced")
    val TIERS = listOf(ClueScrollTier.EASY, ClueScrollTier.MEDIUM, ClueScrollTier.HARD, ClueScrollTier.ELITE)
    private const val ALL = 15
    enum class Result { NOTHING_NEW, STORED, MASTER_HELD, NO_SPACE, MASTER_GIVEN, MISSING_IMPORT }

    fun stored(player: Player): Set<ClueScrollTier> = TIERS.filterIndexed { i, _ ->
        (player.attr[STORED] ?: 0) and (1 shl i) != 0
    }.toSet()

    /** No suspension between snapshot and commit; collecting never takes the same clue twice. */
    fun offer(
        player: Player,
        offered: Set<ClueScrollTier> = TIERS.toSet(),
        masterId: Int? = ClueItems.local(19835),
        boxId: Int? = ClueItems.local(24366),
    ): Result {
        val boxed = ClueContainers.enabled(player)
        val rewardId = (if (boxed) boxId else masterId) ?: return Result.MISSING_IMPORT
        val staged = ItemContainer(player.inventory)
        var bits = (player.attr[STORED] ?: 0) and ALL
        val before = bits
        TIERS.forEachIndexed { index, tier ->
            val flag = 1 shl index
            if (tier !in offered || bits and flag != 0) return@forEachIndexed
            val slot = staged.rawItems.indexOfFirst { it?.id == tier.scrollId }
            if (slot < 0) return@forEachIndexed
            val source = staged[slot]!!
            staged[slot] = if (source.amount == 1) null else Item(source, source.amount - 1)
            bits = bits or flag
        }
        val result = if (bits == ALL) {
            val allowed = if (boxed) ClueContainers.count(player, ClueScrollTier.MASTER) < ClueContainers.capacity(player, ClueScrollTier.MASTER)
                else !ClueContainers.containers(player).any { it.contains(rewardId) }
            when {
                !allowed -> Result.MASTER_HELD
                !staged.add(rewardId, 1, assureFullInsertion = true).hasSucceeded() -> Result.NO_SPACE
                else -> { bits = 0; Result.MASTER_GIVEN }
            }
        } else if (bits != before) Result.STORED else Result.NOTHING_NEW
        for (slot in 0 until staged.capacity) {
            val old = player.inventory[slot]; val next = staged[slot]
            if (old?.id != next?.id || old?.amount != next?.amount || old?.attr != next?.attr) player.inventory[slot] = next
        }
        player.attr[STORED] = bits
        if (result == Result.MASTER_GIVEN && !boxed) player.inventory.firstOrNull { it?.id == rewardId }?.let {
            ClueScrollManager.prepare(player, it, ClueScrollTier.MASTER)
        }
        return result
    }

    enum class Purchase { GIVEN, NO_COINS, NO_SPACE, MISSING_IMPORT }
    fun buyBlueprint(player: Player, blueprint: Int? = ClueItems.local(25612)): Purchase {
        if (blueprint == null) return Purchase.MISSING_IMPORT
        if (player.inventory.getItemCount(995) < 500) return Purchase.NO_COINS
        // The transcript explicitly requires an empty slot before payment.
        if (!player.inventory.hasSpace) return Purchase.NO_SPACE
        val staged = ItemContainer(player.inventory)
        if (!staged.remove(995, 500, assureFullRemoval = true).hasSucceeded()) return Purchase.NO_COINS
        if (!staged.add(blueprint, 1, assureFullInsertion = true).hasSucceeded()) return Purchase.NO_SPACE
        for (slot in 0 until staged.capacity) {
            val old = player.inventory[slot]; val next = staged[slot]
            if (old?.id != next?.id || old?.amount != next?.amount || old?.attr != next?.attr) player.inventory[slot] = next
        }
        return Purchase.GIVEN
    }
}
