package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.refreshBonuses
import gg.rsmod.plugins.content.items.osrs.OsrsOrnamentKits

/**
 * Untradeable combat items that break instead of dropping on an unprotected PvP death.
 *
 * OSRS Wiki (fetched 2026-09-13): "Avernic defender" - killed in PvP without protection, the item
 * becomes broken and remains with the player; the killer receives 600,000 coins (the repair cost).
 * "Items Kept on Death" lists the Avernic defender among untradeables that become broken with the
 * repair cost going to the killer. PvM deaths keep the normal recovery route.
 */
object PvpDeathBreakables {
    data class Breakable(
        val itemId: Int,
        val brokenId: Int,
        val killerCoins: Int,
    )

    private val entries =
        listOf(
            Breakable(Items.AVERNIC_DEFENDER, Items.AVERNIC_DEFENDER_BROKEN, 600_000),
        ).associateBy { it.itemId }

    fun breakableFor(itemId: Int): Breakable? = entries[itemId]

    /**
     * Removes breakable and ornamented stacks from a Wilderness death's lost list so [DeathExecutor]
     * never drops them as-is; returns the filtered result and the removed stacks for [execute].
     */
    fun split(result: DeathResolutionResult): Pair<DeathResolutionResult, List<DeathSlotItem>> {
        if (result.context != DeathContext.WILDERNESS_PVP) return result to emptyList()
        val (converting, rest) =
            result.itemRisk.lost.partition { entries.containsKey(it.item.id) || OsrsOrnamentKits.forOrnamented(it.item.id) != null }
        if (converting.isEmpty()) return result to emptyList()
        return result.copy(itemRisk = result.itemRisk.copy(lost = rest)) to converting
    }

    /** Swaps each stack for its broken id on the victim and drops the repair cost for the killer. */
    fun execute(
        world: World,
        result: DeathResolutionResult,
        breaking: List<DeathSlotItem>,
    ): Int {
        val victim = result.victim
        var broken = 0
        var equipmentChanged = false
        for (slotItem in breaking) {
            val container =
                when (slotItem.source) {
                    DeathContainerSource.INVENTORY -> victim.inventory
                    DeathContainerSource.EQUIPMENT -> victim.equipment
                }
            if (container[slotItem.slot]?.id != slotItem.item.id) continue
            val ornament = OsrsOrnamentKits.forOrnamented(slotItem.item.id)
            if (ornament != null) {
                // "Items Kept on Death": dropped to the PKer as the non-ornamented item plus the ornament kit.
                container[slotItem.slot] = null
                if (slotItem.source == DeathContainerSource.EQUIPMENT) equipmentChanged = true
                world.spawn(GroundItem(Item(ornament.base, slotItem.item.amount), victim.tile, result.killer))
                world.spawn(GroundItem(Item(ornament.kit, slotItem.item.amount), victim.tile, result.killer))
                continue
            }
            val breakable = entries.getValue(slotItem.item.id)
            when (slotItem.source) {
                DeathContainerSource.INVENTORY -> container[slotItem.slot] = Item(breakable.brokenId, slotItem.item.amount)
                DeathContainerSource.EQUIPMENT -> {
                    container[slotItem.slot] = null
                    equipmentChanged = true
                    if (!victim.inventory.add(breakable.brokenId, slotItem.item.amount, assureFullInsertion = true).hasSucceeded()) {
                        victim.deathRecovery.add(breakable.brokenId, slotItem.item.amount, assureFullInsertion = true)
                    }
                }
            }
            broken++
            val killer = result.killer ?: continue
            world.spawn(GroundItem(Item(Items.COINS_995, breakable.killerCoins * slotItem.item.amount), victim.tile, killer))
        }
        if (equipmentChanged) victim.refreshBonuses()
        return broken
    }
}
