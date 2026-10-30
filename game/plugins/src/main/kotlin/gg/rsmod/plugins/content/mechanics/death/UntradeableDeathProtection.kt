package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.addPreservingAttr
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.refreshBonuses
import gg.rsmod.plugins.content.magic.RunePouch
import gg.rsmod.plugins.content.mechanics.pvp.LootKeys
import gg.rsmod.plugins.content.mechanics.trouver.TrouverRegistry

/** What an unprotected PvP death does to one lost untradeable stack (owner 2026-09-26, the OSRS level-20 rule). */
enum class UntradeableFate {
    /** Stays with the victim in broken form; the killer gets the repair price. */
    BROKEN,

    /** Trouver-locked, above level 20: stays with the victim mangled and still locked; the killer gets 500,000. */
    MANGLED,

    /** Unlocked, above level 20: the item is destroyed; the killer gets the OSRS coin amount, else the repair price. */
    DESTROYED,

    /** Rune pouch that is kept (locked, or below level 20): the victim keeps the empty pouch, the runes go to the killer. */
    POUCH_EMPTIED,

    /** Already broken/mangled (a lockable item): stays with the victim as it is, nothing for the killer. */
    UNCHANGED,

    /**
     * Owner 2026-10-04: an untradeable that is not [UntradeableDeathProtection.lockable] (Barrows gloves, quest rewards ...) is
     * always lost; the killer receives its High Alchemy value in coins (OSRS Wiki "Barrows gloves": "When lost in a PvP death,
     * they turn into 78,000 coins" = 60 % of 130,000). Owner 2026-10-24 (B): the lost item can be bought back from Perdu for its store
     * value ([PerduReclaim], [reclaimPrice]) - unless it is one of Perdu's own Lost Property items, which he sells back anyway.
     */
    LOST_FOR_COINS,
}

data class UntradeableOutcome(
    val slotItem: DeathSlotItem,
    val fate: UntradeableFate,
    val killerCoins: Long,
)

/**
 * Untradeables on an unprotected PvP death (owner decisions 2026-09-26, OSRS Wiki "Items Kept on Death" / "Trouver parchment"):
 *
 *  - at or below level 20 Wilderness (every Deadman dangerous area outside the Wilderness has level 0): locked and unlocked
 *    untradeables break and stay with the victim; the killer receives the repair price in coins ([RepairPrices]);
 *  - above level 20: an unlocked untradeable is destroyed and the killer receives the OSRS coin amount (else the repair
 *    price); a Trouver-locked item becomes mangled, stays with the victim and keeps its lock, and the killer receives 500,000;
 *  - rune pouch: the runes are always lost to the killer; the victim keeps the empty pouch when it is locked or the death is
 *    at or below level 20, above level 20 an unlocked pouch is destroyed like any other untradeable.
 *
 * The item-specific conversions ([PvpDeathBreakables.converts]: blowpipes, powered staves, ornament kits ...), loot keys
 * and Deadman emblems keep their own rules and never come here. On a PvM death nothing breaks: every lost item,
 * untradeables included, goes to the gravestone. The death tile decides the Wilderness level ([DeathRules.deepWilderness]),
 * the same for the death, the Items Kept on Death screen and the risk skull.
 */
object UntradeableDeathProtection {
    /** Broken state kept in [ItemAttribute.BROKEN] for items without an imported broken/mangled id. */
    const val STATE_BROKEN = 1
    const val STATE_MANGLED = 2

    fun state(item: Item): Int = item.attr[ItemAttribute.BROKEN] ?: 0

    fun isBroken(item: Item): Boolean = state(item) > 0

    /** Whether [item] is already in a damaged state (attribute, or an imported broken/mangled id). */
    fun isDamaged(item: Item): Boolean =
        isBroken(item) || PvpDeathBreakables.forBroken(item.id) != null || TrouverRegistry.entryForDamaged(item.id) != null

    /**
     * Owner 2026-10-04 death rules: "you always lose everything but keep 1 item with Protect Item; an untradeable you normally
     * need to lock in OSRS (rune pouch ...) you keep, unless you die above level 20 Wilderness and it is not locked with a Trouver
     * parchment; Barrows gloves you always lose". The lockable group is the OSRS Wiki "(l)" list (rune pouches, Dizana's quivers
     * and max cape, Infernal cape and max cape, Void, Fighter torso, ancient sceptres) plus this server's own lockables (owner:
     * fire cape, imbued god/max capes, assemblers stay "kept like lockables") and the PvP-breakable capes/defender.
     */
    val OSRS_LOCKABLE: Set<Int> =
        setOf(
            Items.VOID_KNIGHT_TOP, Items.VOID_KNIGHT_TOP_10611, Items.VOID_KNIGHT_ROBE, Items.VOID_KNIGHT_GLOVES,
            Items.VOID_MAGE_HELM, Items.VOID_RANGER_HELM, Items.VOID_MELEE_HELM,
            Items.VOID_MAGE_HELM_11674, Items.VOID_RANGER_HELM_11675, Items.VOID_MELEE_HELM_11676,
            Items.FIGHTER_TORSO, Items.INFERNAL_MAX_CAPE, Items.BLESSED_DIZANAS_QUIVER, Items.BLESSED_DIZANAS_QUIVER_L,
            // Elite void (OSRS (l) list; untradeable since the 2026-10-04 audit, items.yml).
            Items.ELITE_VOID_KNIGHT_TOP, Items.ELITE_VOID_KNIGHT_ROBE, Items.ELITE_VOID_KNIGHT_TOP_19787, Items.ELITE_VOID_KNIGHT_ROBE_19788,
            Items.ELITE_VOID_KNIGHT_TOP_19789, Items.ELITE_VOID_KNIGHT_ROBE_19790,
        )

    /**
     * OSRS Wiki "Max cape" / "Accumulator max cape": "On a PvP death, the cape is always kept in the inventory, even if not
     * protected." (owner 2026-10-04: max cape untradeable and kept). The hoods have no death text; they stay with their cape.
     */
    val ALWAYS_KEPT_PVP: Set<Int> =
        setOf(Items.MAX_CAPE, Items.MAX_CAPE_20767, Items.MAX_HOOD, Items.MAX_HOOD_20768, Items.ACCUMULATOR_MAX_CAPE, Items.ACCUMULATOR_MAX_HOOD)

    /**
     * Fixed OSRS coins for the killer when one of these untradeables is lost (else [lostCoins] uses the High Alchemy value):
     * "Colossal blade": "If lost in PVP, the item will drop as 225,000 coins for the killer"; "Ring of shadows": "In PvP death, the
     * ring turns into 75,000 coins regardless of Wilderness location"; "Barrows gloves": "they turn into 78,000 coins".
     */
    val FIXED_KILLER_COINS: Map<Int, Long> =
        mapOf(Items.COLOSSAL_BLADE to 225_000L, Items.RING_OF_SHADOWS to 75_000L, Items.GLOVES_7462 to 78_000L)

    fun lockable(itemId: Int): Boolean =
        RunePouch.isPouch(itemId) || itemId in gg.rsmod.plugins.content.mechanics.trouver.TrouverLockables.IDS ||
            PvpDeathBreakables.breakableFor(itemId) != null || PvpDeathBreakables.forBroken(itemId) != null || itemId in OSRS_LOCKABLE

    /** Untradeables this rule handles on a PvP death. */
    fun handles(
        definitions: DefinitionSet,
        itemId: Int,
    ): Boolean {
        if (LootKeys.isKey(itemId) || DeathRules.isEmblem(itemId)) return false
        if (PvpDeathBreakables.converts(itemId)) return false
        if (RunePouch.isPouch(itemId) || TrouverRegistry.isLockedAnyState(itemId) || PvpDeathBreakables.breakableFor(itemId) != null) return true
        if (PvpDeathBreakables.forBroken(itemId) != null) return true
        val def = RepairPrices.itemDef(definitions, itemId) ?: return false
        return !def.tradeable && itemId != Items.COINS_995
    }

    /**
     * Whether [itemId] is valued at its repair price (the keep-1 ranking and the risk value): an untradeable this rule breaks,
     * mangles or destroys for coins - equipment, locked items, the rune pouch. A non-combat or stackable untradeable is kept
     * anyway, so it never takes the Protect Item slot.
     */
    fun valuedAtRepairPrice(
        definitions: DefinitionSet,
        itemId: Int,
    ): Boolean = handles(definitions, itemId) && lockable(itemId)

    fun fateOf(
        definitions: DefinitionSet,
        item: Item,
        deepWilderness: Boolean,
    ): UntradeableOutcomeFate {
        if (item.id in ALWAYS_KEPT_PVP) return UntradeableOutcomeFate(UntradeableFate.UNCHANGED, 0L)
        // A pet stays with its owner (owner confirmed 2026-10-04: "pet kept" on a PvP death; OSRS 2026: pets respawn with you).
        if (gg.rsmod.plugins.content.skills.summoning.Pets.isPetItem(item.id)) return UntradeableOutcomeFate(UntradeableFate.UNCHANGED, 0L)
        // N423: a beast of burden trinket is never lost (design v6: kept on every death).
        if (gg.rsmod.plugins.content.skills.summoning.BurdenTrinkets.isTrinket(item.id)) return UntradeableOutcomeFate(UntradeableFate.UNCHANGED, 0L)
        // A damaged item already paid its killer once (a broken item left over from the old rules): lost, but for nothing.
        if (!lockable(item.id)) return UntradeableOutcomeFate(UntradeableFate.LOST_FOR_COINS, if (isDamaged(item)) 0L else lostCoins(definitions, item))
        val locked = gg.rsmod.plugins.content.mechanics.trouver.TrouverLockables.ALL.any { item.id == it.lockedItemId || item.id == it.brokenItemId || item.id == it.mangledItemId }
        if (isDamaged(item)) return UntradeableOutcomeFate(UntradeableFate.UNCHANGED, 0L)
        if (RunePouch.isPouch(item.id)) {
            return if (locked || !deepWilderness) {
                UntradeableOutcomeFate(UntradeableFate.POUCH_EMPTIED, 0L)
            } else {
                UntradeableOutcomeFate(UntradeableFate.DESTROYED, RepairPrices.destroyCoins(definitions, item.id))
            }
        }
        // N247: OSRS "(broken)" table - fire capes and defenders break above level 20 too ("Broken above 20 Wilderness: Yes").
        val breaksDeep = !locked && PvpDeathBreakables.breakableFor(item.id)?.breaksAboveTwenty == true
        return when {
            !deepWilderness || breaksDeep -> UntradeableOutcomeFate(UntradeableFate.BROKEN, RepairPrices.repairPrice(definitions, item.id))
            locked -> UntradeableOutcomeFate(UntradeableFate.MANGLED, RepairPrices.MANGLED_REPAIR)
            else -> UntradeableOutcomeFate(UntradeableFate.DESTROYED, RepairPrices.destroyCoins(definitions, item.id))
        }
    }

    /** What Perdu asks to give back a lost non-lockable untradeable (owner 2026-10-24, B): its store value, at least 1 coin. */
    fun reclaimPrice(
        definitions: DefinitionSet,
        itemId: Int,
    ): Long = maxOf(1L, RepairPrices.itemDef(definitions, itemId)?.cost?.toLong() ?: 1L)

    /** The killer's coins for a lost non-lockable untradeable: its High Alchemy value (60 % of its value) per item. */
    fun lostCoins(
        definitions: DefinitionSet,
        item: Item,
    ): Long = (FIXED_KILLER_COINS[item.id] ?: ((RepairPrices.itemDef(definitions, item.id)?.cost?.toLong() ?: 0L) * 60L / 100L)) * item.amount

    data class UntradeableOutcomeFate(val fate: UntradeableFate, val killerCoins: Long)

    /**
     * Takes every lost untradeable out of a PvP death's lost list (so [DeathExecutor] never hands it to the killer) and
     * decides its fate. A PvM death returns the result unchanged: its untradeables go to the gravestone.
     */
    fun splitPvp(
        definitions: DefinitionSet,
        result: DeathResolutionResult,
        deepWilderness: Boolean,
    ): Pair<DeathResolutionResult, List<UntradeableOutcome>> {
        if (result.context != DeathContext.WILDERNESS_PVP) return result to emptyList()
        val (untradeable, rest) = result.itemRisk.lost.partition { handles(definitions, it.item.id) }
        if (untradeable.isEmpty()) return result to emptyList()
        val outcomes =
            untradeable.map {
                val fate = fateOf(definitions, it.item, deepWilderness)
                UntradeableOutcome(it, fate.fate, fate.killerCoins)
            }
        return result.copy(itemRisk = result.itemRisk.copy(lost = rest)) to outcomes
    }

    /** The items the killer receives for [outcomes] (coins, and the runes of a rune pouch), without touching the victim. */
    fun killerLoot(outcomes: List<UntradeableOutcome>): List<Item> {
        val loot = mutableListOf<Item>()
        for (outcome in outcomes) {
            if (outcome.killerCoins > 0) loot += Item(Items.COINS_995, outcome.killerCoins.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            if (RunePouch.isPouch(outcome.slotItem.item.id) && outcome.fate != UntradeableFate.UNCHANGED) {
                loot += RunePouch.contents(outcome.slotItem.item)
            }
        }
        return loot
    }

    /**
     * Applies [outcomes] to the victim's containers and returns the killer's items ([killerLoot]). A worn broken, mangled or
     * emptied item is taken off first (to the inventory, else the bank, else a private ground item) so its bonuses stop.
     */
    fun execute(
        victim: Player,
        outcomes: List<UntradeableOutcome>,
    ): List<Item> {
        var equipmentChanged = false
        val applied = mutableListOf<UntradeableOutcome>()
        var damaged = false
        var destroyed = false
        for (outcome in outcomes) {
            val slotItem = outcome.slotItem
            val container =
                when (slotItem.source) {
                    DeathContainerSource.INVENTORY -> victim.inventory
                    DeathContainerSource.EQUIPMENT -> victim.equipment
                }
            val current = container[slotItem.slot] ?: continue
            if (current.id != slotItem.item.id) continue
            applied += outcome
            val replacement: Item? =
                when (outcome.fate) {
                    UntradeableFate.UNCHANGED -> continue
                    UntradeableFate.DESTROYED, UntradeableFate.LOST_FOR_COINS -> null
                    UntradeableFate.POUCH_EMPTIED -> RunePouch.withContents(current, emptyList())
                    UntradeableFate.BROKEN -> damagedCopy(slotItem.item, current, mangled = false)
                    UntradeableFate.MANGLED -> damagedCopy(slotItem.item, current, mangled = true)
                }
            if (outcome.fate == UntradeableFate.BROKEN || outcome.fate == UntradeableFate.MANGLED) damaged = true
            if (outcome.fate == UntradeableFate.DESTROYED) {
                // Perdu's Lost Property keeps what was destroyed, at its repair price (PerduReclaim).
                PerduReclaim.record(victim, current, RepairPrices.repairPrice(victim.world.definitions, current.id) * current.amount)
                destroyed = true
            }
            // Owner 2026-10-24 (B, "alle 1078 items terugkopen bij perdu, alleen als je ermee dood gaat"): every other lost untradeable
            // too, at its store value - the Lost Property items (Colossal blade, god books ...) Perdu sells back on his own list.
            if (outcome.fate == UntradeableFate.LOST_FOR_COINS && !PerduLostProperty.isLostPropertyItem(current.id)) {
                PerduReclaim.record(victim, current, reclaimPrice(victim.world.definitions, current.id) * current.amount)
                destroyed = true
            }
            if (slotItem.source == DeathContainerSource.INVENTORY || replacement == null) {
                container[slotItem.slot] = replacement
                if (slotItem.source == DeathContainerSource.EQUIPMENT) equipmentChanged = true
                continue
            }
            if (outcome.fate == UntradeableFate.POUCH_EMPTIED) {
                container[slotItem.slot] = replacement
                continue
            }
            container[slotItem.slot] = null
            equipmentChanged = true
            val placed =
                victim.inventory.addPreservingAttr(replacement, assureFullInsertion = true).hasSucceeded() ||
                    victim.bank.addPreservingAttr(replacement, assureFullInsertion = true).hasSucceeded()
            if (!placed) victim.world.spawn(GroundItem(replacement, victim.tile, victim))
        }
        if (equipmentChanged) victim.refreshBonuses()
        if (damaged) victim.message("Some of your untradeable items were damaged. Perdu at the Grand Exchange can repair them.")
        if (destroyed) victim.message("Some of your untradeable items were lost. Perdu at the Grand Exchange can return them for a price.")
        return killerLoot(applied)
    }

    /**
     * The damaged form: the imported broken/mangled id where one exists ([PvpDeathBreakables] broken ids, Trouver
     * [gg.rsmod.plugins.content.mechanics.trouver.TrouverLockable.brokenItemId]/[mangledItemId]), otherwise the same id with
     * [ItemAttribute.BROKEN]. [resolved] carries the death's attributes (e.g. a quiver already stripped of its ammo).
     */
    private fun damagedCopy(
        resolved: Item,
        current: Item,
        mangled: Boolean,
    ): Item {
        // Always the resolved item: it is the container's own item, or the copy QuiverDeathRules stripped of the ammo the
        // killer receives - never the live slot, which would keep that ammo a second time.
        val source = resolved
        val lockable = TrouverRegistry.entryForLocked(current.id)
        val importedId =
            if (lockable != null) {
                if (mangled) lockable.mangledItemId else lockable.brokenItemId
            } else {
                PvpDeathBreakables.breakableFor(current.id)?.brokenId
            }
        if (importedId != null) return Item(importedId, current.amount).copyAttr(source)
        return Item(current.id, current.amount).copyAttr(source).also {
            it.attr[ItemAttribute.BROKEN] = if (mangled) STATE_MANGLED else STATE_BROKEN
        }
    }

    /** What Perdu charges to repair [item] (0 when it is not damaged). */
    fun repairCost(
        definitions: DefinitionSet,
        item: Item,
    ): Long {
        val mangled = state(item) == STATE_MANGLED || TrouverRegistry.entryForDamaged(item.id)?.mangledItemId == item.id
        return when {
            mangled -> RepairPrices.MANGLED_REPAIR
            isDamaged(item) -> RepairPrices.repairPrice(definitions, item.id)
            else -> 0L
        } * item.amount
    }

    /** The whole item [item] repairs into (same attributes, broken state removed). */
    fun repaired(item: Item): Item {
        val id =
            PvpDeathBreakables.forBroken(item.id)?.itemId
                ?: TrouverRegistry.entryForDamaged(item.id)?.lockedItemId
                ?: item.id
        return Item(id, item.amount).copyAttr(item).also { it.attr.remove(ItemAttribute.BROKEN) }
    }
}
