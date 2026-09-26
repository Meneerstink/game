package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.refreshBonuses
import gg.rsmod.plugins.content.items.osrs.Blowpipe
import gg.rsmod.plugins.content.items.osrs.OsrsOrnamentKits
import gg.rsmod.plugins.content.items.osrs.StaffOfTheDead

/**
 * Untradeable combat items that break instead of dropping on an unprotected PvP death.
 *
 * OSRS Wiki (fetched 2026-09-13): "Avernic defender" - killed in PvP without protection, the item
 * becomes broken and remains with the player; the killer receives 600,000 coins (the repair cost).
 * "Items Kept on Death" lists the Avernic defender among untradeables that become broken with the
 * repair cost going to the killer. PvM deaths keep the normal recovery route.
 */
object PvpDeathBreakables {
    /**
     * @param repairCost the OSRS-sourced Perdu repair cost - see [BrokenItemRepair] and [RepairPrices].
     * @param killerCoins historical (the sourced coin statement per item); owner 2026-09-26: the killer always receives the repair
     * price ([RepairPrices]), so this value is no longer used.
     */
    data class Breakable(
        val itemId: Int,
        val brokenId: Int,
        val killerCoins: Int,
        val repairCost: Int,
    )

    /**
     * - Avernic defender: item page, broken + 600,000 coins (the repair cost) for the killer - repair
     *   cost and killer coins are the same 600,000 for this item.
     * - Infernal cape: "Items Kept on Death" below level 20 - kept broken, the PKer receives the repair cost;
     *   item page repair cost 225,000 (since 20 September 2023, again the same value for both). Above level 20 the
     *   page says it turns into a pile of coins for the PKer, but no amount is sourced, so the below-20 rule applies
     *   everywhere - OWNER_APPROVED 2026-09-13 ("Combat exactness audit", `OSRS_IMPORT_MASTER.yml`: "provisional
     *   below-20 PvP death rule approved"), not merely a provisional guess.
     * - Imbued god capes: item page - broken below level 20, "no coins go to the killer"; repair 96,000 at Perdu
     *   (`OSRS_IMPORT_MASTER.yml` "Batch capesrings"); above-20 behaviour is not sourced (same PROVISIONAL rule).
     */
    private val entries =
        listOf(
            Breakable(Items.AVERNIC_DEFENDER, Items.AVERNIC_DEFENDER_BROKEN, killerCoins = 600_000, repairCost = 600_000),
            Breakable(Items.INFERNAL_CAPE, Items.INFERNAL_CAPE_BROKEN, killerCoins = 225_000, repairCost = 225_000),
            Breakable(Items.IMBUED_SARADOMIN_CAPE, Items.IMBUED_SARADOMIN_CAPE_BROKEN, killerCoins = 0, repairCost = 96_000),
            Breakable(Items.IMBUED_GUTHIX_CAPE, Items.IMBUED_GUTHIX_CAPE_BROKEN, killerCoins = 0, repairCost = 96_000),
            Breakable(Items.IMBUED_ZAMORAK_CAPE, Items.IMBUED_ZAMORAK_CAPE_BROKEN, killerCoins = 0, repairCost = 96_000),
            // Ava's assembler (raw wiki): unprotected in PvP it "will remain in the player's inventory, but will become
            // broken"; repair 240,000 coins at Perdu. Coins for the killer are not stated: none are dropped (SOURCE_GAP).
            Breakable(Items.AVAS_ASSEMBLER, Items.AVAS_ASSEMBLER_BROKEN, killerCoins = 0, repairCost = 240_000),
            // OSRS-IMPORT capes (item pages): "it will remain in the player's inventory; however, the item will be in a broken, unusable
            // state" (repair at Perdu: imbued max capes 99,000, assembler max capes and Masori assembler 240,000, Dizana's max cape
            // 400,000). Coins for the killer are not stated (none). Dizana's max cape above level 20 ("converted to coins", amount
            // unstated) uses the below-20 rule everywhere (OWNER_APPROVED, like the Infernal cape - see above). SOURCE_GAP: the Masori crafting kit
            // "placed in their gravestone" is not modelled (no gravestone here).
            Breakable(Items.IMBUED_SARADOMIN_MAX_CAPE, Items.IMBUED_SARADOMIN_MAX_CAPE_BROKEN, killerCoins = 0, repairCost = 99_000),
            Breakable(Items.IMBUED_GUTHIX_MAX_CAPE, Items.IMBUED_GUTHIX_MAX_CAPE_BROKEN, killerCoins = 0, repairCost = 99_000),
            Breakable(Items.IMBUED_ZAMORAK_MAX_CAPE, Items.IMBUED_ZAMORAK_MAX_CAPE_BROKEN, killerCoins = 0, repairCost = 99_000),
            Breakable(Items.ASSEMBLER_MAX_CAPE, Items.ASSEMBLER_MAX_CAPE_BROKEN, killerCoins = 0, repairCost = 240_000),
            Breakable(Items.MASORI_ASSEMBLER, Items.MASORI_ASSEMBLER_BROKEN, killerCoins = 0, repairCost = 240_000),
            Breakable(Items.MASORI_ASSEMBLER_MAX_CAPE, Items.MASORI_ASSEMBLER_MAX_CAPE_BROKEN, killerCoins = 0, repairCost = 240_000),
            Breakable(Items.DIZANAS_MAX_CAPE, Items.DIZANAS_MAX_CAPE_BROKEN, killerCoins = 0, repairCost = 400_000),
        ).associateBy { it.itemId }

    private val byBrokenId = entries.values.associateBy { it.brokenId }

    val ALL: Collection<Breakable> get() = entries.values

    fun breakableFor(itemId: Int): Breakable? = entries[itemId]

    /** The [Breakable] a broken item [brokenId] repairs back into, or `null` if it isn't one of these. */
    fun forBroken(brokenId: Int): Breakable? = byBrokenId[brokenId]

    /**
     * Removes breakable and ornamented stacks from a Wilderness death's lost list so [DeathExecutor]
     * never drops them as-is; returns the filtered result and the removed stacks for [execute].
     */
    /**
     * Items with their own OSRS conversion on an unprotected PvP death (owner 2026-09-26: unchanged). Every other
     * untradeable - including the broken-id [entries] and the rune pouch - follows [UntradeableDeathProtection].
     */
    fun converts(itemId: Int): Boolean =
        OsrsOrnamentKits.forPvpConversion(itemId) != null || itemId == Items.TOXIC_BLOWPIPE ||
            itemId == Items.BLAZING_BLOWPIPE ||
            itemId == Items.BOW_OF_FAERDHINEN || itemId == Items.AMULET_OF_BLOOD_FURY || itemId == Items.TOXIC_STAFF_OF_THE_DEAD ||
            itemId in gg.rsmod.plugins.content.items.osrs.AvernicTreads.UPGRADED || itemId == Items.ANCIENT_SCEPTRE ||
            itemId in gg.rsmod.plugins.content.items.osrs.Demonbane.SYNAPSE_PRODUCTS ||
            gg.rsmod.plugins.content.items.osrs.PoweredStaves.chargedTierOf(itemId) != null

    fun split(result: DeathResolutionResult): Pair<DeathResolutionResult, List<DeathSlotItem>> {
        if (result.context != DeathContext.WILDERNESS_PVP) return result to emptyList()
        val (converting, rest) = result.itemRisk.lost.partition { converts(it.item.id) }
        if (converting.isEmpty()) return result to emptyList()
        return result.copy(itemRisk = result.itemRisk.copy(lost = rest)) to converting
    }

    /**
     * Converts each stack for the killer (its OSRS conversion) - broken items and repair coins are [UntradeableDeathProtection]'s job.
     *
     * Owner 2026-09-18 (#6): every killer-bound item goes through [drop] instead of straight onto the
     * floor, so `death.plugin.kts` can feed it into the same loot-key plan as the plain lost items -
     * a kill gives a loot key OR ground loot, never a key with converted loot lying beside it. The
     * default sink spawns ground loot, for callers outside the loot-key flow.
     */
    fun execute(
        world: World,
        result: DeathResolutionResult,
        breaking: List<DeathSlotItem>,
        drop: (Item) -> Unit = { world.spawn(GroundItem(it, result.victim.tile, result.killer)) },
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
            if (slotItem.item.id == Items.TOXIC_BLOWPIPE || slotItem.item.id == Items.BLAZING_BLOWPIPE) {
                // OSRS Wiki "Toxic blowpipe": unprotected, "all scale and dart charges will appear on the floor alongside
                // the blowpipe" - the empty blowpipe, its darts and its scales drop for the killer. Blazing blowpipe: the
                // tradeable ornament kit rule ("Items Kept on Death") also applies - the Toxic blowpipe plus the kit.
                val blowpipe = container[slotItem.slot]!!
                container[slotItem.slot] = null
                if (slotItem.source == DeathContainerSource.EQUIPMENT) equipmentChanged = true
                drop(Item(Items.TOXIC_BLOWPIPE_EMPTY, 1))
                if (slotItem.item.id == Items.BLAZING_BLOWPIPE) drop(Item(Items.BLOWPIPE_ORNAMENT_KIT, 1))
                Blowpipe.dart(blowpipe)?.let { drop(Item(it.itemId, Blowpipe.darts(blowpipe))) }
                Blowpipe.scales(blowpipe).takeIf { it > 0 }?.let { drop(Item(Items.ZULRAHS_SCALES, it)) }
                continue
            }
            if (slotItem.item.id == Items.BOW_OF_FAERDHINEN) {
                // OSRS Wiki "Bow of Faerdhinen": "On unprotected death in PvP, the inactive bow is dropped, and all shards used
                // to charge it will be lost."
                container[slotItem.slot] = null
                if (slotItem.source == DeathContainerSource.EQUIPMENT) equipmentChanged = true
                drop(Item(Items.BOW_OF_FAERDHINEN_INACTIVE, 1))
                continue
            }
            if (slotItem.item.id == Items.AMULET_OF_BLOOD_FURY) {
                // OSRS Wiki "Amulet of blood fury": unprotected PvP death - "the amulet is converted to a normal amulet of fury;
                // the blood shard and any remaining charges are lost" (the fury is lost to the killer as usual).
                container[slotItem.slot] = null
                if (slotItem.source == DeathContainerSource.EQUIPMENT) equipmentChanged = true
                drop(Item(Items.AMULET_OF_FURY, 1))
                continue
            }
            if (slotItem.item.id == Items.ANCIENT_SCEPTRE) {
                // OSRS Wiki "Ancient sceptre": "Unprotected PvP deaths convert it to an Ancient staff for the killer" (the icon is lost).
                container[slotItem.slot] = null
                if (slotItem.source == DeathContainerSource.EQUIPMENT) equipmentChanged = true
                drop(Item(Items.ANCIENT_STAFF, 1))
                continue
            }

            if (slotItem.item.id in gg.rsmod.plugins.content.items.osrs.Demonbane.SYNAPSE_PRODUCTS) {
                // OSRS Wiki "Emberlight", "Scorching bow", "Purging staff" (2026-09-17): "A player killer will receive the synapse from their
                // opponent if it is not one of the protected items." The rest of the weapon is lost.
                container[slotItem.slot] = null
                if (slotItem.source == DeathContainerSource.EQUIPMENT) equipmentChanged = true
                drop(Item(Items.TORMENTED_SYNAPSE, 1))
                continue
            }
            val chargedStaff = gg.rsmod.plugins.content.items.osrs.PoweredStaves.chargedTierOf(slotItem.item.id)
            if (chargedStaff != null) {
                // OSRS Wiki "Trident of the Seas": "If lost on death, only the uncharged trident will appear on the
                // floor" - explicitly sourced for both tridents. SOURCE_GAP (ADAPTED): the Sanguinesti staff's own page
                // does not restate this, but it is the same charged-powered-staff mechanic (`PoweredStaves.Staff`) and
                // this is applied to it too rather than leaving its charges droppable intact - flagged, not silently
                // assumed equally sourced.
                container[slotItem.slot] = null
                if (slotItem.source == DeathContainerSource.EQUIPMENT) equipmentChanged = true
                drop(Item(chargedStaff.uncharged, 1))
                continue
            }
            if (slotItem.item.id in gg.rsmod.plugins.content.items.osrs.AvernicTreads.UPGRADED) {
                // OSRS Wiki "Avernic treads": "the base avernic treads and any applied boots will be dropped for the killer and all
                // used tears will be lost."
                container[slotItem.slot] = null
                if (slotItem.source == DeathContainerSource.EQUIPMENT) equipmentChanged = true
                drop(Item(Items.AVERNIC_TREADS, 1))
                gg.rsmod.plugins.content.items.osrs.AvernicTreads.appliedBoots(slotItem.item.id).forEach { boots ->
                    drop(Item(boots, 1))
                }
                continue
            }
            if (slotItem.item.id == Items.TOXIC_STAFF_OF_THE_DEAD) {
                // OSRS Wiki "Toxic staff of the dead": "any scales that were used to charge it will appear on the floor along with
                // the uncharged staff."
                val scales = StaffOfTheDead.scales(container[slotItem.slot]!!)
                container[slotItem.slot] = null
                if (slotItem.source == DeathContainerSource.EQUIPMENT) equipmentChanged = true
                drop(Item(Items.TOXIC_STAFF_UNCHARGED, 1))
                if (scales > 0) drop(Item(Items.ZULRAHS_SCALES, scales))
                continue
            }
            val ornament = OsrsOrnamentKits.forPvpConversion(slotItem.item.id)
            if (ornament != null) {
                // "Items Kept on Death": dropped to the PKer as the non-ornamented item plus the ornament kit.
                container[slotItem.slot] = null
                if (slotItem.source == DeathContainerSource.EQUIPMENT) equipmentChanged = true
                drop(Item(ornament.base, slotItem.item.amount))
                drop(Item(ornament.kit, slotItem.item.amount))
                continue
            }
        }
        if (equipmentChanged) victim.refreshBonuses()
        return broken
    }
}
