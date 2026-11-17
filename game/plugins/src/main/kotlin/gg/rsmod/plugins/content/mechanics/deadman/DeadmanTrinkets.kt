package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.persistNow
import gg.rsmod.plugins.content.mechanics.combatresponse.Vengeance
import gg.rsmod.plugins.content.mechanics.deadman.Dmm2Ids.ItemId

/** Pinned wiki sources and owner V4/V6/V10 in PHASE2-ITEMS.md. Active effects require an unnoted carried item. */
object DeadmanTrinkets {
    data class AdvancedAppearance(val animation: Int, val ticks: Int) { init { require(animation > 0 && ticks > 0) } }
    @Volatile var advancedAppearance: AdvancedAppearance? = null
    const val FORTUITY_DEPOSIT = 10_000_000
    val LOOT_ENABLED = AttributeKey<Int>("dmm2_avarice_enabled")
    val NOTE_LOOT = AttributeKey<Int>("dmm2_avarice_note")
    val LOOT_MIN_VALUE = AttributeKey<Int>("dmm2_avarice_min_value")
    /** Optional measured OSRS valuation. Until then use the documented local GE-stack-value advice (PHASE3-SOURCES). */
    @Volatile var lootValue: ((Player, Item) -> Int)? = null
    fun valueOfDrop(player: Player, item: Item): Int = lootValue?.invoke(player, item) ?:
        gg.rsmod.plugins.content.mechanics.exchange.GePrice.stack(player.world, item)
            .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    enum class Result { OK, NOT_READY, ITEM_CHANGED, NO_SPACE, NO_COINS, INVALID_SETTING, COOLDOWN, OBSTRUCTED }
    fun carries(player: Player, item: ItemId): Boolean = item.local?.let { id ->
        player.inventory.filterNotNull().any { it.id == id && it.amount > 0 && !it.getDef(player.world.definitions).noted }
    } == true
    fun fortuity(player: Player): Boolean = carries(player, ItemId.FORTUITY)
    fun bonus(player: Player): Double = if (fortuity(player)) 1.0 else 0.0
    /** Fortuity wiki rev15286578 explicitly names bosses. Reuse the existing boss census, including breach bosses. */
    fun fortuityBoss(player: Player, npcId: Int): Boolean = fortuity(player) &&
        gg.rsmod.plugins.content.skills.summoning.BurdenTrinketDrops.sourceOf(player.world, npcId) ==
            gg.rsmod.plugins.content.skills.summoning.BurdenTrinketDrops.Source.BOSS

    fun charge(player: Player, slot: Int, expectedId: Int, charge: Boolean): Result {
        val from = (if (charge) ItemId.FORTUITY_EMPTY else ItemId.FORTUITY).local ?: return Result.NOT_READY
        val to = (if (charge) ItemId.FORTUITY else ItemId.FORTUITY_EMPTY).local ?: return Result.NOT_READY
        if (slot !in 0 until player.inventory.capacity) return Result.ITEM_CHANGED
        val item = player.inventory[slot] ?: return Result.ITEM_CHANGED
        if (expectedId != from || item.id != from || item.amount != 1 || item.hasAnyAttr() || item.getDef(player.world.definitions).noted) return Result.ITEM_CHANGED
        val inventory = ItemContainer(player.inventory)
        if (charge && !inventory.remove(Items.COINS_995, FORTUITY_DEPOSIT).hasSucceeded()) return Result.NO_COINS
        inventory[slot] = Item(to)
        if (!charge && !DeadmanInventory.add(inventory, Item(Items.COINS_995, FORTUITY_DEPOSIT))) return Result.NO_SPACE
        DeadmanInventory.publish(inventory, player.inventory)
        player.persistNow()
        return Result.OK
    }

    /** Result is selected only after validation. A failed/obstructed presentation consumes nothing. */
    fun openAdvanced(player: Player, slot: Int, expectedId: Int, clear: Boolean, roll: (Int) -> Int): Result {
        val from = ItemId.ADVANCED_WEAPONRY.local ?: return Result.NOT_READY
        val rewards = listOf(ItemId.VOIDWAKER, ItemId.CLAWS, ItemId.GODSWORD, ItemId.TWISTED_BOW,
            ItemId.SCYTHE_EMPTY, ItemId.SHADOW_EMPTY, ItemId.DARK_BOW, ItemId.VOLATILE).map { it.local ?: return Result.NOT_READY }
        if (!clear) return Result.OBSTRUCTED
        if (slot !in 0 until player.inventory.capacity) return Result.ITEM_CHANGED
        val item = player.inventory[slot] ?: return Result.ITEM_CHANGED
        if (item.id != from || expectedId != from || item.amount != 1 || item.hasAnyAttr() || item.getDef(player.world.definitions).noted) return Result.ITEM_CHANGED
        val selected = roll(8)
        require(selected in 0..7)
        player.inventory[slot] = Item(rewards[selected])
        player.persistNow()
        return Result.OK
    }

    fun vengeance(player: Player): Result {
        if (!carries(player, ItemId.VENGEANCE)) return Result.ITEM_CHANGED
        return if (Vengeance.activate(player)) Result.OK else Result.COOLDOWN
    }
    fun settings(player: Player, enabled: Boolean, note: Boolean, minValue: Int): Result {
        if (!carries(player, ItemId.AVARICE)) return Result.ITEM_CHANGED
        if (minValue < 0) return Result.INVALID_SETTING
        player.attr[LOOT_ENABLED] = if (enabled) 1 else 0
        player.attr[NOTE_LOOT] = if (note) 1 else 0
        player.attr[LOOT_MIN_VALUE] = minValue
        player.persistNow()
        return Result.OK
    }
    /** Called before spawning a NEW owned NPC drop. Existing ground loot is never consumed by this function. */
    fun collectDrop(player: Player, item: Item, value: Int): Boolean {
        if (!carries(player, ItemId.AVARICE) || player.attr[LOOT_ENABLED] == 0 || value < (player.attr[LOOT_MIN_VALUE] ?: 0)) return false
        if (value < 0 || item.amount <= 0) return false
        val delivered = if (player.attr[NOTE_LOOT] != 0 && !item.hasAnyAttr()) item.toNoted(player.world.definitions) else Item(item)
        val inventory = ItemContainer(player.inventory)
        if (!DeadmanInventory.add(inventory, delivered)) return false
        DeadmanInventory.publish(inventory, player.inventory)
        return true
    }
}
