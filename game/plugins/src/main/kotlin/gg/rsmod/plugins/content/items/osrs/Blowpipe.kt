package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.cfg.Items

/**
 * OSRS-IMPORT Toxic blowpipe charge model (OSRS Wiki "Toxic blowpipe", "Darts", "Tanzanite fang", fetched 2026-09-14).
 *
 * The charged blowpipe stores one dart type and its count ([ItemAttribute.ATTACHED_ITEM_ID] /
 * [ItemAttribute.ATTACHED_ITEM_COUNT]) and its Zulrah's scales ([ItemAttribute.CHARGES]), at most 16,383 of each. Item
 * attributes are copied with the item and saved with the player, so charges survive banking, logout and death recovery.
 *
 * - Ammunition: unpoisoned darts only (wiki item page; Kronos and Zenyte OSRS servers load bronze to dragon). The 667
 *   dart items are loaded; inside the blowpipe each dart adds its OSRS ranged strength from [Dart].
 * - Scales: "a 1/3 chance to not use scales when firing".
 * - Darts: consumed each shot unless an Ava's device saves them - attractor 60 %, accumulator 72 %, assembler 80 %
 *   (wiki "Scale to dart ratio" table).
 * - Toxic Siphon: 50 % energy, accuracy x2, damage x1.5, heals half the damage dealt rounded down.
 * - Venom: 25 % chance per attack. Creation: chisel on Tanzanite fang, 78 Fletching, 120 XP. Dismantle (empty or fang):
 *   20,000 Zulrah's scales.
 */
object Blowpipe {
    const val MAX_AMOUNT = 16_383
    const val DISMANTLE_SCALES = 20_000
    const val FLETCHING_LEVEL = 78
    const val FLETCHING_XP = 120.0
    const val SPECIAL_ENERGY = 50
    const val VENOM_CHANCE = 0.25
    const val SIPHON_ACCURACY = 2.0
    const val SIPHON_DAMAGE = 1.5

    /** SOURCE_GAP: no sourced OSRS text for firing without charges; ADAPTED wording. */
    const val NO_SCALES_MESSAGE = "Your blowpipe needs to be charged with Zulrah's scales."
    const val NO_DARTS_MESSAGE = "Your blowpipe has run out of darts."

    /** Kronos (OSRS rev 184 server) refusal for poisoned darts; single source. */
    const val POISONED_DART_MESSAGE = "You can't use that kind of dart - the venom doesn't mix with other poisons."
    const val DIFFERENT_DART_MESSAGE = "The blowpipe currently contains a different sort of dart."
    const val FULL_DARTS_MESSAGE = "The blowpipe can't hold any more darts."
    const val FULL_SCALES_MESSAGE = "The blowpipe can't hold any more scales."

    /** Chance a scale is kept per shot. */
    const val SCALE_SAVE_CHANCE = 1.0 / 3.0

    /** OSRS Wiki "Darts" ranged strength of every loadable (unpoisoned) dart. */
    enum class Dart(val itemId: Int, val rangedStrength: Int) {
        BRONZE(Items.BRONZE_DART, 1),
        IRON(Items.IRON_DART, 2),
        STEEL(Items.STEEL_DART, 3),
        BLACK(Items.BLACK_DART, 6),
        MITHRIL(Items.MITHRIL_DART, 9),
        ADAMANT(Items.ADAMANT_DART, 17),
        RUNE(Items.RUNE_DART, 26),
        AMETHYST(Items.AMETHYST_DART, 28),
        DRAGON(Items.DRAGON_DART, 35),
        ;

        companion object {
            private val byItem = values().associateBy { it.itemId }

            fun forItem(itemId: Int): Dart? = byItem[itemId]
        }
    }

    /** Wiki "Scale to dart ratio": the share of darts an Ava's device saves (cape slot item id -> chance). */
    val DART_SAVE_CHANCE: Map<Int, Double> = mapOf(Items.AVAS_ATTRACTOR to 0.60, Items.AVAS_ACCUMULATOR to 0.72)

    fun isBlowpipe(itemId: Int): Boolean = itemId == Items.TOXIC_BLOWPIPE || itemId == Items.TOXIC_BLOWPIPE_EMPTY

    fun dart(blowpipe: Item): Dart? = if (darts(blowpipe) > 0) blowpipe.attr[ItemAttribute.ATTACHED_ITEM_ID]?.let { Dart.forItem(it) } else null

    fun darts(blowpipe: Item): Int = blowpipe.attr[ItemAttribute.ATTACHED_ITEM_COUNT] ?: 0

    fun scales(blowpipe: Item): Int = blowpipe.attr[ItemAttribute.CHARGES] ?: 0

    fun canFire(blowpipe: Item): Boolean = blowpipe.id == Items.TOXIC_BLOWPIPE && dart(blowpipe) != null && scales(blowpipe) > 0

    /** The id the blowpipe must carry for its contents: charged while it holds any darts or scales. */
    fun idFor(darts: Int, scales: Int): Int = if (darts > 0 || scales > 0) Items.TOXIC_BLOWPIPE else Items.TOXIC_BLOWPIPE_EMPTY

    enum class LoadOutcome { LOADED, DIFFERENT_DART, FULL, NOT_A_DART }

    data class Load(val outcome: LoadOutcome, val added: Int, val result: Item)

    /** Loads up to [available] darts of [dartItemId]; the returned [Load.result] replaces the blowpipe in its slot. */
    fun loadDarts(blowpipe: Item, dartItemId: Int, available: Int): Load {
        val dart = Dart.forItem(dartItemId) ?: return Load(LoadOutcome.NOT_A_DART, 0, blowpipe)
        val loaded = dart(blowpipe)
        if (loaded != null && loaded != dart) return Load(LoadOutcome.DIFFERENT_DART, 0, blowpipe)
        val current = darts(blowpipe)
        val added = minOf(MAX_AMOUNT - current, available)
        if (added <= 0) return Load(LoadOutcome.FULL, 0, blowpipe)
        return Load(LoadOutcome.LOADED, added, rebuild(blowpipe, dart, current + added, scales(blowpipe)))
    }

    /** Adds up to [available] Zulrah's scales. */
    fun chargeScales(blowpipe: Item, available: Int): Load {
        val current = scales(blowpipe)
        val added = minOf(MAX_AMOUNT - current, available)
        if (added <= 0) return Load(LoadOutcome.FULL, 0, blowpipe)
        return Load(LoadOutcome.LOADED, added, rebuild(blowpipe, dart(blowpipe), darts(blowpipe), current + added))
    }

    /** Removes every dart; returns the blowpipe that replaces it (scales kept). */
    fun unloadDarts(blowpipe: Item): Item = rebuild(blowpipe, null, 0, scales(blowpipe))

    /** Removes darts and scales ("all scales and darts will fall out"): the empty blowpipe. */
    fun uncharge(blowpipe: Item): Item = rebuild(blowpipe, null, 0, 0)

    data class ShotCost(val scaleUsed: Boolean, val dartUsed: Boolean, val result: Item)

    /**
     * Spends the charges of one shot. [scaleRoll] and [dartRoll] are uniform [0, 1) rolls; [capeId] is the worn cape.
     */
    fun spendShot(blowpipe: Item, scaleRoll: Double, dartRoll: Double, capeId: Int?): ShotCost {
        val dart = dart(blowpipe) ?: return ShotCost(false, false, blowpipe)
        val scaleUsed = scaleRoll >= SCALE_SAVE_CHANCE
        val saveChance = capeId?.let { DART_SAVE_CHANCE[it] } ?: 0.0
        val dartUsed = dartRoll >= saveChance
        val darts = darts(blowpipe) - if (dartUsed) 1 else 0
        val scales = (scales(blowpipe) - if (scaleUsed) 1 else 0).coerceAtLeast(0)
        return ShotCost(scaleUsed, dartUsed, rebuild(blowpipe, dart.takeIf { darts > 0 }, darts, scales))
    }

    /** The ranged strength the loaded dart adds on top of the blowpipe's own +20. */
    fun dartStrength(blowpipe: Item?): Int = blowpipe?.takeIf { it.id == Items.TOXIC_BLOWPIPE }?.let { dart(it)?.rangedStrength } ?: 0

    /** Toxic Siphon healing: half the damage dealt, rounded down. */
    fun siphonHeal(damage: Int): Int = damage / 2

    /**
     * Check read-out. SOURCE_CONFLICT on the exact text (Kronos: "Darts: X Scales: P%, N scales", Zenyte: "Darts: X
     * Scales: P%"); both report the dart name and count and the scale percentage of 16,383, which this keeps.
     */
    fun checkMessage(blowpipe: Item, dartName: String?): String {
        val count = darts(blowpipe)
        val dartText = if (count <= 0 || dartName == null) "None" else "$dartName x $count"
        val scales = scales(blowpipe)
        val percent = String.format(java.util.Locale.ROOT, "%.1f", scales * 100.0 / MAX_AMOUNT)
        return "Darts: <col=007f00>$dartText</col>. Scales: <col=007f00>$scales ($percent%)</col>."
    }

    private fun rebuild(blowpipe: Item, dart: Dart?, darts: Int, scales: Int): Item {
        val result = Item(idFor(darts, scales), blowpipe.amount).copyAttr(blowpipe)
        if (darts > 0 && dart != null) {
            result.attr[ItemAttribute.ATTACHED_ITEM_ID] = dart.itemId
            result.attr[ItemAttribute.ATTACHED_ITEM_COUNT] = darts
        } else {
            result.attr.remove(ItemAttribute.ATTACHED_ITEM_ID)
            result.attr.remove(ItemAttribute.ATTACHED_ITEM_COUNT)
        }
        if (scales > 0) result.attr[ItemAttribute.CHARGES] = scales else result.attr.remove(ItemAttribute.CHARGES)
        return result
    }
}
