package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.refreshBonuses
import gg.rsmod.plugins.content.items.osrs.Blowpipe
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

    /**
     * - Avernic defender: item page, broken + 600,000 coins (the repair cost) for the killer.
     * - Infernal cape: "Items Kept on Death" below level 20 - kept broken, the PKer receives the repair cost;
     *   item page repair cost 225,000 (since 20 September 2023). Above level 20 the page says it turns into a
     *   pile of coins for the PKer, but no amount is sourced: PROVISIONAL, the below-20 rule applies everywhere.
     * - Imbued god capes: item page - broken below level 20, "no coins go to the killer"; above-20 behaviour is
     *   not sourced (same PROVISIONAL rule).
     */
    private val entries =
        listOf(
            Breakable(Items.AVERNIC_DEFENDER, Items.AVERNIC_DEFENDER_BROKEN, 600_000),
            Breakable(Items.INFERNAL_CAPE, Items.INFERNAL_CAPE_BROKEN, 225_000),
            Breakable(Items.IMBUED_SARADOMIN_CAPE, Items.IMBUED_SARADOMIN_CAPE_BROKEN, 0),
            Breakable(Items.IMBUED_GUTHIX_CAPE, Items.IMBUED_GUTHIX_CAPE_BROKEN, 0),
            Breakable(Items.IMBUED_ZAMORAK_CAPE, Items.IMBUED_ZAMORAK_CAPE_BROKEN, 0),
            // Ava's assembler (raw wiki): unprotected in PvP it "will remain in the player's inventory, but will become
            // broken"; repair 240,000 coins at Perdu. Coins for the killer are not stated: none are dropped (SOURCE_GAP).
            Breakable(Items.AVAS_ASSEMBLER, Items.AVAS_ASSEMBLER_BROKEN, 0),
        ).associateBy { it.itemId }

    val ALL: Collection<Breakable> get() = entries.values

    fun breakableFor(itemId: Int): Breakable? = entries[itemId]

    /**
     * Removes breakable and ornamented stacks from a Wilderness death's lost list so [DeathExecutor]
     * never drops them as-is; returns the filtered result and the removed stacks for [execute].
     */
    fun split(result: DeathResolutionResult): Pair<DeathResolutionResult, List<DeathSlotItem>> {
        if (result.context != DeathContext.WILDERNESS_PVP) return result to emptyList()
        val (converting, rest) =
            result.itemRisk.lost.partition {
                entries.containsKey(it.item.id) || OsrsOrnamentKits.forOrnamented(it.item.id) != null || it.item.id == Items.TOXIC_BLOWPIPE ||
                    it.item.id == Items.BOW_OF_FAERDHINEN || it.item.id == Items.AMULET_OF_BLOOD_FURY
            }
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
            if (slotItem.item.id == Items.TOXIC_BLOWPIPE) {
                // OSRS Wiki "Toxic blowpipe": unprotected, "all scale and dart charges will appear on the floor alongside
                // the blowpipe" - the empty blowpipe, its darts and its scales drop for the killer.
                val blowpipe = container[slotItem.slot]!!
                container[slotItem.slot] = null
                if (slotItem.source == DeathContainerSource.EQUIPMENT) equipmentChanged = true
                world.spawn(GroundItem(Item(Items.TOXIC_BLOWPIPE_EMPTY, 1), victim.tile, result.killer))
                Blowpipe.dart(blowpipe)?.let { world.spawn(GroundItem(Item(it.itemId, Blowpipe.darts(blowpipe)), victim.tile, result.killer)) }
                Blowpipe.scales(blowpipe).takeIf { it > 0 }?.let { world.spawn(GroundItem(Item(Items.ZULRAHS_SCALES, it), victim.tile, result.killer)) }
                continue
            }
            if (slotItem.item.id == Items.BOW_OF_FAERDHINEN) {
                // OSRS Wiki "Bow of Faerdhinen": "On unprotected death in PvP, the inactive bow is dropped, and all shards used
                // to charge it will be lost."
                container[slotItem.slot] = null
                if (slotItem.source == DeathContainerSource.EQUIPMENT) equipmentChanged = true
                world.spawn(GroundItem(Item(Items.BOW_OF_FAERDHINEN_INACTIVE, 1), victim.tile, result.killer))
                continue
            }
            if (slotItem.item.id == Items.AMULET_OF_BLOOD_FURY) {
                // OSRS Wiki "Amulet of blood fury": unprotected PvP death - "the amulet is converted to a normal amulet of fury;
                // the blood shard and any remaining charges are lost" (the fury is lost to the killer as usual).
                container[slotItem.slot] = null
                if (slotItem.source == DeathContainerSource.EQUIPMENT) equipmentChanged = true
                world.spawn(GroundItem(Item(Items.AMULET_OF_FURY, 1), victim.tile, result.killer))
                continue
            }
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
            if (breakable.killerCoins <= 0) continue
            world.spawn(GroundItem(Item(Items.COINS_995, breakable.killerCoins * slotItem.item.amount), victim.tile, killer))
        }
        if (equipmentChanged) victim.refreshBonuses()
        return broken
    }
}
