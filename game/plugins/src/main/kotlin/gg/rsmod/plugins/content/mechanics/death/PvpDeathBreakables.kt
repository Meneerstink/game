package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.message
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
    /**
     * Death audit 2026-10-04 (owner: "OSRS conversion" for charged/imbued/modified gear; data agent-handoffs/
     * perdu-death-data-2026-10-04.md). What the killer receives for these unprotected items, OSRS Wiki:
     *  - imbued rings: "dying to another player will cause the imbued ring to lose its imbued status" -> the plain ring;
     *    "Ring of suffering (r)/(ri)": the plain ring, the recoil charges are lost;
     *  - "Magic shortbow (i)": "will drop a regular magic shortbow for the person who killed them";
     *  - Slayer helmets (any): "Dropped to the PKer as a black mask ... The other parts are lost";
     *  - "Neitiznot faceguard": "If lost in PvP, it will revert to the normal helm of Neitiznot and basilisk jaw";
     *  - Amulet of rancour (s), volcanic/frozen whip, painted dark bows: "Dropped to the PKer as base item, recolour is destroyed";
     *  - Lava/Steam battlestaff (or): "the staff will revert to its default appearance";
     *  - Dragonfire ward / Ancient wyvern shield: "when recovered, the ... will be completely uncharged".
     * Venator bow and Tonalztics of Ralos ("Chargable items": "the tradeable uncharged item, and ... the items that would result
     * from uncharging the item") are in [killerItems] with their charges.
     */
    val PVP_BASE: Map<Int, List<Int>> =
        mapOf(
            Items.RING_OF_THE_GODS_I to listOf(Items.RING_OF_THE_GODS),
            Items.TYRANNICAL_RING_I to listOf(Items.TYRANNICAL_RING),
            Items.TREASONOUS_RING_I to listOf(Items.TREASONOUS_RING),
            Items.RING_OF_SUFFERING_I to listOf(Items.RING_OF_SUFFERING),
            Items.RING_OF_SUFFERING_R to listOf(Items.RING_OF_SUFFERING),
            Items.RING_OF_SUFFERING_RI to listOf(Items.RING_OF_SUFFERING),
            Items.MAGIC_SHORTBOW_I to listOf(Items.MAGIC_SHORTBOW),
            Items.SLAYER_HELMET to listOf(Items.BLACK_MASK),
            Items.SLAYER_HELMET_E to listOf(Items.BLACK_MASK),
            Items.SLAYER_HELMET_CHARGED to listOf(Items.BLACK_MASK),
            Items.FULL_SLAYER_HELMET to listOf(Items.BLACK_MASK),
            Items.FULL_SLAYER_HELMET_E to listOf(Items.BLACK_MASK),
            Items.FULL_SLAYER_HELMET_CHARGED to listOf(Items.BLACK_MASK),
            Items.NEITIZNOT_FACEGUARD to listOf(Items.HELM_OF_NEITIZNOT, Items.BASILISK_JAW),
            Items.AMULET_OF_RANCOUR_S to listOf(Items.AMULET_OF_RANCOUR),
            Items.VOLCANIC_ABYSSAL_WHIP to listOf(Items.ABYSSAL_WHIP),
            Items.FROZEN_ABYSSAL_WHIP to listOf(Items.ABYSSAL_WHIP),
            Items.DARK_BOW_GREEN to listOf(Items.DARK_BOW),
            Items.DARK_BOW_BLUE to listOf(Items.DARK_BOW),
            Items.DARK_BOW_YELLOW to listOf(Items.DARK_BOW),
            Items.DARK_BOW_WHITE to listOf(Items.DARK_BOW),
            Items.LAVA_BATTLESTAFF_OR to listOf(Items.LAVA_BATTLESTAFF),
            Items.STEAM_BATTLESTAFF_OR to listOf(Items.STEAM_BATTLESTAFF),
            Items.MYSTIC_STEAM_STAFF_OR to listOf(Items.MYSTIC_STEAM_STAFF),
            Items.DRAGONFIRE_WARD to listOf(Items.DRAGONFIRE_WARD_UNCHARGED),
            Items.ANCIENT_WYVERN_SHIELD to listOf(Items.ANCIENT_WYVERN_SHIELD_UNCHARGED),
        )

    fun converts(itemId: Int): Boolean =
        itemId in PVP_BASE || itemId == Items.VENATOR_BOW || itemId == Items.TONALZTICS_OF_RALOS ||
            OsrsOrnamentKits.forPvpConversion(itemId) != null || itemId == Items.TOXIC_BLOWPIPE ||
            itemId == Items.BLAZING_BLOWPIPE ||
            itemId == Items.BOW_OF_FAERDHINEN || itemId == Items.AMULET_OF_BLOOD_FURY || itemId == Items.TOXIC_STAFF_OF_THE_DEAD ||
            itemId in gg.rsmod.plugins.content.items.osrs.AvernicTreads.UPGRADED || itemId == Items.ANCIENT_SCEPTRE ||
            itemId in gg.rsmod.plugins.content.items.osrs.Demonbane.SYNAPSE_PRODUCTS ||
            gg.rsmod.plugins.content.items.osrs.PoweredStaves.chargedTierOf(itemId) != null ||
            gg.rsmod.plugins.content.items.osrs.RevenantWeapons.isCharged(itemId) ||
            itemId in gg.rsmod.plugins.content.items.osrs.RevenantBows.UNCHARGED_FOR || itemId == Items.SERPENTINE_HELM ||
            pointsDeath(itemId) || crystalSeedOnDeath(itemId) != null

    /**
     * OSRS Wiki "Crystal equipment": "Upon death in the Wilderness, the killer will receive the seed form of the crystal equipment."
     * Crystal armour (active or inactive) gives its Crystal armour seed (the same one seed per piece its Revert returns, owner
     * document 2026-09-16), the classic OSRS crystal bow its Crystal seed. The Bow of Faerdhinen has its own rule (inactive bow).
     */
    fun crystalSeedOnDeath(itemId: Int): Int? =
        gg.rsmod.plugins.content.items.osrs.CrystalEquipment.REVERT_SEED[itemId]
            ?: if (itemId == Items.CRYSTAL_BOW_OSRS || itemId == Items.CRYSTAL_BOW_OSRS_INACTIVE) Items.CRYSTAL_SEED else null

    /**
     * OSRS Wiki "Items Kept on Death", rows "Degradeable items" / "Degraded items" (Barrows and Moon equipment): PvP - "Dropped to
     * the PKer as the item's broken version". "Amulet of the Damned": the used amulet "will turn to dust" on death (the full one
     * drops as usual). Audit 2026-10-04 (degradation), [gg.rsmod.plugins.content.items.armor.CombatPoints].
     */
    fun pointsDeath(itemId: Int): Boolean {
        val chain = gg.rsmod.plugins.content.items.armor.CombatPoints.chainFor(itemId) ?: return false
        return chain.end >= 0 || itemId != chain.unused
    }

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
        var equipmentChanged = false
        for (slotItem in breaking) {
            val container =
                when (slotItem.source) {
                    DeathContainerSource.INVENTORY -> victim.inventory
                    DeathContainerSource.EQUIPMENT -> victim.equipment
                }
            val current = container[slotItem.slot]?.takeIf { it.id == slotItem.item.id } ?: continue
            val loot = killerItems(current) ?: continue
            container[slotItem.slot] = null
            if (slotItem.source == DeathContainerSource.EQUIPMENT) equipmentChanged = true
            loot.forEach(drop)
        }
        if (equipmentChanged) victim.refreshBonuses()
        return 0
    }

    /**
     * What the killer receives for one unprotected [item] that [converts] (its OSRS conversion), or `null` when it has none and
     * stays where it is. The one source for the real death ([execute]), the Items Kept on Death screen and the risk skull
     * ([DeathRules.pvpLoss]), so the screen shows exactly what the death drops (owner 2026-10-04: "exactly 100%").
     */
    fun killerItems(item: Item): List<Item>? {
        val id = item.id
        PVP_BASE[id]?.let { parts -> return parts.map { Item(it, item.amount) } }
        if (id == Items.VENATOR_BOW) {
            return listOfNotNull(Item(Items.VENATOR_BOW_UNCHARGED, 1), gg.rsmod.plugins.content.items.osrs.VenatorBow.essence(item).takeIf { it > 0 }?.let { Item(Items.ANCIENT_ESSENCE, it) })
        }
        if (id == Items.TONALZTICS_OF_RALOS) {
            return listOfNotNull(Item(Items.TONALZTICS_OF_RALOS_UNCHARGED, 1), gg.rsmod.plugins.content.items.osrs.Tonalztics.charges(item).takeIf { it > 0 }?.let { Item(Items.SUNFIRE_SPLINTERS, it) })
        }
        if (id == Items.TOXIC_BLOWPIPE || id == Items.BLAZING_BLOWPIPE) {
            // OSRS Wiki "Toxic blowpipe": unprotected, "all scale and dart charges will appear on the floor alongside
            // the blowpipe" - the empty blowpipe, its darts and its scales drop for the killer. Blazing blowpipe: the
            // tradeable ornament kit rule ("Items Kept on Death") also applies - the Toxic blowpipe plus the kit.
            val loot = mutableListOf(Item(Items.TOXIC_BLOWPIPE_EMPTY, 1))
            if (id == Items.BLAZING_BLOWPIPE) loot += Item(Items.BLOWPIPE_ORNAMENT_KIT, 1)
            Blowpipe.dart(item)?.let { loot += Item(it.itemId, Blowpipe.darts(item)) }
            Blowpipe.scales(item).takeIf { it > 0 }?.let { loot += Item(Items.ZULRAHS_SCALES, it) }
            return loot
        }
        crystalSeedOnDeath(id)?.let { return listOf(Item(it, 1)) }
        if (pointsDeath(id)) {
            // OSRS Wiki "Items Kept on Death": Barrows and Moon equipment are "dropped to the PKer as the item's broken version".
            val chain = gg.rsmod.plugins.content.items.armor.CombatPoints.chainFor(id)!!
            return if (chain.end >= 0) listOf(Item(chain.end, item.amount)) else emptyList()
        }
        // OSRS Wiki "Bow of Faerdhinen": "On unprotected death in PvP, the inactive bow is dropped, and all shards used
        // to charge it will be lost."
        if (id == Items.BOW_OF_FAERDHINEN) return listOf(Item(Items.BOW_OF_FAERDHINEN_INACTIVE, 1))
        // OSRS Wiki "Amulet of blood fury": unprotected PvP death - "the amulet is converted to a normal amulet of fury;
        // the blood shard and any remaining charges are lost" (the fury is lost to the killer as usual).
        if (id == Items.AMULET_OF_BLOOD_FURY) return listOf(Item(Items.AMULET_OF_FURY, 1))
        // OSRS Wiki "Ancient sceptre": "Unprotected PvP deaths convert it to an Ancient staff for the killer" (the icon is lost).
        if (id == Items.ANCIENT_SCEPTRE) return listOf(Item(Items.ANCIENT_STAFF, 1))
        // OSRS Wiki "Emberlight", "Scorching bow", "Purging staff" (2026-09-17): "A player killer will receive the synapse from their
        // opponent if it is not one of the protected items." The rest of the weapon is lost.
        if (id in gg.rsmod.plugins.content.items.osrs.Demonbane.SYNAPSE_PRODUCTS) return listOf(Item(Items.TORMENTED_SYNAPSE, 1))
        if (gg.rsmod.plugins.content.items.osrs.RevenantWeapons.isCharged(id)) {
            // OSRS Wiki (revenant weapons): "Upon death in the Wilderness, stored ether is always dropped" - the killer gets the
            // uncharged weapon and every ether, the activation deposit included (the Uncharge refund).
            val (uncharged, ether) = gg.rsmod.plugins.content.items.osrs.RevenantWeapons.uncharge(item)
            return listOf(Item(uncharged.id, 1), Item(Items.REVENANT_ETHER, ether))
        }
        if (id in gg.rsmod.plugins.content.items.osrs.RevenantBows.UNCHARGED_FOR) {
            // OSRS Wiki "Craw's bow": "any revenant ether stored as ammo within the bow will always be dropped and the initial
            // 1,000 activation ether will always disappear"; "Webweaver bow": "... will always be dropped and the initial 1,000
            // activation ether will always be provided to the killer". The killer gets the uncharged bow as well.
            val loot = mutableListOf(Item(gg.rsmod.plugins.content.items.osrs.RevenantBows.UNCHARGED_FOR.getValue(id), 1))
            revenantBowEther(item).takeIf { it > 0 }?.let { loot += Item(Items.REVENANT_ETHER, it) }
            return loot
        }
        if (id == Items.SERPENTINE_HELM) {
            // OSRS Wiki "Serpentine helm": "If a player dies with the helmet in PvP and it is not protected, any scales that were
            // used to charge it will appear on the floor along with the uncharged helmet."
            val scales = gg.rsmod.plugins.content.items.osrs.SerpentineHelm.scales(item)
            return listOfNotNull(Item(Items.SERPENTINE_HELM_UNCHARGED, 1), scales.takeIf { it > 0 }?.let { Item(Items.ZULRAHS_SCALES, it) })
        }
        gg.rsmod.plugins.content.items.osrs.PoweredStaves.chargedTierOf(id)?.let {
            // OSRS Wiki "Trident of the Seas": "If lost on death, only the uncharged trident will appear on the
            // floor" - explicitly sourced for both tridents. SOURCE_GAP (ADAPTED): the Sanguinesti staff's own page
            // does not restate this, but it is the same charged-powered-staff mechanic (`PoweredStaves.Staff`) and
            // this is applied to it too rather than leaving its charges droppable intact - flagged, not silently
            // assumed equally sourced.
            return listOf(Item(it.uncharged, 1))
        }
        if (id in gg.rsmod.plugins.content.items.osrs.AvernicTreads.UPGRADED) {
            // OSRS Wiki "Avernic treads": "the base avernic treads and any applied boots will be dropped for the killer and all
            // used tears will be lost."
            return listOf(Item(Items.AVERNIC_TREADS, 1)) +
                gg.rsmod.plugins.content.items.osrs.AvernicTreads.appliedBoots(id).map { Item(it, 1) }
        }
        if (id == Items.TOXIC_STAFF_OF_THE_DEAD) {
            // OSRS Wiki "Toxic staff of the dead": "any scales that were used to charge it will appear on the floor along with
            // the uncharged staff."
            val scales = StaffOfTheDead.scales(item)
            return listOfNotNull(Item(Items.TOXIC_STAFF_UNCHARGED, 1), scales.takeIf { it > 0 }?.let { Item(Items.ZULRAHS_SCALES, it) })
        }
        // "Items Kept on Death": dropped to the PKer as the non-ornamented item plus the ornament kit.
        OsrsOrnamentKits.forPvpConversion(id)?.let { return listOf(Item(it.base, item.amount), Item(it.kit, item.amount)) }
        return null
    }

    /** The ether a lost or protected revenant bow gives the killer: what it stores, plus the 1,000 deposit for the Webweaver bow. */
    private fun revenantBowEther(bow: Item): Int {
        val stored = gg.rsmod.plugins.content.items.osrs.RevenantBows.ether(bow)
        val deposit = if (bow.id == Items.WEBWEAVER_BOW) gg.rsmod.plugins.content.items.osrs.RevenantBows.ACTIVATION_ETHER else 0
        return stored + deposit
    }

    /**
     * OSRS Wiki, every revenant weapon page: on a death in the Wilderness the stored ether "will always be dropped, even if it is
     * one of the player's protected items". A protected chainmace or sceptre keeps its activation and loses only the stored ether;
     * a protected Craw's or Webweaver bow is left uncharged (the deposit disappears, or goes to the killer for the Webweaver bow -
     * [revenantBowEther]). Returns the ether for the killer.
     */
    fun stripProtectedEther(
        victim: gg.rsmod.game.model.entity.Player,
        result: DeathResolutionResult,
    ): List<Item> {
        if (result.context != DeathContext.WILDERNESS_PVP) return emptyList()
        val dropped = mutableListOf<Item>()
        var equipmentChanged = false
        for (slotItem in result.itemRisk.protected) {
            val container =
                when (slotItem.source) {
                    DeathContainerSource.INVENTORY -> victim.inventory
                    DeathContainerSource.EQUIPMENT -> victim.equipment
                }
            val current = container[slotItem.slot]?.takeIf { it.id == slotItem.item.id } ?: continue
            when {
                current.id in gg.rsmod.plugins.content.items.osrs.RevenantBows.UNCHARGED_FOR -> {
                    revenantBowEther(current).takeIf { it > 0 }?.let { dropped += Item(Items.REVENANT_ETHER, it) }
                    container[slotItem.slot] = Item(gg.rsmod.plugins.content.items.osrs.RevenantBows.UNCHARGED_FOR.getValue(current.id), 1)
                    if (slotItem.source == DeathContainerSource.EQUIPMENT) equipmentChanged = true
                }
                gg.rsmod.plugins.content.items.osrs.RevenantWeapons.isCharged(current.id) -> {
                    val stored = gg.rsmod.plugins.content.items.osrs.RevenantWeapons.ether(current)
                    if (stored <= 0) continue
                    dropped += Item(Items.REVENANT_ETHER, stored)
                    container[slotItem.slot] =
                        Item(current.id, current.amount).copyAttr(current).also { it.attr[gg.rsmod.game.model.item.ItemAttribute.CHARGES] = 0 }
                    if (slotItem.source == DeathContainerSource.EQUIPMENT) equipmentChanged = true
                }
            }
        }
        if (equipmentChanged) victim.refreshBonuses()
        return dropped
    }

    /**
     * OSRS Wiki "Amulet of the Damned" / audit 2026-10-04 (degradation): the used amulet turns to dust on death - PvM and PvP, kept
     * or not (the full amulet goes the normal way). Removed before the death is resolved, so it is never kept, dropped or graved.
     */
    fun crumbleUsedAmulets(victim: gg.rsmod.game.model.entity.Player): Int {
        var crumbled = 0
        listOf(victim.inventory, victim.equipment).forEach { container ->
            for (slot in 0 until container.capacity) {
                if (container[slot]?.id == Items.AMULET_OF_THE_DAMNED) {
                    container[slot] = null
                    crumbled++
                }
            }
        }
        if (crumbled > 0) {
            victim.refreshBonuses()
            victim.message("<col=ff0000>Your amulet of the damned has crumbled to dust.")
        }
        return crumbled
    }
}
