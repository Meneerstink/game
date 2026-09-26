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
import gg.rsmod.plugins.content.mechanics.pvp.LootingBag
import gg.rsmod.plugins.content.mechanics.pvp.emblem.DeadmanEmblem
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

    /** Already broken/mangled, stackable (tokens) or without combat use: stays with the victim as it is, nothing for the killer. */
    UNCHANGED,
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
 * The item-specific conversions ([PvpDeathBreakables.converts]: blowpipes, powered staves, ornament kits ...), loot keys,
 * looting bags and Deadman emblems keep their own rules and never come here. On a PvM death nothing breaks: every lost item,
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

    /** Untradeables this rule handles on a PvP death. */
    fun handles(
        definitions: DefinitionSet,
        itemId: Int,
    ): Boolean {
        if (LootKeys.isKey(itemId) || LootingBag.isBag(itemId) || DeadmanEmblem.isEmblem(itemId)) return false
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
    ): Boolean {
        if (!handles(definitions, itemId)) return false
        if (RunePouch.isPouch(itemId) || TrouverRegistry.isLockedAnyState(itemId) || PvpDeathBreakables.breakableFor(itemId) != null) return true
        val def = RepairPrices.itemDef(definitions, itemId) ?: return false
        return !def.stackable && def.equipSlot >= 0
    }

    fun fateOf(
        definitions: DefinitionSet,
        item: Item,
        deepWilderness: Boolean,
    ): UntradeableOutcomeFate {
        val locked = TrouverRegistry.isLockedAnyState(item.id)
        if (isDamaged(item)) return UntradeableOutcomeFate(UntradeableFate.UNCHANGED, 0L)
        if (RunePouch.isPouch(item.id)) {
            return if (locked || !deepWilderness) {
                UntradeableOutcomeFate(UntradeableFate.POUCH_EMPTIED, 0L)
            } else {
                UntradeableOutcomeFate(UntradeableFate.DESTROYED, RepairPrices.destroyCoins(definitions, item.id))
            }
        }
        if (RepairPrices.itemDef(definitions, item.id)?.stackable == true) return UntradeableOutcomeFate(UntradeableFate.UNCHANGED, 0L)
        // OSRS Wiki "Items Kept on Death": "Any untradeable item without any combat use is kept on death" - only equipment breaks
        // (and pays the killer), so a pile of free non-combat untradeables can never mint coins.
        if (!locked && PvpDeathBreakables.breakableFor(item.id) == null && (RepairPrices.itemDef(definitions, item.id)?.equipSlot ?: -1) < 0) {
            return UntradeableOutcomeFate(UntradeableFate.UNCHANGED, 0L)
        }
        return when {
            !deepWilderness -> UntradeableOutcomeFate(UntradeableFate.BROKEN, RepairPrices.repairPrice(definitions, item.id))
            locked -> UntradeableOutcomeFate(UntradeableFate.MANGLED, RepairPrices.MANGLED_REPAIR)
            else -> UntradeableOutcomeFate(UntradeableFate.DESTROYED, RepairPrices.destroyCoins(definitions, item.id))
        }
    }

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
                    UntradeableFate.DESTROYED -> null
                    UntradeableFate.POUCH_EMPTIED -> RunePouch.withContents(current, emptyList())
                    UntradeableFate.BROKEN -> damagedCopy(slotItem.item, current, mangled = false)
                    UntradeableFate.MANGLED -> damagedCopy(slotItem.item, current, mangled = true)
                }
            if (outcome.fate == UntradeableFate.BROKEN || outcome.fate == UntradeableFate.MANGLED) damaged = true
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
        val source = if (resolved.hasAnyAttr() || !current.hasAnyAttr()) resolved else current
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
